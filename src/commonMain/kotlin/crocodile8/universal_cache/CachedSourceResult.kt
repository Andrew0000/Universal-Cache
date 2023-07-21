package crocodile8.universal_cache

data class CachedSourceResult<T : Any>(
    val value: T,
    val fromCache: Boolean,
    val originTimeStamp: Long?,
)
