@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.engine.env

import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.engine.EnvironmentError
import ca.rttv.chatcalc.engine.types.MathObject
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import me.ancientri.rimelib.config.ConfigBuilder
import me.ancientri.rimelib.util.color.ColorPalette

data class UserEnvironment(private val values: Map<String, MathObject> = emptyMap()) : MutableEnvironment {
	inline val parent: Environment get() = GlobalEnvironment

	override operator fun set(key: String, value: MathObject) {
		if (key in parent) {
			val existing = parent.getOrThrow(key)
			throw EnvironmentError {
				"Cannot shadow existing ${existing.type.asString()} '" colored ColorPalette.ERROR
				key colored ColorPalette.TEXT
				"'." colored ColorPalette.ERROR
			}
		}
		UserEnvironmentManager.updateConfig {
			if (key in values) {
				UserEnvironmentManager.logger.warn("Overriding key '$key' with value '${values[key]!!.toPrettyText().string}' in user environment to '${value.toPrettyText().string}'.")
			} else {
				UserEnvironmentManager.logger.info("Setting key '$key' to value '${value.toPrettyText().string}' in user environment.")
			}
			values[key] = value
		}
	}

	override fun remove(key: String) {
		if (key !in values) {
			throw EnvironmentError {
				"Key '" colored ColorPalette.ERROR
				key colored ColorPalette.TEXT
				"' not found in user environment." colored ColorPalette.ERROR
			}
		}
		UserEnvironmentManager.updateConfig {
			values -= key
		}
	}

	override operator fun contains(key: String): Boolean = key in values || key in parent

	override operator fun get(key: String): MathObject? = values[key] ?: parent[key]

	// MapEnvironment creates a copy of the given map, so the original map is untouched. We can also pass in the GlobalEnvironment so there's no shadowing of globals.
	class Builder(env: UserEnvironment) : MapEnvironment(env.values, env.parent /* GlobalEnvironment */), ConfigBuilder<UserEnvironment> {
		override fun set(key: String, value: MathObject) {
			if (key in requireNotNull(parent)) {
				val existing = parent.getOrThrow(key)
				throw EnvironmentError {
					"Cannot shadow existing ${existing.type.asString()} '" colored ColorPalette.ERROR
					key colored ColorPalette.TEXT
					"'." colored ColorPalette.ERROR
				}
			}
			super.set(key, value)
		}

		override fun build(): UserEnvironment = UserEnvironment(values)
	}

	companion object {
		val CODEC: Codec<UserEnvironment> = Codec.compoundList(Codec.STRING, MathObject.CODEC).xmap(
			{ list ->
				val map = LinkedHashMap<String, MathObject>(list.size)
				list.forEach { pair -> map[pair.first] = pair.second }
				UserEnvironment(map)
			},
			{ env ->
				val map = env.values
				val list = ArrayList<Pair<String, MathObject>>(map.size)
				map.forEach { (key, value) -> list += Pair(key, value) }
				list
			}
		)
	}
}
