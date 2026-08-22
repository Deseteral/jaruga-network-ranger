package xyz.deseteral.jaruga.ranger.lookout.external

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import xyz.deseteral.jaruga.ranger.lookout.domain.PersistentCache

class FilePersistentCache<T>(
    private val path: Path,
    private val fileSystem: FileSystem,
    private val stringFormat: StringFormat,
    private val serializer: KSerializer<T>,
) : PersistentCache<T> {
    override fun read(defaultProvider: () -> T): T {
        return if (fileSystem.exists(path)) {
            val text = fileSystem.source(path).buffered().use { source ->
                source.readString(Charsets.UTF_8)
            }
            stringFormat.decodeFromString(serializer, text)
        } else defaultProvider().also { write(it) }
    }

    override fun write(value: T) {
        val text = stringFormat.encodeToString(serializer, value)
        fileSystem.sink(path).buffered().use { sink ->
            sink.writeString(text)
        }
    }

    companion object {
        inline fun <reified T> json(name: String, dataDirectoryPath: Path, fileSystem: FileSystem, format: Boolean = false) = FilePersistentCache(
            path = Path(dataDirectoryPath, "$name.json"),
            fileSystem = fileSystem,
            stringFormat = Json { prettyPrint = format },
            serializer = serializer<T>(),
        )
    }
}
