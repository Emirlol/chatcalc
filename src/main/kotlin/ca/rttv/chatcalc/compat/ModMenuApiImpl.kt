package ca.rttv.chatcalc.compat

import ca.rttv.chatcalc.config.ConfigScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class ModMenuApiImpl : ModMenuApi {
	override fun getModConfigScreenFactory() = ConfigScreenFactory(::ConfigScreen)
}