@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.config

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.ancientri.rimelib.util.client
import me.ancientri.symbols.config.ConfigClass

import java.text.DecimalFormat

@ConfigClass
data class Config(
	val decimalFormat: DecimalFormat = DecimalFormat("#,##0.##"),
	val radians: Boolean = true,
	val copyType: CopyType = CopyType.CHAT_HISTORY,
	val displayAbove: Boolean = true
) {
	inline fun convertFromDegrees(value: Double) = if (radians) Math.toRadians(value) else value

	inline fun convertFromRadians(value: Double) = if (radians) value else Math.toDegrees(value)

	inline fun convertToRadians(value: Double) = if (radians) value else Math.toRadians(value)

	inline fun applyCopyType(input: String?) = when (copyType) {
		CopyType.CHAT_HISTORY -> client.inGameHud.chatHud.addToMessageHistory(input)
		CopyType.CLIPBOARD -> client.keyboard.clipboard = input
		else -> {}
	}

	companion object {
		val CODEC: Codec<Config> = RecordCodecBuilder.create { instance ->
			instance.group(
				Codec.STRING.xmap(::DecimalFormat, DecimalFormat::toPattern).fieldOf(DECIMAL_FORMAT).forGetter(Config::decimalFormat),
				Codec.BOOL.fieldOf(RADIANS).forGetter(Config::radians),
				CopyType.CODEC.fieldOf(COPY_TYPE).forGetter(Config::copyType),
				Codec.BOOL.fieldOf(DISPLAY_ABOVE).forGetter(Config::displayAbove)
			).apply(instance, ::Config)
		}

		const val DECIMAL_FORMAT = "decimal_format"
		const val RADIANS = "radians"
		const val COPY_TYPE = "copy_type"
		const val DISPLAY_ABOVE = "display_above"
	}
}
