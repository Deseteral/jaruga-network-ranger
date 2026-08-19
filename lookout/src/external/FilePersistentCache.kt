package xyz.deseteral.jaruga.lookout.external

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import xyz.deseteral.jaruga.lookout.domain.PersistentCache

class FilePersistentCache<T>(
    name: String,
    private val fileSystem: FileSystem,
    private val serializer: KSerializer<T>,
) : PersistentCache<T> {
    private val path = Path("$name.json")
    private val json = Json { prettyPrint = true } // TODO: Remove pretty print.

    override fun read(defaultProvider: () -> T): T {
        return if (fileSystem.exists(path)) {
            val text = fileSystem.source(path).buffered().use { source ->
                source.readString(Charsets.UTF_8)
            }
            json.decodeFromString(serializer, text)
        } else defaultProvider().also { write(it) }
    }

    override fun write(value: T) {
        val text = json.encodeToString(serializer, value)
        fileSystem.sink(path).buffered().use { sink ->
            sink.writeString(text)
        }
    }

    companion object {
        inline fun <reified T> json(name: String, fileSystem: FileSystem) =
            FilePersistentCache(name, fileSystem, serializer<T>())
    }
}
