package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.api.v1.*;
import java.util.Optional;

/** One conversion shared by public reads, lifecycle notifications and client identity replies. */
final class PoliticalSnapshots {
    private PoliticalSnapshots() { }
    static EntitySnapshot of(Kingdom kingdom) {
        return new EntitySnapshot(EntityRef.kingdom(kingdom.id()), kingdom.name().display(), kingdom.founderId(),
                kingdom.rulerId(), Optional.of(kingdom.id()), Optional.of(kingdom.capitalSettlementId()),
                PoliticalColors.rgb(kingdom.color()), Optional.of(kingdom.id()), kingdom.dimension().location(), kingdom.capitalPos());
    }
    static EntitySnapshot of(KingdomSavedData data, Settlement city) {
        var kingdom = data.sovereign(city);
        return new EntitySnapshot(EntityRef.city(city.id()), city.name().display(), city.founderId(), city.ownerId(),
                Optional.ofNullable(city.sovereign()), kingdom == null ? Optional.empty() : Optional.of(kingdom.capitalSettlementId()),
                PoliticalColors.rgb(data.politicalColor(city)), Optional.ofNullable(city.sovereign()), city.dimension().location(), city.obeliskPos());
    }
}
