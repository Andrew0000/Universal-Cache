package crocodile8.universal_cache.keep

data class CacheKey<P: Any>(
    val requestParams: P,
    val additionalKey: Any?,
)