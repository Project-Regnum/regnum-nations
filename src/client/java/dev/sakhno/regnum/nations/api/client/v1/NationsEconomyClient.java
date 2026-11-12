package dev.sakhno.regnum.nations.api.client.v1;

/** Optional Fabric entrypoint `regnum-nations:economy_client`, invoked on the client only.
 * Register EconomyUi panels here. Register the matching provider ID through
 * NationsIntegrations.registerEconomy in the common `regnum-nations:integration` entrypoint.
 * Declare these entrypoints in the companion mod, with a dependency on regnum-nations.
 * Nations never imports or depends on the companion mod.
 */
@FunctionalInterface
public interface NationsEconomyClient {
    void initialize();
}
