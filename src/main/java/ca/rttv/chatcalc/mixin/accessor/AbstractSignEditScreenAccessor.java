package ca.rttv.chatcalc.mixin.accessor;

import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.util.SelectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
	@Accessor
	String[] getMessages();

	@Accessor
	int getCurrentRow();

	@Accessor
	SelectionManager getSelectionManager();
}
