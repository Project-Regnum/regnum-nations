package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.chat.ChatUi;
import dev.sakhno.regnum.nations.chat.CommandHelp;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class KingdomCommands {
	private KingdomCommands() { }

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher));
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
				var root = dispatcher.register(Commands.literal("regnum")
						.executes(context -> help(context.getSource()))
                        .then(Commands.literal("help").executes(context -> help(context.getSource()))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, CommandHelp.PAGES))
                                        .executes(context -> help(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
						.then(Commands.literal("found").executes(context -> help(context.getSource()))
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(context -> found(context.getSource(), StringArgumentType.getString(context, "name")))))
						.then(Commands.literal("menu").executes(context -> {
							ServerPlayer player = context.getSource().getPlayer();
							if (player == null) return fail(context.getSource(), new FoundingException(FoundingException.Reason.INVALID_PLAYER));
							KingdomManagementServer.openMenu(player, null);
							return 1;
						}))
						.then(Commands.literal("city").executes(context -> { var player=context.getSource().getPlayerOrException(); KingdomManagementServer.openCityMenu(player,null); return 1; }))
						.then(Commands.literal("invite").then(Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(context -> {
							var source=context.getSource(); var player=source.getPlayerOrException();
							var recipient=net.minecraft.commands.arguments.EntityArgument.getPlayer(context,"player");
							try {
								var id=KingdomService.get(source.getServer()).cities().invite(player,recipient);
								ChatUi.notify(recipient, ChatUi.text("city.regnum-nations.invitation",player.getGameProfile().getName(),id.toString())
                                        .append(ChatUi.line(ChatUi.run(Component.translatable("chat.regnum-nations.accept"), "/regnum join " + id,
                                                Component.translatable("chat.regnum-nations.join_hint")))),false);
								ChatUi.confirm(source, () -> ChatUi.text("city.regnum-nations.invited",recipient.getGameProfile().getName()),false); return 1;
							} catch (FoundingException e) { return fail(source,e); }
						})))
						.then(Commands.literal("join").then(Commands.argument("kingdom",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(context -> {
							var source=context.getSource(); var player=source.getPlayerOrException();
							try { KingdomService.get(source.getServer()).cities().join(player,net.minecraft.commands.arguments.UuidArgument.getUuid(context,"kingdom"));
								ChatUi.confirm(source, () -> ChatUi.text("city.regnum-nations.joined"),false); return 1;
							} catch (FoundingException e) { return fail(source,e); }
						})))
						.then(WarfareCommands.commands())
                        .then(ObeliskRelocationCommands.commands())
                        .then(CitizenshipCommands.commands())
						.then(KingdomAdminCommands.commands())
						.then(Commands.literal("claim").executes(context -> territory(context.getSource(), "claim")))
						.then(Commands.literal("unclaim").executes(context -> territory(context.getSource(), "unclaim"))
                                .then(Commands.literal("confirm").executes(context -> territory(context.getSource(), "confirm_unclaim"))))
						.then(Commands.literal("territory").executes(context -> territory(context.getSource(), "info")))
						.then(Commands.literal("kingdom").executes(context -> kingdom(context.getSource()))));
				dispatcher.register(Commands.literal("reigns")
						.executes(context -> help(context.getSource())).redirect(root));
                dispatcher.register(Commands.literal("nations")
                        .executes(context -> help(context.getSource())).redirect(root));
	}

	private static int help(CommandSourceStack source) {
		return help(source, 1);
	}

	private static int help(CommandSourceStack source, int page) {
		source.sendSuccess(() -> CommandHelp.page(page, source.hasPermission(3)), false);
		return 1;
	}

	private static int found(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return fail(source, new FoundingException(FoundingException.Reason.INVALID_PLAYER));
		try {
			Kingdom kingdom = KingdomService.get(source.getServer()).found(player, name);
			ChatUi.confirm(source, () -> ChatUi.text("message.regnum-nations.founding.success",
					kingdom.name().display(), kingdom.id().toString()), false);
			return 1;
		} catch (FoundingException exception) {
			return fail(source, exception);
		}
	}

	private static int kingdom(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return fail(source, new FoundingException(FoundingException.Reason.INVALID_PLAYER));
		KingdomService service = KingdomService.get(source.getServer());
		try {
			service.data().requireAvailable();
		} catch (FoundingException exception) {
			return fail(source, exception);
		}
		Settlement city=service.data().byOwner(player.getUUID());
		Kingdom kingdom=service.data().sovereign(city);
		if (kingdom == null) {
			ChatUi.fail(source, ChatUi.text("message.regnum-nations.founding.no_kingdom"));
			return 0;
		}
		Kingdom.CapitalStatus status = service.resolveCapital(kingdom);
		source.sendSuccess(() -> ChatUi.heading(Component.translatable("chat.regnum-nations.kingdom_overview"))
                .append(ChatUi.field("chat.regnum-nations.kingdom", kingdom.name().display()))
                .append(ChatUi.field("chat.regnum-nations.identifier", kingdom.id()))
                .append(ChatUi.field("chat.regnum-nations.dimension", kingdom.dimension().location()))
                .append(ChatUi.field("chat.regnum-nations.capital", kingdom.capitalPos().toShortString()))
                .append(ChatUi.field("chat.regnum-nations.status", ChatUi.text("message.regnum-nations.capital." + status.name().toLowerCase(Locale.ROOT))))
                .append(ChatUi.field("chat.regnum-nations.settlements", service.data().settlements().stream().filter(c -> kingdom.id().equals(c.sovereign())).count()))
                .append(ChatUi.field("chat.regnum-nations.territories", service.data().claimCount(kingdom.id())))
                .append(ChatUi.line(ChatUi.menu())), false);
		return 1;
	}

	private static int territory(CommandSourceStack source, String action) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return fail(source, new FoundingException(FoundingException.Reason.INVALID_PLAYER));
		KingdomSavedData data = KingdomService.get(source.getServer()).data();
		try {
			data.requireAvailable();
			ClaimPos pos = ClaimPos.at(player.level().dimension(), player.blockPosition());
			if (!action.equals("info")) {
                if (action.equals("unclaim")) {
                    TerritoryServer.prepareAbandonment(player,pos,null);
                    source.sendSuccess(() -> ChatUi.warning(ChatUi.text("territory.regnum-nations.command_confirm",pos.x(),pos.z()))
                            .append(ChatUi.line(ChatUi.run(Component.translatable("territory.regnum-nations.confirm"), "/regnum unclaim confirm",
                                    Component.translatable("chat.regnum-nations.abandon_hint")))),false); return 1;
                }
                if (action.equals("confirm_unclaim")) TerritoryServer.confirmCommand(player);
                else new ClaimService(data).manage(player,false);
				ChatUi.confirm(source, () -> ChatUi.text("message.regnum-nations.territory." + (action.equals("confirm_unclaim")?"unclaim":action),
						pos.dimension().location().toString(), pos.x(), pos.z())
                        .append(ChatUi.line(ChatUi.run(Component.translatable("chat.regnum-nations.view_territory"), "/regnum territory",
                                Component.translatable("chat.regnum-nations.territory_hint")))), false);
			} else {
				Settlement owner = data.settlement(data.claimOwner(pos));
				Component name = owner == null ? ChatUi.text("message.regnum-nations.territory.wilderness")
						: Component.literal(owner.name().display());
				source.sendSuccess(() -> ChatUi.heading(Component.translatable("chat.regnum-nations.territory_overview"))
                        .append(ChatUi.line(ChatUi.text("message.regnum-nations.territory.info",
						pos.dimension().location().toString(), pos.x(), pos.z(), name,
						owner == null ? Component.translatable("chat.regnum-nations.none") : owner.id().toString(), owner == null ? 0 : data.settlementClaimCount(owner.id())))), false);
                TerritoryServer.describe(data,player);
				Settlement own = data.byOwner(player.getUUID());
				if (own != null && !own.id().equals(data.claimOwner(ClaimPos.at(own.dimension(), own.obeliskPos())))) {
					ChatUi.fail(source, ChatUi.text("message.regnum-nations.founding.capital_conflict"));
				}
			}
			return 1;
		} catch (FoundingException exception) {
			return fail(source, exception);
		}
	}

	private static int fail(CommandSourceStack source, FoundingException exception) {
		ChatUi.fail(source, ChatUi.text(exception.translationKey()));
		return 0;
	}
}
