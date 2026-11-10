package dev.sakhno.regnum.nations.kingdom;

import java.util.UUID;

/** The initial city policy. Political affiliation grants no municipal permissions. */
public final class CityPermissions {
    private CityPermissions() { }

    public static boolean canManage(Settlement city, UUID player) {
        return city != null && city.ownerId().equals(player);
    }

    public static boolean canBuild(KingdomSavedData data, Settlement city, UUID player) {
        return city != null && player != null && data.isCitizen(city.id(), player);
    }
}
