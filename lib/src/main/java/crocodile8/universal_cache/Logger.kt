package crocodile8.universal_cache

internal object Logger {

    internal fun log(text: () -> String) {
        if (UniversalCache.printLogs) {
            println("[UCache] ${text()}     : ${System.currentTimeMillis()}")
        }
    }
}