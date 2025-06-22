package ca.rttv.chatcalc.config

import me.ancientri.rimelib.config.impl.JsonCodecConfigManager
import me.ancientri.rimelib.util.FabricLoader

private const val CONFIG_FILE_NAME = "chatcalc.json"

object ConfigManager : JsonCodecConfigManager<Configv2, Configv2Builder>(
	FabricLoader.configDir.resolve(CONFIG_FILE_NAME),
	Configv2.CODEC,
	Configv2()
) {
	override var config: Configv2 = loadConfig() ?: default
		private set

	override fun modifyConfig(builder: Configv2Builder.() -> Unit): Configv2 = config.update(builder)

	override fun setConfig(config: Configv2) {
		this.config = config
	}
}