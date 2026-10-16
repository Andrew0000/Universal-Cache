package crocodile8.universal_cache.test_utils

import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.CachedSourceResult
import crocodile8.universal_cache.request.Requester
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    fun zeroTimeProvider() = object : TimeProvider {
        override fun get(): Long = 0L
    }
}

/**
 * Should contain 'When' statement from Given-When-Then.
 */
suspend fun action(block: suspend () -> Unit) {
    block()
}

/**
 * Should contain 'Then' statement from Given-When-Then.
 */
suspend fun result(block: suspend () -> Unit) {
    block()
}

suspend fun <P : Any, T : Any> CachedSource<P, T>.assertNoOngoings() {
    assertEquals(0, getOngoingSize())
}

suspend fun <P : Any, T : Any> Requester<P, T>.assertNoOngoings() {
    assertEquals(0, getOngoingSize())
}

infix fun <T> List<T>.assert(expectedList: List<T>) {
    assertEquals(expectedList, this)
}

infix fun <T> List<T>.assertAnyOrder(expectedList: List<T>) {
    assertEquals(expectedList.size, this.size)
    assertTrue(expectedList.containsAll(this))
    assertTrue(this.containsAll(expectedList))
}

suspend infix fun AtomicInteger?.assert(expected: Int) {
    this?.get().assert(expected)
}

infix fun Int?.assert(expected: Int) {
    assertEquals(expected, this)
}

infix fun Boolean?.assert(expected: Boolean) {
    assertEquals(expected, this)
}

infix fun <T : Any> CachedSourceResult<T>?.assert(another: CachedSourceResult<T>) {
    assertEquals(another, this)
}

@OptIn(ExperimentalStdlibApi::class)
fun TestScope.getTestDispatcher() =
    coroutineContext[CoroutineDispatcher.Key] as CoroutineDispatcher
