package crocodile8.universal_cache.keep

data class CachedData<T : Any>(
    val value: T,
    val time: Long?,
)
