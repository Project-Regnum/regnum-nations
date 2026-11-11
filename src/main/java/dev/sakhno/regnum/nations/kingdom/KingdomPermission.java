package dev.sakhno.regnum.nations.kingdom;

/** Kingdom capabilities follow existing founder authority; city roles grant no kingdom powers. */
public enum KingdomPermission {
    MANAGE_DIPLOMACY;
    public boolean allows(KingdomSavedData data,Kingdom kingdom,java.util.UUID player) {
        Kingdom current=kingdom==null?null:data.byId(kingdom.id());
        return current!=null&&current.founderId().equals(player);
    }
}
