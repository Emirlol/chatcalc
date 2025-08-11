package ca.rttv.chatcalc

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.function.Function

//eval is nullable to allow removing the function from the config JSON.
//It's only null when the constant is being removed.
data class CustomFunction(val name: String, val eval: String?, val params: List<String>) {
	fun get(values: DoubleArray): Double {
		require(values.size == params.size) { "Invalid number of arguments for custom function" }
		requireNotNull(eval) { "This function isn't meant to be called!" }

		val pair = name to params.size

		require(!ChatCalc.FUNCTION_TABLE.contains(pair)) { "Tried to call function twice, recursively" }

		val parameters = arrayOfNulls<FunctionParameter>(values.size)
		for (i in parameters.indices) {
			parameters[i] = FunctionParameter(params[i], values[i])
		}

		ChatCalc.FUNCTION_TABLE.add(pair)
		val value = MathEngine.of().eval(eval, parameters)
		ChatCalc.FUNCTION_TABLE.remove(pair)
		return value
	}

	override fun toString() = name + '(' + params.joinToString(ChatCalc.SEPARATOR) + ")=" + eval

	companion object {
		private val FUNCTION_REGEX = Regex("(?<name>[a-zA-Z]+)\\((?<params>(?:[a-zA-Z]+;)*?[a-zA-Z]+)\\)")

		private val CODEC: Codec<CustomFunction> = RecordCodecBuilder.create {
			it.group(
				Codec.STRING.fieldOf("name").forGetter(CustomFunction::name),
				Codec.STRING.fieldOf("eval").forGetter(CustomFunction::eval),
				Codec.STRING.listOf().fieldOf("params").forGetter(CustomFunction::params)
			).apply(it, ::CustomFunction)
		}

		// Decodes both string and object representations of CustomFunction, while only encoding the object representation.
		val BACKWARDS_COMPATIBLE_CODEC: Codec<CustomFunction> = Codec.either(Codec.STRING, CODEC).xmap(
			{ it.map(::fromString, Function.identity()) },
			{ Either.right(it) } // Kotlin's having some trouble when I change this to lambda reference for some reason, despite being valid.
		)

		fun fromString(text: String) = fromString(text.split('=').dropLastWhile { it.isEmpty() })

		fun fromString(split: List<String>): CustomFunction? {
			val lhs = split.firstOrNull()?.trim() ?: return null
			val match = FUNCTION_REGEX.matchEntire(lhs) ?: return null

			return CustomFunction(match.groupValues[1], split.lastOrNull()?.trim(), match.groupValues[2].split(ChatCalc.SEPARATOR))
		}
	}
}
