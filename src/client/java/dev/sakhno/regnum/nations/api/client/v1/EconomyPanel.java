package dev.sakhno.regnum.nations.api.client.v1;

import dev.sakhno.regnum.nations.api.v1.*;
import java.util.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A navigation action or content screen owned by a companion mod. requiredPermissions uses AND
 * semantics. Scope is explicit; use separate panels/policies for city and kingdom treasuries.
 * create must return a fresh screen and use returnTo for its back action. Financial packets,
 * storage and server validation remain entirely with the provider.
 */
public record EconomyPanel(ResourceLocation id, Category category, Component title, Set<EntityRef.Scope> scopes,
        Set<EconomyPermission> requiredPermissions, Factory factory) {
    public enum Category { TREASURY, MARKETS, CONTRACTS, TRADE_ROUTES, ADMINISTRATION }
    @FunctionalInterface public interface Factory { Screen create(EconomyContext context, Screen returnTo); }
    public EconomyPanel {
        Objects.requireNonNull(id); Objects.requireNonNull(category); Objects.requireNonNull(title); Objects.requireNonNull(factory);
        scopes = Set.copyOf(scopes); requiredPermissions = Set.copyOf(requiredPermissions); title = title.copy();
        if (scopes.isEmpty()) throw new IllegalArgumentException("A panel needs a political scope");
    }
    @Override public Component title() { return title.copy(); }
}
