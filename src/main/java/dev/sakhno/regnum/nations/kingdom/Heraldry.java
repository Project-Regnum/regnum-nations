package dev.sakhno.regnum.nations.kingdom;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;

/** Canonical, immutable arms. No textures, names, player identities or rendering state. */
public record Heraldry(int version, Shield shield, Tincture field, Division division, Tincture secondary,
                       Ordinary ordinary, Tincture ordinaryTincture, List<Charge> charges) {
    public static final int VERSION=1, MAX_CHARGES=3;
    public interface Named { String id(); }
    public enum Shield implements Named {
        HEATER, KITE, ROUND, FRENCH, BOUCHE;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Tincture implements Named {
        OR(0xe4b642,true), ARGENT(0xe9e4d3,true), GULES(0x9e2935,false), AZURE(0x254e91,false),
        SABLE(0x22252b,false), VERT(0x28644a,false), PURPURE(0x693c78,false);
        public final int rgb; public final boolean metal;
        Tincture(int rgb,boolean metal) { this.rgb=rgb; this.metal=metal; }
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Division implements Named {
        PLAIN, PER_PALE, PER_FESS, PER_BEND, PER_SALTIRE, QUARTERLY;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Ordinary implements Named {
        NONE, PALE, FESS, BEND, CHEVRON, CROSS, SALTIRE;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum Emblem implements Named {
        LION, EAGLE, WOLF, STAG, CROWN, SWORD, FLEUR_DE_LIS, TOWER, SUN, STAR, ROSE;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    /** Position is percentage of the field, size is percentage of its width; rotation is quarter turns. */
    public record Charge(Emblem emblem,Tincture tincture,int x,int y,int scale,int rotation,boolean mirrored) {
        public Charge {
            Objects.requireNonNull(emblem); Objects.requireNonNull(tincture);
            if (x<15||x>85||y<15||y>80||scale<20||scale>85||rotation<0||rotation>3)
                throw new IllegalArgumentException("Heraldic charge outside bounds");
        }
    }
    public Heraldry {
        Objects.requireNonNull(shield); Objects.requireNonNull(field); Objects.requireNonNull(division);
        Objects.requireNonNull(secondary); Objects.requireNonNull(ordinary); Objects.requireNonNull(ordinaryTincture);
        charges=List.copyOf(charges);
        if (version!=VERSION||charges.size()>MAX_CHARGES) throw new IllegalArgumentException("Unsupported heraldry");
    }
    public static Heraldry defaults(UUID kingdom) {
        long hash=kingdom.getMostSignificantBits()^Long.rotateLeft(kingdom.getLeastSignificantBits(),23);
        var random=new Random(hash);
        return new Heraldry(VERSION,Shield.values()[random.nextInt(Shield.values().length)],
                Tincture.values()[2+random.nextInt(5)],Division.PLAIN,Tincture.ARGENT,Ordinary.NONE,Tincture.OR,
                List.of(new Charge(Emblem.values()[random.nextInt(Emblem.values().length)],Tincture.OR,50,46,65,0,false)));
    }
    /** Traditional rule of tincture is guidance, including charges over divided fields. */
    public boolean tinctureWarning() {
        if (ordinary!=Ordinary.NONE&&(ordinaryTincture.metal==field.metal||division!=Division.PLAIN&&ordinaryTincture.metal==secondary.metal)) return true;
        return charges.stream().anyMatch(c -> c.tincture.metal==field.metal
                ||division!=Division.PLAIN&&c.tincture.metal==secondary.metal
                ||ordinary!=Ordinary.NONE&&c.tincture.metal==ordinaryTincture.metal);
    }
    public CompoundTag toTag() {
        var tag=new CompoundTag(); tag.putInt("Version",version); tag.putString("Shield",shield.id());
        tag.putString("Field",field.id()); tag.putString("Division",division.id()); tag.putString("Secondary",secondary.id());
        tag.putString("Ordinary",ordinary.id()); tag.putString("OrdinaryTincture",ordinaryTincture.id());
        var list=new ListTag();
        for (var c:charges) {
            var e=new CompoundTag(); e.putString("Emblem",c.emblem.id()); e.putString("Tincture",c.tincture.id());
            e.putInt("X",c.x); e.putInt("Y",c.y); e.putInt("Scale",c.scale); e.putInt("Rotation",c.rotation); e.putBoolean("Mirrored",c.mirrored); list.add(e);
        }
        tag.put("Charges",list); return tag;
    }
    public static Heraldry fromTag(CompoundTag tag) {
        int version=integer(tag,"Version");
        if (!(tag.get("Charges") instanceof ListTag list)||list.size()>MAX_CHARGES
                ||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid charges");
        var charges=new ArrayList<Charge>();
        for (var item:list) {
            var c=(CompoundTag)item;
            if (!c.contains("Mirrored",Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing mirror flag");
            charges.add(new Charge(named(Emblem.values(),c.getString("Emblem")),named(Tincture.values(),c.getString("Tincture")),
                    integer(c,"X"),integer(c,"Y"),integer(c,"Scale"),integer(c,"Rotation"),c.getBoolean("Mirrored")));
        }
        return new Heraldry(version,named(Shield.values(),tag.getString("Shield")),named(Tincture.values(),tag.getString("Field")),
                named(Division.values(),tag.getString("Division")),named(Tincture.values(),tag.getString("Secondary")),
                named(Ordinary.values(),tag.getString("Ordinary")),named(Tincture.values(),tag.getString("OrdinaryTincture")),charges);
    }
    private static int integer(CompoundTag tag,String key) {
        if (!tag.contains(key,Tag.TAG_INT)) throw new IllegalArgumentException("Missing numeric heraldry field"); return tag.getInt(key);
    }
    private static <T extends Enum<T> & Named> T named(T[] values,String id) {
        for (var value:values) if (value.id().equals(id)) return value;
        throw new IllegalArgumentException("Unknown heraldic identifier");
    }
    // Bounded wire format, stable textual IDs. Maximum 473 bytes, independent of arbitrary NBT.
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(version); b.writeUtf(shield.id(),16); b.writeUtf(field.id(),16); b.writeUtf(division.id(),16);
        b.writeUtf(secondary.id(),16); b.writeUtf(ordinary.id(),16); b.writeUtf(ordinaryTincture.id(),16); b.writeByte(charges.size());
        for (var c:charges) {
            b.writeUtf(c.emblem.id(),16); b.writeUtf(c.tincture.id(),16); b.writeByte(c.x); b.writeByte(c.y);
            b.writeByte(c.scale); b.writeByte(c.rotation); b.writeBoolean(c.mirrored);
        }
    }
    public static Heraldry read(FriendlyByteBuf b) {
        int version=b.readVarInt(); var shield=named(Shield.values(),b.readUtf(16)); var field=named(Tincture.values(),b.readUtf(16));
        var division=named(Division.values(),b.readUtf(16)); var secondary=named(Tincture.values(),b.readUtf(16));
        var ordinary=named(Ordinary.values(),b.readUtf(16)); var tincture=named(Tincture.values(),b.readUtf(16));
        int count=b.readUnsignedByte(); if (count>MAX_CHARGES) throw new IllegalArgumentException("Too many charges");
        var charges=new ArrayList<Charge>();
        for (int i=0;i<count;i++) charges.add(new Charge(named(Emblem.values(),b.readUtf(16)),named(Tincture.values(),b.readUtf(16)),
                b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readBoolean()));
        return new Heraldry(version,shield,field,division,secondary,ordinary,tincture,charges);
    }
}
