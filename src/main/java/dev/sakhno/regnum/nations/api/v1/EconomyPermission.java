package dev.sakhno.regnum.nations.api.v1;

/** Stable textual identifiers, evaluated against exactly one EntityRef, never its parent. */
public enum EconomyPermission {
    ECONOMY_VIEW_TREASURY,
    ECONOMY_DEPOSIT_TREASURY,
    ECONOMY_WITHDRAW_TREASURY,
    ECONOMY_MANAGE_TREASURY,
    ECONOMY_SET_TAX,
    ECONOMY_CREATE_MARKET,
    ECONOMY_MANAGE_MARKET,
    ECONOMY_MANAGE_CONTRACTS,
    ECONOMY_MANAGE_TRADE_ROUTES,
    ECONOMY_MANAGE_MINT
}
