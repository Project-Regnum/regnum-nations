package dev.sakhno.regnum.nations.kingdom;

import java.util.Locale;

/** Business failures are translated by the interaction/command adapters. */
public final class FoundingException extends Exception {
	public enum Reason {
		INVALID_PLAYER, NO_SELECTION, EXPIRED, WRONG_DIMENSION, TOO_FAR, OBELISK_UNAVAILABLE,
		ALREADY_BOUND, ALREADY_FOUNDER, INVALID_NAME, DUPLICATE_NAME, DATA_UNAVAILABLE, TERRITORY_OCCUPIED, NOT_ADJACENT,
		NOT_OWNER, CAPITAL_CHUNK, CAPITAL_CONFLICT, NO_KINGDOM, NOT_IN_CHUNK, DOMAIN_LIMIT, DOMAIN_REPAIR, INVALID_COLOR, STATE_ANCHOR, ALREADY_AFFILIATED, INVITATION_REQUIRED,
        CITY_MISSING, NO_CITY, ALREADY_CITIZEN, NOT_CITIZEN, OWNER_CANNOT_LEAVE, CITIZEN_INVITATION_REQUIRED, PLAYER_OFFLINE, CITIZEN_RATE_LIMIT, CITY_IS_CAPITAL, CITIZEN_UNKNOWN_PLAYER
	}

	private final Reason reason;

	public FoundingException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public String translationKey() {
		return "message.regnum-nations.founding." + reason.name().toLowerCase(Locale.ROOT);
	}
}
