package dev.sakhno.regnum.nations.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared native lifecycle policy; individual screens own their composition and background. */
public abstract class RoyalScreen extends Screen {
    protected RoyalScreen(Component title) { super(title); }
    @Override public final void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float delta) {
        // Screen.render() calls this after our foreground; vanilla blur would blur that foreground.
    }
    @Override protected final void setInitialFocus() {
        // 1.21.1 calls this again after init/rebuildWidgets: retain the explicit form/tab/canvas focus.
        if (getFocused()==null) super.setInitialFocus();
    }
    @Override public final boolean isPauseScreen() { return false; }
}
