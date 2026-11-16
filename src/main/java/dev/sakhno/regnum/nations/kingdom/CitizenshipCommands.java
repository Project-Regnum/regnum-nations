package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.chat.ChatUi;
import dev.sakhno.regnum.nations.chat.CommandHelp;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
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
                .executes(ctx -> help(ctx.getSource(),1))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource(),1))
                        .then(Commands.argument("page",IntegerArgumentType.integer(1,CommandHelp.CITIZEN_PAGES))
                                .executes(ctx -> help(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"page")))))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            Settlement city = service.invite(actor, target);
                            ChatUi.notify(target, ChatUi.citizenInvitation(actor.getGameProfile().getName(), city.name().display(), city.id()), false);
                            ChatUi.confirm(ctx.getSource(), () -> text("invited", target.getGameProfile().getName()), false);
                        }))))
                .then(Commands.literal("accept").then(Commands.argument("cityUUID", UuidArgument.uuid())
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            service.accept(actor, UuidArgument.getUuid(ctx, "cityUUID"));
                            ChatUi.confirm(ctx.getSource(), () -> text("accepted"), false);
                        }))))
                .then(Commands.literal("decline").then(Commands.argument("cityUUID", UuidArgument.uuid())
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            service.decline(actor, UuidArgument.getUuid(ctx, "cityUUID"));
                            ChatUi.confirm(ctx.getSource(), () -> text("declined"), false);
                        }))))
                .then(Commands.literal("leave").executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                    service.leave(actor);
                    ChatUi.confirm(ctx.getSource(), () -> text("left"), false);
                })))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("role").then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.List.of("officer", "citizen"), builder))
                                .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                                    UUID target = target(service, actor, StringArgumentType.getString(ctx, "player"));
                                    CityRole role;
                                    try { role = CityRole.valueOf(StringArgumentType.getString(ctx, "role").toUpperCase(Locale.ROOT)); }
                                    catch (IllegalArgumentException invalid) { throw new FoundingException(FoundingException.Reason.INVALID_CITY_ROLE); }
                                    service.assignRole(actor, target, role);
                                    ChatUi.confirm(ctx.getSource(), () -> text("role_assigned", name(actor, target), target.toString(), roleName(role)), false);
                                })))))
                .then(Commands.literal("roles").executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                    service.current(actor);
                    var result=ChatUi.heading(Component.translatable("chat.regnum-nations.roles"));
                    for (CityRole role : CityRole.values()) {
                        result.append(ChatUi.line(text("role_description." + role.name().toLowerCase(Locale.ROOT), roleName(role))));
                    }
                    ctx.getSource().sendSuccess(() -> result, false);
                })))
                .then(Commands.literal("permissions").executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                    CityRole role = service.role(actor, actor.getUUID());
                    var result=ChatUi.heading(text("permissions", roleName(role)));
                    for (CityPermission permission : CityPermission.values()) {
                        boolean allowed = service.permits(actor, permission);
                        result.append(ChatUi.line(text("permission_entry",
                                text("permission." + permission.name().toLowerCase(Locale.ROOT)),
                                ChatUi.text("ui.regnum-nations." + (allowed ? "yes" : "no")))));
                    }
                    ctx.getSource().sendSuccess(() -> result, false);
                })))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                        .executes(ctx -> execute(ctx.getSource(), (service, actor) -> {
                            String input = StringArgumentType.getString(ctx, "player");
                            UUID target = target(service, actor, input);
                            service.kick(actor, target);
                            ChatUi.confirm(ctx.getSource(), () -> text("kicked", input), false);
                            var online = ctx.getSource().getServer().getPlayerList().getPlayer(target);
                            if (online != null) ChatUi.notify(online, text("removed"), false);
                        }))));
    }

    private static int help(CommandSourceStack source,int page) {
        source.sendSuccess(() -> CommandHelp.citizens(page),false);
        return 1;
    }

    private static int list(CommandSourceStack source, int page) throws CommandSyntaxException {
        return execute(source, (service, actor) -> {
            var members=service.list(actor);
            Settlement city=service.current(actor);
            int pages=Math.max(1,(members.size()+7)/8);
            if (page>pages) throw KingdomService.adminError("page",pages);
            var result=ChatUi.heading(text("list",city.name().display(),members.size()));
            for (int i=(page-1)*8;i<Math.min(page*8,members.size());i++) {
                UUID member=members.get(i);
                String name=name(actor,member);
                result.append(ChatUi.line(text("role_entry",name.isEmpty()?member.toString():name,member.toString(),roleName(service.role(actor,member)))));
            }
            result.append(ChatUi.pagination(page,pages,p -> "/regnum citizen list " + p));
            source.sendSuccess(() -> result,false);
        });
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
            ChatUi.fail(source, ChatUi.text(failure.translationKey()));
            return 0;
        }
    }

    private static Component roleName(CityRole role) {
        return text("role." + role.name().toLowerCase(Locale.ROOT));
    }

    private static Component text(String key, Object... args) {
        return ChatUi.text("citizen.regnum-nations." + key, args);
    }
}
