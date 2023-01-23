package crocodile8.universal_cache

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
    fun `FromCache NEVER + 1 request`() = runTest {
        var collected = -1
        val source = CachedSource<Unit, Int>(source = { 1 })
        source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
            .collect {
                collected = it
            }
        Assert.assertEquals(1, collected)
    }

    @Test
    fun `FromCache NEVER + 3 parallel`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            delay(200)
            sourceInvocationCnt.incrementAndGet()
        })
        val a1 = async {
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected1 = it
                }
        }
        val a2 = async {
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected2 = it
                }
        }
        val a3 = async {
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected3 = it
                }
        }
        a1.await()
        a2.await()
        a3.await()
        Assert.assertEquals(1, collected1)
        Assert.assertEquals(1, collected2)
        Assert.assertEquals(1, collected3)
    }

    @Test
    fun `FromCache NEVER + 3 parallel + 3 parallel`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        var collected4 = -1
        var collected5 = -1
        var collected6 = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            delay(200)
            sourceInvocationCnt.incrementAndGet()
        })
        val a1 = async {
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected1 = it
                }
        }
        val a2 = async {
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected2 = it
                }
        }
        val a3 = async {
            Thread.sleep(100) // Emulate real heavy operation that blocks thread
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected3 = it
                }
        }
        a1.await()
        a2.await()
        a3.await()
        Assert.assertEquals(1, collected1)
        Assert.assertEquals(1, collected2)
        Assert.assertEquals(1, collected3)

        val a4 = async {
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected4 = it
                }
        }
        val a5 = async {
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected5 = it
                }
        }
        val a6 = async {
            Thread.sleep(100) // Emulate real heavy operation that blocks thread
            source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected6 = it
                }
        }
        a4.await()
        a5.await()
        a6.await()
        Assert.assertEquals(2, collected4)
        Assert.assertEquals(2, collected5)
        Assert.assertEquals(2, collected6)
    }

    @Test
    fun `FromCache IF_FAILED + 1 success`() = runTest {
        var collected = -1
        val source = CachedSource<Unit, Int>(source = { 1 })
        source.get(Unit, fromCache = FromCache.IF_FAILED, CacheRequirement())
            .collect {
                collected = it
            }
        Assert.assertEquals(1, collected)
    }

    @Test
    fun `FromCache IF_FAILED + 1 failure = Catch exception`() = runTest {
        var collected = -1
        val source = CachedSource<Unit, Int>(source = { throw RuntimeException() })
        source.get(Unit, fromCache = FromCache.IF_FAILED, CacheRequirement())
            .catch { collected = 2 }
            .collect {
                collected = it
            }
        Assert.assertEquals(2, collected)
    }

    @Test
    fun `FromCache IF_FAILED + 1 success + 1 failure = Get cached`() = runTest {
        var collected = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })
        source.get(Unit, fromCache = FromCache.IF_FAILED, CacheRequirement())
            .collect {
                // It's warm-up call
            }
        source.get(Unit, fromCache = FromCache.IF_FAILED, CacheRequirement())
            .collect {
                collected = it
            }
        Assert.assertEquals(1, collected)
    }

    @Test
    fun `FromCache IF_HAVE + 1 failure = Catch exception`() = runTest {
        var collected = -1
        val source = CachedSource<Unit, Int>(source = { throw RuntimeException() })
        source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement())
            .catch { collected = 2 }
            .collect {
                collected = it
            }
        Assert.assertEquals(2, collected)
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + 1 failure = Get cached`() = runTest {
        var collected = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })
        source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement())
            .collect {
                // It's warm-up call
            }
        source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement())
            .collect {
                collected = it
            }
        Assert.assertEquals(1, collected)
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
        val sourceFlow1 = source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement(shareOngoingRequest = false))
        val sourceFlow2 = source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement(shareOngoingRequest = false))
        sourceFlow1.collect {
            // Cache warm-up
        }
        sourceFlow2.collect {
            collected = it
        }
        Assert.assertEquals(1, collected)
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + 1 in parallel + 1 failure = Get cached`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            delay(200)
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })
        val a1 = async {
            source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement())
                .collect {
                    collected1 = it
                }
        }
        val a2 = async {
            source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement())
                .collect {
                    collected2 = it
                }
        }
        a1.await()
        a2.await()
        source.get(Unit, fromCache = FromCache.IF_HAVE, CacheRequirement())
            .collect {
                collected3 = it
            }
        Assert.assertEquals(1, collected1)
        Assert.assertEquals(1, collected2)
        Assert.assertEquals(1, collected3)
    }

    @Test
    fun `FromCache CACHE_THEN_LOAD + 1 success + 1 success = Get cached + load`() = runTest {
        val collected = mutableListOf<Int>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            sourceInvocationCnt.incrementAndGet()
        })
        source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
            .collect {
                // Warm-up cache
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
            .collect {
                collected += it
            }
        Assert.assertEquals(listOf(1, 2), collected)
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
        source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
            .collect {
                // Warm-up cache
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
            .catch { caught = true }
            .collect {
                collected += it
            }
        Assert.assertTrue(caught)
        Assert.assertEquals(listOf(1), collected)
    }

    @Test
    fun `FromCache CACHED_THEN_LOAD + 1 success + 1 failure + again = Get cached + catch + cached + load`() = runTest {
        val collected1 = mutableListOf<Int>()
        val collected2 = mutableListOf<Int>()
        var caught1 = false
        var caught2 = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<Unit, Int>(source = {
            val cnt = sourceInvocationCnt.incrementAndGet()
            if (cnt % 2 == 1) {
                cnt
            } else {
                throw RuntimeException()
            }
        })
        source.get(Unit, fromCache = FromCache.NEVER, CacheRequirement())
            .collect {
                // Warm-up cache
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
            .catch { caught1 = true }
            .collect {
                collected1+= it
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
            .catch { caught2 = true }
            .collect {
                collected2+= it
            }
        Assert.assertTrue(caught1)
        Assert.assertFalse(caught2)
        Assert.assertEquals(listOf(1), collected1)
        Assert.assertEquals(listOf(1, 3), collected2)
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
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
            .catch { exception = true }
            .collect {
                collected += it
            }
        Assert.assertFalse(exception)
        Assert.assertEquals(listOf(1), collected)
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
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
            .catch { exception1 = true }
            .collect {
                collected1 += it
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
            .catch { exception2 = true }
            .collect {
                collected2 += it
            }
        source.get(Unit, fromCache = FromCache.CACHED_THEN_LOAD, CacheRequirement())
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
    }

}
