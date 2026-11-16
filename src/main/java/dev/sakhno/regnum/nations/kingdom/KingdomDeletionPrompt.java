package dev.sakhno.regnum.nations.kingdom;

import java.util.UUID;
import dev.sakhno.regnum.nations.chat.ChatUi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Native Minecraft chat confirmation; all identifiers originate in the server registry/service. */
final class KingdomDeletionPrompt {
    private KingdomDeletionPrompt() { }
    private static Component text(String key,Object... args) { return ChatUi.text("admin.regnum-nations."+key,args); }
    static String command(UUID kingdom,String action,UUID token) {
        return "/regnum admin delete "+kingdom+" "+action+" "+token;
    }
    static Component create(Kingdom kingdom,UUID token,boolean existing) {
        String confirm=command(kingdom.id(),"confirm",token),cancel=command(kingdom.id(),"cancel",token);
        Component danger=ChatUi.run(text("delete_action"),confirm,text("delete_action_hint",kingdom.name().display()))
                .withStyle(ChatFormatting.RED,ChatFormatting.BOLD);
        Component dismiss=ChatUi.run(text("delete_cancel_action"),cancel,text("delete_cancel_hint"));
        Component fallback=ChatUi.suggest(text("delete_fallback",confirm,cancel),confirm,
                Component.translatable("chat.regnum-nations.copy_confirmation_hint"));
        return ChatUi.warning(text(existing?"delete_pending_same":"delete_heading",kingdom.name().display()))
                .append(ChatUi.line(text("delete_consequences")))
                .append(ChatUi.line(danger)).append("  ").append(dismiss).append(ChatUi.line(fallback));
    }
}
