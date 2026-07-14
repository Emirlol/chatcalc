package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.env.EnvDelta
import ca.rttv.chatcalc.engine.types.MathObject
import com.danrusu.pods4k.immutableArrays.ImmutableArray
import com.danrusu.pods4k.immutableArrays.emptyImmutableArray
import me.ancientri.rimelib.util.text.TextBuilder
import me.ancientri.rimelib.util.text.text
import net.minecraft.text.Text

sealed interface EngineResult {
	/**
	 * Simple value return.
	 * @property output The output value.
	 * @property deltas The environment changes that occurred during evaluation.
	 */
	data class Value(val output: MathObject, val deltas: ImmutableArray<EnvDelta> = emptyImmutableArray()) : EngineResult

	/**
	 * Append the output to the right of the input.
	 * @property output The output value.
	 * @property deltas The environment changes that occurred during evaluation.
	 */
	data class Append(val output: MathObject, val deltas: ImmutableArray<EnvDelta> = emptyImmutableArray()) : EngineResult

	/**
	 * Clear the input, with an optional feedback message.
	 * @property feedback The feedback message to display.
	 * @property deltas The environment changes that occurred during evaluation.
	 */
	data class Clear(val feedback: Text? = null, val deltas: ImmutableArray<EnvDelta> = emptyImmutableArray()) : EngineResult {
		constructor(deltas: ImmutableArray<EnvDelta> = emptyImmutableArray(), builder: TextBuilder.() -> Unit) : this(text(builder), deltas)
	}

	/**
	 * Errors during evaluation.
	 * @property error The error that occurred.
	 */
	data class Error<E: EngineError>(val error: E) : EngineResult
}