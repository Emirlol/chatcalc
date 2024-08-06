package ca.rttv.chatcalc

import com.google.gson.*
import com.mojang.datafixers.util.Pair
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import kotlin.system.measureTimeMillis

object Config {
	val JSON: JsonObject = JsonObject()
	private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
	private val CONFIG_FILE: File = FabricLoader.getInstance().configDir.resolve("chatcalc.json").toFile()
	private val LOGGER: Logger = LoggerFactory.getLogger("Chatcalc")
	val FUNCTIONS = hashMapOf<Pair<String, Int>, CustomFunction>()
	val CONSTANTS = hashMapOf<String, CustomConstant>()
	private val DEFAULTS = mapOf(
		ConfigConstants.DECIMAL_FORMAT to ConfigConstants.DECIMAL_FORMAT_DEFAULT,
		ConfigConstants.RADIANS to false.toString(),
		ConfigConstants.COPY_TYPE to ConfigConstants.CHAT_HISTORY,
		ConfigConstants.DISPLAY_ABOVE to true.toString(),
	)

	init {
		if (!CONFIG_FILE.exists()) {
			runCatching {
				CONFIG_FILE.createNewFile()
				CONFIG_FILE.writer().use {
					it.write("{\n")
					for ((key, value) in DEFAULTS) {
						it.write("    \"$key\": \"$value\",\n")
					}
					it.write("    \"${ConfigConstants.FUNCTIONS}\": []\n")
					it.write("}")
				}
			}.onFailure { LOGGER.error("[Chatcalc] Failed to write the config JSON!", it) }
		}

		if (CONFIG_FILE.exists() && CONFIG_FILE.isFile && CONFIG_FILE.canRead()) {
			readJson()
		} else {
			LOGGER.error("[Chatcalc] Failed to read the config JSON!")
		}
	}

	val decimalFormat: DecimalFormat
		get() = try {
			DecimalFormat(JSON[ConfigConstants.DECIMAL_FORMAT].asString)
		} catch (e: Exception) {
			LOGGER.error("[Chatcalc] Invalid decimal format config! Defaulting back to \"${ConfigConstants.DECIMAL_FORMAT_DEFAULT}\".", e)
			DecimalFormat(ConfigConstants.DECIMAL_FORMAT_DEFAULT)
		}

	fun convertFromDegrees(value: Double) = if (JSON[ConfigConstants.RADIANS].asString.toBoolean()) Math.toRadians(value) else value

	fun convertFromRadians(value: Double) = if (JSON[ConfigConstants.RADIANS].asString.toBoolean()) value else Math.toDegrees(value)

	fun convertToRadians(value: Double) = if (JSON[ConfigConstants.RADIANS].asString.toBoolean()) value else Math.toRadians(value)

	fun radians() = JSON[ConfigConstants.RADIANS].asString.toBoolean()

	fun refreshJson() {
		runCatching {
			val writer = FileWriter(CONFIG_FILE)
			JSON.add(ConfigConstants.FUNCTIONS, FUNCTIONS.values.asSequence().map(CustomFunction::toString).fold(JsonArray()) { obj, string -> obj.add(string); obj })
			JSON.add(ConfigConstants.CONSTANTS, CONSTANTS.values.asSequence().map(CustomConstant::toString).fold(JsonArray()) { obj, string -> obj.add(string); obj })
			writer.write(GSON.toJson(JSON))
			JSON.remove(ConfigConstants.FUNCTIONS)
			JSON.remove(ConfigConstants.CONSTANTS)
			writer.close()
		}.onFailure { LOGGER.error("[Chatcalc] Failed to refresh the config JSON!", it) }
	}

	fun readJson() = measureTimeMillis {
		runCatching {
			val json = JsonParser.parseString(CONFIG_FILE.readText()).asJsonObject

			DEFAULTS.forEach { (key, defaultValue) ->
				JSON.add(key, if (json[key].isJsonPrimitive && json[key].asJsonPrimitive.isString) json[key].asJsonPrimitive else JsonPrimitive(defaultValue))
			}

			json[ConfigConstants.FUNCTIONS].let {
				if (it !is JsonArray) return@let
				for (function in it) {
					if (function is JsonPrimitive && function.isString) {
						val func = CustomFunction.fromString(function.getAsString())
						if (func != null) FUNCTIONS[Pair(func.name, func.params.size)] = func
					}
				}
			}
			json[ConfigConstants.CONSTANTS].let {
				if (it !is JsonArray) return@let
				for (constant in it) {
					if (constant is JsonPrimitive && constant.isString) {
						val const = CustomConstant.fromString(constant.getAsString())
						if (const != null) CONSTANTS[const.name] = const
					}
				}
			}
		}.onFailure { LOGGER.error("[Chatcalc] Failed to read the config JSON!", it) }
	}.let { LOGGER.info("[Chatcalc] Loaded config in $it ms") }

	fun displayAbove() = JSON[ConfigConstants.DISPLAY_ABOVE].asString.toBoolean()

	fun saveToChatHud(input: String?) {
		if (JSON[ConfigConstants.COPY_TYPE].asString.equals(ConfigConstants.CHAT_HISTORY, true)) {
			MinecraftClient.getInstance().inGameHud.chatHud.addToMessageHistory(input)
		}
	}

	fun func(name: String, values: DoubleArray): Double {
		val func = FUNCTIONS[Pair(name, values.size)]
		requireNotNull(func) { "Tried to call unknown function: $name" }
		return func.get(values)
	}

	fun saveToClipboard(input: String?) {
		if (JSON[ConfigConstants.COPY_TYPE].asString.equals(ConfigConstants.CLIPBOARD, true)) {
			MinecraftClient.getInstance().keyboard.clipboard = input
		}
	}

	fun makeEngine(): MathEngine = NibbleMathEngine()

	object ConfigConstants {
		const val DECIMAL_FORMAT = "decimal_format"
		const val RADIANS = "radians"
		const val COPY_TYPE = "copy_type"
		const val DISPLAY_ABOVE = "display_above"
		const val FUNCTIONS = "functions"
		const val CONSTANTS = "constants"
		const val CLIPBOARD = "clipboard"
		const val CHAT_HISTORY = "chat_history"
		const val DECIMAL_FORMAT_DEFAULT = "#,##0.##"
	}
}
