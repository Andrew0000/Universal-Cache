package crocodile8.universal_cache.request

import crocodile8.universal_cache.UniversalCache
import crocodile8.universal_cache.test_utils.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
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

        action {
            val a1 = async {
                requester.requestShared("1", dispatcher = getTestDispatcher()).collect {}
            }
            a1.await()
            val a2 = async {
                requester.requestShared("1", dispatcher = getTestDispatcher()).collect {}
            }
            val a3 = async {
                requester.requestShared("1", dispatcher = getTestDispatcher()).collect {}
            }
            a2.await()
            a3.await()
        }

        result {
            sourceInvocationCnt assert 2
            requester.assertNoOngoings()
        }
    }

    @Test
    fun `1 collect + wait + 2 collect in parallel (1 flow instance) = 2 real requests`() = runTest {
        val sourceInvocationCnt = AtomicInteger()
        val requester = Requester<String, Int>(source = {
            delay(100)
            sourceInvocationCnt.incrementAndGet()
        })
        val flow = requester.requestShared("1", dispatcher = getTestDispatcher())

        action {
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
        }

        result {
            sourceInvocationCnt assert 2
            requester.assertNoOngoings()
        }
    }

    @Test
    fun `1 collect + wait + 3 collect in parallel with different params = 2 + 1 real requests`() = runTest {
        val sourceInvocationCnt = mapOf(
            "1" to AtomicInteger(),
            "2" to AtomicInteger(),
        )
        val requester = Requester<String, Int>(source = {
            delay(100)
            sourceInvocationCnt[it]!!.incrementAndGet()
        })

        action {
            val a1 = async {
                requester.requestShared("1", dispatcher = getTestDispatcher()).collect {}
            }
            a1.await()
            val a2 = async {
                requester.requestShared("1", dispatcher = getTestDispatcher()).collect {}
            }
            val a3 = async {
                requester.requestShared("2", dispatcher = getTestDispatcher()).collect {}
            }
            val a4 = async {
                requester.requestShared("1", dispatcher = getTestDispatcher()).collect {}
            }
            a2.await()
            a3.await()
            a4.await()
        }

        result {
            sourceInvocationCnt["1"] assert 2
            sourceInvocationCnt["2"] assert 1
            requester.assertNoOngoings()
        }
    }

    @Test
    fun `1 collect + wait + 3 collect in parallel with different params (2 flow instances) = 2 + 1 real requests`() = runTest {
        val sourceInvocationCnt = mapOf(
            "1" to AtomicInteger(),
            "2" to AtomicInteger(),
        )
        val requester = Requester<String, Int>(source = {
            delay(100)
            sourceInvocationCnt[it]!!.incrementAndGet()
        })
        val flow1 = requester.requestShared("1", dispatcher = getTestDispatcher())
        val flow2 = requester.requestShared("2", dispatcher = getTestDispatcher())

        action {
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
        }

        result {
            sourceInvocationCnt["1"] assert 2
            sourceInvocationCnt["2"] assert 1
            requester.assertNoOngoings()
        }
    }

}
