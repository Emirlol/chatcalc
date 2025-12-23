package ca.rttv.chatcalc.config

import ca.rttv.chatcalc.ChatCalc
import ca.rttv.chatcalc.engine.env.UserEnvironment
import com.mojang.serialization.Codec
import me.ancientri.rimelib.config.dfu.JsonCodecConfigManager
import me.ancientri.rimelib.util.FabricLoader
import org.slf4j.Logger
import java.nio.file.Path

private const val ENV_FILE_NAME = "chatcalc_userenv.json"

object UserEnvironmentManager : JsonCodecConfigManager<UserEnvironment, UserEnvironment.Builder>() {
	override val codec: Codec<UserEnvironment>
		get() = UserEnvironment.CODEC

	override val logger: Logger = ChatCalc.loggerFactory.createLogger(this)

	override val configPath: Path = FabricLoader.configDir.resolve(ENV_FILE_NAME)

	override val default: UserEnvironment
		get() = UserEnvironment()

	override fun builder(config: UserEnvironment): UserEnvironment.Builder = UserEnvironment.Builder(config)
}