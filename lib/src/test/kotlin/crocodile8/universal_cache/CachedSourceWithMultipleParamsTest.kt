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
internal class CachedSourceWithMultipleParamsTest {

    @Before
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `FromCache NEVER + 1 request`() = runTest {
        var collected = -1
        val source = CachedSource<String, Int>(source = { 1 })
        source.get("1", fromCache = FromCache.NEVER, CacheRequirement())
            .collect {
                collected = it
            }
        Assert.assertEquals(1, collected)
    }

    @Test
    fun `FromCache NEVER + 2+1 parallel`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        val source = CachedSource<String, Int>(source = {
            delay(100)
            it.toInt()
        })
        val a1 = async {
            source.get("1", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected1 = it
                }
        }
        val a2 = async {
            source.get("1", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected2 = it
                }
        }
        val a3 = async {
            source.get("2", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected3 = it
                }
        }
        a1.await()
        a2.await()
        a3.await()
        Assert.assertEquals(1, collected1)
        Assert.assertEquals(1, collected2)
        Assert.assertEquals(2, collected3)
    }

    @Test
    fun `FromCache NEVER + 2+1 parallel + 2+1 parallel`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        var collected4 = -1
        var collected5 = -1
        var collected6 = -1
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            delay(200)
            sourceInvocationCnt.incrementAndGet()
        })
        val a1 = async {
            source.get("1", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected1 = it
                }
        }
        val a2 = async {
            source.get("1", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected2 = it
                }
        }
        val a3 = async {
            Thread.sleep(100) // Emulate real heavy operation that blocks thread
            source.get("2", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected3 = it
                }
        }
        a1.await()
        a2.await()
        a3.await()
        Assert.assertEquals(1, collected1)
        Assert.assertEquals(1, collected2)
        Assert.assertEquals(2, collected3)

        val a4 = async {
            source.get("3", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected4 = it
                }
        }
        val a5 = async {
            source.get("3", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected5 = it
                }
        }
        val a6 = async {
            Thread.sleep(100) // Emulate real heavy operation that blocks thread
            source.get("4", fromCache = FromCache.NEVER, CacheRequirement())
                .collect {
                    collected6 = it
                }
        }
        a4.await()
        a5.await()
        a6.await()
        Assert.assertEquals(3, collected4)
        Assert.assertEquals(3, collected5)
        Assert.assertEquals(4, collected6)
    }

    @Test
    fun `FromCache IF_FAILED + 1 success`() = runTest {
        var collected = -1
        val source = CachedSource<String, Int>(source = { 1 })
        source.get("1", fromCache = FromCache.IF_FAILED, CacheRequirement())
            .collect {
                println("collect in test: $it")
                collected = it
            }
        Assert.assertEquals(1, collected)
    }

    @Test
    fun `FromCache IF_FAILED + 1 failure = Catch exception`() = runTest {
        var collected = -1
        val source = CachedSource<String, Int>(source = { throw RuntimeException() })
        source.get("1", fromCache = FromCache.IF_FAILED, CacheRequirement())
            .catch { collected = 2 }
            .collect {
                collected = it
            }
        Assert.assertEquals(2, collected)
    }

    @Test
    fun `FromCache IF_FAILED + 1 success + 1 failure + 1 failure with another param = Get cached + catch`() = runTest {
        var collected = -1
        var caught = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })
        source.get("1", fromCache = FromCache.IF_FAILED, CacheRequirement())
            .collect {
                // It's warm-up call
            }
        source.get("1", fromCache = FromCache.IF_FAILED, CacheRequirement())
            .collect {
                collected = it
            }
        source.get("2", fromCache = FromCache.IF_FAILED, CacheRequirement())
            .catch { caught = true }
            .collect {
                collected = it
            }
        Assert.assertEquals(1, collected)
        Assert.assertTrue(caught)
    }

    @Test
    fun `FromCache IF_HAVE + 1 failure = Catch exception`() = runTest {
        var collected = -1
        val source = CachedSource<String, Int>(source = { throw RuntimeException() })
        source.get("1", fromCache = FromCache.IF_HAVE, CacheRequirement())
            .catch { collected = 2 }
            .collect {
                collected = it
            }
        Assert.assertEquals(2, collected)
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + 1 failure + 1 failure with another param = Get cached + catch`() = runTest {
        var collected = -1
        var caught = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })
        source.get("1", fromCache = FromCache.IF_HAVE, CacheRequirement())
            .collect {
                // It's warm-up call
            }
        source.get("1", fromCache = FromCache.IF_HAVE, CacheRequirement())
            .collect {
                collected = it
            }
        source.get("2", fromCache = FromCache.IF_HAVE, CacheRequirement())
            .catch { caught = true }
            .collect {
                collected = it
            }
        Assert.assertEquals(1, collected)
        Assert.assertTrue(caught)
    }

    @Test
    fun `FromCache IF_HAVE + 1 success + 1 with another param + 1 failure = Catch`() = runTest {
        var collected1 = -1
        var collected2 = -1
        var collected3 = -1
        var caught2 = false
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            delay(200)
            if (sourceInvocationCnt.incrementAndGet() == 1) {
                1
            } else {
                throw RuntimeException()
            }
        })
        source.get("1", fromCache = FromCache.IF_HAVE, CacheRequirement())
            .collect {
                collected1 = it
            }
        source.get("2", fromCache = FromCache.IF_HAVE, CacheRequirement())
            .catch { caught2 = true }
            .collect {
                collected2 = it
            }
        source.get("1", fromCache = FromCache.IF_HAVE, CacheRequirement())
            .collect {
                collected3 = it
            }
        Assert.assertEquals(1, collected1)
        Assert.assertEquals(-1, collected2)
        Assert.assertTrue(caught2)
        Assert.assertEquals(1, collected3)
    }

}
