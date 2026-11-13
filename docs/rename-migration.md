# Project Reigns → Regnum Nations

Regnum Nations is the nations and territorial management module of Project Regnum.
This rename changes branding, Java packages and presentation resources. It keeps
Minecraft 1.21.1, Java 21 bytecode, gameplay rules, persistence schema 3 and version
0.6.3. Existing schema 1/2 readers and migrations are unchanged.

## Compatibility contract

| Identifier | Decision and reason |
| --- | --- |
| Fabric mod ID | `regnum-nations`; provides `project-reigns` for existing mod dependencies. This alias does not migrate Minecraft registry IDs. |
| Java packages / entrypoints | `dev.sakhno.regnum.nations` / `RegnumNations*`. Integrations importing old classes or mixin paths must update. |
| Gradle coordinates / JAR | `dev.sakhno.regnum.nations:regnum-nations:0.6.3`; install `regnum-nations-0.6.3.jar`. |
| Block, item and block entity IDs | Keep `project-reigns:kingdom_obelisk` and `project-reigns:city_obelisk`. Changing these would orphan placed blocks, block entities and inventory items. No duplicate registrations or automatic world conversion. |
| Saved data | Keep `<world>/data/project-reigns-kingdoms.dat`, NBT keys, schema version, UUIDs, ownership, affiliations, claims, colors and deletion tombstones. Renaming the file would silently create an empty registry. |
| Political map configuration | Keep `config/project-reigns-map.properties` and `radiusChunks`, so server settings survive. |
| Network payload IDs and codecs | Keep the `project-reigns:*` channels and layouts. Install matching Regnum Nations versions on clients and servers; mixed-version play has not been validated. |
| Key binding | Keep `key.project-reigns.map` and `category.project-reigns` to preserve saved controls. |
| Commands | `/nations` is preferred; `/reigns` remains an alias with the same execution and permission checks. Old scripts and saved command blocks continue to work. |
| Presentation resources | Move language files, block models, textures, icon and Living Atlas assets to `assets/regnum-nations/`. Keep blockstates and item model adapters in `assets/project-reigns/`, pointing to the new models. Keep the old block-name and key-binding translation keys. |
| Data pack resources | Keep obelisk loot tables under `data/project-reigns/` and legacy item references in loot tables and mining tags because their IDs follow the retained registry IDs. |

`RegnumNations.id` builds new presentation identifiers. `legacyId` is reserved for
the retained registry and wire identifiers. Do not replace compatibility literals
as part of future branding cleanups.

The subsequent [terrain integration](living-atlas-terrain.md) adds new
`regnum-nations:atlas_session` and `regnum-nations:atlas_markers` channels without
changing existing codecs. Its separate `regnum-nations-atlas-world.dat` contains
only a cache UUID; deleting it invalidates exploration caches without changing the
retained kingdom registry, its schema or gameplay identities.

Resource packs replacing presentation files or translation keys must update their
namespace to `regnum-nations`. Packs using the retained blockstate/item adapters or
loot-table paths can continue to target those paths. Historical validation records
in the gameplay documentation describe earlier builds and are not evidence that
this rename received manual visual testing.

