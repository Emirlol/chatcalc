package ca.rttv.chatcalc.display

import ca.rttv.chatcalc.ChatCalc
import ca.rttv.chatcalc.Section
import ca.rttv.chatcalc.config.ConfigManager
import ca.rttv.chatcalc.engine.EngineResult
import ca.rttv.chatcalc.engine.MathEngine
import ca.rttv.chatcalc.engine.types.SourceRepresentable
import kotlinx.coroutines.future.future
import me.ancientri.rimelib.util.CoroutineUtil
import me.ancientri.rimelib.util.client
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.text.text
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture

// Displays the result of the expression the cursor is in as a tooltip above the text field.
abstract class DisplayAbove() {
	protected abstract val x: Int
	protected abstract val y: Int
	protected open val centered: Boolean = true

	/**
	 * @return `true` if the keycode is handled by this class.
	 */
	open fun allowKeyPress(keycode: Int) = keycode != ChatCalc.COMPLETION_KEY

	fun shouldRender(): Boolean = ConfigManager.config.displayAbove

	abstract fun parseSection(): Section?

	@Volatile
	private var calculationFuture: Pair<String, CompletableFuture<EngineResult>>? = null

	@Volatile
	private var cachedTextToRender: Pair<String, Text?>? = null

	// Screens render tooltip at the end of their render pass with `renderWithTooltip`, so any tooltip to be rendered must be added before the screen is rendered.
	// Therefore, this method should be called before the screen is rendered.
	@JvmOverloads
	fun render(drawContext: DrawContext, x: Int = this.x, y: Int = this.y) {
		val section = parseSection() ?: return
		if (ChatCalc.NUMBER.matches(section.text)) return

		val textToRender = getTextToRender(section.text) ?: return
		val lines = client.textRenderer.wrapLines(textToRender, Int.MAX_VALUE) // We just need the LineBreakingVisitor to split the lines
		val lineHeight = client.textRenderer.fontHeight
		val textWidth = lines.maxOf { client.textRenderer.getWidth(it) }
		val textHeight = lines.size * lineHeight

		val renderX = 9 + if (centered) x - (5 + textWidth / 2) else x
		val renderY = y - textHeight - 4

		val padding = 2
		val bgColor = ColorPalette.BASE.value
		drawContext.fill(renderX - padding, renderY - padding, renderX + textWidth + padding, renderY + textHeight + padding, bgColor)
		for (i in lines.indices) {
			drawContext.drawText(client.textRenderer, lines[i], renderX, renderY + i * lineHeight, ColorPalette.TEXT.value, true)
		}
	}

	private val calculating: Text get() = "Calculating...".text(ColorPalette.SUBTEXT2)

	private fun getTextToRender(evalStr: String): Text? {
		val cached = cachedTextToRender
		if (cached != null) {
			if (evalStr == cached.first) return cached.second
			cachedTextToRender = null
		}

		val calculation = calculationFuture
		if (calculation != null) {
			if (calculation.first == evalStr) {
				if (calculation.second.isDone) {
					try {
						val result = calculation.second.getNow(null)
						cachedTextToRender = evalStr to processEngineResult(evalStr, result)
					} catch (_: Exception) {
						cachedTextToRender = evalStr to null
					}
				} else return calculating
			}
			calculationFuture?.second?.cancel(true)
			calculationFuture = null
		}

		calculationFuture = evalStr to CoroutineUtil.globalJob.future { MathEngine.eval(evalStr, dryRun = true) }
		return null // No return value for the first try since it might not be a valid string at all, we don't want to show "Calculating..." on literally every string input
	}

	private fun processEngineResult(evalStr: String, result: EngineResult) = when (result) {
		is EngineResult.Value -> {
			val output = result.output
			if (output is SourceRepresentable && output.toSourceString() == evalStr) null
			else text {
				"= ".text(ColorPalette.SUBTEXT2)
				+output.toPrettyText()
			}
		}

		is EngineResult.Append -> { // This is so that we can still display the result at the end of the expression.
			val output = result.output
			text {
				"= " colored ColorPalette.SUBTEXT2
				+output.toPrettyText()
			}
		}

		is EngineResult.Clear -> result.feedback
		is EngineResult.Error<*> -> null // No need to display the error, it just means this isn't a valid expression.
	}
}