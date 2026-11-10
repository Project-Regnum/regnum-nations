// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1; see THIRD_PARTY_NOTICES.md.
package dev.sakhno.regnum.nations.client.terrain.diorama;

/**
 * The isolated vertical camera for Living Atlas. Elevation never moves a claim.
 *
 * <p>The camera looks north and down at the style's pitch from the horizon, so the map shows
 * block tops and their south faces. Map space is measured in blocks:
 * <ul>
 *   <li>{@code u = x} (east is right)</li>
 *   <li>{@code v = z * sin - y * cos} (south is down, and higher blocks move up the screen)</li>
 * </ul>
 */
public final class Projection {
	// The default style's angle until a style is applied.
	private static final double sin = 1;
	private static final double cos = 0;

	/** Map units per tile edge. */
	public static final int TILE_UNITS = 128;
	/** Texture pixels per map unit. */
	public static final int PIXELS_PER_UNIT = 4;
	public static final int TILE_PIXELS = TILE_UNITS * PIXELS_PER_UNIT;
	/** Fog coverage cells per tile edge (one cell covers {@code TILE_UNITS / COVERAGE_CELLS} units). */
	public static final int COVERAGE_CELLS = 64;

	private Projection() {
	}

	/** Changes the camera angle. Everything baked before is wrong afterwards; see {@link MapStyle}. */
	static void setPitch(double degrees) {
		if (degrees != 90) throw new IllegalArgumentException("Living Atlas requires a vertical camera");
	}

	public static double sin() {
		return sin;
	}

	public static double cos() {
		return cos;
	}

	public static double v(double y, double z) {
		return z * sin - y * cos;
	}

	/** World z where a ray through map row {@code v} crosses height {@code y}. */
	public static double zAt(double v, double y) {
		return (v + y * cos) / sin;
	}
}
