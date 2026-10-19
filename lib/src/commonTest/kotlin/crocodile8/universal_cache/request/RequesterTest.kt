package crocodile8.universal_cache.request

import crocodile8.universal_cache.UniversalCache
import crocodile8.universal_cache.test_utils.AtomicInteger
import crocodile8.universal_cache.test_utils.action
import crocodile8.universal_cache.test_utils.assert
import crocodile8.universal_cache.test_utils.assertNoOngoings
import crocodile8.universal_cache.test_utils.result
import crocodile8.universal_cache.test_utils.runRepeatedTest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

internal class RequesterTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `1 collect + wait + 2 collect in parallel = 2 real requests`() = runRepeatedTest(testDispatcher) {
        val sourceInvocationCnt = AtomicInteger()
        val requester = Requester<String, Int>(source = {
            delay(100.milliseconds)
            sourceInvocationCnt.incrementAndGet()
        })

        action {
            val a1 = async {
                requester.requestShared("1", dispatcher = testDispatcher).collect {}
            }
            a1.await()
            val a2 = async {
                requester.requestShared("1", dispatcher = testDispatcher).collect {}
            }
            val a3 = async {
                requester.requestShared("1", dispatcher = testDispatcher).collect {}
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
    fun `1 collect + wait + 2 collect in parallel + 1 flow instance = 2 real requests`() = runRepeatedTest(testDispatcher) {
        val sourceInvocationCnt = AtomicInteger()
        val requester = Requester<String, Int>(source = {
            delay(100.milliseconds)
            sourceInvocationCnt.incrementAndGet()
        })
        val flow = requester.requestShared("1", dispatcher = testDispatcher)

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
    fun `1 collect + wait + 3 collect in parallel with different params = 2 + 1 real requests`() = runRepeatedTest(testDispatcher) {
        val sourceInvocationCnt = mapOf(
            "1" to AtomicInteger(),
            "2" to AtomicInteger(),
        )
        val requester = Requester<String, Int>(source = {
            delay(100.milliseconds)
            sourceInvocationCnt[it]!!.incrementAndGet()
        })

        action {
            val a1 = async {
                requester.requestShared("1", dispatcher = testDispatcher).collect {}
            }
            a1.await()
            val a2 = async {
                requester.requestShared("1", dispatcher = testDispatcher).collect {}
            }
            val a3 = async {
                requester.requestShared("2", dispatcher = testDispatcher).collect {}
            }
            val a4 = async {
                requester.requestShared("1", dispatcher = testDispatcher).collect {}
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
    fun `1 collect + wait + 3 collect in parallel with different params + 2 flow instances = 2 + 1 real requests`() = runRepeatedTest(testDispatcher) {
        val sourceInvocationCnt = mapOf(
            "1" to AtomicInteger(),
            "2" to AtomicInteger(),
        )
        val requester = Requester<String, Int>(source = {
            delay(100.milliseconds)
            sourceInvocationCnt[it]!!.incrementAndGet()
        })
        val flow1 = requester.requestShared("1", dispatcher = testDispatcher)
        val flow2 = requester.requestShared("2", dispatcher = testDispatcher)

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

    @Test
    fun `Collect + fail + collect + cancel + collect twice = The last 2 successful + same`() = runRepeatedTest(testDispatcher) {
        val sourceInvocationCnt = AtomicInteger()
        var exceptionCaught = false
        var secondCompletion: Throwable? = null
        var result1 = ""
        var result2 = ""
        val requester = Requester<String, String>(source = {
            delay(100.milliseconds)
            when (val cnt = sourceInvocationCnt.incrementAndGet()) {
                1 -> throw RuntimeException("Test exception")
                else -> "param_$it->result_$cnt"
            }
        })
        val flow = requester.requestShared("1", dispatcher = testDispatcher)

        action {
            flow
                .catch { exceptionCaught = true }
                .collect {}

            coroutineScope {
                val l = launch {
                    flow
                        .onCompletion { secondCompletion = it }
                        .collect {}
                }
                delay(10.milliseconds)
                l.cancel()
            }

            coroutineScope {
                launch { flow.collect { result1 = it } }
                launch { flow.collect { result2 = it } }
            }
        }

        result {
            exceptionCaught assert true
            assertIs<CancellationException>(secondCompletion)
            result1 assert "param_1->result_2"
            result2 assert "param_1->result_2"
            requester.assertNoOngoings()
        }
    }

    @Test
    fun `Collect + fail + collect + cancel + collect twice = The last 2 successful + same v2`() = runRepeatedTest(testDispatcher) {
        val sourceInvocationCnt = AtomicInteger()
        var exceptionCaught = false
        var secondCompletion: Throwable? = null
        var result1 = ""
        var result2 = ""
        val requester = Requester<String, String>(source = {
            delay(100.milliseconds)
            when (val cnt = sourceInvocationCnt.incrementAndGet()) {
                1 -> throw RuntimeException("Test exception")
                else -> "param_$it->result_$cnt"
            }
        })

        action {
            requester.requestShared("1", dispatcher = testDispatcher)
                .catch { exceptionCaught = true }
                .collect {}

            coroutineScope {
                val l = launch {
                    requester.requestShared("1", dispatcher = testDispatcher)
                        .onCompletion { secondCompletion = it }
                        .collect {}
                }
                delay(10.milliseconds)
                l.cancel()
            }

            coroutineScope {
                launch {
                    requester.requestShared("1", dispatcher = testDispatcher)
                        .collect { result1 = it }
                }
                launch {
                    requester.requestShared("1", dispatcher = testDispatcher)
                        .collect { result2 = it }
                }
            }
        }

        result {
            exceptionCaught assert true
            assertIs<CancellationException>(secondCompletion)
            result1 assert "param_1->result_2"
            result2 assert "param_1->result_2"
            requester.assertNoOngoings()
        }
    }

    @Test
    fun `Collect + fail + collect + cancel + collect twice different params = The last 2 successful + different`() = runRepeatedTest(testDispatcher) {
        val sourceInvocationCnt = AtomicInteger()
        var exceptionCaught = false
        var secondCompletion: Throwable? = null
        var result1 = ""
        var result2 = ""
        val requester = Requester<String, String>(source = {
            delay(100.milliseconds)
            when (val cnt = sourceInvocationCnt.incrementAndGet()) {
                1 -> throw RuntimeException("Test exception")
                else -> "param_$it->result_$cnt"
            }
        })
        val flow1 = requester.requestShared("1", dispatcher = testDispatcher)
        val flow2 = requester.requestShared("2", dispatcher = testDispatcher)

        action {
            flow1
                .catch { exceptionCaught = true }
                .collect {}

            coroutineScope {
                val l = launch {
                    flow1
                        .onCompletion { secondCompletion = it }
                        .collect {}
                }
                delay(10.milliseconds)
                l.cancel()
            }

            coroutineScope {
                launch { flow1.collect { result1 = it } }
                launch { flow2.collect { result2 = it } }
            }
        }

        result {
            exceptionCaught assert true
            assertIs<CancellationException>(secondCompletion)
            result1 assert "param_1->result_2"
            result2 assert "param_2->result_3"
            requester.assertNoOngoings()
        }
    }

}
