package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.chat.ChatUi;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;

/** Attached once to /regnum; compatibility redirects share all authorization checks. */
final class WarfareCommands {
    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("warfare").then(Commands.literal("cancel").requires(source -> source.hasPermission(3))
                .then(Commands.argument("objective",UuidArgument.uuid()).executes(context -> {
                    var source=context.getSource(); KingdomService.requireAdministrator(source);
                    try {
                        KingdomService.get(source.getServer()).warfare().cancel(UuidArgument.getUuid(context,"objective"));
                        ChatUi.confirm(source, () -> ChatUi.text("warfare.regnum-nations.updated"),true); return 1;
                    } catch (FoundingException failure) { ChatUi.fail(source, ChatUi.text(failure.translationKey())); return 0; }
                })));
    }
}
