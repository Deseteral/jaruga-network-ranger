import {readFileSync} from "node:fs";
import {createServer, type IncomingMessage, type ServerResponse} from "node:http";

type FaultMode = "ok" | "error" | "timeout" | "close";
type Source = "homeAssistant" | "teslaMate" | "discord";

interface HassEntity {
  entity_id: string;
  state: string;
}

interface DiscordMessage {
  id: string;
  body: unknown;
}

type LookoutCheckState = "SUCCESS" | "FAILURE";

interface LookoutCheckResult {
  description: string | null;
  state: LookoutCheckState;
  severity: "MAJOR" | "MINOR";
  timestamp: string;
}

interface LookoutFirewatch {
  checks: Record<string, (LookoutCheckResult | null)[]>;
  degraded: boolean;
}

interface MockState {
  hassEntities: HassEntity[];
  hassToken: string;
  tmVersion: string;
  faults: Record<Source, FaultMode>;
}

const state: MockState = {
  hassEntities: [
    {entity_id: "update.home_assistant_core_update", state: "off"},
    {entity_id: "update.hacs_update", state: "off"},
  ],
  hassToken: "hass-dev-token",
  tmVersion: "1.34.2",
  faults: {homeAssistant: "ok", teslaMate: "ok", discord: "ok"},
};

const discordMessages: DiscordMessage[] = [];

const UI_HTML = readFileSync(new URL("./mock-server.html", import.meta.url), "utf8");

const HTML_TEMPLATES = new Map<string, string>();
for (const match of UI_HTML.matchAll(/<template\s+id="([^"]+)">([\s\S]*?)<\/template>/g)) {
  const name = match[1];
  const template = match[2];
  if (!!name && !!template) HTML_TEMPLATES.set(name, template);
}

function renderTemplate(name: string, values: Record<string, string>): string {
  const template = HTML_TEMPLATES.get(name);
  if (!template) throw new Error(`Unknown HTML template: ${name}`);
  return template.replaceAll(/\{\{\s*([A-Za-z][A-Za-z0-9]*)\s*\}\}/g, (_, key: string) => {
    const value = values[key];
    if (value === undefined) throw new Error(`Missing value "${key}" for HTML template "${name}".`);
    return value;
  });
}

async function readForm(req: IncomingMessage): Promise<Record<string, string>> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Object.fromEntries(new URLSearchParams(Buffer.concat(chunks).toString()));
}

async function readJson(req: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString());
}

function send(res: ServerResponse, status: number, body: string, contentType: string): void {
  res.writeHead(status, {"Content-Type": contentType});
  res.end(body);
}

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  send(res, status, JSON.stringify(body), "application/json");
}

