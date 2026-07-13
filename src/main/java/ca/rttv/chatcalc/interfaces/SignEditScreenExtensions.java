package ca.rttv.chatcalc.interfaces;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface SignEditScreenExtensions {
	@NotNull
	static SignEditScreenExtensions getExtensions(AbstractSignEditScreen screen) { return (SignEditScreenExtensions) screen; }

	@NotNull
	Event<Runnable> onSetRow();
}
