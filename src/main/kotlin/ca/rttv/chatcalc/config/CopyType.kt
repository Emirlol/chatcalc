package ca.rttv.chatcalc.config

import net.minecraft.util.StringIdentifiable
import net.minecraft.util.StringIdentifiable.EnumCodec

enum class CopyType : StringIdentifiable {
	CHAT_HISTORY,
	CLIPBOARD,
	NONE;

	override fun asString(): String = name.lowercase()

	companion object {
		val CODEC: EnumCodec<CopyType> = StringIdentifiable.createCodec(CopyType::values)
	}
}