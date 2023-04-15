package crocodile8.universal_cache

import crocodile8.universal_cache.request.Requester
import org.junit.Assert

suspend fun <P: Any, T: Any> CachedSource<P, T>.assertNoOngoings() {
    Assert.assertEquals(0, getOngoingSize())
}

suspend fun <P: Any, T: Any> Requester<P, T>.assertNoOngoings() {
    Assert.assertEquals(0, getOngoingSize())
}
