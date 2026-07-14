package ca.rttv.chatcalc

import ca.rttv.chatcalc.async.CalculationManager
import ca.rttv.chatcalc.config.ConfigManager
import ca.rttv.chatcalc.config.ConfigScreen
import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.display.ChatScreenDisplay
import ca.rttv.chatcalc.display.SignScreenDisplay
import me.ancientri.rimelib.util.CoroutineUtil
import me.ancientri.rimelib.util.LoggerFactory
import me.ancientri.rimelib.util.client
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.command.register
import me.ancientri.rimelib.util.text.text
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.screen.ScreenTexts

object ChatCalc {
	fun init() {
		ConfigManager.init() // Initialize the config manager to load the configuration
		UserEnvironmentManager.init()
		ChatScreenDisplay.init()
		SignScreenDisplay.init()
		ClientCommandRegistrationCallback.EVENT.register("chatcalc") {
			literal("config") {
				executes {
					client.send {
						client.setScreen(ConfigScreen(null))
					}
				}
			}
		}

		with(CoroutineUtil.globalJob) {
			CalculationManager.startWorker()
		}
	}

	val loggerFactory = LoggerFactory("ChatCalc")

	val chatPrefix = text {
		"[" colored ColorPalette.SURFACE3
		"ChatCalc" colored ColorPalette.ACCENT
		"]" colored ColorPalette.SURFACE1
		+ScreenTexts.SPACE
	}
		get() = field.copy()
}
