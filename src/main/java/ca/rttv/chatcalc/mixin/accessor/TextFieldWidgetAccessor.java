package ca.rttv.chatcalc.mixin.accessor;

import net.minecraft.client.gui.widget.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

@Mixin(TextFieldWidget.class)
public interface TextFieldWidgetAccessor {
	@Accessor
	@Nullable
	Consumer<@NotNull String> getChangedListener();
}
