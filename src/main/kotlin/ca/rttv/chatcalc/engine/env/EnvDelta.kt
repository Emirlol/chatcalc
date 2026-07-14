package ca.rttv.chatcalc.engine.env

import ca.rttv.chatcalc.engine.types.MathObject

interface EnvDelta {
	/**
	 * Represents a set operation, overwriting the value of a key in the environment.
	 */
	data class Set(val key: String, val value: MathObject) : EnvDelta

	/**
	 * Represents a remove operation, removing a key from the environment.
	 */
	data class Remove(val key: String) : EnvDelta
}

fun MutableEnvironment.applyDeltas(deltas: Iterable<EnvDelta>) {
	for (delta in deltas) {
		when (delta) {
			is EnvDelta.Set -> this[delta.key] = delta.value
			is EnvDelta.Remove -> this -= delta.key
		}
	}
}