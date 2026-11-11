package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Both /nations and its redirect use these handlers and the same service authorization. */
public final class ObeliskRelocationCommands {
    private ObeliskRelocationCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("relocate")
                .executes(c -> { c.getSource().sendSuccess(() -> text("help"),false); return 1; })
                .then(destination("city",false)).then(destination("state",true))
                .then(Commands.literal("confirm").then(Commands.argument("token",UuidArgument.uuid()).executes(c -> execute(c,false,true))));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> destination(String name,boolean state) {
        return Commands.literal(name).then(Commands.argument("x",IntegerArgumentType.integer())
                .then(Commands.argument("y",IntegerArgumentType.integer())
                        .then(Commands.argument("z",IntegerArgumentType.integer()).executes(c -> execute(c,state,false)))));
    }
    private static int execute(CommandContext<CommandSourceStack> context,boolean state,boolean confirm) throws CommandSyntaxException {
        var source=context.getSource(); var player=source.getPlayerOrException(); var service=KingdomService.get(source.getServer());
        try {
            if (confirm) {
                service.relocation().commit(player,UuidArgument.getUuid(context,"token"));
                source.sendSuccess(() -> text("success"),false);
            } else {
                UUID target;
                if (state) {
                    var kingdom=service.data().byFounder(player.getUUID());
                    if (kingdom==null) throw new FoundingException(FoundingException.Reason.NO_KINGDOM);
                    target=kingdom.id();
                } else target=service.cities().owned(player).id();
                var pos=new BlockPos(IntegerArgumentType.getInteger(context,"x"),IntegerArgumentType.getInteger(context,"y"),IntegerArgumentType.getInteger(context,"z"));
                UUID token=service.relocation().select(player,target,state,player.level().dimension(),pos);
                source.sendSuccess(() -> text("selected",pos.toShortString()).append(" ").append(text("confirm")
                        .withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/nations relocate confirm "+token)))),false);
            }
            return 1;
        } catch (FoundingException e) { source.sendFailure(Component.translatable(e.translationKey())); return 0; }
    }
    public static LiteralArgumentBuilder<CommandSourceStack> recovery() {
        return Commands.literal("recover").requires(s -> s.hasPermission(3)).then(recoveryType("city",false)).then(recoveryType("state",true));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> recoveryType(String name,boolean state) {
        return Commands.literal(name).then(Commands.argument("id",UuidArgument.uuid()).executes(c -> {
            var source=c.getSource(); UUID target=UuidArgument.getUuid(c,"id"); String outcome="rejected";
            try {
                KingdomService.get(source.getServer()).relocation().recover(source,target,state); outcome="success";
                source.sendSuccess(() -> text("recovered"),false); return 1;
            } catch (FoundingException e) { outcome=e.reason().name(); source.sendFailure(Component.translatable(e.translationKey())); return 0; }
            finally { KingdomAdminCommands.audit(source,"recover-"+name,target.toString(),"canonical saved position",outcome); }
        }));
    }
    private static net.minecraft.network.chat.MutableComponent text(String key,Object... args) { return Component.translatable("relocation.regnum-nations."+key,args); }
}
