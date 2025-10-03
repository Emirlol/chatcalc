package ca.rttv.chatcalc

import ca.rttv.chatcalc.config.ConfigManager.config
import me.ancientri.rimelib.util.player
import net.minecraft.util.math.MathHelper
import java.util.function.DoubleSupplier

object BuiltinConstants {
	val CONSTANTS: Map<String, DoubleSupplier> = mapOf(
		"random" to DoubleSupplier { Math.random() },
		"rand" to DoubleSupplier { Math.random() },
		"rad" to DoubleSupplier { if (config.radians) 1.0 else 57.29577951308232 },
		"deg" to DoubleSupplier { if (config.radians) 0.017453292519943295 else 1.0 },
		"yaw" to DoubleSupplier { config.convertFromDegrees(MathHelper.wrapDegrees(player!!.yaw).toDouble()) },
		"pitch" to DoubleSupplier { config.convertFromDegrees(MathHelper.wrapDegrees(player!!.pitch).toDouble()) },
		"pi" to DoubleSupplier { Math.PI },
		"tau" to DoubleSupplier { 2.0 * Math.PI },
		"e" to DoubleSupplier { Math.E },
		"phi" to DoubleSupplier { 1.6180339887498948482 },
		"x" to DoubleSupplier { player!!.entityPos .x },
		"y" to DoubleSupplier { player!!.entityPos.y },
		"z" to DoubleSupplier { player!!.entityPos.z },
	)
}