function sendError(res: ServerResponse, status: number, message: string): void {
  sendJson(res, status, {message});
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function respondMutation(res: ServerResponse): void {
  send(res, 200, renderStateFragment(), "text/html; charset=utf-8");
}

function applyFault(res: ServerResponse, source: Source): boolean {
  switch (state.faults[source]) {
    case "error":
      sendError(res, 500, `Injected ${source} failure.`);
      return false;
    case "timeout":
      return false;
    case "close":
      res.socket?.destroy();
      return false;
    default:
      return true;
  }
}

// --- Mocked source handlers -------------------------------------------------

function handleHassStates(req: IncomingMessage, res: ServerResponse): void {
  const auth = req.headers.authorization;
  if (auth !== `Bearer ${state.hassToken}`) {
    sendError(res, 401, "Unauthorized");
    return;
  }
  sendJson(res, 200, state.hassEntities);
}

function handleTeslaMateSettings(res: ServerResponse): void {
  const html = `<tr><th>Version</th><td>${escapeHtml(state.tmVersion)}</td></tr>`;
  send(res, 200, html, "text/html; charset=utf-8");
}

async function handleDiscordCreate(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const message = {
    id: String(discordMessages.length + 1),
    body: await readJson(req),
  };
  discordMessages.push(message);
  sendJson(res, 200, {id: message.id});
}

async function handleDiscordUpdate(
  req: IncomingMessage,
  res: ServerResponse,
  rawMessageId: string,
): Promise<void> {
  const body = await readJson(req);
  const message = discordMessages.find(({id}) => id === decodeURIComponent(rawMessageId));
  if (!message) {
    sendError(res, 404, "Unknown Discord message.");
    return;
  }
  message.body = body;
  sendJson(res, 200, {id: message.id});
}

// --- UI handlers -------------------------------------------------------------

async function handleUiHassEntities(
  req: IncomingMessage,
  res: ServerResponse,
  action?: string,
): Promise<void> {
  if (!action) {
    const body = await readForm(req);
    const entityId = body.entity_id?.trim() ?? "";
    const entityState = body.state?.trim() ?? "";
    const existing = state.hassEntities.find((e) => e.entity_id === entityId);
    if (existing) {
      existing.state = entityState;
    } else {
      state.hassEntities.push({entity_id: entityId, state: entityState});
    }
  } else {
    const entityId = decodeURIComponent(action);
    state.hassEntities = state.hassEntities.filter((e) => e.entity_id !== entityId);
  }
  respondMutation(res);
}

async function handleUiTeslaMateVersion(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const body = await readForm(req);
  if (!body.version) return;
  state.tmVersion = body.version;
  respondMutation(res);
}

async function handleUiFault(req: IncomingMessage, res: ServerResponse, rawSource: string): Promise<void> {
  const source = rawSource as Source;
  const body = await readForm(req);
  state.faults[source] = body.mode as FaultMode;
  respondMutation(res);
}

async function handleUiLookoutStatus(res: ServerResponse): Promise<void> {
  let timelineRows: string;
  let degraded = "unknown";
  try {
    const firewatch = await fetchLookoutStatus();
    timelineRows = renderTimelineRows(firewatch);
    degraded = firewatch.degraded ? "yes" : "no";
  } catch (ex: unknown) {
    timelineRows = renderMessageRow(`Could not reach lookout: ${ex instanceof Error ? ex.message : String(ex)}`);
  }
  send(res, 200, renderTemplate("lookout-firewatch", {timelineRows, degraded}), "text/html; charset=utf-8");
}

// --- Web UI ------------------------------------------------------------------

const FAULT_MODES: FaultMode[] = ["ok", "error", "timeout", "close"];

function renderStateFragment(): string {
  const entityRows = state.hassEntities.map((entity) => {
    return renderTemplate("entity-row", {
      entityId: escapeHtml(entity.entity_id),
      encodedEntityId: escapeHtml(encodeURIComponent(entity.entity_id)),
      entityState: escapeHtml(entity.state),
    });
  }).join("");

  const faultRows = (Object.keys(state.faults) as Source[])
    .map((source) => renderTemplate("fault-row", {
      source: escapeHtml(source),
      options: FAULT_MODES.map((mode) => renderTemplate("fault-mode-option", {
        mode,
        selected: state.faults[source] === mode ? " selected" : "",
      })).join(""),
    }))
    .join("");

  const discordMessageRows = discordMessages.toReversed().map((message) => renderTemplate("discord-message-row", {
    messageId: escapeHtml(message.id),
    body: escapeHtml(JSON.stringify(message.body, null, 2) ?? ""),
  })).join("");

  return renderTemplate("state-fragment", {
    entityRows,
    tmVersion: escapeHtml(state.tmVersion),
    faultRows,
    discordMessageRows,
    mockStateJson: escapeHtml(JSON.stringify(state, null, 2) ?? ""),
  });
}

// --- Lookout status ----------------------------------------------------------

const LOOKOUT_URL = "http://localhost:8081";

const CHECK_STATE_SYMBOLS: Record<LookoutCheckState | "unknown", string> = {
  SUCCESS: "▮",
  FAILURE: "▯",
  unknown: "•",
};

async function fetchLookoutStatus(): Promise<LookoutFirewatch> {
  const response = await fetch(new URL("/firewatch", LOOKOUT_URL));
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return (await response.json()) as LookoutFirewatch;
}

function renderMessageRow(message: string): string {
  return renderTemplate("lookout-firewatch-message-row", {message: escapeHtml(message)});
}

function renderTimelineRows(status: LookoutFirewatch): string {
  const checks = Object.entries(status.checks);
  if (checks.length === 0) return renderMessageRow("No checks known to Lookout yet.");
  return checks
    .map(([checkId, timeline]) => renderTemplate("lookout-timeline-row", {
      checkId: escapeHtml(checkId),
      timeline: timeline
        .map((result) => CHECK_STATE_SYMBOLS[result?.state ?? "unknown"])
        .join(""),
    }))
    .join("");
}

// --- Routing -----------------------------------------------------------------

async function route(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const path = new URL(req.url ?? "/", "http://localhost").pathname;

  if (req.method === "GET" && path === "/") {
    send(res, 200, UI_HTML, "text/html; charset=utf-8");
    return;
  }

  if (req.method === "GET" && path === "/ui/state") {
    send(res, 200, renderStateFragment(), "text/html; charset=utf-8");
    return;
  }

  if (req.method === "GET" && path === "/ui/lookout/firewatch") {
    await handleUiLookoutStatus(res);
    return;
  }

  if (req.method === "POST" && path === "/ui/ha/entities") {
    await handleUiHassEntities(req, res);
    return;
  }

  const entityPath = path.match(/^\/ui\/ha\/entities\/([^/]+)$/);
  if (req.method === "DELETE" && entityPath !== null) {
    await handleUiHassEntities(req, res, entityPath[1]);
    return;
  }

  if (req.method === "PUT" && path === "/ui/tm/version") {
    await handleUiTeslaMateVersion(req, res);
    return;
  }

  const faultPath = path.match(/^\/ui\/faults\/([^/]+)$/);
  if (req.method === "PUT" && faultPath !== null) {
    await handleUiFault(req, res, faultPath[1]!);
    return;
  }

  if (req.method === "GET" && path === "/ha/api/states") {
    if (!applyFault(res, "homeAssistant")) return;
    handleHassStates(req, res);
    return;
  }

  if (req.method === "GET" && path === "/tm/settings") {
    if (!applyFault(res, "teslaMate")) return;
    handleTeslaMateSettings(res);
    return;
  }

  const discordMessagePath = path.match(/^\/discord\/messages\/([^/]+)$/);
  if (req.method === "PATCH" && discordMessagePath !== null) {
    if (!applyFault(res, "discord")) return;
    await handleDiscordUpdate(req, res, discordMessagePath[1]!);
    return;
  }

  if (req.method === "POST" && path === "/discord") {
    if (!applyFault(res, "discord")) return;
    await handleDiscordCreate(req, res);
    return;
  }

  sendError(res, 404, `Not found: ${req.method} ${path}`);
}

const server = createServer((req, res) => {
  route(req, res).catch((ex: unknown) => {
    console.error(`Error handling ${req.method} ${req.url}:`, ex);
    if (!res.headersSent) {
      sendError(res, 500, "Internal server error.");
    } else {
      res.destroy();
    }
  });
});

const PORT = 9930;
server.listen(PORT, () =>
  console.log(`Jaruga Ranger mock server listening on http://localhost:${PORT}`));
