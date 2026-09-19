package ca.rttv.chatcalc.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.font.TextFieldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {

	@Accessor("messages")
	String[] getMessages();

	@Accessor("line")
	int getCurrentRow();

	@Accessor("signField")
	TextFieldHelper getSelectionManager();
}