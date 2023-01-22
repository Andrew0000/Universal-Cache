package crocodile8.universal_cache

data class CacheRequirement(
    val maxAge: Long = 0,
    val canUseOngoingRequest: Boolean = true,
)