package ca.rttv.chatcalc

import ca.rttv.chatcalc.config.ConfigManager
import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.display.ChatScreenDisplay
import ca.rttv.chatcalc.display.SignScreenDisplay
import me.ancientri.rimelib.util.LoggerFactory
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.text.text
import net.minecraft.screen.ScreenTexts

object ChatCalc {
	fun init() {
		ConfigManager.init() // Initialize the config manager to load the configuration
		UserEnvironmentManager.init()
		ChatScreenDisplay.init()
		SignScreenDisplay.init()
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
