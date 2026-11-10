// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1; see THIRD_PARTY_NOTICES.md.
package dev.sakhno.regnum.nations.client.terrain.diorama.bake;

import dev.sakhno.regnum.nations.client.terrain.diorama.Projection;

/**
 * One tile of the map. {@code layer} is {@link #SURFACE} for the sky view, or the cut height for a
 * cave slice. {@code lod} 0 is a baked tile; each level above covers twice the distance in the
 * same pixels, made by shrinking its four children, for zooming far out.
 */
public record TileKey(int layer, int tileU, int tileV, int lod) {
	public static final int SURFACE = Integer.MIN_VALUE;
	/** Coarsest overview level: one tile covers 4096 blocks across. */
	public static final int MAX_LOD = 5;

	public TileKey(int layer, int tileU, int tileV) {
		this(layer, tileU, tileV, 0);
	}

	public boolean isCave() {
		return this.layer != SURFACE;
	}

	/** Map units across this tile. */
	public double units() {
		return (double) Projection.TILE_UNITS * (1 << this.lod);
	}

	public double minU() {
		return this.tileU * this.units();
	}

	public double minV() {
		return this.tileV * this.units();
	}

	/** The overview tile one level up that contains this one. */
	public TileKey parent() {
		return new TileKey(this.layer, Math.floorDiv(this.tileU, 2), Math.floorDiv(this.tileV, 2), this.lod + 1);
	}
}
