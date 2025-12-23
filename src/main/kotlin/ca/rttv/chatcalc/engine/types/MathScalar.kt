package ca.rttv.chatcalc.engine.types

import ca.rttv.chatcalc.config.ConfigManager.config
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.text.text
import net.minecraft.text.Text
import net.minecraft.util.StringIdentifiable
import kotlin.reflect.full.companionObjectInstance

sealed interface MathScalar : MathObject, SourceRepresentable {
	override val type: MathType get() = MathType.SCALAR
	val value: Double
	val radix: Int

	fun withRadix(radix: Int): MathScalar

	override fun toPrettyText(): Text = config.decimalFormat.format(value).text(ColorPalette.PEACH)

	companion object : MathObjectCodecProvider<MathScalar> {
		// Factory method
		inline operator fun invoke(value: Double, radix: Int = 10): MathScalar = RealScalar(value, radix)

		private val companionCodec = StringIdentifiable.createBasicCodec {
			MathScalarCodecProvider::class.sealedSubclasses
				.mapNotNull { it.objectInstance }
				.toTypedArray()
		}
		override val mapCodec: MapCodec<MathScalar> = companionCodec.dispatchMap(
			"impl",
			{ scalar -> scalar::class.companionObjectInstance as MathScalarCodecProvider<*> },
			MathScalarCodecProvider<*>::mapCodec
		)
	}
}

private sealed interface MathScalarCodecProvider<T : MathScalar> : MathObjectCodecProvider<T>, StringIdentifiable {
	override val mapCodec: MapCodec<T>
}

data class RealScalar(override val value: Double, override val radix: Int = 10) : MathScalar {
	override fun toSourceString(): String = formatValue(value, radix)

	override fun withRadix(radix: Int): RealScalar = RealScalar(value, radix)

	companion object : MathScalarCodecProvider<RealScalar> {
		override val mapCodec: MapCodec<RealScalar> = RecordCodecBuilder.mapCodec {
			it.group(
				Codec.DOUBLE.fieldOf("value").forGetter(RealScalar::value),
				Codec.INT.fieldOf("radix").forGetter(RealScalar::radix)
			).apply(it, ::RealScalar)
		}

		override fun asString(): String = "real"
	}
}

internal fun formatValue(value: Double, radix: Int) = when (radix) {
	// We don't care about decimal format here to obey the contract of SourceRepresentable.
	// If we shorten the decimal point, we'll lose information, and the parsed value will be different from the original.
	10 -> value.toString()
	// These have to be BigDecimal conversions because Long doesn't cut it (max value is 2^63 while doubles go up to 10^308)
	16 -> "0x" + value.toBigDecimal().toBigInteger().toString(16)
	8 -> "0o" + value.toBigDecimal().toBigInteger().toString(8)
	2 -> "0b" + value.toBigDecimal().toBigInteger().toString(2)
	else -> throw IllegalArgumentException("Unsupported radix: $radix")
}

internal fun prettyFormatValue(value: Double, radix: Int): String = when (radix) {
	// We can actually truncate the decimal point here since this value is for display purposes only.
	10 -> config.decimalFormat.format(value)
	// These have to be BigDecimal conversions because Long doesn't cut it (max value is 2^63 while doubles go up to 10^308)
	16 -> "0x" + value.toBigDecimal().toBigInteger().toString(16)
	8 -> "0o" + value.toBigDecimal().toBigInteger().toString(8)
	2 -> "0b" + value.toBigDecimal().toBigInteger().toString(2)
	else -> throw IllegalArgumentException("Unsupported radix: $radix")
}

