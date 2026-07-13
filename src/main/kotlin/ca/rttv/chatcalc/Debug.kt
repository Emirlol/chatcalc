package ca.rttv.chatcalc

import ca.rttv.chatcalc.ChatCalc.chatPrefix
import me.ancientri.rimelib.util.FabricLoader
import me.ancientri.rimelib.util.text.TextBuilder
import me.ancientri.rimelib.util.text.sendText
import me.ancientri.rimelib.util.text.text
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text

inline val debug get() = FabricLoader.isDevelopmentEnvironment || System.getProperty("chatcalc.debug").toBoolean()

/**
 * Sends a debug message to the player.
 * This is for easier visibility of debug outputs when there's not much output to see, and it's not worthy of putting into a logger as reading from logs is harder.
 *
 * Adds a chat prefix to the message.
 */
inline fun PlayerEntity.debugSend(text: Text) {
	if (debug) sendText(chatPrefix.append(text))
}

/**
 * Sends a debug message to the player.
 * This is for easier visibility of debug outputs when there's not much output to see, and it's not worthy of putting into a logger as reading from logs is harder.
 *
 * Adds a chat prefix to the message.
 */
inline fun PlayerEntity.debugSend(builder: TextBuilder.() -> Unit) {
	debugSend(text(builder))
}