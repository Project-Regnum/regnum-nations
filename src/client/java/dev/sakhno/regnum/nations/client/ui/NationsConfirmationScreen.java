package dev.sakhno.regnum.nations.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Shared narrated confirmation for companion screens. Confirmation calls the supplied action
 * exactly once; the provider must still authorize its request on the server.
 */
public final class NationsConfirmationScreen extends RoyalScreen {
    private final Screen parent;
    private final Component explanation;
    private final Runnable confirmed;
    private final boolean destructive;
    private FolioBounds bounds;
    private boolean submitted;
    public NationsConfirmationScreen(Screen parent, Component title, Component explanation, boolean destructive, Runnable confirmed) {
        super(title); this.parent = parent; this.explanation = explanation.copy(); this.destructive = destructive;
        this.confirmed = java.util.Objects.requireNonNull(confirmed);
    }
    @Override protected void init() {
        bounds = folioBounds(width, height, 480, 240);
        addRenderableWidget(paragraph(font, bounds.left(), bounds.y() + HEADER + GAP, bounds.contentWidth() - GAP,
                Math.max(CONTROL, bounds.footerY() - bounds.y() - HEADER - GAP * 2), explanation));
        int w = (bounds.contentWidth() - GAP) / 2;
        var confirm = addRenderableWidget(new RoyalButton(bounds.left(), bounds.footerY(), w,
                Component.translatable("economy.regnum-nations.confirm"), b -> {
                    if (submitted) return;
                    submitted = true; b.active = false; minecraft.setScreen(parent); confirmed.run();
                }, destructive ? ButtonStyle.DANGER : ButtonStyle.PRIMARY));
        confirm.active = !submitted;
        addRenderableWidget(new RoyalButton(bounds.left() + w + GAP, bounds.footerY(), w, text("cancel"), b -> onClose()));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(explanation); }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BACKGROUND);
        frame(g, font, title, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        super.render(g, mx, my, delta);
    }
}
