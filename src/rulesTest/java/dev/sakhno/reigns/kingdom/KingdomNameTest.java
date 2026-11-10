package dev.sakhno.reigns.kingdom;

/** Dependency-free unit checks; run by testKingdomNames as part of check. */
public final class KingdomNameTest {
	public static void main(String[] args) {
		equal("Golden Vale", new KingdomName("  Golden   Vale  ").display());
		equal(new KingdomName("GOLDEN VALE").key(), new KingdomName("golden vale").key());
		equal(new KingdomName("Золота Долина").key(), new KingdomName("ЗОЛОТА ДОЛИНА").key());
		equal(new KingdomName("Café").key(), new KingdomName("Cafe\u0301").key());
		equal("Кам’янець-Подільський", new KingdomName("Кам’янець-Подільський").display());
		equal("King's Vale", new KingdomName("King's Vale").display());
		new KingdomName("A".repeat(32));
		new KingdomName("123");
		for (String invalid : new String[] {"", "  ", "ab", "A".repeat(33), "---", "'''", "§aKingdom",
				"Name\n", "Name\tHere", "Name\u200bHere", "<Kingdom>", "\"Golden Vale\"", "😀 Kingdom"}) {
			try {
				new KingdomName(invalid);
				throw new AssertionError("Accepted invalid name: " + invalid);
			} catch (IllegalArgumentException expected) {
				// Expected validation failure.
			}
		}
		System.out.println("KingdomName unit checks passed");
	}

	private static void equal(Object expected, Object actual) {
		if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
	}
}
