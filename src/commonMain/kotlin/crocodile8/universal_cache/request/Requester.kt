package crocodile8.universal_cache.request

import crocodile8.universal_cache.CachedSourceNoParams
import crocodile8.universal_cache.utils.BooleanRef
import crocodile8.universal_cache.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Requester<P : Any, T : Any>(
    private val source: suspend (params: P) -> T,
) {

    private val ongoings = mutableMapOf<P, Flow<T>>()
    private val ongoingsLock = Mutex()

    /**
     * Wraps source request with a flow. Doesn't check for ongoings.
     *
     * @see [requestShared]
     */
    suspend fun request(
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
    suspend fun requestShared(
        params: P,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Flow<T> {
        val lazyFlow = suspend {
            ongoingsLock.withLock {

                var ongoingFlow = ongoings[params]
                Logger.log { "requestShared: $params / ongoing: $ongoingFlow" }

                if (ongoingFlow == null) {
                    val scope = CoroutineScope(dispatcher)
                    val isInOngoings = BooleanRef(true)
                    ongoingFlow =
                        flow { emit(source(params)) }
                            .flowOn(dispatcher)
                            .map { Result.success(it) }
                            .catch { emit(Result.failure(it)) }
                            .take(1)
                            .onEach {
                                // It's better to release ongoing earlier then .onCompletion()
                                // but only .onCompletion() will be called on cancellation
                                // so try in both places
                                removeOngoing(params, isInOngoings)
                            }
                            .onCompletion {
                                Logger.log { "requestShared onCompletion: $params" }
                                removeOngoing(params, isInOngoings)
                                scope.cancel()
                            }
                            .shareIn(
                                scope,
                                SharingStarted.WhileSubscribed(),
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
        }
        return flow {
            emitAll(lazyFlow())
        }
    }

    internal suspend fun getOngoingSize() =
        ongoingsLock.withLock {
            val size = ongoings.size
            Logger.log { "getOngoingSize: $size" }
            size
        }

    private suspend fun removeOngoing(params: P, isInOngoings: BooleanRef) =
        withContext(NonCancellable) {
            try {
                ongoingsLock.withLock {
                    if (isInOngoings.value) {
                        ongoings.remove(params)
                        isInOngoings.value = false
                        Logger.log { "requestShared removeOngoing: $params, size: ${ongoings.size}" }
                    }
                }
            } catch (t: Throwable) {
                Logger.log { "requestShared removeOngoing -> error in lock: $t" }
                throw t
            }
        }
}
