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
        return create(kingdom.name().display(),existing,"delete_",command(kingdom.id(),"confirm",token),command(kingdom.id(),"cancel",token));
    }
    static Component createCity(Settlement city,long claims,int members,UUID token,boolean existing) {
        String command="/regnum admin settlement "+city.id()+" delete ";
        return create(city.name().display(),existing,"city_delete_",
                command+"confirm "+token,command+"cancel "+token,city.id(),claims,members);
    }
    private static Component create(String name,boolean existing,String key,
                                    String confirm,String cancel,Object... details) {
        Component danger=ChatUi.run(text(key+"action"),confirm,text(key+"action_hint",name))
                .withStyle(ChatFormatting.RED,ChatFormatting.BOLD);
        Component dismiss=ChatUi.run(text("delete_cancel_action"),cancel,text("delete_cancel_hint"));
        Component fallback=ChatUi.suggest(text("delete_fallback",confirm,cancel),confirm,
                Component.translatable("chat.regnum-nations.copy_confirmation_hint"));
        return ChatUi.warning(text(key+(existing?"pending_same":"heading"),name))
                .append(ChatUi.line(text(key+"consequences",details)))
                .append(ChatUi.line(danger)).append("  ").append(dismiss).append(ChatUi.line(fallback));
    }
}
