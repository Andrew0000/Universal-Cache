package crocodile8.universal_cache.time

interface TimeProvider {

    fun get(): Long
}

expect fun universalCacheCurrentTimeMillis(): Long
