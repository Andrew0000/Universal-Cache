package crocodile8.universal_cache.request

import crocodile8.universal_cache.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Requester<P : Any, T : Any>(
    private val source: suspend (params: P) -> T,
) {

    private val ongoings = mutableMapOf<P, Flow<T>>()
    private val ongoingsLock = Mutex()

    suspend fun request(
        params: P,
    ): Flow<T> =
        flow { emit(source(params))  }

    suspend fun requestShared(
        params: P,
    ): Flow<T> {
        val flow = ongoingsLock.withLock {

            var ongoingFlow = ongoings[params]
            Logger.log { "requestShared: $params / ongoing: $ongoingFlow / ${System.currentTimeMillis()}" }

            if (ongoingFlow == null) {
                val scope = CoroutineScope(Dispatchers.IO)
                ongoingFlow =
                    flow { emit(source(params)) }
                    .map { Result.success(it) }
                    .catch { emit(Result.failure(it)) }
                    .onCompletion {
                        ongoingsLock.withLock {
                            ongoings.remove(params)
                            scope.cancel()
                        }
                    }
                    .shareIn(
                        scope,
                        SharingStarted.WhileSubscribed(replayExpirationMillis = 1000),
                        1
                    )
                    .take(1)
                    // Shared flow doesn't throw exceptions so wrap and re-throw possible exceptions
                    .map {
                        if (it.isSuccess) {
                            it.getOrThrow()
                        } else {
                            throw it.exceptionOrNull()!!
                        }
                    }
                ongoings[params] = ongoingFlow
            }
            ongoingFlow
        }
        return flow
    }
}
