package crocodile8.universal_cache

import crocodile8.universal_cache.test_utils.AtomicInteger
import crocodile8.universal_cache.test_utils.TestUtils
import crocodile8.universal_cache.test_utils.action
import crocodile8.universal_cache.test_utils.assert
import crocodile8.universal_cache.test_utils.assertAnyOrder
import crocodile8.universal_cache.test_utils.assertNoOngoings
import crocodile8.universal_cache.test_utils.result
import crocodile8.universal_cache.test_utils.runRepeatedTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

internal class CachedSourceExtTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `getOrRequest + have cache + predicate ok = Get from cache`() = runRepeatedTest(testDispatcher) {
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
    fun `getOrRequest + have cache + predicate false = Request again`() = runRepeatedTest(testDispatcher) {
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
    fun `observeAndRequest + 3 emits with same key + 2 emits with different key = All needed are collected`() = runRepeatedTest(testDispatcher) {
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
            collected assertAnyOrder listOf(1, 2, 5, 6)
            invocations assert 6
            source.assertNoOngoings()
        }
    }

    @Test
    fun `observeAndRequest + 3 emits + 2 emits different key + error = All needed are collected`() = runRepeatedTest(testDispatcher) {
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
            collected assertAnyOrder listOf(1, 5, 6)
            invocations assert 6
            source.assertNoOngoings()
        }
    }

    @Test
    fun `observeAndRequest + cancel + observeAndRequest = Second is collected`() = runRepeatedTest(testDispatcher) {
        val collected = mutableListOf<Int>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(
            source = {
                delay(100.milliseconds)
                sourceInvocationCnt.incrementAndGet()
            },
            dispatcher = testDispatcher,
        )

        action {
            val l = launch {
                source.observeAndRequest("1")
                    .take(1)
                    .collect { collected += it }
            }
            delay(50.milliseconds)
            l.cancel()

            val l2 = launch {
                source.observeAndRequest("1")
                    .take(1)
                    .collect { collected += it }
            }
            l2.join()
        }

        result {
            collected assertAnyOrder listOf(1)
            source.assertNoOngoings()
        }
    }

    @Test
    fun `observeAndRequest + instant source + real threads = first value is never lost`() = runRepeatedTest {
            // Don't use the virtual-time scheduler: everything below runs on real threads
            withContext(Dispatchers.Default) {
                var result: Int? = null
                val source = CachedSource<Int, Int>(
                    source = { params -> params * 10 },
                    // Unconfined = the source runs inline and completes immediately,
                    // which gives the request the best chance to finish before
                    // the `updates` collector has subscribed.
                    dispatcher = Dispatchers.Unconfined,
                )

                action {
                    result = source.observeAndRequest(params = 1).first()
                }

                result {
                    // null = timeout = the initial update was emitted before we subscribed to it.
                    assertEquals(10, result)
                    source.assertNoOngoings()
                }
            }
        }

    @Test
    fun `observeAndRequest + failing source = retries then throws`() = runRepeatedTest {
        // Don't use the virtual-time scheduler: everything below runs on real threads
        withContext(Dispatchers.Default) {
            val invocations = AtomicInteger()
            var error: Throwable? = null
            val source = CachedSource<Int, Int>(
                source = {
                    invocations.incrementAndGet()
                    throw RuntimeException("Test exception")
                },
                dispatcher = Dispatchers.Default,
            )

            action {
                source.observeAndRequest(params = 1, requestRetryCount = 2)
                    .catch { error = it }
                    .collect {}
            }

            result {
                assertEquals("Test exception", error?.message)
                assertEquals(3, invocations.get()) // 1 initial attempt + 2 retries
                source.assertNoOngoings()
            }
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
