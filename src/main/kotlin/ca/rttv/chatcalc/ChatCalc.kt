package ca.rttv.chatcalc

import ca.rttv.chatcalc.config.ConfigManager
import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.display.ChatScreenDisplay
import ca.rttv.chatcalc.display.SignScreenDisplay
import ca.rttv.chatcalc.engine.EngineError
import ca.rttv.chatcalc.engine.EngineResult.*
import ca.rttv.chatcalc.engine.MathEngine
import ca.rttv.chatcalc.engine.types.SourceRepresentable
import me.ancientri.rimelib.util.LoggerFactory
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.player
import me.ancientri.rimelib.util.text.sendText
import me.ancientri.rimelib.util.text.text
import net.minecraft.screen.ScreenTexts
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer

object ChatCalc {
	fun init() {
		ConfigManager.init() // Initialize the config manager to load the configuration
		UserEnvironmentManager.init()
		ChatScreenDisplay.init()
		SignScreenDisplay.init()
	}

	val loggerFactory = LoggerFactory("ChatCalc")

	const val COMPLETION_KEY = GLFW.GLFW_KEY_TAB

	val NUMBER = Regex("[-+]?(\\d,?)*(\\.\\d+)?")

	val chatPrefix = text {
		"[" colored ColorPalette.SURFACE3
		"ChatCalc" colored ColorPalette.ACCENT
		"]" colored ColorPalette.SURFACE1
		+ScreenTexts.SPACE
	}
		get() = field.copy()

	/**
	 * @return Whether the text was parsed successfully.
	 *         If it was, the [setMethod] will be called somewhere in the process.
	 */
	@JvmStatic
	fun tryParse(originalText: String, cursor: Int, setMethod: Consumer<String>): Boolean {
		val section = ChatHelper.getSection(originalText, cursor)
		try {
			when (val result = MathEngine.eval(section.text, dryRun = false)) {
				is Clear -> ChatHelper.replaceSection(section, "", setMethod)
				is Value -> {
					val output = result.output
					if (output is SourceRepresentable) {
						val str = output.toSourceString()
						if (str == null) player?.sendText {
							+chatPrefix
							"Output value cannot be represented in source form." colored ColorPalette.ERROR
						} else ChatHelper.replaceSection(section, str, setMethod)
					} else return false // Builtin function or something that can't be represented in source form
				}

				is Append -> {
					val output = result.output
					if (output is SourceRepresentable) {
						val str = output.toSourceString()
						if (str == null) player?.sendText {
							+chatPrefix
							"Output value cannot be represented in source form." colored ColorPalette.ERROR
						} else ChatHelper.appendAfterSection(section, str, setMethod)
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
}
