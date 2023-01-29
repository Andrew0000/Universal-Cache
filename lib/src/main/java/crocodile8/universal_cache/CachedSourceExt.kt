package crocodile8.universal_cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.take

suspend fun <P : Any, T : Any> CachedSource<P, T>.getOrRequest(
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

suspend fun <P : Any, T : Any> CachedSource<P, T>.requestAndObserve(
    params: P,
    requestRetryCount: Long = 2,
): Flow<T> =
    flow {
        emitAll(
            merge(
                updates
                    .filter { it.first == params }
                    .map { it.second.value },

                get(params, FromCache.NEVER)
                    .retry(requestRetryCount)
                    .take(1)
                    // Don't emit from this stream because all updates are emitted from .updates anyway.
                    // The downside: a value from .updates (from another parallel request)
                    // can arrive before this request.
                    // More complex solution is needed to solve this behaviour.
                    .filter { false },
            )
        )
    }
