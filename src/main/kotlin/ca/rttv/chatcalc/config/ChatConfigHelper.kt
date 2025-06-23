package ca.rttv.chatcalc.config

import ca.rttv.chatcalc.config.ConfigManager.config
import ca.rttv.chatcalc.config.Configv2.Companion.COPY_TYPE
import ca.rttv.chatcalc.config.Configv2.Companion.DECIMAL_FORMAT
import ca.rttv.chatcalc.config.Configv2.Companion.DISPLAY_ABOVE
import ca.rttv.chatcalc.config.Configv2.Companion.RADIANS
import java.text.DecimalFormat

object ChatConfigHelper {
	/**
	 * Retrieves the value of a configuration key.
	 *
	 * @param key The configuration key to retrieve.
	 * @return The value of the configuration key, or null if the key does not exist.
	 */
	fun getConfigValue(key: String): String? = when (key) {
		DECIMAL_FORMAT -> config.decimalFormat.toPattern()
		RADIANS -> config.radians.toString()
		COPY_TYPE -> config.copyType.name.lowercase()
		DISPLAY_ABOVE -> config.displayAbove.toString()
		else -> null
	}

	/**
	 * Updates the value of a configuration key.
	 *
	 * @param key The configuration key to update.
	 * @param value The new value for the configuration key.
	 * @throws IllegalArgumentException if the key is unknown or the value is invalid.
	 */
	fun putConfigValue(key: String, value: String) {
		when (key) {
			DECIMAL_FORMAT -> {
				val format = try {
					DecimalFormat(value)
				} catch (e: IllegalArgumentException) {
					throw IllegalArgumentException("Invalid decimal format: $value", e)
				}
				ConfigManager.updateConfig { decimalFormat = format }
			}

			RADIANS -> {
				val booleanValue = value.toBooleanStrictOrNull() ?: throw IllegalArgumentException("Invalid boolean value for radians: $value")
				ConfigManager.updateConfig { radians = booleanValue }
			}

			COPY_TYPE -> {
				val type = CopyType.entries.find { it.name.equals(value, ignoreCase = true) } ?: throw IllegalArgumentException("Invalid copy type: $value")
				ConfigManager.updateConfig { copyType = type }
			}

			DISPLAY_ABOVE -> {
				val booleanValue = value.toBooleanStrictOrNull() ?: throw IllegalArgumentException("Invalid boolean value for displayAbove: $value")
				ConfigManager.updateConfig { displayAbove = booleanValue }
			}

			else -> throw IllegalArgumentException("Unknown config key: $key")
		}
	}
}