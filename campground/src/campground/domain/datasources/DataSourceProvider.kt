package xyz.deseteral.jaruga.ranger.campground.domain.datasources

import java.util.concurrent.ConcurrentHashMap

class DataSourceProvider<DataSourceT : Any, ProviderT : Any>(
    private val provide: (dataSource: DataSourceT) -> ProviderT,
) {
    // TODO: Replace ConcurrentHashMap with lock from kotlinx.atomicfu.
    private val cache = ConcurrentHashMap<DataSourceT, ProviderT>()

    fun of(dataSource: DataSourceT): ProviderT =
        cache.computeIfAbsent(dataSource) { provide(it) }
}
