@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.engine.types

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.text.text
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

/**
 * An interface for objects that can be turned back into their source representation, so they can be parsed back.
 *
 * Classes that might conditionally be source-representable should return null if their condition is not satisfied.
 */
interface SourceRepresentable {
	fun toSourceString(): String?
}

sealed interface MathObject {
	val type: MathType

	/**
	 * Used for displaying this object in the UI with colors. Does not have to conform to the language's format.
	 */
	fun toPrettyText(): Text

	companion object {
		private val types: EnumMap<MathType, KClass<out MathObject>> = EnumMap(
			mapOf(
				MathType.SCALAR to MathScalar::class,
				MathType.TENSOR to MathTensor::class,
				MathType.BOOLEAN to MathBoolean::class,
				MathType.FUNCTION to UserDefinedFunction::class
			)
		)

		val CODEC: Codec<MathObject> = MathType.CODEC.dispatch(
			"type",
			{ mathObject ->
				mathObject.type
			},
			{ typeName ->
				val type = requireNotNull(types[typeName]) { "MathObject type not found for typeName $typeName" }
				type.mapCodec
			}
		)
	}
}


@JvmInline
value class MathBoolean(val value: Boolean) : MathObject, SourceRepresentable {
	override val type: MathType get() = MathType.BOOLEAN

	override fun toSourceString(): String = value.toString() // TODO: Check whether this is formatted properly (mainly about capitalization)

	override fun toPrettyText(): Text = if (value) {
		"true".text(ColorPalette.SUCCESS)
	} else {
		"false".text(ColorPalette.ERROR)
	}

	companion object : MathObjectCodecProvider<MathBoolean> {
		override val mapCodec: MapCodec<MathBoolean> = Codec.BOOL.fieldOf("value").xmap(::MathBoolean, MathBoolean::value)
	}
}

data class ComputedProperty(val name: String, val supplier: () -> MathObject) : MathObject {
	override val type: MathType get() = MathType.ANY

	override fun toPrettyText(): Text = ScreenTexts.ELLIPSIS // Not meant to be displayed directly. The value is unwrapped by the interpreter anyway, this will never be visible.
}

sealed interface MathObjectCodecProvider<T : MathObject> {
	val mapCodec: MapCodec<T>
}

val <T : MathObject> KClass<T>.mapCodec: MapCodec<T>
	get() = (companionObjectInstance as? MathObjectCodecProvider<T>)?.mapCodec ?: throw IllegalStateException("MathObjectCodecProvider not found for type $this")
