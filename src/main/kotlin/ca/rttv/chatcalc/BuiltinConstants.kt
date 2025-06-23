package ca.rttv.chatcalc

import ca.rttv.chatcalc.config.ConfigManager.config
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper
import java.util.function.DoubleSupplier

object BuiltinConstants {
	val CONSTANTS: Map<String, DoubleSupplier> = mapOf(
		"random" to DoubleSupplier { Math.random() },
		"rand" to DoubleSupplier { Math.random() },
		"rad" to DoubleSupplier { if (config.radians) 1.0 else 57.29577951308232 },
		"deg" to DoubleSupplier { if (config.radians) 0.017453292519943295 else 1.0 },
		"yaw" to DoubleSupplier { config.convertFromDegrees(MathHelper.wrapDegrees(MinecraftClient.getInstance().player!!.yaw).toDouble()) },
		"pitch" to DoubleSupplier { config.convertFromDegrees(MathHelper.wrapDegrees(MinecraftClient.getInstance().player!!.pitch).toDouble()) },
		"pi" to DoubleSupplier { Math.PI },
		"tau" to DoubleSupplier { 2.0 * Math.PI },
		"e" to DoubleSupplier { Math.E },
		"phi" to DoubleSupplier { 1.6180339887498948482 },
		"x" to DoubleSupplier { MinecraftClient.getInstance().player!!.pos.x },
		"y" to DoubleSupplier { MinecraftClient.getInstance().player!!.pos.y },
		"z" to DoubleSupplier { MinecraftClient.getInstance().player!!.pos.z },
	)
}
