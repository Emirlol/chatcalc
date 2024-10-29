package ca.rttv.chatcalc

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.text.Text

val client: MinecraftClient get() = MinecraftClient.getInstance()
val player: ClientPlayerEntity? get() = client.player

// This method exists to add a default false overlay parameter to the sendMessage method, as the method with no overlay parameter doesn't exist in 1.21.3 anymore
fun ClientPlayerEntity.sendText(text: Text, overlay: Boolean = false) = sendMessage(text, overlay)
// And this one is just for convenience
fun ClientPlayerEntity.sendMessage(text: String, overlay: Boolean = false) = sendMessage(Text.literal(text), overlay)