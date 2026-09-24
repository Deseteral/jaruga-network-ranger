package xyz.deseteral.jaruga.ranger.campground.domain.datasources

class DataSourceProvider<DataSourceT, ProviderT>(
    private val provide: (dataSource: DataSourceT) -> ProviderT,
) {
    private val cache: MutableMap<DataSourceT, ProviderT> = mutableMapOf()

    fun of(dataSource: DataSourceT): ProviderT =
        cache.getOrPut(dataSource) { provide(dataSource) }
}
