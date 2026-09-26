package xyz.deseteral.jaruga.ranger.lookout.external

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import xyz.deseteral.jaruga.ranger.lookout.domain.CacheError
import xyz.deseteral.jaruga.ranger.lookout.domain.PersistentCache
import java.io.IOException

private val logger = KotlinLogging.logger {}

class FilePersistentCache<T>(
    private val path: Path,
    private val fileSystem: FileSystem,
    private val stringFormat: StringFormat,
    private val serializer: KSerializer<T>,
) : PersistentCache<T> {
    override fun read(defaultProvider: () -> T): Result<T, CacheError> {
        if (!fileSystem.exists(path)) {
            return write(defaultProvider())
        }

        return try {
            val text = fileSystem.source(path).buffered().use { source ->
                source.readString(Charsets.UTF_8)
            }
            Ok(stringFormat.decodeFromString(serializer, text))
        } catch (ex: IOException) {
            logger.error(ex) { "Could not read cache file $path." }
            Err(CacheError.Read(path))
        } catch (ex: SerializationException) {
            logger.error(ex) { "Could not decode cache file $path." }
            Err(CacheError.Read(path))
        }
    }

    override fun write(value: T): Result<T, CacheError> {
        return try {
            val text = stringFormat.encodeToString(serializer, value)
            fileSystem.sink(path).buffered().use { sink ->
                sink.writeString(text)
            }
            Ok(value)
        } catch (ex: IOException) {
            logger.error(ex) { "Could not write cache file $path." }
            Err(CacheError.Write(path))
        } catch (ex: SerializationException) {
            logger.error(ex) { "Could not encode cache file $path." }
            Err(CacheError.Write(path))
        }
    }

    companion object {
        inline fun <reified T> json(
            name: String,
            dataDirectoryPath: Path,
            fileSystem: FileSystem,
            format: Boolean = false,
        ) = FilePersistentCache(
            path = Path(dataDirectoryPath, "$name.json"),
            fileSystem = fileSystem,
            stringFormat = Json { prettyPrint = format },
            serializer = serializer<T>(),
        )
    }
}
