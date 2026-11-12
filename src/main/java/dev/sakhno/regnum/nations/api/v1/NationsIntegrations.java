package dev.sakhno.regnum.nations.api.v1;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Register an Economy provider in its common NationsIntegration.initialize callback.
 * Match this ID when registering client panels. Only matching providers installed on both
 * server and client are advertised. This registry holds capabilities, never financial state.
 */
public final class NationsIntegrations {
    private static final Set<ResourceLocation> ECONOMY = new LinkedHashSet<>();
    private NationsIntegrations() { }
    public static void registerEconomy(ResourceLocation provider) {
        Objects.requireNonNull(provider);
        if (ECONOMY.size() >= 16 && !ECONOMY.contains(provider)) throw new IllegalStateException("Too many Economy providers");
        ECONOMY.add(provider);
    }
    public static Set<ResourceLocation> economyProviders() { return Set.copyOf(ECONOMY); }
}
