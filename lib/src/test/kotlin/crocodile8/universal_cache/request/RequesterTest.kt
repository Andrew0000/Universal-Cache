package crocodile8.universal_cache.request

import crocodile8.universal_cache.UniversalCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
internal class RequesterTest {

    @Before
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `1 collect + wait + 2 collect in parallel = 2 real requests`() = runTest {
        val sourceInvocationCnt = AtomicInteger()
        val requester = Requester<String, Int>(source = {
            delay(100)
            sourceInvocationCnt.incrementAndGet()
        })
        val a1 = async {
            requester.requestShared("1").collect {}
        }
        a1.await()
        val a2 = async {
            requester.requestShared("1").collect {}
        }
        val a3 = async {
            requester.requestShared("1").collect {}
        }
        a2.await()
        a3.await()

        Assert.assertEquals(2, sourceInvocationCnt.get())
        Assert.assertEquals(0, requester.getOngoingSize())
    }

    @Test
    fun `1 collect + wait + 2 collect in parallel (1 flow instance) = 2 real requests`() = runTest {
        val sourceInvocationCnt = AtomicInteger()
        val requester = Requester<String, Int>(source = {
            delay(100)
            sourceInvocationCnt.incrementAndGet()
        })
        val flow = requester.requestShared("1")
        val a1 = async {
            flow.collect {}
        }
        a1.await()
        val a2 = async {
            flow.collect {}
        }
        val a3 = async {
            flow.collect {}
        }
        a2.await()
        a3.await()

        Assert.assertEquals(2, sourceInvocationCnt.get())
        Assert.assertEquals(0, requester.getOngoingSize())
    }

    @Test
    fun `1 collect + wait + 3 collect in parallel with different params = 2 + 1 real requests`() = runTest {
        val sourceInvocationCnt = mapOf<String, AtomicInteger>(
            "1" to AtomicInteger(),
            "2" to AtomicInteger(),
        )
        val requester = Requester<String, Int>(source = {
            delay(100)
            sourceInvocationCnt[it]!!.incrementAndGet()
        })
        val a1 = async {
            requester.requestShared("1").collect {}
        }
        a1.await()
        val a2 = async {
            requester.requestShared("1").collect {}
        }
        val a3 = async {
            requester.requestShared("2").collect {}
        }
        val a4 = async {
            requester.requestShared("1").collect {}
        }
        a2.await()
        a3.await()
        a4.await()

        Assert.assertEquals(2, sourceInvocationCnt["1"]?.get())
        Assert.assertEquals(1, sourceInvocationCnt["2"]?.get())
        Assert.assertEquals(0, requester.getOngoingSize())
    }

    @Test
    fun `1 collect + wait + 3 collect in parallel with different params (2 flow instances) = 2 + 1 real requests`() = runTest {
        val sourceInvocationCnt = mapOf<String, AtomicInteger>(
            "1" to AtomicInteger(),
            "2" to AtomicInteger(),
        )
        val requester = Requester<String, Int>(source = {
            delay(100)
            sourceInvocationCnt[it]!!.incrementAndGet()
        })
        val flow1 = requester.requestShared("1")
        val flow2 = requester.requestShared("2")
        val a1 = async {
            flow1.collect {}
        }
        a1.await()
        val a2 = async {
            flow1.collect {}
        }
        val a3 = async {
            flow2.collect {}
        }
        val a4 = async {
            flow1.collect {}
        }
        a2.await()
        a3.await()
        a4.await()

        Assert.assertEquals(2, sourceInvocationCnt["1"]?.get())
        Assert.assertEquals(1, sourceInvocationCnt["2"]?.get())
        Assert.assertEquals(0, requester.getOngoingSize())
    }

}
