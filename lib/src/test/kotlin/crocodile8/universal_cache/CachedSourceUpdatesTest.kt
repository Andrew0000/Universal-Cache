package crocodile8.universal_cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CachedSourceUpdatesTest {

    @Before
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `FromCache NEVER = Updates are collected`() = runTest {
        val collectedUpdates = mutableListOf<Int>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            sourceInvocationCnt.incrementAndGet()
        })
        val a = async {
            source.updates.take(3).collect { (_, result) ->
                collectedUpdates += result.value
            }
        }
        source.get("", FromCache.NEVER).collect {}
        source.get("", FromCache.NEVER).collect {}
        source.get("", FromCache.NEVER).collect {}
        a.await()
        Assert.assertArrayEquals(
            listOf(1, 2, 3).toIntArray(),
            collectedUpdates.toIntArray()
        )
    }

    @Test
    fun `FromCache IF_HAVE = Updates are collected`() = runTest {
        val collectedUpdates = mutableListOf<Int>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            sourceInvocationCnt.incrementAndGet()
        })
        val a = async {
            source.updates.take(2).collect { (_, result) ->
                collectedUpdates += result.value
            }
        }
        source.get("1", FromCache.IF_HAVE).collect {}
        source.get("1", FromCache.IF_HAVE).collect {}
        source.get("2", FromCache.IF_HAVE).collect {}
        a.await()
        Assert.assertArrayEquals(
            listOf(1, 2).toIntArray(),
            collectedUpdates.toIntArray()
        )
        println("end")
    }
}