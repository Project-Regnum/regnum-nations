// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1; see THIRD_PARTY_NOTICES.md.
package dev.sakhno.regnum.nations.client.terrain.diorama.render.gpu;

/**
 * A square RGBA texture the mod fills from the CPU: map tiles, the fog mask and noise, icons.
 * Render thread only. How it lives on the GPU depends on the Minecraft version, so callers only
 * see this.
 */
public interface MapTexture extends AutoCloseable {
	/** A texture with {@code levels} mip levels, level 0 being {@code size} x {@code size}. */
	static MapTexture create(String label, int size, int levels) {
		return new GlIdTexture(size, levels);
	}

	/**
	 * Writes {@code edge} x {@code edge} pixels at (x, y) of one mip level, from the start of
	 * {@code abgr}. Each int is one pixel as ABGR, which is RGBA in memory.
	 */
	void write(int level, int x, int y, int edge, int[] abgr);

	void bind(int slot, Sampling sampling);

	@Override
	void close();
}
