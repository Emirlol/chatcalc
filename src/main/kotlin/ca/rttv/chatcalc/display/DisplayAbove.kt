package ca.rttv.chatcalc.display

import ca.rttv.chatcalc.ChatCalc
import ca.rttv.chatcalc.MathEngine
import ca.rttv.chatcalc.config.ConfigManager
import me.ancientri.rimelib.util.client
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.text.text
import net.minecraft.client.gui.DrawContext
import java.util.*

abstract class DisplayAbove() {
	protected abstract val x: Int
	protected abstract val y: Int
	private var evaluationCache: Pair<String, OptionalDouble>? = null
	protected open val centered: Boolean = true

	/**
	 * @return `true` if the keycode is handled by this class.
	 */
	open fun allowKeyPress(keycode: Int) = keycode != ChatCalc.COMPLETION_KEY

	fun shouldRender(): Boolean {
		if (!ConfigManager.config.displayAbove) {
			evaluationCache = null
			return false
		}
		return true
	}

	abstract fun parseWord(): String?

	// Screens render tooltip at the end of their render pass with `renderWithTooltip`, so any tooltip to be rendered must be added before the screen is rendered.
	// Therefore, this method should be called before the screen is rendered.
	@JvmOverloads
	fun render(drawContext: DrawContext, x: Int = this.x, y: Int = this.y) {
		val word = parseWord() ?: return
		if (ChatCalc.NUMBER.matches(word)) {
			evaluationCache = null
			return
		}
		runCatching {
			val result: Double
			if (evaluationCache != null && evaluationCache!!.first == word) {
				if (evaluationCache!!.second.isEmpty) return
				result = evaluationCache!!.second.asDouble
			} else {
				ChatCalc.FUNCTION_TABLE.clear()
				ChatCalc.CONSTANT_TABLE.clear()
				result = MathEngine.of().eval(word, arrayOfNulls(0))
				evaluationCache = word to OptionalDouble.of(result)
			}
			val text = "=${ConfigManager.config.decimalFormat.format(result)}".text(ColorPalette.TEXT)
			drawContext.drawTooltip(
				text,
				if (centered) x - (5 + client.textRenderer.getWidth(text) / 2)
				else x,
				y
			)
		}.onFailure { evaluationCache = word to OptionalDouble.empty() }
	}
}