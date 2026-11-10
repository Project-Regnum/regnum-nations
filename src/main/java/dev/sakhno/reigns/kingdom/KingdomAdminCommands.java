package dev.sakhno.reigns.kingdom;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sakhno.reigns.ProjectReigns;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;

/** Permission-gated command adapter. Services repeat authorization at every mutation boundary. */
public final class KingdomAdminCommands {
    private static final int PAGE_SIZE = 8;
    private KingdomAdminCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("admin").requires(source -> source.hasPermission(3))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource(),1))
                        .then(Commands.argument("page",IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(),IntegerArgumentType.getInteger(context,"page")))))
                .then(Commands.literal("info").then(target().executes(KingdomAdminCommands::info)))
                .then(Commands.literal("delete").then(target().executes(context -> delete(context,"request"))
                        .then(Commands.literal("confirm").then(Commands.argument("token",StringArgumentType.word()).executes(context -> delete(context,"confirm"))))
                        .then(Commands.literal("cancel").then(Commands.argument("token",StringArgumentType.word()).executes(context -> delete(context,"cancel"))))))
                .then(Commands.literal("settlements").executes(context -> cities(context,1))
                        .then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(context -> cities(context,IntegerArgumentType.getInteger(context,"page")))))
                .then(Commands.literal("settlement").then(Commands.argument("id",StringArgumentType.word()).executes(KingdomAdminCommands::cityInfo)))
                .then(Commands.literal("tp").then(target().executes(context -> mutate(context,"tp"))))
                .then(Commands.literal("rename").then(target()
                        .then(Commands.argument("newName",StringArgumentType.greedyString()).executes(context -> mutate(context,"rename")))))
                .then(Commands.literal("unclaim").then(target()
                        .then(Commands.argument("chunkX",IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ",IntegerArgumentType.integer())
                                        .executes(context -> mutate(context,"unclaim"))
                                        .then(Commands.argument("dimension",DimensionArgument.dimension())
                                                .executes(context -> mutate(context,"unclaim")))))));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,String> target() {
        return Commands.argument("kingdom",StringArgumentType.string()).suggests((context,builder) -> {
            if (!context.getSource().hasPermission(3)) return builder.buildFuture();
            var data = KingdomService.get(context.getSource().getServer()).data();
            try { data.requireAvailable(); }
            catch (FoundingException e) { return builder.buildFuture(); }
            var suggestions = data.kingdoms().stream().sorted(Comparator.comparing(k -> k.name().key())).limit(100)
                    .flatMap(k -> java.util.stream.Stream.of(k.id().toString(),StringArgumentType.escapeIfRequired(k.name().display()))).toList();
            return SharedSuggestionProvider.suggest(suggestions,builder);
        });
    }
    private static Component text(String key,Object... args) { return Component.translatable("admin.project-reigns."+key,args); }
    private static int help(CommandSourceStack source) throws CommandSyntaxException {
        KingdomService.requireAdministrator(source);
        source.sendSuccess(() -> text("help"),false); return 1;
    }
    private static int list(CommandSourceStack source,int page) throws CommandSyntaxException {
        KingdomService.requireAdministrator(source);
        var service = KingdomService.get(source.getServer());
        try {
            service.data().requireAvailable();
            var kingdoms = service.data().kingdoms().stream().sorted(Comparator.comparing((Kingdom k) -> k.name().key()).thenComparing(Kingdom::id)).toList();
            int pages = Math.max(1,(kingdoms.size()+PAGE_SIZE-1)/PAGE_SIZE);
            if (page > pages) throw KingdomService.adminError("page",pages);
            source.sendSuccess(() -> text("list_header",page,pages,kingdoms.size()),false);
            if (kingdoms.isEmpty()) source.sendSuccess(() -> text("empty"),false);
            for (int i=(page-1)*PAGE_SIZE;i<Math.min(page*PAGE_SIZE,kingdoms.size());i++) {
                Kingdom kingdom = kingdoms.get(i);
                source.sendSuccess(() -> text("list_entry",kingdom.name().display(),kingdom.id().toString(),
                        founder(source,kingdom),kingdom.founderId().toString(),service.data().claimCount(kingdom.id())),false);
            }
            return 1;
        } catch (FoundingException e) { source.sendFailure(ruleError(e)); return 0; }
    }
    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource(); KingdomService.requireAdministrator(source);
        var service = KingdomService.get(source.getServer());
        try {
            Kingdom kingdom = service.identify(StringArgumentType.getString(context,"kingdom"));
            var status = service.resolveCapital(kingdom);
            source.sendSuccess(() -> text("info",kingdom.name().display(),kingdom.id().toString(),founder(source,kingdom),
                    kingdom.founderId().toString(),kingdom.dimension().location().toString(),kingdom.capitalPos().toShortString(),
                    Component.translatable("message.project-reigns.capital."+status.name().toLowerCase(Locale.ROOT)),
                    kingdom.capitalObeliskId().toString(),service.data().claimCount(kingdom.id())),false);
            return 1;
        } catch (FoundingException e) { source.sendFailure(ruleError(e)); return 0; }
    }
    private static int mutate(CommandContext<CommandSourceStack> context,String action) {
        var source = context.getSource();
        String input = StringArgumentType.getString(context,"kingdom");
        String target = input;
        String details = "";
        try {
            KingdomService.requireAdministrator(source);
            var service = KingdomService.get(source.getServer());
            Kingdom kingdom = service.identify(input);
            target = kingdom.id().toString();
            Component result;
            switch (action) {
                case "rename" -> {
                    String raw = StringArgumentType.getString(context,"newName").strip();
                    if (raw.startsWith("\"")) {
                        var reader = new StringReader(raw); raw = reader.readQuotedString();
                        if (reader.canRead()) throw KingdomService.adminError("invalid_name");
                    }
                    Kingdom renamed = service.rename(source,kingdom.id(),raw);
                    details = "old="+kingdom.name().display()+" new="+renamed.name().display();
                    result = text("renamed",kingdom.name().display(),renamed.name().display(),kingdom.id().toString());
                }
                case "unclaim" -> {
                    // Resolve inside the audited handler so a missing dimension is logged as a rejected action.
                    var dimension = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("dimension"))
                            ? DimensionArgument.getDimension(context,"dimension").dimension() : source.getLevel().dimension();
                    var pos = new ClaimPos(dimension,IntegerArgumentType.getInteger(context,"chunkX"),IntegerArgumentType.getInteger(context,"chunkZ"));
                    details = pos.toString();
                    new ClaimService(service.data()).forceUnclaim(source,kingdom.id(),pos);
                    result = text("unclaimed",kingdom.name().display(),pos.x(),pos.z(),dimension.location().toString());
                }
                case "tp" -> {
                    details = kingdom.dimension().location()+" "+kingdom.capitalPos().toShortString();
                    var landing = service.teleportAdministrator(source,kingdom.id());
                    result = text("teleported",kingdom.name().display(),kingdom.dimension().location().toString(),landing.toShortString());
                }
                default -> throw new IllegalStateException("Unknown administrative operation");
            }
            audit(source,action,target,details,"success");
            source.sendSuccess(() -> result,false); return 1;
        } catch (FoundingException e) {
            audit(source,action,target,details,e.reason().name()); source.sendFailure(ruleError(e)); return 0;
        } catch (CommandSyntaxException e) {
            audit(source,action,target,details,e.getRawMessage().getString());
            source.sendFailure(e.getRawMessage() instanceof Component component ? component : Component.literal(e.getRawMessage().getString())); return 0;
        }
    }
    private static int delete(CommandContext<CommandSourceStack> context,String action) {
        var source=context.getSource(); String input=StringArgumentType.getString(context,"kingdom"); boolean serviceCalled=false;
        try {
            KingdomService.requireAdministrator(source); var service=KingdomService.get(source.getServer()); Kingdom kingdom=service.identify(input);
            if (!action.equals("request")) {
                java.util.UUID token;
                try { token=java.util.UUID.fromString(StringArgumentType.getString(context,"token")); }
                catch (IllegalArgumentException e) { throw KingdomService.adminError("delete_confirmation_invalid"); }
                serviceCalled=true;
                if (action.equals("cancel")) service.cancelDeletion(source,kingdom.id(),token);
                else service.confirmDeletion(source,kingdom.id(),token);
                source.sendSuccess(() -> text(action.equals("cancel")?"delete_cancelled":"deleted",kingdom.name().display()),false);
            } else {
                serviceCalled=true; var request=service.prepareDeletion(source,kingdom.id());
                source.sendSuccess(() -> KingdomDeletionPrompt.create(kingdom,request.token(),request.existing()),false);
            }
            return 1;
        } catch (FoundingException e) {
            if (!serviceCalled) audit(source,"delete",input,"",e.reason().name()); source.sendFailure(ruleError(e)); return 0;
        } catch (CommandSyntaxException e) {
            if (!serviceCalled) audit(source,"delete",input,"",e.getRawMessage().getString());
            source.sendFailure(e.getRawMessage() instanceof Component c?c:Component.literal(e.getRawMessage().getString())); return 0;
        }
    }
    private static int cities(CommandContext<CommandSourceStack> context,int page) throws CommandSyntaxException {
        var source=context.getSource(); KingdomService.requireAdministrator(source); var data=KingdomService.get(source.getServer()).data();
        try {
            data.requireAvailable(); var cities=data.settlements().stream().sorted(Comparator.comparing((Settlement city) -> city.name().key()).thenComparing(Settlement::id)).toList();
            int pages=Math.max(1,(cities.size()+PAGE_SIZE-1)/PAGE_SIZE); if (page>pages) throw KingdomService.adminError("page",pages);
            source.sendSuccess(() -> text("cities_header",page,pages,cities.size()),false);
            for (int i=(page-1)*PAGE_SIZE;i<Math.min(page*PAGE_SIZE,cities.size());i++) {
                Settlement city=cities.get(i); source.sendSuccess(() -> text("city_entry",city.name().display(),city.id().toString(),city.ownerId().toString(),
                        city.sovereign()==null?text("independent"):Component.literal(city.sovereign().toString()),data.settlementClaimCount(city.id())),false);
            }
            return 1;
        } catch (FoundingException e) { source.sendFailure(ruleError(e)); return 0; }
    }
    private static int cityInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source=context.getSource(); KingdomService.requireAdministrator(source); var service=KingdomService.get(source.getServer()); var data=service.data();
        try {
            data.requireAvailable(); java.util.UUID id;
            try {
                String identifier=StringArgumentType.getString(context,"id"); id=java.util.UUID.fromString(identifier);
                if (!id.toString().equalsIgnoreCase(identifier)) throw new IllegalArgumentException("Not a canonical UUID");
            }
            catch (IllegalArgumentException e) { throw KingdomService.adminError("unknown_city"); }
            Settlement city=data.settlement(id); if (city==null) throw KingdomService.adminError("unknown_city");
            source.sendSuccess(() -> text("city_info",city.name().display(),city.id().toString(),city.ownerId().toString(),city.founderId().toString(),city.dimension().location().toString(),
                    city.obeliskPos().toShortString(),city.obeliskId().toString(),city.sovereign()==null?text("independent"):Component.literal(city.sovereign().toString()),
                    data.settlementClaimCount(id),Component.translatable("message.project-reigns.capital."+service.cities().resolve(city).name().toLowerCase(Locale.ROOT)),
                    Component.translatable("ui.project-reigns."+(data.ownedSettlements(city.ownerId()).size()>1||!city.id().equals(data.claimOwner(ClaimPos.at(city.dimension(),city.obeliskPos())))?"yes":"no"))),false);
            return 1;
        } catch (FoundingException e) { source.sendFailure(ruleError(e)); return 0; }
    }

    private static Component ruleError(FoundingException e) {
        return switch (e.reason()) {
            case INVALID_NAME -> text("invalid_name");
            case DUPLICATE_NAME -> text("duplicate_name");
            case NOT_OWNER -> text("not_owner");
            case DATA_UNAVAILABLE -> text("data_unavailable");
            default -> Component.translatable(e.translationKey());
        };
    }
    private static Component founder(CommandSourceStack source,Kingdom kingdom) {
        var online = source.getServer().getPlayerList().getPlayer(kingdom.founderId());
        if (online != null) return Component.literal(online.getGameProfile().getName());
        var cache = source.getServer().getProfileCache();
        return cache == null ? text("unknown_founder") : cache.get(kingdom.founderId())
                .map(profile -> (Component)Component.literal(profile.getName())).orElseGet(() -> text("unknown_founder"));
    }
    static void audit(CommandSourceStack source,String action,String target,String details,String outcome) {
        ProjectReigns.LOGGER.info("Reigns admin executor={} entity={} action={} target={} details={} outcome={}",
                clean(source.getTextName()),source.getEntity() == null ? "console" : source.getEntity().getUUID(),
                action,clean(target),clean(details),clean(outcome));
    }
    private static String clean(String value) { return value.replaceAll("[\\p{Cntrl}§]","?"); }
}
