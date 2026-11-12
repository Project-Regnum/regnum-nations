package dev.sakhno.regnum.nations.api.client.v1;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.api.v1.EntityRef;
import dev.sakhno.regnum.nations.client.ui.EconomyScreen;
import dev.sakhno.regnum.nations.networking.EconomyNavigationPayloads;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/** Small client-only panel registry. Registration occurs during client initialization.
 * Navigation appears only when provider IDs match the connected server's capabilities.
 * Reusable visual primitives are in client.ui.KingdomUi, RoyalScreen and NationsConfirmationScreen.
 */
public final class EconomyUi {
    private record Registration(ResourceLocation provider, EconomyPanel panel) { }
    private static final Map<ResourceLocation, Registration> PANELS = new LinkedHashMap<>();
    private static Set<ResourceLocation> serverProviders = Set.of();
    private EconomyUi() { }
    public static void register(ResourceLocation provider, EconomyPanel panel) {
        Objects.requireNonNull(provider); Objects.requireNonNull(panel);
        if (PANELS.size() >= 64) throw new IllegalStateException("Too many Economy panels");
        if (PANELS.putIfAbsent(panel.id(), new Registration(provider, panel)) != null)
            throw new IllegalArgumentException("Duplicate Economy panel " + panel.id());
    }
    public static boolean available(EntityRef.Scope scope) {
        return ClientPlayNetworking.canSend(EconomyNavigationPayloads.Request.TYPE)
                && PANELS.values().stream().anyMatch(r -> serverProviders.contains(r.provider) && r.panel.scopes().contains(scope));
    }
    public static List<EconomyPanel> panels(EconomyContext context) {
        return PANELS.values().stream().filter(r -> serverProviders.contains(r.provider))
                .map(Registration::panel).filter(p -> p.scopes().contains(context.entity().ref().scope())
                        && context.permissions().containsAll(p.requiredPermissions())).toList();
    }
    /** Opens the Nations political context and category navigation, not a financial account. */
    public static Screen screen(Screen parent, EntityRef entity) { return new EconomyScreen(parent, entity); }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EconomyNavigationPayloads.Capabilities.TYPE,
                (payload, context) -> serverProviders = Set.copyOf(payload.providers()));
        ClientPlayNetworking.registerGlobalReceiver(EconomyNavigationPayloads.State.TYPE, (payload, context) -> {
            if (context.client().screen instanceof EconomyScreen screen) screen.accept(payload);
        });
        ClientPlayConnectionEvents.INIT.register((handler, client) -> serverProviders = Set.of());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverProviders = Set.of());
        for (var integration : FabricLoader.getInstance().getEntrypointContainers("regnum-nations:economy_client", NationsEconomyClient.class)) {
            var registered = Set.copyOf(PANELS.keySet());
            try { integration.getEntrypoint().initialize(); }
            catch (RuntimeException failure) {
                PANELS.keySet().removeIf(id -> !registered.contains(id));
                RegnumNations.LOGGER.error("Economy UI integration failed: {}", integration.getProvider().getMetadata().getId(), failure);
            }
        }
    }
}
