package dev.sakhno.reigns.kingdom;

import java.text.Normalizer;
import java.util.Locale;

/** Display and uniqueness rules, independent of Minecraft and command parsing. */
public record KingdomName(String display) {
	public KingdomName {
		display = Normalizer.normalize(display, Normalizer.Form.NFC);
		if (!display.codePoints().allMatch(KingdomName::allowed)) {
			throw new IllegalArgumentException("Unsupported kingdom name character");
		}
		display = display.strip().replaceAll(" +", " ");
		int length = display.codePointCount(0, display.length());
		if (length < 3 || length > 32 || display.codePoints().noneMatch(Character::isLetterOrDigit)) {
			throw new IllegalArgumentException("Kingdom names require 3–32 characters and a letter or digit");
		}
	}

	private static boolean allowed(int codePoint) {
		int type = Character.getType(codePoint);
		return Character.isLetterOrDigit(codePoint) || type == Character.NON_SPACING_MARK
				|| type == Character.COMBINING_SPACING_MARK || codePoint == ' ' || codePoint == '-'
				|| codePoint == '\'' || codePoint == '’';
	}

	public String key() {
		return display.toLowerCase(Locale.ROOT);
	}
}
