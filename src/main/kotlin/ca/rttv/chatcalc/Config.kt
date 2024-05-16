package ca.rttv.chatcalc

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap
import com.google.gson.*
import com.mojang.datafixers.util.Pair
import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.DecimalFormat
import java.util.stream.Collectors

object Config {
    val JSON: JsonObject = JsonObject()
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val CONFIG_FILE: File = File(".", "config/chatcalc.json")
    val FUNCTIONS: MutableMap<Pair<String, Int>, CustomFunction>
    val CONSTANTS: MutableMap<String, CustomConstant>
    val LOGGER = LoggerFactory.getLogger("Chatcalc")
    private val DEFAULTS: ImmutableMap<String, String> = ImmutableMap.builder<String, String>()
        .put("decimal_format", "#,##0.##")
        .put("radians", "false")
        .put("copy_type", "chat_history")
        .put("display_above", "true")
        .build()

    init {
        val dir = File(".", "config")
        if ((dir.exists() && dir.isDirectory || dir.mkdirs()) && !CONFIG_FILE.exists()) {
            runCatching {
                CONFIG_FILE.createNewFile()
                val writer = FileWriter(CONFIG_FILE)
                writer.write("{\n")
                for ((key, value) in DEFAULTS) {
                    writer.write(String.format("    \"%s\": \"%s\",%n", key, value))
                }
                writer.write("    \"functions\": []\n")
                writer.write("}")
                writer.close()
            }.onFailure { LOGGER.error("[Chatcalc] Failed to write the config JSON!", it) }
        }
        FUNCTIONS = hashMapOf()
        CONSTANTS = hashMapOf()
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
            BufferedReader(FileReader(CONFIG_FILE)).use { reader ->
                val json = runCatching { JsonParser.parseString(reader.lines().collect(Collectors.joining("\n"))).asJsonObject }.getOrElse { JsonObject() }

                DEFAULTS.forEach { (key, defaultValue) -> JSON.add(key, if (json[key].isJsonPrimitive && json[key].asJsonPrimitive.isString) json[key].asJsonPrimitive else JsonPrimitive(defaultValue)) }
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
        Preconditions.checkArgument(func != null) { "Tried to call unknown function: $name" }
        return func!!.get(values)
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
