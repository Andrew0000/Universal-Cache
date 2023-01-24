package crocodile8.universal_cache

import crocodile8.universal_cache.keep.Cache
import crocodile8.universal_cache.keep.MemoryCache
import crocodile8.universal_cache.request.Requester
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachedSource<P : Any, T : Any>(
    source: suspend (params: P) -> T,
    private val cache: Cache<P, T> = MemoryCache(1),
) {
    private val requester = Requester(source)
    private val cacheLock = Mutex()

    suspend fun get(
        params: P,
        fromCache: FromCache,
        cacheRequirement: CacheRequirement,
        additionalKey: Any? = null,
    ): Flow<T> =
        getRaw(params, fromCache, cacheRequirement, additionalKey)
            .map { it.value }

    suspend fun getRaw(
        params: P,
        fromCache: FromCache,
        cacheRequirement: CacheRequirement,
        additionalKey: Any? = null,
    ): Flow<CachedSourceResult<T>> {
        val lazyFlow = suspend {
            when (fromCache) {
                FromCache.NEVER -> {
                    getFromSource(params, additionalKey, shareOngoing = cacheRequirement.shareOngoingRequest)
                }
                FromCache.IF_FAILED -> {
                    getFromSource(params, additionalKey, shareOngoing = cacheRequirement.shareOngoingRequest)
                        .catch {
                            val cached = getFromCache(params, additionalKey, cacheRequirement)
                            if (cached != null) {
                                emit(CachedSourceResult(cached, fromCache = true))
                            } else {
                                throw it
                            }
                        }
                }
                FromCache.IF_HAVE -> {
                    val cached = getFromCache(params, additionalKey, cacheRequirement)
                    Logger.log { "get IF_HAVE: $params / cached: $cached" }
                    if (cached != null) {
                        flow { emit(CachedSourceResult(cached, fromCache = true)) }
                    } else {
                        getFromSource(params, additionalKey, shareOngoing = cacheRequirement.shareOngoingRequest)
                    }
                }
                FromCache.CACHED_THEN_LOAD -> {
                    val cached = getFromCache(params, additionalKey, cacheRequirement)
                    Logger.log { "get FROM_CACHE_THEN_LOAD: $params / cached: $cached" }
                    flow {
                        if (cached != null) {
                            emitAll(flowOf(CachedSourceResult(cached, fromCache = true)))
                        }
                        emitAll(
                            getFromSource(params, additionalKey, shareOngoing = cacheRequirement.shareOngoingRequest)
                        )
                    }
                }
            }
        }
        return flow {
            emitAll(lazyFlow())
        }
    }

    private suspend fun getFromSource(
        params: P,
        additionalKey: Any?,
        shareOngoing: Boolean
    ): Flow<CachedSourceResult<T>> =
        when {
            shareOngoing -> requester.requestShared(params)
            else -> requester.request(params)
        }
            .onEach {
                Logger.log { "getFromSource: $params -> $it" }
                putToCache(it, params, additionalKey)
            }
            .map { CachedSourceResult(it, fromCache = false) }

    private suspend fun getFromCache(params: P, additionalKey: Any?, cacheRequirement: CacheRequirement): T? {
        //TODO check for cacheRequirement
        cacheLock.withLock {
            return cache.get(params, additionalKey)
        }
    }

    private suspend fun putToCache(value: T, params: P, additionalKey: Any?) {
        cacheLock.withLock {
            cache.put(value, params, additionalKey)
        }
    }
}