package crocodile8.universal_cache.test_utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//TODO think about "kotlinx-atomicfu" if there is a way only to testImplement it

class AtomicInteger(
    private var value: Int = 0,
) {

    private val lock = Mutex()

    suspend fun get(): Int =
        lock.withLock {
            value
        }

    suspend fun incrementAndGet(): Int =
        lock.withLock {
            ++value
        }
}