package crocodile8.universal_cache

import crocodile8.universal_cache.TestUtils.zeroTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
internal class CachedSourceTest {

    @Before
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `FromCache ONLY + no cache = Exception`() = runTest {
        var collected = -1
        var caught = false
        val source = CachedSource<Unit, Int>(source = { 1 })

        action {
            source.get(Unit, fromCache = FromCache.ONLY)
                .catch { caught = true }
                .collect { collected = it }
        }

        result {
            caught assert true
            collected assert -1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache ONLY + have cache = Get cached`() = runTest {
        var collected: CachedSourceResult<Int>? = null
        var caught = false
        val source = CachedSource<Unit, Int>(
            source = { 2 },
            timeProvider = zeroTimeProvider(),
        )

        action {
            source.get(Unit, fromCache = FromCache.NEVER)
                .collect { /* Cache warm-up */ }
            source.getRaw(Unit, fromCache = FromCache.ONLY)
                .catch { caught = true }
                .collect { collected = it }
        }

        result {
            caught assert false
            collected assert CachedSourceResult(2, fromCache = true, originTimeStamp = 0)
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache ONLY + have cache but old for maxAge = Exception`() = runTest {
        var collected = -1
        var caught = false
        val source = CachedSource<Unit, Int>(source = { 2 })

        action {
            source.get(Unit, fromCache = FromCache.NEVER)
                .collect { /* Cache warm-up */ }
            source.get(Unit, fromCache = FromCache.ONLY, maxAge = 0)
                .catch { caught = true }
                .collect { collected = it }
        }

        result {
            caught assert true
            collected assert -1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache NEVER + 1 request`() = runTest {
        var collected = -1
        val source = CachedSource<Unit, Int>(source = { 1 })

        action {
            source.get(Unit, fromCache = FromCache.NEVER)
                .collect { collected = it }
        }

        result {
            collected assert 1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache NEVER + 3 parallel = Receive from the same ongoing`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            delay(50) // Delay to allow several requests attach to the same ongoing
            sourceInvocationCnt.incrementAndGet()
        })

        action {
            val a1 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected1 = it }
            }
            val a2 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected2 = it }
            }
            val a3 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected3 = it }
            }
            a1.await()
            a2.await()
            a3.await()
        }

        result {
            collected1 assert 1
            collected2 assert 1
            collected3 assert 1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache NEVER + 3 parallel + don't share = Receive different values`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(
            source = {
                delay(50) // Delay to allow several requests attach to the same ongoing
                sourceInvocationCnt.incrementAndGet()
            },
            dispatcher = getTestDispatcher(),
        )

        action {
            val a1 = async {
                source.get(Unit, fromCache = FromCache.NEVER, shareOngoingRequest = false)
                    .collect { collected1 = it }
            }
            val a2 = async {
                delay(5) // Ensure order of results
                source.get(Unit, fromCache = FromCache.NEVER, shareOngoingRequest = false)
                    .collect { collected2 = it }
            }
            val a3 = async {
                delay(10) // Ensure order of results
                source.get(Unit, fromCache = FromCache.NEVER, shareOngoingRequest = false)
                    .collect { collected3 = it }
            }
            a1.await()
            a2.await()
            a3.await()
        }

        result {
            collected1 assert 1
            collected2 assert 2
            collected3 assert 3
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache NEVER + 3 parallel + 3 parallel = First 3 receive from 1st ongoing, Second 3 receive from 2nd ongoing`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        var collected4 = -1
        var collected5 = -1
        var collected6 = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            delay(50) // Delay to allow several requests attach to the same ongoing
            sourceInvocationCnt.incrementAndGet()
        })

        action {
            val a1 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected1 = it }
            }
            val a2 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected2 = it }
            }
            val a3 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected3 = it }
            }
            a1.await()
            a2.await()
            a3.await()
            val a4 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected4 = it }
            }
            val a5 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected5 = it }
            }
            val a6 = async {
                source.get(Unit, fromCache = FromCache.NEVER)
                    .collect { collected6 = it }
            }
            a4.await()
            a5.await()
            a6.await()
        }

        result {
            collected1 assert 1
            collected2 assert 1
            collected3 assert 1
            collected4 assert 2
            collected5 assert 2
            collected6 assert 2
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_FAILED + 1 success = Get not from cache`() = runTest {
        var collected: CachedSourceResult<Int>? = null
        val source = CachedSource<Unit, Int>(source = { 1 })

        action {
            source.getRaw(Unit, fromCache = FromCache.IF_FAILED)
                .collect { collected = it }
        }

        result {
            collected?.value assert 1
            collected?.fromCache assert false
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_FAILED + 1 failure = Catch exception`() = runTest {
        var collected = -1
        var caught = false
        val source = CachedSource<Unit, Int>(source = { throw RuntimeException() })

        action {
            source.get(Unit, fromCache = FromCache.IF_FAILED)
                .catch { caught = true }
                .collect { collected = it }
        }

        result {
            caught assert true
            collected assert -1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_FAILED + 1 success + 1 failure = Get cached`() = runTest {
        var collected: CachedSourceResult<Int>? = null
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })

        action {
            source.get(Unit, fromCache = FromCache.IF_FAILED)
                .collect { /* It's warm-up call */ }
            source.getRaw(Unit, fromCache = FromCache.IF_FAILED)
                .collect { collected = it }
        }

        result {
            collected?.value assert 1
            collected?.fromCache assert true
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_HAVE + 1 failure = Catch exception`() = runTest {
        var collected = -1
        var caught = false
        val source = CachedSource<Unit, Int>(source = { throw RuntimeException() })

        action {
            source.get(Unit, fromCache = FromCache.IF_HAVE)
                .catch { caught = true }
                .collect { collected = it }
        }

        result {
            caught assert true
            collected assert -1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + 1 failure = Get cached`() = runTest {
        var collected: CachedSourceResult<Int>? = null
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })

        action {
            source.get(Unit, fromCache = FromCache.IF_HAVE)
                .collect { /* It's warm-up call */ }
            source.getRaw(Unit, fromCache = FromCache.IF_HAVE)
                .collect { collected = it }
        }

        result {
            collected?.value assert 1
            collected?.fromCache assert true
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + 1 failure + create flows at the same time = Get cached, laziness works`() = runTest {
        var collected = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            delay(100)
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })

        action {
            val sourceFlow1 = source.get(Unit, fromCache = FromCache.IF_HAVE, shareOngoingRequest = false)
            val sourceFlow2 = source.get(Unit, fromCache = FromCache.IF_HAVE, shareOngoingRequest = false)
            sourceFlow1.collect { /* Cache warm-up */ }
            sourceFlow2.collect { collected = it }
        }

        result {
            collected assert 1
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + 1 in parallel + 1 failure = Get cached`() = runTest {
        var collected1: CachedSourceResult<Int>? = null
        var collected2: CachedSourceResult<Int>? = null
        var collected3: CachedSourceResult<Int>? = null
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(
            source = {
                delay(50) // Delay to allow several requests attach to the same ongoing
                if (sourceInvocationCnt.incrementAndGet() == 1) {
                    1
                } else {
                    throw RuntimeException()
                }
            },
            timeProvider = zeroTimeProvider(),
        )

        action {
            val a1 = async {
                source.getRaw(Unit, fromCache = FromCache.IF_HAVE)
                    .collect { collected1 = it }
            }
            val a2 = async {
                source.getRaw(Unit, fromCache = FromCache.IF_HAVE)
                    .collect { collected2 = it }
            }
            a1.await()
            a2.await()
            source.getRaw(Unit, fromCache = FromCache.IF_HAVE)
                .collect { collected3 = it }
        }

        result {
            collected1 assert CachedSourceResult(1, fromCache = false, originTimeStamp = 0L)
            collected2 assert CachedSourceResult(1, fromCache = false, originTimeStamp = 0L)
            collected3 assert CachedSourceResult(1, fromCache = true, originTimeStamp = 0L)
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache CACHE_THEN_LOAD + 1 success + 1 success = Get cached + load`() = runTest {
        val collected = mutableListOf<CachedSourceResult<Int>>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(
            source = {
                sourceInvocationCnt.incrementAndGet()
            },
            timeProvider = zeroTimeProvider(),
        )

        action {
            source.get(Unit, fromCache = FromCache.NEVER)
                .collect { /* Warm-up cache */ }
            source.getRaw(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
                .collect { collected += it }
        }

        result {
            collected assert listOf(
                CachedSourceResult(1, fromCache = true, originTimeStamp = 0L),
                CachedSourceResult(2, fromCache = false, originTimeStamp = 0L)
            )
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache CACHED_THEN_LOAD + 1 success + 1 failure = Get cached + catch`() = runTest {
        val collected = mutableListOf<Int>()
        var caught = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })

        action {
            source.get(Unit, fromCache = FromCache.NEVER)
                .collect { /* Warm-up cache */ }
            source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
                .catch { caught = true }
                .collect { collected += it }
        }

        result {
            caught assert true
            collected assert listOf(1)
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache CACHED_THEN_LOAD + 1 success + 1 failure + again = Get cached + catch + cached + load`() = runTest {
        val collected1 = mutableListOf<CachedSourceResult<Int>>()
        val collected2 = mutableListOf<CachedSourceResult<Int>>()
        var caught1 = false
        var caught2 = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(
            source = {
                val cnt = sourceInvocationCnt.incrementAndGet()
                if (cnt % 2 == 1) {
                    cnt
                } else {
                    throw RuntimeException()
                }
            },
            timeProvider = zeroTimeProvider(),
        )

        action {
            source.get(Unit, fromCache = FromCache.NEVER)
                .collect { /* Warm-up cache */ }
            source.getRaw(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
                .catch { caught1 = true }
                .collect { collected1 += it }
            source.getRaw(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
                .catch { caught2 = true }
                .collect { collected2 += it }
        }

        result {
            caught1 assert true
            caught2 assert false
            collected1 assert listOf(
                CachedSourceResult(1, fromCache = true, originTimeStamp = 0L)
            )
            collected2 assert listOf(
                CachedSourceResult(1, fromCache = true, originTimeStamp = 0L),
                CachedSourceResult(3, fromCache = false, originTimeStamp = 0L)
            )
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache CACHED_THEN_LOAD + no cache + 1 success = Load`() = runTest {
        val collected = mutableListOf<Int>()
        var exception = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
            .catch { exception = true }
            .collect {
                collected += it
            }
        Assert.assertFalse(exception)
        Assert.assertEquals(listOf(1), collected)
        source.assertNoOngoings()
    }

    @Test
    fun `FromCache CACHED_THEN_LOAD + no cache + success + failure + success = Load + catch + cache + load`() = runTest {
        val collected1 = mutableListOf<Int>()
        val collected2 = mutableListOf<Int>()
        val collected3 = mutableListOf<Int>()
        var exception1 = false
        var exception2 = false
        var exception3 = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            val cnt = sourceInvocationCnt.incrementAndGet()
            if (cnt % 2 == 1) {
                cnt
            } else {
                throw RuntimeException()
            }
        })
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
            .catch { exception1 = true }
            .collect {
                collected1 += it
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
            .catch { exception2 = true }
            .collect {
                collected2 += it
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD)
            .catch { exception3 = true }
            .collect {
                collected3 += it
            }
        Assert.assertFalse(exception1)
        Assert.assertTrue(exception2)
        Assert.assertFalse(exception3)
        Assert.assertEquals(listOf(1), collected1)
        Assert.assertEquals(listOf(1), collected2)
        Assert.assertEquals(listOf(1, 3), collected3)
        source.assertNoOngoings()
    }

    @Test
    fun `FromCache NEVER + load + cancel + load = Loading cancelled + load`() = runTest {
        val collected1 = mutableListOf<CachedSourceResult<Int>>()
        val collected2 = mutableListOf<CachedSourceResult<Int>>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(
            source = {
                delay(100)
                sourceInvocationCnt.incrementAndGet()
            },
            timeProvider = zeroTimeProvider(),
            dispatcher = getTestDispatcher(),
        )
        val a1 = async {
            source.getRaw(Unit, fromCache = FromCache.NEVER)
                .collect {
                    collected1 += it
                }
        }
        delay(50)
        a1.cancel()
        source.getRaw(Unit, fromCache = FromCache.NEVER)
            .collect {
                collected2 += it
            }
        Assert.assertEquals(listOf<CachedSourceResult<Int>>(), collected1)
        Assert.assertEquals(listOf(CachedSourceResult(1, false, 0L)), collected2)

        // Warning! Theoretically may be unstable if first cancellation takes more time than test (in fact async)
        source.assertNoOngoings()
    }

    @Test
    fun `FromCache NEVER + load + cancel + load another param = No ongoings`() = runTest {
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(
            source = {
                delay(100)
                sourceInvocationCnt.incrementAndGet()
            },
            timeProvider = zeroTimeProvider(),
            dispatcher = getTestDispatcher(),
        )
        val a1 = async {
            source.getRaw("1", fromCache = FromCache.NEVER)
                .collect {}
        }
        delay(50)
        a1.cancel()
        source.getRaw("2", fromCache = FromCache.NEVER)
            .collect {}

        // Warning! Theoretically may be unstable if first cancellation takes more time than test (in fact async)
        source.assertNoOngoings()
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + clear + 1 success = Get cached + load`() = runTest {
        var collected1: CachedSourceResult<Int>? = null
        var collected2: CachedSourceResult<Int>? = null
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            sourceInvocationCnt.incrementAndGet()
        }, timeProvider = zeroTimeProvider())
        source.get(Unit, fromCache = FromCache.IF_HAVE)
            .collect {
                // It's warm-up call
            }
        source.getRaw(Unit, fromCache = FromCache.IF_HAVE)
            .collect {
                collected1 = it
            }
        source.clearCache()
        source.getRaw(Unit, fromCache = FromCache.IF_HAVE)
            .collect {
                collected2 = it
            }
        collected1 assert CachedSourceResult(1, fromCache = true, 0L)
        collected2 assert CachedSourceResult(2, fromCache = false, 0L)
        source.assertNoOngoings()
    }

}
