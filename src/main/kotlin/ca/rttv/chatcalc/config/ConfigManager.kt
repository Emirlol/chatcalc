package ca.rttv.chatcalc.config

import ca.rttv.chatcalc.ChatCalc
import com.mojang.serialization.Codec
import me.ancientri.rimelib.config.dfu.JsonCodecConfigManager
import me.ancientri.rimelib.util.FabricLoader
import org.slf4j.Logger
import java.nio.file.Path

private const val CONFIG_FILE_NAME = "chatcalc.json"

object ConfigManager : JsonCodecConfigManager<Configv2, Configv2Builder>() {
	override val logger: Logger = ChatCalc.loggerFactory.createLogger(this)

	override val configPath: Path = FabricLoader.configDir.resolve(CONFIG_FILE_NAME)

	override val default: Configv2 get() = Configv2()

	override val codec: Codec<Configv2> = Configv2.CODEC

	override fun builder(config: Configv2) = Configv2Builder(config)
}