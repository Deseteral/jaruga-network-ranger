package xyz.deseteral.jaruga.ranger.lookout.domain

import com.github.michaelbull.result.Result
import kotlinx.io.files.Path

interface PersistentCache<T> {
    fun read(defaultProvider: () -> T): Result<T, CacheError>
    fun write(value: T): Result<T, CacheError>
}

sealed interface CacheError {
    val path: Path

    data class Read(override val path: Path) : CacheError
    data class Write(override val path: Path) : CacheError
}
