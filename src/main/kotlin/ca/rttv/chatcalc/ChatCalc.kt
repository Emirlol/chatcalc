package ca.rttv.chatcalc

import ca.rttv.chatcalc.config.ChatConfigHelper
import ca.rttv.chatcalc.config.ConfigManager
import ca.rttv.chatcalc.config.ConfigManager.config
import com.mojang.datafixers.util.Either
import com.mojang.datafixers.util.Pair
import debugSend
import me.ancientri.rimelib.util.LoggerFactory
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.interleaveWith
import me.ancientri.rimelib.util.player
import me.ancientri.rimelib.util.text.sendText
import me.ancientri.rimelib.util.text.text
import me.ancientri.rimelib.util.text.translatable
import net.minecraft.client.MinecraftClient
import net.minecraft.screen.ScreenTexts
import java.util.function.Consumer

object ChatCalc {
	fun init() {
		ConfigManager // Initialize the config manager to load the configuration
	}

	val loggerFactory = LoggerFactory("ChatCalc")

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

	val chatPrefix = text {
		"[" colored ColorPalette.SURFACE3
		"ChatCalc" colored ColorPalette.ACCENT
		"]" colored ColorPalette.SURFACE1
		+ScreenTexts.SPACE
	}
		get() = field.copy()

	/**
	 * @return Whether the text was parsed successfully.
	 *         If it was, the [setMethod] will be called somewhere in the process.
	 */
	@JvmStatic
	fun tryParse(originalText: String, cursor: Int, setMethod: Consumer<String>): Boolean {
		player?.debugSend("--- Parsing ---".text(ColorPalette.ACCENT))
		val client = MinecraftClient.getInstance()
		var text = ChatHelper.getSection(originalText, cursor)
		// region debug stuff
		player?.debugSend {
			"Original text: " colored ColorPalette.TEXT
			originalText colored ColorPalette.ACCENT
		}
		player?.debugSend {
			"Cursor: " colored ColorPalette.TEXT
			cursor.toString() colored ColorPalette.ACCENT
		}
		player?.debugSend {
			"Section text: " colored ColorPalette.TEXT
			text colored ColorPalette.ACCENT
		} // endregion

		val split = text.split('=').dropLastWhile(String::isEmpty).dropWhile(String::isEmpty) // Split on the first '=' and remove any empty strings at the end or beginning
		// region debug stuff
		player?.debugSend {
			"Split: " colored ColorPalette.TEXT
			split.toString() colored ColorPalette.ACCENT
		} // endregion
		if (split.size == 2) {
			val configValue = ChatConfigHelper.getConfigValue(split[0])
			if (configValue != null) {
				try {
					ChatConfigHelper.putConfigValue(split[0], split[1])
				} catch (e: IllegalArgumentException) {
					player?.sendText {
						+chatPrefix
						e.message?.colored(ColorPalette.ERROR) ?: "Invalid value for given key".colored(ColorPalette.ERROR) // This shouldn't happen, but just in case
					}
					return false
				}
				return ChatHelper.replaceSection(originalText, cursor, "", setMethod) // Remove the section to indicate the value was parsed
			} else {
				val either = parseDeclaration(split)
				if (either != null) {
					val left = either.left()
					val right = either.right()
					if (left.isPresent) {
						var removed = false
						// Remove any existing function first, so we can overwrite it
						ConfigManager.updateConfig {
							// It has to be made mutable in case it's not already. This is the case when the config is loaded from a file.
							// If it's already an ArrayList, then this operation uses arrayCopy which is very fast anyway, so it's not a big deal if we're unnecessarily making a copy.
							functions = functions.toMutableList()
							removed = functions.removeIf { it.name == left.get().name && it.params.size == left.get().params.size } // Has to be an exact overload match, otherwise we'll end up deleting unexpected functions
							functions += left.get()
						}
						player?.sendText {
							+chatPrefix
							if (removed) "Overwrote existing custom function " colored ColorPalette.TEXT
							else "Added custom function " colored ColorPalette.TEXT

							left.get().name colored ColorPalette.ACCENT
							" with " colored ColorPalette.TEXT
							left.get().params.size.toString() colored ColorPalette.ACCENT
							" parameters" colored ColorPalette.TEXT
						}
						return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
					} else if (right.isPresent) {
						var removed = false
						ConfigManager.updateConfig {
							constants = constants.toMutableList()
							removed = constants.removeIf { it.name == right.get().name }
							constants += right.get()
						}
						player?.sendText {
							+chatPrefix
							if (removed) "Overwrote existing custom constant " colored ColorPalette.TEXT
							else "Added custom constant " colored ColorPalette.TEXT

							right.get().name colored ColorPalette.ACCENT
							" = " colored ColorPalette.TEXT
							right.get().eval.orEmpty() colored ColorPalette.ACCENT
						}

						return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
					}
				}
			}
		} else if (split.size == 1) {
			val lhs = split[0].trim()
			val configValue = ChatConfigHelper.getConfigValue(lhs)
			if (configValue != null) {
				return ChatHelper.replaceSection(originalText, cursor, configValue, setMethod)
			} else if (ChatConfigHelper.getConfigValue(lhs.substring(0, lhs.length - 1)) != null && lhs.endsWith("?") && client.player != null) {
				player?.sendText {
					+chatPrefix
					("chatcalc." + lhs.substring(0, lhs.length - 1) + ".description").translatable colored ColorPalette.TEXT
				}
				return false
			} else if (text.getOrNull(split[0].length) == '=') { // The = check allows only acting if this is a declaration in the form of `expr=` rather than `=expr`
				val either = parseDeclaration(split)
				if (either != null) {
					val left = either.left()
					val right = either.right()
					if (left.isPresent) {
						var removed = false
						ConfigManager.updateConfig {
							functions = functions.toMutableList()
							removed = functions.removeIf { it.name == left.get().name && it.params.size == left.get().params.size } // Has to be an exact overload match, otherwise we'll end up deleting unexpected functions
						}
						if (removed) {
							// region debug stuff
							player?.sendText {
								+chatPrefix
								"Removed custom function " colored ColorPalette.TEXT
								left.get().name colored ColorPalette.ACCENT
								" with " colored ColorPalette.TEXT
								left.get().params.size.toString() colored ColorPalette.ACCENT
								" parameters" colored ColorPalette.TEXT
							} // endregion
							return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
						} else {
							player?.sendText {
								+chatPrefix
								"There's no function with the signature " colored ColorPalette.WARNING
								left.get().name colored ColorPalette.ACCENT
								left.get().params.joinToString(";", "(", ")") colored ColorPalette.ACCENT
								"." colored ColorPalette.WARNING
							}
							return false
						}
					} else if (right.isPresent) {
						var removed = false
						ConfigManager.updateConfig {
							constants = constants.toMutableList()
							removed = constants.removeIf { it.name == right.get().name }
						}
						if (removed) {
							player?.sendText {
								+chatPrefix
								"Removed custom constant " colored ColorPalette.TEXT
								right.get().name colored ColorPalette.ACCENT
								"=" colored ColorPalette.TEXT
								right.get().eval.orEmpty() colored ColorPalette.ACCENT
							}
							return ChatHelper.replaceSection(originalText, cursor, "", setMethod)
						} else player?.sendText {
							+chatPrefix
							"There's no constant with the name " colored ColorPalette.WARNING
							right.get().name colored ColorPalette.ACCENT
							"." colored ColorPalette.WARNING
							return false
						}
					}
				}
			}
		}

		when {
			(text == "config?" || text == "cfg?") -> {
				player?.sendText("chatcalc.config.description".translatable.text(ColorPalette.TEXT))
				return false
			}

			text == "testcases?" -> {
				Testcases.test(Testcases.TESTCASES)
				return false
			}

			text == "functions?" -> {
				player?.sendText {
					+chatPrefix
					if (config.functions.isEmpty()) {
						"There are no custom functions defined yet." colored ColorPalette.WARNING
						return@sendText
					}

					"Currently defined custom functions are: " colored ColorPalette.ACCENT
					+ScreenTexts.LINE_BREAK
					config.functions.asSequence()
						.map(CustomFunction::toString)
						.map {
							it.text {
								color = ColorPalette.TEXT
								clickEvent = copyToClipboard(it)
								hoverEvent = showText("Click to copy to clipboard".text(ColorPalette.GREEN))
							}
						}
						.interleaveWith(ScreenTexts.LINE_BREAK)
						.forEach(::append)
				}
				return false
			}

			text == "constants?" -> {
				player?.sendText {
					+chatPrefix
					if (config.constants.isEmpty()) {
						"There are no custom constants defined yet." colored ColorPalette.WARNING
						return@sendText
					}

					"Currently defined custom constants are: " colored ColorPalette.ACCENT
					+ScreenTexts.LINE_BREAK
					config.constants.asSequence()
						.map(CustomConstant::toString)
						.map {
							it.text {
								clickEvent = copyToClipboard(it)
								hoverEvent = showText("Click to copy to clipboard".text(ColorPalette.GREEN))
							}
						}
						.interleaveWith(ScreenTexts.LINE_BREAK)
						.forEach(::append)
				}
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
					CONSTANT_TABLE.clear()
					FUNCTION_TABLE.clear()
					val result = MathEngine.of().eval(text, arrayOfNulls(0))
					var solution = config.decimalFormat.format(result)
					if (solution == "-0") solution = "0"
					config.saveToChatHud(originalText)
					config.saveToClipboard(originalText)
					return if (add) ChatHelper.addSectionAfterIndex(text, cursor, "=$solution", setMethod)
					else ChatHelper.replaceSection(originalText, cursor, solution, setMethod)
				} catch (t: Throwable) {
					return false
				}
			}
		}
	}

	private fun parseDeclaration(split: List<String>) = CustomFunction.fromString(split)?.let { Either.left(it) } ?: CustomConstant.fromString(split).let { if (it != null) Either.right(it) else null }
}
