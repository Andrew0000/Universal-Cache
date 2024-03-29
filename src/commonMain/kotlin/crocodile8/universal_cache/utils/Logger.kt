package crocodile8.universal_cache.utils

import crocodile8.universal_cache.UniversalCache
import crocodile8.universal_cache.time.universalCacheCurrentTimeMillis

internal object Logger {

    internal fun log(text: () -> String) {
        if (UniversalCache.printLogs) {
            println("[UCache] ${text()}     : ${universalCacheCurrentTimeMillis()}")
        }
    }
}