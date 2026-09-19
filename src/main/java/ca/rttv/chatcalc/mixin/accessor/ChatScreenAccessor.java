package ca.rttv.chatcalc.mixin.accessor;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
	@Accessor("commandSuggestions")
	CommandSuggestions getChatInputSuggestor();

	@Accessor("input")
    EditBox getChatField();
}