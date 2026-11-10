package dev.sakhno.reigns.kingdom;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Native Minecraft chat confirmation; all identifiers originate in the server registry/service. */
final class KingdomDeletionPrompt {
    private KingdomDeletionPrompt() { }
    private static Component text(String key,Object... args) { return Component.translatable("admin.project-reigns."+key,args); }
    static String command(UUID kingdom,String action,UUID token) {
        return "/reigns admin delete "+kingdom+" "+action+" "+token;
    }
    static Component create(Kingdom kingdom,UUID token,boolean existing) {
        String confirm=command(kingdom.id(),"confirm",token),cancel=command(kingdom.id(),"cancel",token);
        // Native red is intentionally distinct from ordinary gold UI actions and legible in chat.
        Component danger=text("delete_action").copy().withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,confirm))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,text("delete_action_hint",kingdom.name().display()))));
        Component dismiss=text("delete_cancel_action").copy().withStyle(style -> style.withColor(ChatFormatting.GRAY).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,cancel))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,text("delete_cancel_hint"))));
        Component fallback=text("delete_fallback",confirm,cancel).copy().withStyle(style -> style.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,confirm)));
        return text(existing?"delete_pending_same":"delete_heading",kingdom.name().display()).copy().withStyle(ChatFormatting.RED)
                .append("\n").append(text("delete_consequences").copy().withStyle(ChatFormatting.WHITE))
                .append("\n").append(danger).append("  ").append(dismiss).append("\n").append(fallback);
    }
}
