package ca.rttv.chatcalc.async

import ca.rttv.chatcalc.engine.EngineResult
import ca.rttv.chatcalc.engine.Statement
import java.util.concurrent.CompletableFuture

data class InterpreterTask(
	val payload: List<Statement>,
	val response: CompletableFuture<EngineResult>
)

