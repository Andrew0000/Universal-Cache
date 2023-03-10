package crocodile8.universal_cache.keep

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemoryCache<P : Any, T : Any>(
    private val cacheSize: Int = 1,
) : Cache<P, T> {

    init {
        if (cacheSize <= 0) {
            throw IllegalArgumentException("Cache size must be > 0 or you don't need cache")
        }
    }

    private val cacheMap = mutableMapOf<CacheKey<P>, CachedData<T>>()
    private val cacheList = mutableSetOf<CacheKey<P>>()
    private val cacheLock = Mutex()

    override suspend fun get(params: P, additionalKey: Any?): CachedData<T>? {
        val key = CacheKey(params, additionalKey)
        cacheLock.withLock {
            return cacheMap[key]
        }
    }

    override suspend fun put(value: T, params: P, additionalKey: Any?, time: Long) {
        val key = CacheKey(params, additionalKey)
        cacheLock.withLock {
            cacheMap[key] = CachedData(value, time)
            cacheList += key
            while (cacheSize > 0 && cacheList.size > cacheSize) {
                val oldestKey = cacheList.first()
                cacheMap.remove(oldestKey)
                cacheList.remove(oldestKey)
            }
        }
    }

    override suspend fun clear() {
        cacheLock.withLock {
            cacheMap.clear()
            cacheList.clear()
        }
    }
}
