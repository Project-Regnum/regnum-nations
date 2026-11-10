// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1; see THIRD_PARTY_NOTICES.md.
package dev.sakhno.regnum.nations.client.terrain.diorama.render.gpu;

// A MapTexture that ImmediateCanvas binds by hand before 1.21.6.
interface LegacyTexture extends MapTexture {
	// Sets the texture's sampling and puts it in a shader texture slot for the next draw.
	void bind(int slot, Sampling sampling);
}
