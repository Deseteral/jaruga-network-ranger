package xyz.deseteral.jaruga.ranger.lookout.domain

interface PersistentCache<T> {
    fun read(defaultProvider: () -> T): T
    fun write(value: T)
}