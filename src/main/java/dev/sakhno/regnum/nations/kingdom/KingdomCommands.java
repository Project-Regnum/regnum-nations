package dev.sakhno.regnum.nations.kingdom;

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
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
				var root = dispatcher.register(Commands.literal("nations")
						.executes(context -> help(context.getSource()))
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
								recipient.displayClientMessage(Component.translatable("city.regnum-nations.invitation",player.getGameProfile().getName(),id.toString()),false);
								source.sendSuccess(() -> Component.translatable("city.regnum-nations.invited",recipient.getGameProfile().getName()),false); return 1;
							} catch (FoundingException e) { return fail(source,e); }
						})))
						.then(Commands.literal("join").then(Commands.argument("kingdom",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(context -> {
							var source=context.getSource(); var player=source.getPlayerOrException();
							try { KingdomService.get(source.getServer()).cities().join(player,net.minecraft.commands.arguments.UuidArgument.getUuid(context,"kingdom"));
								source.sendSuccess(() -> Component.translatable("city.regnum-nations.joined"),false); return 1;
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
		});
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("message.regnum-nations.founding.help"), false);
		return 1;
	}

	private static int found(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return fail(source, new FoundingException(FoundingException.Reason.INVALID_PLAYER));
		try {
			Kingdom kingdom = KingdomService.get(source.getServer()).found(player, name);
			source.sendSuccess(() -> Component.translatable("message.regnum-nations.founding.success",
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
			source.sendFailure(Component.translatable("message.regnum-nations.founding.no_kingdom"));
			return 0;
		}
		Kingdom.CapitalStatus status = service.resolveCapital(kingdom);
		source.sendSuccess(() -> Component.translatable("message.regnum-nations.kingdom.info", kingdom.name().display(),
				kingdom.id().toString(), kingdom.dimension().location().toString(), kingdom.capitalPos().toShortString(),
				Component.translatable("message.regnum-nations.capital." + status.name().toLowerCase(Locale.ROOT))), false);
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
                    source.sendSuccess(() -> Component.translatable("territory.regnum-nations.command_confirm",pos.x(),pos.z()),false); return 1;
                }
                if (action.equals("confirm_unclaim")) TerritoryServer.confirmCommand(player);
                else new ClaimService(data).manage(player,false);
				source.sendSuccess(() -> Component.translatable("message.regnum-nations.territory." + (action.equals("confirm_unclaim")?"unclaim":action),
						pos.dimension().location().toString(), pos.x(), pos.z()), false);
			} else {
				Settlement owner = data.settlement(data.claimOwner(pos));
				Component name = owner == null ? Component.translatable("message.regnum-nations.territory.wilderness")
						: Component.literal(owner.name().display());
				source.sendSuccess(() -> Component.translatable("message.regnum-nations.territory.info",
						pos.dimension().location().toString(), pos.x(), pos.z(), name,
						owner == null ? "—" : owner.id().toString(), owner == null ? 0 : data.settlementClaimCount(owner.id())), false);
                TerritoryServer.describe(data,player);
				Settlement own = data.byOwner(player.getUUID());
				if (own != null && !own.id().equals(data.claimOwner(ClaimPos.at(own.dimension(), own.obeliskPos())))) {
					source.sendFailure(Component.translatable("message.regnum-nations.founding.capital_conflict"));
				}
			}
			return 1;
		} catch (FoundingException exception) {
			return fail(source, exception);
		}
	}

	private static int fail(CommandSourceStack source, FoundingException exception) {
		source.sendFailure(Component.translatable(exception.translationKey()));
		return 0;
	}
}
