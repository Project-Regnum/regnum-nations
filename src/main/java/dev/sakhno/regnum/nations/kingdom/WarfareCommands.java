package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/** Attached once to the Nations command root, so the /reigns redirect shares all checks. */
final class WarfareCommands {
    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("warfare").then(Commands.literal("cancel").requires(source -> source.hasPermission(3))
                .then(Commands.argument("objective",UuidArgument.uuid()).executes(context -> {
                    var source=context.getSource(); KingdomService.requireAdministrator(source);
                    try {
                        KingdomService.get(source.getServer()).warfare().cancel(UuidArgument.getUuid(context,"objective"));
                        source.sendSuccess(() -> Component.translatable("warfare.regnum-nations.updated"),true); return 1;
                    } catch (FoundingException failure) { source.sendFailure(Component.translatable(failure.translationKey())); return 0; }
                })));
    }
}
