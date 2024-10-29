package ca.rttv.chatcalc

import com.mojang.datafixers.util.Either
import com.mojang.datafixers.util.Pair
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import java.util.function.Consumer
import kotlin.system.measureNanoTime

object ChatCalc {
    /**
     * Table to temporarily store constants and check for the existence of when a constant is called to prevent infinite recursion.
     */
    @JvmField
    val CONSTANT_TABLE: HashSet<String> = HashSet()
    /**
     * Table to temporarily store functions and check for the existence of when a function is called to prevent infinite recursion.
     */
    @JvmField
    val FUNCTION_TABLE: HashSet<Pair<String, Int>> = HashSet()
    @JvmField
    val NUMBER = Regex("[-+]?(\\d,?)*(\\.\\d+)?")
    val CONSTANT = Regex("[a-zA-Z]+")
    const val SEPARATOR: String = ";"
    const val SEPARATOR_CHAR: Char = ';'

    /**
     * @return Whether the text was parsed successfully.
     *         If it was, the [setMethod] will be called somewhere in the process.
     */
    @JvmStatic
    fun tryParse(originalText: String, cursor: Int, setMethod: Consumer<String>): Boolean {
        val client = MinecraftClient.getInstance()
        var text = ChatHelper.getSection(originalText, cursor)

        val split = text.split('=').dropLastWhile { it.isEmpty() }
        if (split.size == 2) {
            if (Config.JSON.has(split[0])) {
                Config.JSON.addProperty(split[0], split[1])
                Config.refreshJson()
                return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
            } else {
                val either = parseDeclaration(split)
                if (either != null) {
                    val left = either.left()
                    val right = either.right()
                    if (left.isPresent) {
                        if (FabricLoader.getInstance().isDevelopmentEnvironment) {
                            player?.sendMessage("Added custom function ${left.get().name} with ${left.get().params.size} parameters")
                        }
                        Config.FUNCTIONS[Pair(left.get().name, left.get().params.size)] = left.get()
                        Config.refreshJson()
                        return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
                    } else if (right.isPresent) {
                        if (FabricLoader.getInstance().isDevelopmentEnvironment) {
                            player?.sendMessage("Added custom constant ${left.get().name} with ${left.get().params.size} parameters")
                        }
                        Config.CONSTANTS[right.get().name] = right.get()
                        Config.refreshJson()
                        return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
                    }
                }
            }
        } else if (split.size == 1) {
            if (Config.JSON.has(split[0])) {
                return ChatHelper.replaceSection(originalText, cursor, Config.JSON[split[0]].asString, setMethod)
            } else if (split[0].isNotEmpty() && Config.JSON.has(split[0].substring(0, split[0].length - 1)) && split[0].endsWith("?") && client.player != null) {
                player?.sendText(Text.translatable("chatcalc." + split[0].substring(0, split[0].length - 1) + ".description"))
                return false
            } else {
                val either = parseDeclaration(split)
                if (either != null) {
                    val left = either.left()
                    val right = either.right()
                    if (left.isPresent) {
                        val pair = Pair(left.get().name, left.get().params.size)
                        if (Config.FUNCTIONS.containsKey(pair)) {
                            Config.FUNCTIONS.remove(pair)
                            if (FabricLoader.getInstance().isDevelopmentEnvironment) {
                                player?.sendMessage("Removed custom function ${left.get().name} with ${left.get().params.size} parameters")
                            }
                            Config.refreshJson()
                            return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
                        }
                    } else if (right.isPresent && Config.CONSTANTS.containsKey(right.get().name)) {
                        Config.CONSTANTS.remove(right.get().name)
                        if (FabricLoader.getInstance().isDevelopmentEnvironment) {
                            player?.sendMessage("Removed custom constant ${left.get().name} with ${left.get().params} parameters")
                        }
                        Config.refreshJson()
                        return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
                    }
                }
            }
        }

        when {
            (text == "config?" || text == "cfg?" || text == "?") -> {
                player?.sendText(Text.translatable("chatcalc.config.description"))
                return false
            }

            text == "testcases?" -> {
                Testcases.test(Testcases.TESTCASES)
                return false
            }

            text == "functions?" -> {
                player?.sendText(
                    Config.FUNCTIONS.values.asSequence()
                        .map(CustomFunction::toString)
                        .map { Text.literal(it).styled { style: Style -> style.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, it)).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy to clipboard"))) } }
                        .reduceOrNull { a, b -> a.append(Text.literal("\n").append(b)) }
                        ?.let { Text.literal("Currently defined custom functions are:").append(it) }
                        ?: Text.literal("There are no custom functions defined.")
                )
                return false
            }

            text == "constants?" -> {
                player?.sendText(
                    Config.CONSTANTS.values.asSequence()
                        .map(CustomConstant::toString)
                        .map { Text.literal(it).styled { style: Style -> style.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, it)).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy to clipboard"))) } }
                        .reduceOrNull { a, b -> a.append(Text.literal("\n").append(b)) }
                        ?.let { Text.literal("Currently defined custom constants are: \n").append(it) }
                        ?: Text.literal("There are no custom constants defined.")
                )
                return false
            }

            NUMBER.matches(text) -> return false

            else -> {
                var add = false
                if (text.endsWith("=")) {
                    text = text.substring(0, text.length - 1)
                    add = true
                }
                try {
                    val result: Double
                    measureNanoTime {
                        CONSTANT_TABLE.clear()
                        FUNCTION_TABLE.clear()
                        result = Config.makeEngine().eval(text, arrayOfNulls(0))
                    }.let {
                        if (FabricLoader.getInstance().isDevelopmentEnvironment) {
                            player?.sendMessage("Took " + it + "ns to parse equation", true)
                            player?.sendMessage("Took " + it + "ns to parse equation", false)
                        }
                    }
                    var solution = Config.decimalFormat.format(result) // so fast that creating a new one everytime doesn't matter, also lets me use fields
                    if (solution == "-0") {
                        solution = "0"
                    }
                    Config.saveToChatHud(originalText)
                    Config.saveToClipboard(originalText)
                    return if (add) ChatHelper.addSectionAfterIndex(text, cursor, "=$solution", setMethod) else ChatHelper.replaceSection(text, cursor, solution, setMethod)
                } catch (t: Throwable) {
                    return false
                }
            }
        }
    }

    private fun parseDeclaration(split: List<String>) = CustomFunction.fromString(split)?.let { Either.left(it) } ?: CustomConstant.fromString(split).let { if (it != null) Either.right(it) else null }
}
