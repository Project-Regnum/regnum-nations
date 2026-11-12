package dev.sakhno.regnum.nations.api.v1;

/** Optional Fabric entrypoint `regnum-nations:integration` (common environment).
 * Register NationsEvents listeners or server integration here. This callback runs once during
 * Nations initialization; acquire NationsApi only once a server exists. No Economy dependency
 * or implementation class is referenced by Nations. Client UI uses a separate client entrypoint.
 */
@FunctionalInterface
public interface NationsIntegration {
    void initialize();
}
