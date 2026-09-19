package ca.rttv.chatcalc.config

import net.minecraft.util.StringRepresentable


enum class CopyType : StringRepresentable {
	CHAT_HISTORY {
		override fun getSerializedName(): String {
			return "chat-history"
		}
	},
	CLIPBOARD {
		override fun getSerializedName(): String {
			return "clipboard"
		}
	},
	NONE {
		override fun getSerializedName(): String {
			return "none"
		}
	};

	fun asString(): String = name.lowercase()

	companion object {
		val CODEC: StringRepresentable.EnumCodec<CopyType> = StringRepresentable.fromEnum(CopyType::values)
	}
}