import ca.rttv.chatcalc.ChatCalc.chatPrefix
import me.ancientri.rimelib.util.FabricLoader
import me.ancientri.rimelib.util.text.TextBuilder
import me.ancientri.rimelib.util.text.sendText
import me.ancientri.rimelib.util.text.text
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text

inline val debug get() = FabricLoader.isDevelopmentEnvironment

fun PlayerEntity.debugSend(text: Text) {
	if (debug) sendText(chatPrefix.append(text))
}

fun PlayerEntity.debugSend(builder: TextBuilder.() -> Unit) {
	if (debug) sendText(chatPrefix.append(text(builder)))
}