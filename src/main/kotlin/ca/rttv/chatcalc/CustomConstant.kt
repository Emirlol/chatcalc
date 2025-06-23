package ca.rttv.chatcalc

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.function.Function

//eval is nullable to allow removing the constant from the config JSON.
//It's only null when the constant is being removed.
data class CustomConstant(val name: String, val eval: String?) {
	fun get(): Double {
		require(name !in ChatCalc.CONSTANT_TABLE) { "Tried to compute constant a second time, recursively" }
		requireNotNull(eval) { "This constant isn't meant to have a value!" }

		ChatCalc.CONSTANT_TABLE += name
		val value = MathEngine.of().eval(eval, arrayOfNulls(0))
		ChatCalc.CONSTANT_TABLE -= name
		return value
	}

	override fun toString() = "$name=$eval"

	companion object {
		private val CODEC = RecordCodecBuilder.create {
			it.group(
				Codec.STRING.fieldOf("name").forGetter(CustomConstant::name),
				Codec.STRING.fieldOf("eval").forGetter(CustomConstant::eval)
			).apply(it, ::CustomConstant)
		}

		val BACKWARDS_COMPATIBLE_CODEC: Codec<CustomConstant> = Codec.either(Codec.STRING, CODEC).xmap(
			{ it.map(::fromString, Function.identity()) },
			{ Either.right(it) } // Kotlin's having some trouble when I change this to lambda reference for some reason, despite being valid.
		)

		fun fromString(text: String) = fromString(text.split('=').dropLastWhile { it.isEmpty() })

		fun fromString(split: List<String>): CustomConstant? {
			val lhs = split.firstOrNull()?.trim() ?: return null
			if (!ChatCalc.CONSTANT.matches(lhs)) return null

			return CustomConstant(lhs, split.lastOrNull()?.trim())
		}
	}
}
