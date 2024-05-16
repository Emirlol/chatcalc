package ca.rttv.chatcalc

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper

object MathematicalConstant {
    val CONSTANTS = mapOf(
        "random" to { Math.random() },
        "rand" to { Math.random() },
        "rad" to { if (Config.radians()) 1.0 else 57.29577951308232 },
        "deg" to { if (Config.radians()) 0.017453292519943295 else 1.0 },
        "yaw" to { Config.convertFromDegrees(MathHelper.wrapDegrees(MinecraftClient.getInstance().player!!.yaw).toDouble()) },
        "pitch" to { Config.convertFromDegrees(MathHelper.wrapDegrees(MinecraftClient.getInstance().player!!.pitch).toDouble()) },
        "pi" to { Math.PI },
        "tau" to { 2.0 * Math.PI },
        "e" to { Math.E },
        "phi" to { 1.6180339887498948482 },
        "x" to { MinecraftClient.getInstance().player!!.pos.x },
        "y" to { MinecraftClient.getInstance().player!!.pos.y },
        "z" to { MinecraftClient.getInstance().player!!.pos.z },
    )
}
