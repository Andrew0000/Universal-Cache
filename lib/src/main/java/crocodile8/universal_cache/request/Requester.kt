package crocodile8.universal_cache.request

import crocodile8.universal_cache.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class Requester<P : Any, T : Any>(
    private val source: suspend (params: P) -> T,
) {

    private val ongoings = mutableMapOf<P, Flow<T>>()
    private val ongoingsLock = Mutex()

    suspend fun request(
        params: P,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Flow<T> =
        flow { emit(source(params)) }
            .flowOn(dispatcher)

    suspend fun requestShared(
        params: P,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Flow<T> {
        val flow = ongoingsLock.withLock {

            var ongoingFlow = ongoings[params]
            Logger.log { "requestShared: $params / ongoing: $ongoingFlow" }

            if (ongoingFlow == null) {
                val scope = CoroutineScope(dispatcher)
                val isActive = AtomicBoolean(true)
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
                            tryRemoveOngoing(params, isActive)
                        }
                        .onCompletion {
                            Logger.log { "requestShared onCompletion: $params" }
                            tryRemoveOngoing(params, isActive)
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
        return flow
    }

    internal suspend fun getOngoingSize() =
        ongoingsLock.withLock {
            val size = ongoings.size
            Logger.log { "getOngoingSize: $size" }
            size
        }

    private suspend fun tryRemoveOngoing(params: P, isActive: AtomicBoolean) {
        if (isActive.getAndSet(false)) {
            withContext(NonCancellable) {
                try {
                    ongoingsLock.withLock {
                        ongoings.remove(params)
                        Logger.log { "requestShared removeOngoing: $params, size: ${ongoings.size}" }
                    }
                } catch (t: Throwable) {
                    Logger.log { "requestShared removeOngoing -> error in lock: $t" }
                    throw t
                }
            }
        }
    }
}
