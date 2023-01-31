package crocodile8.universal_cache.utils

/**
 * Basically the same as [kotlin.jvm.internal.Ref.BooleanRef] but not JVM and Serializable
 */
internal class BooleanRef(var value: Boolean = false)
