package crocodile8.universal_cache

import crocodile8.universal_cache.test_utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CachedSourceUpdatesTest {

    @BeforeTest
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `FromCache NEVER = Updates are collected`() = runTest {
        val collectedUpdates = mutableListOf<Int>()
        val (source, _) = TestUtils.createStringIntSource()

        action {
            val a = async {
                source.updates.take(3).collect { (_, result) ->
                    collectedUpdates += result.value
                }
            }
            source.get("", FromCache.NEVER).collect {}
            source.get("", FromCache.NEVER).collect {}
            source.get("", FromCache.NEVER).collect {}
            a.await()
        }

        result {
            collectedUpdates assertAnyOrder listOf(1, 2, 3)
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache IF_HAVE = Updates are collected`() = runTest {
        val collectedUpdates = mutableListOf<Int>()
        val (source, _) = TestUtils.createStringIntSource()

        action {
            val a = async {
                source.updates.take(2).collect { (_, result) ->
                    collectedUpdates += result.value
                }
            }
            source.get("1", FromCache.IF_HAVE).collect {}
            source.get("1", FromCache.IF_HAVE).collect {}
            source.get("2", FromCache.IF_HAVE).collect {}
            a.await()
        }

        result {
            collectedUpdates assertAnyOrder listOf(1, 2)
            source.assertNoOngoings()
        }
    }

    @Test
    fun `FromCache NEVER + multiple success + errors = Updates and errors are collected`() = runTest {
        val collectedUpdates = mutableListOf<Int>()
        val collectedErrors = mutableListOf<Throwable>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            val cnt = sourceInvocationCnt.incrementAndGet()
            if (cnt % 2 == 1) {
                cnt
            } else {
                throw RuntimeException(cnt.toString())
            }
        })

        action {
            val a1 = async {
                source.updates.take(4).collect { (_, result) ->
                    collectedUpdates += result.value
                }
            }
            val a2 = async {
                source.errors.take(4).collect { (_, throwable) ->
                    collectedErrors += throwable
                }
            }
            source.get("1", FromCache.NEVER).catch {}.collect {}
            source.get("2", FromCache.NEVER).catch {}.collect {}
            source.get("3", FromCache.NEVER).catch {}.collect {}
            source.get("4", FromCache.NEVER).catch {}.collect {}
            source.get("5", FromCache.NEVER).catch {}.collect {}
            source.get("6", FromCache.NEVER).catch {}.collect {}
            source.get("7", FromCache.NEVER).catch {}.collect {}
            source.get("8", FromCache.NEVER).catch {}.collect {}
            a1.await()
            a2.await()
        }

        result {
            collectedUpdates assertAnyOrder listOf(1, 3, 5, 7)
            collectedErrors.map { it.message } assertAnyOrder listOf("2", "4", "6", "8")
            source.assertNoOngoings()
        }
    }

}