The subsequent [v0.7.0 citizenship migration](cities-and-sovereignty.md#persistence-and-migration)
adds schema-4 `Citizens` in the same saved-data file. All legacy identities and
records remain. Rollback from schema 4 requires the pre-upgrade world backup.
[v0.7.1 roles](cities-and-sovereignty.md#schema-5-upgrade-and-rollback) adds a role
inside each existing membership and writes schema 5. Schema-4 citizens default to
`CITIZEN`; rollback to v0.7.0 likewise requires the pre-upgrade world backup.

## Upgrade and rollback

1. Stop the server and back up the entire world and `config/` directory.
2. Replace the old Project Reigns JAR with the Regnum Nations JAR on the server
   and all clients. Remove the old JAR; do not install both together.
3. Keep world files and the map configuration at their existing paths. Do not
   rename or delete `project-reigns-kingdoms.dat` or change NBT/registry IDs.
4. On a copy of the world, inspect existing city/state obelisks, inventories,
   kingdom and city records, claim protection, political colors and maps. Confirm
   the saved M binding and Living Atlas screens before using the production save.
5. For rollback, stop the server, restore the pre-upgrade world/config backup and
   reinstall the matching old JARs. Older releases may not understand newer schemas;
   restoring the backup is safer than assuming backwards compatibility.

Automated checks cover literal legacy registry identifiers, block entity and item
NBT, loading the old saved-data filename from disk, stable record/claim identities,
mod metadata and the command alias, alongside existing gameplay tests. A real
player-world upgrade and interactive client visual checks still require manual review.

v0.8.0 [obelisk relocation](obelisk-relocation.md) retains schema 5, all registry
identities and existing packet layouts. Anchor positions may change within owned
territory; canonical recovery uses the same saved records. No migration is needed.

v0.8.1 [citizen management](citizen-management-ui.md) retains schema 5 and all
existing wire contracts. UI concurrency stamps and invitation IDs are ephemeral;
only two additive citizen UI channels are introduced. No migration is required.

v0.8.4 [kingdom administration](kingdom-administration.md) adds schema-6 ruler,
kingdom-role and persistent city-invitation fields. Existing identities, municipal
roles, affiliations, claims and legacy channels remain. Schema 1–5 founders migrate
to rulers; downgrade requires restoring the pre-upgrade world backup.

v0.8.3 [kingdom diplomacy](kingdom-diplomacy.md) writes schema 6 in the same saved
file, adding UUID-based agreements, declarations, invitations and pair revisions.
Schemas 1–5 migrate without replacing existing records. Registry IDs, filenames,
key bindings and existing wire layouts remain unchanged; two diplomacy channels
are additive. Rollback requires the pre-upgrade world backup.

## v0.8.5 combined schema

The integrated release writes **schema 7**, preserving both independently allocated
schema-6 formats and schemas 1–5. Administration ruler/roles/city invitations and
all diplomacy contracts coexist in the same legacy saved-data filename. Migration
uses actual fields rather than release-number ordering, preserves transferred
rulers and extension fields, and fails closed with original NBT retained when
recognized data is invalid. Legacy diplomacy requests gain issuer attribution to
the sender's ruler because the branch format did not store a player issuer.
Existing registry IDs, UUIDs, key bindings, config names and wire layouts remain.
Back up world/config first; old binaries cannot safely write schema 7. Recover
an older release only by restoring the pre-upgrade backup. See the
[integration compatibility contract](integration-audit-v0.8.5.md#final-persistence-contract).


v0.9.0 [heraldry and royal banners](heraldry-royal-banners.md) depends on the
unmerged v0.8.5 integration (schema 7) and adds schema 8. The saved filename and
legacy contracts remain; official arms key off kingdom UUIDs. New banner IDs use
`regnum-nations`. Back up the entire stopped world before upgrading; restoring the
pre-upgrade backup is required for rollback.

v0.10.0 [interstate diplomacy](diplomacy-interstate-relations.md) writes schema 9
in the same saved-data file. Existing UUID contracts migrate without replacement;
proposal types, non-aggression treaties, wars and pair cooldowns are additive.
Existing channels/codecs, financial policies and Nations API v1 remain intact;
expanded diplomacy uses new channels. Rollback requires the pre-upgrade backup.

v0.12.0 [warfare and sieges](warfare-sieges.md) retains schema 9 and adds an
optional, independently versioned `Warfare` compound to the same saved-data file.
Existing diplomacy, identifiers, city ownership, claims and Economy records are
unchanged. Siege victories retain pending conquest results without transferring
ownership. Malformed warfare data is preserved and disables warfare only. Older
binaries do not process active sieges; gameplay rollback requires a pre-upgrade
world backup. The warfare request/state channels are additive.
