package crocodile8.universal_cache

import crocodile8.universal_cache.keep.Cache
import crocodile8.universal_cache.keep.MemoryCache

@Suppress("Unused")
fun <T: Any> CachedSourceNoParams(
    source: suspend () -> T,
    cache: Cache<Int, T> = MemoryCache(1),
) = CachedSource(
    { source() }, cache
)

suspend fun <T: Any> CachedSource<Int, T>.get(
    fromCache: FromCache,
    shareOngoingRequest: Boolean = true,
    maxAge: Long? = null,
    additionalKey: Any? = null,
) = get(
    0,
    fromCache,
    shareOngoingRequest,
    maxAge,
    additionalKey,
)