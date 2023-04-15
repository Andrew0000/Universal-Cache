package crocodile8.universal_cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class CachedSourceExtTest {

    @Before
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `getOrRequest + have cache + predicate ok = Get from cache`() = runTest {
        var collected: Int? = null
        val (source, invocations) = TestUtils.createStringIntSource()

        action {
            source.get("1", FromCache.NEVER)
                .collect { /* Cache warm-up */ }
            source.getOrRequest("1", fromCachePredicate = { true })
                .collect { collected = it }
        }

        result {
            collected assert 1
            invocations assert 1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `getOrRequest + have cache + predicate false = Request again`() = runTest {
        var collected: Int? = null
        val (source, invocations) = TestUtils.createStringIntSource()

        action {
            source.get("1", FromCache.NEVER)
                .collect { /* Cache warm-up */ }
            source.getOrRequest("1", fromCachePredicate = { false })
                .collect { collected = it }
        }

        result {
            collected assert 2
            invocations assert 2
            source.assertNoOngoings()
        }
    }

    @Test
    fun `observeAndRequest + 3 emits with same key + 2 emits with different key = All needed are collected`() = runTest {
        val mutexUntilFirstEmit = Mutex(locked = true)
        val collected = mutableListOf<Int>()
        val (source, invocations) = TestUtils.createStringIntSource()

        action {
            val a = async {
                source.observeAndRequest("1")
                    .take(4)
                    .onEach { mutexUntilFirstEmit.unlockOnce() }
                    .collect { collected += it }
            }
            mutexUntilFirstEmit.await()
            source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
            source.get("2", FromCache.NEVER, shareOngoingRequest = false).collect {}
            source.get("3", FromCache.NEVER, shareOngoingRequest = false).collect {}
            source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
            source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
            a.await()
            println("collected: $collected")
        }

        result {
            collected assertContainsAnyOrder listOf(1, 2, 5, 6)
            invocations assert 6
            source.assertNoOngoings()
        }
    }

    @Test
    fun `observeAndRequest + 3 emits + 2 emits different key + error = All needed are collected`() = runTest {
        val mutexUntilFirstEmit = Mutex(locked = true)
        val collected = mutableListOf<Int>()
        val (source, invocations) = TestUtils.createStringIntSource {
            val cnt = it.incrementAndGet()
            if (cnt == 2) {
                throw RuntimeException()
            } else {
                cnt
            }
        }

        action {
            val a = async {
                source.observeAndRequest("1")
                    .take(3)
                    .onEach { mutexUntilFirstEmit.unlockOnce() }
                    .collect { collected += it }
            }
            mutexUntilFirstEmit.await()
            source.get("1", FromCache.NEVER, shareOngoingRequest = false).catch {}.collect {}
            source.get("2", FromCache.NEVER, shareOngoingRequest = false).collect {}
            source.get("3", FromCache.NEVER, shareOngoingRequest = false).collect {}
            source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
            source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
            a.await()
            println("collected: $collected")
        }

        result {
            collected assertContainsAnyOrder listOf(1, 5, 6)
            invocations assert 6
            source.assertNoOngoings()
        }
    }

    /**
     * Waits for [Mutex.unlock] if this Mutex was constructed with locked = true.
     */
    private suspend fun Mutex.await() {
        withLock {  }
    }

    /**
     * Avoids "Mutex is not locked" error in case of multiple unlocks.
     */
    private fun Mutex.unlockOnce() {
        if (isLocked) {
            unlock()
        }
    }
}
