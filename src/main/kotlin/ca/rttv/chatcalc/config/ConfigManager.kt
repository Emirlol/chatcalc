package ca.rttv.chatcalc.config

import me.ancientri.rimelib.config.impl.JsonCodecConfigManager
import me.ancientri.rimelib.util.FabricLoader

private const val CONFIG_FILE_NAME = "chatcalc.json"

object ConfigManager : JsonCodecConfigManager<Configv2, Configv2Builder>(
	FabricLoader.configDir.resolve(CONFIG_FILE_NAME),
	Configv2.CODEC,
	Configv2()
) {
	override fun builder(config: Configv2) = Configv2Builder(config)

	override fun setFromBuilder(builder: Configv2Builder): Configv2 {
		config = builder.build()
		return config
	}
}