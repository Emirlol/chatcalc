package ca.rttv.chatcalc.async

import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.engine.EngineResult
import ca.rttv.chatcalc.engine.Statement
import ca.rttv.chatcalc.engine.astvisitors.Interpreter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

object CalculationManager {
	val taskChannel = Channel<InterpreterTask>(Channel.UNLIMITED)

	context(scope: CoroutineScope)
	fun startWorker() = scope.launch(Dispatchers.Default) {
		for (task in taskChannel) {
			try {
				val result = Interpreter(UserEnvironmentManager::config, dryRun = true).interpret(task.payload)
				task.response.complete(result)
			} catch (e: Exception) {
				task.response.completeExceptionally(e)
			}
		}
	}

	fun requestInterpretation(input: List<Statement>): CompletableFuture<EngineResult> {
		val deferred = CompletableFuture<EngineResult>()
		taskChannel.trySend(InterpreterTask(input, deferred))
		return deferred
	}
}