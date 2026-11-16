# Canonical commands and native chat

Regnum Nations now uses `/regnum` for all visible commands, help, UI hints and
chat actions. Messages share a restrained gold/gray palette, with green success,
yellow warnings and red errors. Status words are translated, so meaning does not
depend on color. English and Ukrainian resources retain the same key set and
placeholder arguments.

## Components and delivery

`src/main/java/dev/sakhno/regnum/nations/chat/ChatUi.java` owns the native Minecraft
`Component`, `Style`, `ClickEvent` and `HoverEvent` presentation rules:

- A translated `[Regnum]` prefix, headings, sections, fields and indented lines.
- Information, success, warning and error messages, including action-bar delivery.
- Gold value components for names, identifiers, coordinates and counts. Supplied
  components are copied; translations, styles and interactive children remain native.
- Consistent bracketed run/suggest actions with explanatory translated hover text.
  Actions require the `/regnum` root. Argument-taking help entries suggest editable
  input; complete navigation commands run directly.
- One-based pagination that renders only available previous/next destinations.
- Command feedback helpers that preserve existing private/broadcast behavior.
  Custom Brigadier errors can pass through a catch block without duplicate prefixes.

`CommandHelp.java` composes three command-center pages, two citizenship pages and
three administrator pages using these components. The command adapters retain all
permission, argument and suggestion builders. Domain services remain authoritative;
a clickable action always returns through the registered command/service checks.
No client-only classes, custom fonts or external UI libraries are needed.

Kingdom overviews use labeled fields. Territory details, roles and permissions are
grouped into readable messages. Citizen and administrator lists contain eight
entries per page; administrator entries link to the corresponding inspection
command. Territory boundary notifications retain their existing triggers and
existing action-bar/chat delivery. Invitations expose accept/decline actions;
relocation and abandonment expose their existing server-validated confirmation.
Dissolution retains its exact server-issued UUID/token, red confirmation, cancellation
and editable text fallback.

## Commands and compatibility

| Command | Behavior |
| --- | --- |
| `/regnum`, `/regnum help [1–3]` | Public command-center pages. Administration link appears only for permission level 3+. |
| `/regnum city`, `kingdom`, `territory`, `menu` | Existing city interface, kingdom information, territory inspection and management menu. |
| `/regnum citizen`, `/regnum citizen help [1–2]` | Citizenship command pages. Existing citizen actions remain available. |
| `/regnum citizen list [page]` | Citizen list with role labels and navigation; omitted page defaults to 1. |
| `/regnum admin`, `/regnum admin help [1–3]` | Administration pages, retaining permission level 3 and service authorization. |
| `/regnum admin list [page]`, `/regnum admin settlements [page]` | Existing paginated records with native navigation and inspection actions. |

All other registered subcommands retain their handlers and argument shapes. There
is no fabricated map command or button. The existing M binding and map interface
remain available.

