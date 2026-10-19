package crocodile8.universal_cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

fun <P : Any, T : Any> CachedSource<P, T>.getOrRequest(
    params: P,
    fromCachePredicate: (cached: CachedSourceResult<T>) -> Boolean,
): Flow<T> =
    flow {
        getRaw(params, FromCache.IF_HAVE)
            .collect {
                if (!it.fromCache || fromCachePredicate(it)) {
                    emit(it.value)
                } else {
                    emitAll(
                        getRaw(params, FromCache.NEVER)
                            .map { result -> result.value }
                    )
                }
            }
    }

fun <P : Any, T : Any> CachedSource<P, T>.observeAndRequest(
    params: P,
    requestRetryCount: Long = 2,
): Flow<T> =
    channelFlow {
        val subscribed = CompletableDeferred<Unit>()
        launch {
            updates
                .onSubscription { subscribed.complete(Unit) }
                .filter { it.first == params }
                .collect { send(it.second.value) }
        }
        subscribed.await()
        get(params, FromCache.NEVER)
            .retry(requestRetryCount)
            .collect { /* delivered via `updates` */ }
    }
