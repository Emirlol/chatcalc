package ca.rttv.chatcalc.mixin.accessor;

import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public interface ChatInputSuggesterAccessor {
	@Accessor
	@Nullable
	CompletableFuture<@NotNull Suggestions> getPendingSuggestions();
}
