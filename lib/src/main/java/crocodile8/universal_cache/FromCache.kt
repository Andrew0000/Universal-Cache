package crocodile8.universal_cache

enum class FromCache {

    NEVER,
    IF_FAILED,
    IF_HAVE,
    ONLY,
    CACHED_THEN_LOAD,
}