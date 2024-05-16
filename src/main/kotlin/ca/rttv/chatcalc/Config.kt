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

object Config {
    val JSON: JsonObject = JsonObject()
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val CONFIG_FILE: File = FabricLoader.getInstance().configDir.resolve("chatcalc.json").toFile()
    private val LOGGER: Logger = LoggerFactory.getLogger("Chatcalc")
    val FUNCTIONS = hashMapOf<Pair<String, Int>, CustomFunction>()
    val CONSTANTS = hashMapOf<String, CustomConstant>()
    private val DEFAULTS = mapOf(
        "decimal_format" to "#,##0.##",
        "radians" to "false",
        "copy_type" to "chat_history",
        "display_above" to "true",
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
                    it.write("    \"functions\": []\n")
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
        get() = DecimalFormat(JSON["decimal_format"].asString)

    fun convertFromDegrees(value: Double): Double {
        return if (JSON["radians"].asString.toBoolean()) Math.toRadians(value) else value
    }

    fun convertFromRadians(value: Double): Double {
        return if (JSON["radians"].asString.toBoolean()) value else Math.toDegrees(value)
    }

    fun convertToRadians(value: Double): Double {
        return if (JSON["radians"].asString.toBoolean()) value else Math.toRadians(value)
    }

    fun radians(): Boolean {
        return JSON["radians"].asString.toBoolean()
    }

    fun refreshJson() {
        runCatching {
            val writer = FileWriter(CONFIG_FILE)
            JSON.add("functions", FUNCTIONS.values.asSequence().map(CustomFunction::toString).fold(JsonArray()) { obj, string -> obj.add(string); obj })
            JSON.add("constants", CONSTANTS.values.asSequence().map(CustomConstant::toString).fold(JsonArray()) { obj, string -> obj.add(string); obj })
            writer.write(GSON.toJson(JSON))
            JSON.remove("functions")
            JSON.remove("constants")
            writer.close()
        }.onFailure { LOGGER.error("[Chatcalc] Failed to refresh the config JSON!", it) }
    }

    fun readJson() {
        runCatching {
            val json = JsonParser.parseString(CONFIG_FILE.readText()).asJsonObject

            DEFAULTS.forEach { (key, defaultValue) ->
                JSON.add(key, if (json[key].isJsonPrimitive && json[key].asJsonPrimitive.isString) json[key].asJsonPrimitive else JsonPrimitive(defaultValue))
            }

            json["functions"].let {
                if (it !is JsonArray) return@let
                for (function in it) {
                    if (function is JsonPrimitive && function.isString) {
                        val func = CustomFunction.fromString(function.getAsString())
                        if (func != null) FUNCTIONS[Pair(func.name, func.params.size)] = func
                    }
                }
            }
            json["constants"].let {
                if (it !is JsonArray) return@let
                for (constant in it) {
                    if (constant is JsonPrimitive && constant.isString) {
                        val const = CustomConstant.fromString(constant.getAsString())
                        if (const != null) CONSTANTS[const.name] = const
                    }
                }
            }
        }.onFailure { LOGGER.error("[Chatcalc] Failed to read the config JSON!", it) }
    }

    fun displayAbove(): Boolean {
        return JSON["display_above"].asString.toBoolean()
    }

    fun saveToChatHud(input: String?) {
        if (JSON["copy_type"].asString.equals("chat_history", true)) {
            MinecraftClient.getInstance().inGameHud.chatHud.addToMessageHistory(input)
        }
    }

    fun func(name: String, values: DoubleArray): Double {
        val func = FUNCTIONS[Pair(name, values.size)]
        require(func != null) { "Tried to call unknown function: $name" }
        return func.get(values)
    }

    fun saveToClipboard(input: String?) {
        if (JSON["copy_type"].asString.equals("clipboard", true)) {
            MinecraftClient.getInstance().keyboard.clipboard = input
        }
    }

    fun makeEngine(): MathEngine {
        return NibbleMathEngine()
    }
}
