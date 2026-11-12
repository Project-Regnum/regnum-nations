package dev.sakhno.regnum.nations.api.client.v1;

import dev.sakhno.regnum.nations.api.v1.EntitySnapshot;
import dev.sakhno.regnum.nations.client.heraldry.HeraldryClient;
import dev.sakhno.regnum.nations.client.heraldry.HeraldryTextures;
import dev.sakhno.regnum.nations.client.ui.KingdomUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Reuses the existing heraldry cache/renderer; independent cities render their political color. */
public final class NationsIdentity {
    private NationsIdentity() { }
    public static void draw(GuiGraphics g, Font font, EntitySnapshot identity, int x, int y, int width) {
        if (identity.heraldry().isPresent()) HeraldryTextures.draw(g, HeraldryClient.arms(identity.heraldry().orElseThrow()), x, y, 24, 30);
        else { g.fill(x + 2, y + 3, x + 20, y + 25, KingdomUi.GOLD); g.fill(x + 4, y + 5, x + 18, y + 23, identity.color()); }
        g.drawString(font, KingdomUi.ellipsize(font, Component.literal(identity.name()), width - 34), x + 34, y + 3, KingdomUi.INK, false);
        g.drawString(font, KingdomUi.ellipsize(font, Component.translatable("economy.regnum-nations.scope." + identity.ref().scope().name().toLowerCase(java.util.Locale.ROOT)), width - 34),
                x + 34, y + 17, KingdomUi.MUTED, false);
    }
}
