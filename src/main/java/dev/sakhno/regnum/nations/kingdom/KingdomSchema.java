package dev.sakhno.regnum.nations.kingdom;

import java.util.*;
import net.minecraft.nbt.*;

/** Additive, deterministic schema-8 normalization and lossless extension-field retention. */
final class KingdomSchema {
    static final int CURRENT=8;
    private static final Set<String> ROOT=fields("Version Kingdoms Settlements Claims DeletedStateAnchors Citizens KingdomRoles CityInvitations Diplomacy BannerSecret");
    private static final Map<String,Set<String>> FIELDS=Map.of(
            "Kingdoms",fields("Id Name Founder Dimension Position Obelisk CreatedAt CapitalStatus CapitalSettlement Color Government Ruler Heraldry HeraldryRecovery"),
            "Settlements",fields("Id Name Founder Owner Dimension Position Obelisk CreatedAt CapitalStatus Sovereign Color LegacyAnchor"),
            "Claims",fields("Dimension X Z Kingdom Settlement"),
            "DeletedStateAnchors",fields("Id Dimension Position Obelisk"),
            "Citizens",fields("Player Settlement Role"),
            "KingdomRoles",fields("Kingdom Player Role"),
            "CityInvitations",fields("Id Kingdom City Inviter CreatedAt"),
            "Diplomacy",fields("First Second Revision Alliance AcceptedAt Hostility Invitation"),
            "Invitation",fields("Id Sender Recipient CreatedAt ExpiresAt Inviter"),
            "Hostility",fields("Declarer"));
    private KingdomSchema() { }
    private static Set<String> fields(String names) { return Set.of(names.split(" ")); }

    static CompoundTag migrate(CompoundTag source) {
        if (!source.contains("Version",Tag.TAG_INT)||source.getInt("Version")<1||source.getInt("Version")>CURRENT)
            throw new IllegalArgumentException("Unknown kingdom saved-data schema");
        CompoundTag tag=source.copy(); int version=tag.getInt("Version");
        // The two independent v6 formats are distinguished by administration fields,
        // never by release ordering. Missing rulers in an administration save are corrupt.
        ListTag kingdoms=tag.getList("Kingdoms",Tag.TAG_COMPOUND);
        boolean administration=tag.contains("KingdomRoles")||tag.contains("CityInvitations")
                ||kingdoms.stream().anyMatch(t -> ((CompoundTag)t).contains("Ruler"));
        for (Tag item:kingdoms) {
            CompoundTag kingdom=(CompoundTag)item;
            if (!kingdom.contains("Ruler")&&(version<6||version==6&&!administration))
                kingdom.putUUID("Ruler",kingdom.getUUID("Founder"));
            if (!kingdom.hasUUID("Ruler")) throw new IllegalArgumentException("Missing or malformed ruler");
        }
        // These collections are optional; a present value must still pass the strict reader.
        for (String key:List.of("KingdomRoles","CityInvitations","Diplomacy"))
            if (!tag.contains(key)) tag.put(key,new ListTag());
        tag.putInt("Version",CURRENT);
        return tag;
    }

    /** Only copy fields we do not own. Never resurrect deleted records/optional known fields. */
    static void retainExtensions(CompoundTag source,CompoundTag target) {
        copyUnknown(source,target,ROOT);
        for (String key:ROOT) {
            if (source.get(key) instanceof ListTag old && target.get(key) instanceof ListTag current)
                retainRecords(key,old,current);
        }
    }
    private static void retainRecords(String kind,ListTag old,ListTag current) {
        Map<String,CompoundTag> records=new HashMap<>();
        for (Tag item:old) if (item instanceof CompoundTag record) records.put(identity(kind,record),record);
        for (Tag item:current) if (item instanceof CompoundTag record) {
            CompoundTag previous=records.get(identity(kind,record));
            if (previous==null) continue;
            copyUnknown(previous,record,FIELDS.get(kind));
            if (kind.equals("Diplomacy")) {
                if (previous.get("Invitation") instanceof CompoundTag before && record.get("Invitation") instanceof CompoundTag after
                        &&Objects.equals(before.get("Id"),after.get("Id"))) copyUnknown(before,after,FIELDS.get("Invitation"));
                if (previous.get("Hostility") instanceof ListTag before && record.get("Hostility") instanceof ListTag after)
                    retainRecords("Hostility",before,after);
            }
        }
    }
    private static String identity(String kind,CompoundTag tag) {
        return switch (kind) {
            case "Claims" -> tag.getString("Dimension")+":"+tag.getInt("X")+":"+tag.getInt("Z");
            case "KingdomRoles" -> tag.get("Kingdom")+":"+tag.get("Player");
            case "Citizens" -> String.valueOf(tag.get("Player"));
            case "Hostility" -> String.valueOf(tag.get("Declarer"));
            case "Diplomacy" -> {
                String first=String.valueOf(tag.get("First")),second=String.valueOf(tag.get("Second"));
                yield first.compareTo(second)<0?first+":"+second:second+":"+first;
            }
            default -> String.valueOf(tag.get("Id"));
        };
    }
    private static void copyUnknown(CompoundTag source,CompoundTag target,Set<String> known) {
        for (String key:source.getAllKeys()) if (!known.contains(key)) target.put(key,source.get(key).copy());
    }
}
