package crocodile8.universal_cache

import crocodile8.universal_cache.request.Requester
import org.junit.Assert

suspend fun <P: Any, T: Any> CachedSource<P, T>.assertNoOngoings() {
    Assert.assertEquals(0, getOngoingSize())
}

suspend fun <P: Any, T: Any> Requester<P, T>.assertNoOngoings() {
    Assert.assertEquals(0, getOngoingSize())
}

fun <T> List<T>.assertContainsInAnyOrder(vararg args: T) {
    val expectedList = args.asList()
    Assert.assertEquals(expectedList.size, this.size)
    Assert.assertTrue(expectedList.containsAll(this))
    Assert.assertTrue(this.containsAll(expectedList))
}
