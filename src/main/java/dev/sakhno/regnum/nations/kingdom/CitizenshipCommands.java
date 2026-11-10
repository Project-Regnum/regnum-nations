package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Commands resolve the authenticated actor; only targets come from command arguments. */
final class CitizenshipCommands {
    private CitizenshipCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("citizen")
                .executes(ctx -> { ctx.getSource().sendSuccess(() -> text("help"), false); return 1; })
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            Settlement city = service.invite(actor, target);
                            target.displayClientMessage(text("invitation", actor.getGameProfile().getName(),
                                    city.name().display(), city.id().toString(), city.id().toString()), false);
                            ctx.getSource().sendSuccess(() -> text("invited", target.getGameProfile().getName()), false);
                        }))))
                .then(Commands.literal("accept").then(Commands.argument("cityUUID", UuidArgument.uuid())
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            service.accept(actor, UuidArgument.getUuid(ctx, "cityUUID"));
                            ctx.getSource().sendSuccess(() -> text("accepted"), false);
                        }))))
                .then(Commands.literal("decline").then(Commands.argument("cityUUID", UuidArgument.uuid())
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            service.decline(actor, UuidArgument.getUuid(ctx, "cityUUID"));
                            ctx.getSource().sendSuccess(() -> text("declined"), false);
                        }))))
                .then(Commands.literal("leave").executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                    service.leave(actor);
                    ctx.getSource().sendSuccess(() -> text("left"), false);
                })))
                .then(Commands.literal("list").executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                    var members = service.list(actor);
                    Settlement city = service.current(actor);
                    ctx.getSource().sendSuccess(() -> text("list", city.name().display(), members.size()), false);
                    for (UUID member : members) {
                        String name = name(actor, member);
                        ctx.getSource().sendSuccess(() -> text(member.equals(city.ownerId()) ? "owner_entry" : "entry",
                                name, member.toString()), false);
                    }
                })))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            String input = StringArgumentType.getString(ctx, "player");
                            UUID target = target(service, actor, input);
                            service.kick(actor, target);
                            ctx.getSource().sendSuccess(() -> text("kicked", input), false);
                            var online = ctx.getSource().getServer().getPlayerList().getPlayer(target);
                            if (online != null) online.displayClientMessage(text("removed"), false);
                        }))));
    }

    /** Resolve offline citizens from local UUID/profile records; never ask an external name service. */
    private static UUID target(CitizenshipService service, ServerPlayer actor, String input) throws FoundingException {
        try { return UUID.fromString(input); }
        catch (IllegalArgumentException ignored) { }
        var online = actor.serverLevel().getServer().getPlayerList().getPlayerByName(input);
        if (online != null) return online.getUUID();
        UUID match = null;
        for (UUID member : service.list(actor)) {
            if (name(actor, member).equalsIgnoreCase(input)) {
                if (match != null) throw new FoundingException(FoundingException.Reason.CITIZEN_UNKNOWN_PLAYER);
                match = member;
            }
        }
        if (match == null) throw new FoundingException(FoundingException.Reason.CITIZEN_UNKNOWN_PLAYER);
        return match;
    }

    private static String name(ServerPlayer viewer, UUID player) {
        var server = viewer.serverLevel().getServer();
        var online = server.getPlayerList().getPlayer(player);
        if (online != null) return online.getGameProfile().getName();
        var cache = server.getProfileCache();
        return cache == null ? "" : cache.get(player).map(profile -> profile.getName()).orElse("");
    }

    private interface Action {
        void run(CitizenshipService service, ServerPlayer actor) throws FoundingException, CommandSyntaxException;
    }

    private static int execute(CommandSourceStack source, Action action) throws CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        try {
            action.run(KingdomService.get(source.getServer()).citizenship(), actor);
            return 1;
        } catch (FoundingException failure) {
            source.sendFailure(Component.translatable(failure.translationKey()));
            return 0;
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("citizen.regnum-nations." + key, args);
    }
}
