// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1; see THIRD_PARTY_NOTICES.md.
package dev.sakhno.regnum.nations.client.terrain.diorama.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;

/** Reads a section's palette type, which vanilla keeps private. */
final class Sections {

	private Sections() {
	}

	/** The block filling the section if it uses a single-value palette, otherwise null. */
	static BlockState singleValue(PalettedContainer<BlockState> states) {
		// No bitsPerEntry() yet. The serialized size is one byte for the bits, the palette, and the
		// storage's longs: a single-value palette needs at most 6 bytes and has no storage, while any
		// other palette stores 4096 entries of at least one bit, 512 bytes or more.
		return states.getSerializedSize() < 512 ? states.get(0, 0, 0) : null;
	}
}
