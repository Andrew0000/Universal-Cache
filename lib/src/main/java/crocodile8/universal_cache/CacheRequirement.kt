package crocodile8.universal_cache

data class CacheRequirement(
    val maxAge: Long = 0,
    val shareOngoingRequest: Boolean = true,
)