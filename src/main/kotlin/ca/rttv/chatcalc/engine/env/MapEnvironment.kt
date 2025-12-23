@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.engine.env

import ca.rttv.chatcalc.engine.types.MathObject

open class MapEnvironment(
	values: Map<String, MathObject>? = null,
	val parent: Environment? = null
) : MutableEnvironment {
	val values: MutableMap<String, MathObject> = values?.toMutableMap() ?: mutableMapOf() // .toMutableMap always creates a new map, so we can safely use it here

	override fun remove(key: String) {
		// Only checking this env, we don't want to allow changing the parent.
		if (key in values) values -= key
	}

	override operator fun contains(key: String): Boolean = key in values || (parent?.contains(key) == true)

	override operator fun get(key: String): MathObject? = values[key] ?: parent?.get(key)

	override operator fun set(key: String, value: MathObject) { // Allows shadowing parent
		values[key] = value
	}
}

