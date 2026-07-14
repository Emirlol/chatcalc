package ca.rttv.chatcalc.mixin;

import ca.rttv.chatcalc.interfaces.SignEditScreenExtensions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin implements SignEditScreenExtensions {

	@SuppressWarnings("AddedMixinMembersNamePattern")
	@Override
	public @NotNull Event<Runnable> onSetRow() {
		return onSetRow;
	}

	@Unique
	public Event<Runnable> onSetRow = EventFactory.createArrayBacked(Runnable.class, listeners ->
			() -> { for (var listener : listeners) listener.run(); }
	);

	@Inject(method = "setCurrentRowMessage", at = @At("TAIL"))
	public void onSetRow(String message, CallbackInfo ci) {
		onSetRow.invoker().run();
	}
}
