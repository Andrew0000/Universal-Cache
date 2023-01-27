package crocodile8.universal_cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.take

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
                    .take(1),
            )
                .distinctUntilChanged()
        )
    }