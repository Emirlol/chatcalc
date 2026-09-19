import ca.rttv.chatcalc.ChatCalc.chatPrefix
import me.ancientri.rimelib.util.FabricLoader

import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.Component

import me.ancientri.rimelib.util.text.TextBuilder
import me.ancientri.rimelib.util.text.text

inline val debug get() = FabricLoader.isDevelopmentEnvironment

fun Player.debugSend(text: Component) {
	if (debug) {
		this.sendSystemMessage(chatPrefix.copy().append(text))
	}
}

fun Player.debugSend(builder: TextBuilder.() -> Unit) {
	if (debug) {
		this.sendSystemMessage(chatPrefix.copy().append(text(builder)))
	}
}