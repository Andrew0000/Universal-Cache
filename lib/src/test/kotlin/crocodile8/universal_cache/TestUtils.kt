package crocodile8.universal_cache

import crocodile8.universal_cache.request.Requester
import org.junit.Assert
import java.util.concurrent.atomic.AtomicInteger

object TestUtils {

    fun createStringIntSource(
        onInvoke: suspend (AtomicInteger) -> Int = { it.incrementAndGet() },
    ): Pair<CachedSource<String, Int>, AtomicInteger> {
        val invocationCnt = AtomicInteger()
        val source = CachedSource<String, Int>(source = {
            onInvoke(invocationCnt)
        })
        return source to invocationCnt
    }
}

suspend fun <P: Any, T: Any> CachedSource<P, T>.assertNoOngoings() {
    Assert.assertEquals(0, getOngoingSize())
}

suspend fun <P: Any, T: Any> Requester<P, T>.assertNoOngoings() {
    Assert.assertEquals(0, getOngoingSize())
}

infix fun <T> List<T>.assertContainsAnyOrder(expectedList: List<T>) {
    Assert.assertEquals(expectedList.size, this.size)
    Assert.assertTrue(expectedList.containsAll(this))
    Assert.assertTrue(this.containsAll(expectedList))
}

fun <T> List<T>.assertContainsInAnyOrder(vararg args: T) {
    val expectedList = args.asList()
    Assert.assertEquals(expectedList.size, this.size)
    Assert.assertTrue(expectedList.containsAll(this))
    Assert.assertTrue(this.containsAll(expectedList))
}

infix fun AtomicInteger.assert(expected: Int) {
    this.get().assert(expected)
}

infix fun Int?.assert(expected: Int) {
    Assert.assertEquals(expected, this)
}