The pre-existing [migration contract](rename-migration.md#compatibility-contract)
requires old scripts and saved command blocks to continue working. For that
specific compatibility requirement, `/nations` and `/reigns` redirect to the same
`/regnum` tree, with the same permission checks and suggestions. They are absent
from current player-facing translation values and clickable commands.

No gameplay storage or wire migration is introduced. Registry IDs, UUIDs, NBT
keys/schema, saved-data filenames, config filenames, key-binding IDs, resource
adapters, mod dependency alias and network payload layouts are unchanged. Remaining
case-insensitive `reigns` references are these compatibility contracts, migration
fixtures/tests, or historical release records. Do not mechanically rename them.

Read-only review of the default `main` branches on 2026-11-14 found no `reigns` or
`/nations` references in the text sources of Project Regnum's
[Auth](https://github.com/Project-Regnum/regnum-auth),
[World](https://github.com/Project-Regnum/regnum-world),
[Economy](https://github.com/Project-Regnum/regnum-economy) or
[Agriculture](https://github.com/Project-Regnum/regnum-agriculture) repositories.
No other repository was changed.

## Verification

Executed successfully:

```sh
./gradlew build compileGametestJava -x runGameTest -x resetGameTestWorld --no-daemon --console=plain
python tools/verify_mod_jar.py build/libs/regnum-nations-0.12.0.jar
python tools/validate_obelisk_assets.py
git diff --check
```

`testChatUi`, included in `check`, runs on Java 21. It initializes vanilla registries
needed by hover components but starts no client, server, world or GameTest runner.
It exercises actual native text and Brigadier registration, public help execution,
help bounds, required command nodes, canonical/compatibility routing, permission
gates, suggestions, English/Ukrainian rendering, translated status words, hover
presence, action parsing, pagination and dissolution token/danger styling.
`testKingdomNames` also passed on Java 21. The ordinary `test` task has no sources;
it is not evidence of gameplay integration coverage.

GameTest assertions were updated for canonical commands, nested components and
combined list messages. Those sources compiled, but **no GameTests were run**.
Compilation reported the existing deprecated `makeMockServerPlayerInLevel()` use
in `TerritoryHardeningTests.java`.

The artifact verifier checks Java 21 bytecode, locale parity/arguments, valid Unicode,
absence of obsolete visible branding, common/client isolation, packaged assets and
legacy resource adapters. It also excludes the standalone chat tests from the mod
JAR. A source diff confirmed no changes to saved-data, registries, network payloads,
config paths or dependency versions. The artifact is
`build/libs/regnum-nations-0.12.0.jar`.

In-game visual inspection, actual player command execution and real-world save/reload
were not performed. Storage/network compatibility is supported by unchanged contracts
and artifact/source checks; those checks do not establish mixed-version client/server
support. The existing matching-client/server requirement remains.

## Changed files

- [AGENTS.md](../AGENTS.md)
- [README.md](../README.md)
- [build.gradle](../build.gradle)
- [docs/chat-ui-overhaul.md](../docs/chat-ui-overhaul.md)
- [docs/cities-and-sovereignty.md](../docs/cities-and-sovereignty.md)
- [docs/citizen-management-ui.md](../docs/citizen-management-ui.md)
- [docs/diplomacy-interstate-relations.md](../docs/diplomacy-interstate-relations.md)
- [docs/heraldry-royal-banners.md](../docs/heraldry-royal-banners.md)
- [docs/integration-audit-v0.8.5.md](../docs/integration-audit-v0.8.5.md)
- [docs/kingdom-administration.md](../docs/kingdom-administration.md)
- [docs/kingdom-diplomacy.md](../docs/kingdom-diplomacy.md)
- [docs/kingdom-foundation.md](../docs/kingdom-foundation.md)
- [docs/kingdom-management.md](../docs/kingdom-management.md)
- [docs/kingdom-obelisk.md](../docs/kingdom-obelisk.md)
- [docs/obelisk-relocation.md](../docs/obelisk-relocation.md)
- [docs/release-hardening-v0.9.1.md](../docs/release-hardening-v0.9.1.md)
- [docs/rename-migration.md](../docs/rename-migration.md)
- [docs/territory-border-management.md](../docs/territory-border-management.md)
- [docs/territory-claims.md](../docs/territory-claims.md)
- [docs/ui-design-system.md](../docs/ui-design-system.md)
- [docs/ux-and-administration.md](../docs/ux-and-administration.md)
- [docs/warfare-sieges.md](../docs/warfare-sieges.md)
- [src/chatTest/java/dev/sakhno/regnum/nations/kingdom/ChatUiTest.java](../src/chatTest/java/dev/sakhno/regnum/nations/kingdom/ChatUiTest.java)
- [src/gametest/java/dev/sakhno/regnum/nations/block/KingdomObeliskTests.java](../src/gametest/java/dev/sakhno/regnum/nations/block/KingdomObeliskTests.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/CitizenshipTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/CitizenshipTests.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/CityRoleTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/CityRoleTests.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/KingdomAdminTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/KingdomAdminTests.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/KingdomFoundingTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/KingdomFoundingTests.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/RenameCompatibilityTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/RenameCompatibilityTests.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/RoyalCartographyTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/RoyalCartographyTests.java)
- [src/main/java/dev/sakhno/regnum/nations/block/CityObeliskBlock.java](../src/main/java/dev/sakhno/regnum/nations/block/CityObeliskBlock.java)
- [src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlock.java](../src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlock.java)
- [src/main/java/dev/sakhno/regnum/nations/block/RoyalBannerItem.java](../src/main/java/dev/sakhno/regnum/nations/block/RoyalBannerItem.java)
- [src/main/java/dev/sakhno/regnum/nations/chat/ChatUi.java](../src/main/java/dev/sakhno/regnum/nations/chat/ChatUi.java)
- [src/main/java/dev/sakhno/regnum/nations/chat/CommandHelp.java](../src/main/java/dev/sakhno/regnum/nations/chat/CommandHelp.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/CitizenManagement.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/CitizenManagement.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/CitizenshipCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/CitizenshipCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/DiplomacyService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/DiplomacyService.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomAdminCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomAdminCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomDeletionPrompt.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomDeletionPrompt.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomService.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/ObeliskRelocationCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/ObeliskRelocationCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryProtection.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryProtection.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryServer.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryServer.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/WarfareCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/WarfareCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/WarfareService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/WarfareService.java)
- [src/main/resources/assets/regnum-nations/lang/en_us.json](../src/main/resources/assets/regnum-nations/lang/en_us.json)
- [src/main/resources/assets/regnum-nations/lang/uk_ua.json](../src/main/resources/assets/regnum-nations/lang/uk_ua.json)
- [tools/verify_heraldry_restart.py](../tools/verify_heraldry_restart.py)
- [tools/verify_mod_jar.py](../tools/verify_mod_jar.py)
