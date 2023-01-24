package crocodile8.universal_cache

import crocodile8.universal_cache.keep.Cache
import crocodile8.universal_cache.keep.CachedData
import crocodile8.universal_cache.keep.MemoryCache
import crocodile8.universal_cache.request.Requester
import crocodile8.universal_cache.time.SystemTimeProvider
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachedSource<P : Any, T : Any>(
    source: suspend (params: P) -> T,
    private val cache: Cache<P, T> = MemoryCache(1),
    private val timeProvider: TimeProvider = SystemTimeProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val requester = Requester(source)
    private val cacheLock = Mutex()

    suspend fun clearCache() {
        cache.clear()
    }

    suspend fun get(
        params: P,
        fromCache: FromCache,
        shareOngoingRequest: Boolean = true,
        maxAge: Long? = null,
        additionalKey: Any? = null,
    ): Flow<T> =
        getRaw(params, fromCache, shareOngoingRequest, maxAge, additionalKey)
            .map { it.value }

    suspend fun getRaw(
        params: P,
        fromCache: FromCache,
        shareOngoingRequest: Boolean = true,
        maxAge: Long? = null,
        additionalKey: Any? = null,
    ): Flow<CachedSourceResult<T>> {
        val lazyFlow = suspend {
            when (fromCache) {

                FromCache.NEVER -> {
                    getFromSource(params, additionalKey, shareOngoing = shareOngoingRequest)
                }

                FromCache.IF_FAILED -> {
                    getFromSource(params, additionalKey, shareOngoing = shareOngoingRequest)
                        .catch {
                            val cached = getFromCache(params, additionalKey, maxAge)
                            if (cached != null) {
                                emit(CachedSourceResult(cached.value, fromCache = true, originTimeStamp = cached.time))
                            } else {
                                throw it
                            }
                        }
                }

                FromCache.IF_HAVE -> {
                    val cached = getFromCache(params, additionalKey, maxAge)
                    Logger.log { "get IF_HAVE: $params / cached: $cached" }
                    if (cached != null) {
                        flow { emit(CachedSourceResult(cached.value, fromCache = true, originTimeStamp = cached.time)) }
                    } else {
                        getFromSource(params, additionalKey, shareOngoing = shareOngoingRequest)
                    }
                }

                FromCache.CACHED_THEN_LOAD -> {
                    val cached = getFromCache(params, additionalKey, maxAge)
                    Logger.log { "get FROM_CACHE_THEN_LOAD: $params / cached: $cached" }
                    flow {
                        if (cached != null) {
                            emitAll(flowOf(CachedSourceResult(cached.value, fromCache = true, originTimeStamp = cached.time)))
                        }
                        emitAll(
                            getFromSource(params, additionalKey, shareOngoing = shareOngoingRequest)
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
            shareOngoing -> requester.requestShared(params, dispatcher)
            else -> requester.request(params, dispatcher)
        }
            .map { CachedSourceResult(it, fromCache = false, originTimeStamp = timeProvider.get()) }
            .onEach {
                Logger.log { "getFromSource: $params -> $it" }
                putToCache(it.value, params, additionalKey, time = it.originTimeStamp ?: timeProvider.get())
            }

    private suspend fun getFromCache(params: P, additionalKey: Any?, maxAge: Long?): CachedData<T>? {
        cacheLock.withLock {
            val cachedData = cache.get(params, additionalKey)
            if (cachedData != null && cachedData.isOkByMaxAge(maxAge)) {
                return cachedData
            }
            return null
        }
    }

    private fun <T : Any> CachedData<T>.isOkByMaxAge(maxAge: Long?): Boolean {
        if (maxAge == null) {
            return true
        }
        if (time == null) {
            return false
        }
        val age = timeProvider.get() - time
        return age < maxAge
    }

    private suspend fun putToCache(value: T, params: P, additionalKey: Any?, time: Long) {
        cacheLock.withLock {
            cache.put(value, params, additionalKey, time)
        }
    }
}