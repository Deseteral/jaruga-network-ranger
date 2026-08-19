package xyz.deseteral.jaruga.lookout.domain

interface PersistentCache<T> {
    fun read(defaultProvider: () -> T): T
    fun write(value: T)
}