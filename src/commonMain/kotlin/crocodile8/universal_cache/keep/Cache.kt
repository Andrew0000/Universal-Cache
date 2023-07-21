package crocodile8.universal_cache.keep

interface Cache<P : Any, T : Any> {

    suspend fun get(params: P, additionalKey: Any?): CachedData<T>?

    suspend fun put(value: T, params: P, additionalKey: Any?, time: Long)

    suspend fun clear()
}