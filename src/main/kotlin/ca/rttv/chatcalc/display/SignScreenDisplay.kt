package ca.rttv.chatcalc.display

import ca.rttv.chatcalc.ChatCalc
import ca.rttv.chatcalc.ChatHelper
import ca.rttv.chatcalc.mixin.accessor.AbstractSignEditScreenAccessor
import me.ancientri.rimelib.util.client
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen
import net.minecraft.client.gui.tooltip.WidgetTooltipPositioner

// These positions are taken from the `drawCenteredTextWithShadow` call in AbstractSignEditScreen.render()
class SignScreenDisplay(val screen: AbstractSignEditScreen) : DisplayAbove() {
	override val x: Int get() = (screen.width - WidgetTooltipPositioner.field_42158 - 12) / 2
	override val y: Int = 40 + client.textRenderer.fontHeight + 17

	override fun parseWord(): String? = with(screen as AbstractSignEditScreenAccessor) {
		if (selectionManager == null) return null
		val line = messages[currentRow]
		if (line.isEmpty()) return null
		return ChatHelper.getSection(line, selectionManager.selectionEnd)
	}

	override fun allowKeyPress(keycode: Int): Boolean = with(screen as AbstractSignEditScreenAccessor) {
		super.allowKeyPress(keycode) || selectionManager == null || !ChatCalc.tryParse(messages[currentRow], selectionManager.selectionEnd) {
			messages[currentRow] = it
			selectionManager.putCursorAtEnd()
		}
	}

	companion object {
		fun init() {
			ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
				if (screen !is AbstractSignEditScreen) return@register
				val display = SignScreenDisplay(screen)
				ScreenKeyboardEvents.allowKeyPress(screen).register { _, keycode, _, _ ->
					display.allowKeyPress(keycode)
				}
				ScreenEvents.beforeRender(screen).register { _, drawContext, _, _, _ ->
					if (display.shouldRender()) display.render(drawContext)
				}
			}
		}
	}
}