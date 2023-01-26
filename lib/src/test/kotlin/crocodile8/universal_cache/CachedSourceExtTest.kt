package crocodile8.universal_cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
internal class CachedSourceExtTest {

    @Before
    fun setUp() {
        UniversalCache.printLogs = true
    }

    @Test
    fun `requestAndObserve + 3 emits with same key + 2 emits with different key`() = runTest {
        val collected = mutableListOf<Int>()
        val sourceInvocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            sourceInvocationCnt.incrementAndGet()
        })
        val a = async {
            source.requestAndObserve("1")
                .take(4)
                .collect { collected += it }
        }
        delay(10) // wait requestAndObserve starts in async
        source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
        source.get("2", FromCache.NEVER, shareOngoingRequest = false).collect {}
        source.get("3", FromCache.NEVER, shareOngoingRequest = false).collect {}
        source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
        source.get("1", FromCache.NEVER, shareOngoingRequest = false).collect {}
        a.await()
        // Order is not guaranteed so check result in this way
        Assert.assertEquals(4, collected.size)
        Assert.assertTrue(collected.contains(1))
        Assert.assertTrue(collected.contains(2))
        Assert.assertTrue(collected.contains(5))
        Assert.assertTrue(collected.contains(6))
        Assert.assertEquals(0, source.getOngoingSize())
    }

}
