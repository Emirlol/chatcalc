package ca.rttv.chatcalc.display

import ca.rttv.chatcalc.ChatCalc.chatPrefix
import ca.rttv.chatcalc.ChatHelper
import ca.rttv.chatcalc.Section
import ca.rttv.chatcalc.async.CalculationManager
import ca.rttv.chatcalc.config.ConfigManager
import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.debugSend
import ca.rttv.chatcalc.display.Display.CachedResult.Success
import ca.rttv.chatcalc.engine.EngineError
import ca.rttv.chatcalc.engine.EngineResult
import ca.rttv.chatcalc.engine.EngineResult.*
import ca.rttv.chatcalc.engine.env.applyDeltas
import ca.rttv.chatcalc.engine.lexer.Lexer
import ca.rttv.chatcalc.engine.parser.Parser
import ca.rttv.chatcalc.engine.types.SourceRepresentable
import me.ancientri.rimelib.util.client
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.player
import me.ancientri.rimelib.util.text.sendText
import me.ancientri.rimelib.util.text.text
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern

// Displays the result of the expression the cursor is in as a tooltip above the text field.
abstract class Display() {
	protected abstract val x: Int
	protected abstract val y: Int
	protected open val centered: Boolean = true

	/**
	 * @return `true` if the keycode is handled by this class.
	 */
	open fun allowKeyPress(keycode: Int) = keycode != COMPLETION_KEY

	/**
	 * Calculates and caches the expression around the cursor.
	 *
	 * Does not cause side effects.
	 */
	fun calculateForRendering() {
		val section = parseSection()
		if (section == null || section.text.isEmpty() || NUMBER_PATTERN.matcher(section.text).matches()) {
			calculationFuture?.second?.cancel(true)
			calculationFuture = null
			cachedResult = null
			return
		}
		if (section.text == cachedResult?.section?.text) return // No need to trigger a new calculation if the text hasn't changed

		val parsed = try {
			val (lexed, source) = Lexer(section.text).scanTokens()
			if (lexed.isEmpty()) return
			Parser(lexed, source).parse()
		} catch (_: EngineError) {
			return // Not a valid expression, no need to pass the message
		}

		calculationFuture = section.text to CalculationManager.requestInterpretation(parsed)
	}

	fun shouldRender(): Boolean = ConfigManager.config.displayAbove

	/**
	 * Parses the section the cursor is in.
	 */
	abstract fun parseSection(): Section?

	@Volatile
	private var calculationFuture: Pair<String, CompletableFuture<EngineResult>>? = null

	private var cachedResult: CachedResult? = null

	// Screens render tooltip at the end of their render pass with `renderWithTooltip`, so any tooltip to be rendered must be added before the screen is rendered.
	// Therefore, this method should be called before the screen is rendered.
	@JvmOverloads
	fun render(drawContext: DrawContext, x: Int = this.x, y: Int = this.y) {
		val section = parseSection() ?: return
		if (NUMBER_PATTERN.matcher(section.text).matches()) return // Displaying basic numbers is not useful and might be obtrusive

		val textToRender = getTextToRender(section) ?: return
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

	private fun getTextToRender(section: Section): Text? {
		val cached = cachedResult
		if (cached != null && cached is CachedResult.Success) {
			if (section.text == cached.section.text) return cached.renderText
			cachedResult = null
		}

		val calculation = calculationFuture
		if (calculation != null) {
			if (calculation.first == section.text) {
				// Repeatedly checking this on render is easier to manage than callbacks, which would require a lot of extra code to handle edge cases
				if (calculation.second.isDone) {
					try {
						val result = calculation.second.getNow(null)
						cachedResult = CachedResult.Success(section, result, processEngineResult(section.text, result))
					} catch (_: Exception) {
						cachedResult = CachedResult.Failure(section)
					}
				} else return calculating
			}
			calculationFuture?.second?.cancel(true)
			calculationFuture = null
		}

		return null
	}

	private fun processEngineResult(evalStr: String, result: EngineResult) = when (result) {
		is Value -> {
			val output = result.output
			if (output is SourceRepresentable && output.toSourceString() == evalStr) null // No need to display the exact same thing the user input
			else text {
				"= ".text(ColorPalette.SUBTEXT2)
				+output.toPrettyText()
			}
		}

		is Append -> { // This is so that we can still display the result at the end of the expression.
			val output = result.output
			text {
				"= " colored ColorPalette.SUBTEXT2
				+output.toPrettyText()
			}
		}

		is Clear -> result.feedback
		is Error<*> -> null // No need to display the error, it just means this isn't a valid expression.
	}

	/**
	 * Attempts to parse the text at the given cursor position.
	 * @return Whether the text was parsed successfully.
	 *         If it was, the [setMethod] will be called somewhere in the process.
	 */
	fun applyResults(setMethod: Consumer<String>): Boolean {
		val cached = cachedResult ?: return false
		if (cached !is Success) return false

		try {
			when (val result = cached.result) {
				is Clear -> {
					UserEnvironmentManager.config.applyDeltas(result.deltas.asIterable())
					ChatHelper.replaceSection(cached.section, "", setMethod)
				}
				is Value -> {
					UserEnvironmentManager.config.applyDeltas(result.deltas.asIterable())
					val output = result.output
					if (output is SourceRepresentable) {
						when (val str = output.toSourceString()) {
							null -> player?.sendText {
								+chatPrefix
								"Output value cannot be represented in source form." colored ColorPalette.ERROR
							}
							else -> {
								ChatHelper.replaceSection(cached.section, str, setMethod)
								ConfigManager.config.applyCopyType(str)
							}
						}
					} else return false // Builtin function or something that can't be represented in source form
				}

				is Append -> {
					UserEnvironmentManager.config.applyDeltas(result.deltas.asIterable())
					val output = result.output
					if (output is SourceRepresentable) {
						when (val str = output.toSourceString()) {
							null -> player?.sendText {
								+chatPrefix
								"Output value cannot be represented in source form." colored ColorPalette.ERROR
							}
							else -> {
								ChatHelper.appendAfterSection(cached.section, str, setMethod)
								ConfigManager.config.applyCopyType(str)
							}
						}
					} else return false
				}

				is Error<*> -> player?.debugSend {
					+result.error.messageText
				}
			}
			return true
		} catch (e: EngineError) {
			player?.debugSend {
				+e.messageText
			}
			return false
		}
	}

	companion object {
		const val COMPLETION_KEY = GLFW.GLFW_KEY_TAB
		val NUMBER_PATTERN: Pattern = Pattern.compile("[-+]?(\\d,?)*(\\.\\d+)?")
	}

	protected sealed interface CachedResult {
		val section: Section
		/**
		 * Represents a successful calculation.
		 */
		data class Success(override val section: Section, val result: EngineResult, val renderText: Text? = null) : CachedResult

		/**
		 * Represents a failed calculation, which can be used to avoid re-calculating the same section again.
		 */
		data class Failure(override val section: Section) : CachedResult
	}
}