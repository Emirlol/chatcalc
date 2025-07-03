@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.config

import ca.rttv.chatcalc.CustomConstant
import ca.rttv.chatcalc.CustomFunction
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.ancientri.symbols.config.ConfigClass
import net.minecraft.client.MinecraftClient
import java.text.DecimalFormat

@ConfigClass
data class Configv2(
	val decimalFormat: DecimalFormat = DecimalFormat("#,##0.##"),
	val radians: Boolean = true,
	val copyType: CopyType = CopyType.CHAT_HISTORY,
	val displayAbove: Boolean = true,
	val functions: MutableList<CustomFunction> = mutableListOf(),
	val constants: MutableList<CustomConstant> = mutableListOf()
) {
	inline fun convertFromDegrees(value: Double) = if (radians) Math.toRadians(value) else value

	inline fun convertFromRadians(value: Double) = if (radians) value else Math.toDegrees(value)

	inline fun convertToRadians(value: Double) = if (radians) value else Math.toRadians(value)

	inline fun saveToChatHud(input: String?) {
		if (copyType == CopyType.CHAT_HISTORY) MinecraftClient.getInstance().inGameHud.chatHud.addToMessageHistory(input)
	}

	inline fun saveToClipboard(input: String?) {
		if (copyType == CopyType.CLIPBOARD) MinecraftClient.getInstance().keyboard.clipboard = input
	}

	companion object {
		val CODEC: Codec<Configv2> = RecordCodecBuilder.create { instance ->
			instance.group(
				Codec.STRING.xmap(::DecimalFormat, DecimalFormat::toPattern).fieldOf("decimal_format").forGetter(Configv2::decimalFormat),
				Codec.BOOL.fieldOf("radians").forGetter(Configv2::radians),
				CopyType.CODEC.fieldOf("copy_type").forGetter(Configv2::copyType),
				Codec.BOOL.fieldOf("display_above").forGetter(Configv2::displayAbove),
				CustomFunction.BACKWARDS_COMPATIBLE_CODEC.listOf().fieldOf("functions").forGetter(Configv2::functions),
				CustomConstant.BACKWARDS_COMPATIBLE_CODEC.listOf().fieldOf("constants").forGetter(Configv2::constants)
			).apply(instance, ::Configv2)
		}

		const val DECIMAL_FORMAT = "decimal_format"
		const val RADIANS = "radians"
		const val COPY_TYPE = "copy_type"
		const val DISPLAY_ABOVE = "display_above"
		const val FUNCTIONS = "functions"
		const val CONSTANTS = "constants"
	}
}
