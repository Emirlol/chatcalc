package ca.rttv.chatcalc.engine.env

import ca.rttv.chatcalc.engine.types.MathObject

interface Environment {
	operator fun contains(key: String): Boolean

	operator fun get(key: String): MathObject?

	fun getOrThrow(key: String): MathObject = get(key) ?: throw NoSuchElementException("Key '$key' not found")
}

interface MutableEnvironment : Environment {
    operator fun set(key: String, value: MathObject)

    fun remove(key: String)

	operator fun minusAssign(key: String) = remove(key)
}