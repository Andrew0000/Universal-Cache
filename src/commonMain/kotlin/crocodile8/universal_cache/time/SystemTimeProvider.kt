package crocodile8.universal_cache.time

object SystemTimeProvider : TimeProvider {

    override fun get(): Long = universalCacheCurrentTimeMillis()
}
