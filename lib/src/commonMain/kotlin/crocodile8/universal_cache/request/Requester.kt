package crocodile8.universal_cache.request

import crocodile8.universal_cache.CachedSourceNoParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Requester<P : Any, T : Any>(
    private val source: suspend (params: P) -> T,
) {

    private val ongoings = mutableMapOf<P, Ongoing<T>>()
    private val ongoingsLock = Mutex()

    /**
     * Wraps source request with a flow. Doesn't check for ongoings.
     *
     * @see [requestShared]
     */
    fun request(
        params: P,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Flow<T> =
        flow { emit(source(params)) }
            .flowOn(dispatcher)

    /**
     * Makes a request or attaches to an ongoing request
     * if there is any in progress with the given params.
     *
     * @param params request parameters, also used as key for sharing.
     * Must be 1) a data-class or 2) primitive or 3) has equals/hash code implemented for proper distinction.
     * Use [Unit] ot [Int] or look at [CachedSourceNoParams] if there are no parameters for request.
     *
     * @param dispatcher [CoroutineDispatcher] that will be used for request.
     *
     * @return shared flow that other callers with same [params] can be attached to.
     */
    fun requestShared(
        params: P,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Flow<T> = flow {
        val ongoing = ongoingsLock.withLock {
            val existing = ongoings[params]?.takeIf { !it.deferred.isCompleted }
            val item = existing ?: createOngoing(params, dispatcher)
                .also { ongoings[params] = it }
            item.subscribers++
            item
        }

        // No suspension point between leaving the lock and entering try/finally,
        // so the release() is guaranteed.

        val value = try {
            ongoing.deferred.await()
        } finally {
            release(params, ongoing)
        }
        emit(value)
    }

    internal suspend fun getOngoingSize() =
        ongoingsLock.withLock { ongoings.size }

    private fun createOngoing(params: P, dispatcher: CoroutineDispatcher): Ongoing<T> {
        val scope = CoroutineScope(dispatcher + Job())
        val deferred = scope.async {
            source(params)
        }
        deferred.invokeOnCompletion {
            scope.cancel()
        }
        return Ongoing(deferred, scope)
    }

    private suspend fun release(params: P, ongoing: Ongoing<T>) =
        withContext(NonCancellable) {
            ongoingsLock.withLock {
                ongoing.subscribers--
                if (ongoing.subscribers == 0) {
                    if (ongoings[params] === ongoing) {
                        ongoings.remove(params)
                    }
                    ongoing.scope.cancel()
                }
            }
        }
}

private class Ongoing<T>(
    val deferred: Deferred<T>,
    val scope: CoroutineScope,
) {
    var subscribers: Int = 0
}
