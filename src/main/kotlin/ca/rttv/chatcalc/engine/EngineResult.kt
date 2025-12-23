package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.types.MathObject
import me.ancientri.rimelib.util.text.TextBuilder
import me.ancientri.rimelib.util.text.text
import net.minecraft.text.Text

sealed interface EngineResult {
	/**
	 * Simple value return.
	 */
	data class Value(val output: MathObject) : EngineResult

	/**
	 * Append the output to the right of the input.
	 */
	data class Append(val output: MathObject) : EngineResult

	/**
	 * Clear the input, with an optional feedback message.
	 */
	data class Clear(val feedback: Text? = null)  : EngineResult {
		constructor(builder: TextBuilder.() -> Unit) : this(text(builder))
	}

	/**
	 * Errors during evaluation.
	 */
	data class Error<E: EngineError>(val error: E) : EngineResult
}