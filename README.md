# Regnum Nations

The nations and territorial management module of **Project Regnum**, a multiplayer
Fabric mod for Minecraft 1.21.1. Previously named Project Reigns. The first functional slice is
the Kingdom Obelisk: a placeable capital with a persistent server UUID and explicit,
server-authoritative kingdom founding. Kingdom records and capital bindings persist
in world saved data. Basic chunk claims and city membership protection are implemented.
The v0.6.0 milestone adds independent city-states, personal settlement-owned land,
sovereign kingdoms with chosen political colors, and confirmed safe dissolution.
The v0.6.1 Royal Cartography milestone unifies the founding, management and map
visuals and adds clickable, cancellable administrative dissolution confirmations.
The v0.6.3 Living Atlas extends the approved Kingdom folio across City, Territories,
both founding forms and the political map: shared engraved parchment and ornaments,
a municipal seal, ceremonial charters and stronger political borders. The Kingdom
composition and deterministic heraldry remain. Server rules and networking are
unchanged. Rendered checks cover City, Found City and the independent Kingdom state;
the remaining screens await manual visual review (see the design system).
Use City and State Obelisks, the compact `/nations menu`, and M for the political map.
The v0.7.0 citizenship milestone adds owner-issued player invitations, persistent
UUID membership and citizen building access. v0.7.1 adds fixed owner/officer/citizen
roles and centralized permissions; officers may invite while owners retain claim
and membership management. Use `/nations citizen` for help.
v0.8.0 adds safe owner-confirmed obelisk relocation, active-anchor destruction
protection and explicit administrator recovery. See [relocation and settlement
integrity](docs/obelisk-relocation.md) for commands, restrictions and crash recovery.
v0.8.1 adds [in-game citizen management](docs/citizen-management-ui.md): open
City → Citizens for the private roster, invitations, roles, removal and leaving.
v0.8.2 adds [territory protection hardening](docs/territory-protection-hardening.md):
server-side guards for explosions, pistons, fluids, dispensers, fire, projectiles
and entity terrain changes, with secure settings in the existing properties file.
One personal city/domain and membership in at most one city are enforced.
v0.8.5 integrates [kingdom administration](docs/kingdom-administration.md) and
[kingdom diplomacy](docs/kingdom-diplomacy.md) with protection: persistent government
roles, leadership transfer, city membership and alliance/hostility management.
Rulers and chancellors manage diplomacy; alliances grant no city/building rights.
Schema 7 reads schemas 1–5 and both independently developed schema-6 formats.
Back up the complete world/config before upgrading; downgrade requires that backup.
See the [integration audit, compatibility limits, verification and multiplayer checklist](docs/integration-audit-v0.8.5.md).
Configurable role permissions, economy and warfare remain unimplemented.

See [UI design system, Living Atlas reference and screenshot steps](docs/ui-design-system.md).

Living Atlas now draws explored Minecraft terrain beneath translucent political
territories, with city/capital markers and a Layers control for combined,
political-only and terrain-only views. The terrain pipeline is adapted from MIT
Diorama; no mapping mod is required. See the [assessment, cache behavior and
verification instructions](docs/living-atlas-terrain.md) and
[third-party attribution](THIRD_PARTY_NOTICES.md).

See [cities, v0.7.1 roles, citizenship, migration, permissions and manual checks](docs/cities-and-sovereignty.md).

See [v0.5.1 UX, administration, safety, and manual checks](docs/ux-and-administration.md).

See [v0.5.0 kingdom management, networking, and manual gameplay checks](docs/kingdom-management.md).

See [Kingdom Obelisk development and manual tests](docs/kingdom-obelisk.md).
See [City and Kingdom models, in-game captures and Blockbench editing](docs/obelisk-models.md).
See [animated crystals, authoritative source export and validation](docs/animated-obelisk-crystals.md).
See [kingdom founding, persistence, policies, and manual tests](docs/kingdom-foundation.md).

See [territory claims, protections, and manual checks](docs/territory-claims.md).

## Upgrading from Project Reigns

Install matching client/server versions and replace the old JAR; do not load both.
World registry IDs, UUIDs, saved-data filenames, map configuration and the saved M
binding stay compatible. `/nations` is the preferred command; `/reigns` remains an
alias. Presentation assets and Java packages now use the Regnum Nations name.
See the [migration contract and upgrade steps](docs/rename-migration.md) before
upgrading a player world. Build output is `build/libs/regnum-nations-0.8.5.jar`.

## Development

Use JDK 25 to run Gradle, matching the existing CI configuration. The mod compiles
for Java 21 and requires Java 21 or newer in Minecraft. Install both JDK 25 and
JDK 21: the build uses JDK 25, while rule checks, GameTests and integration UI checks
select a Java 21 toolchain. CI installs both without changing mod dependencies.
Set `JAVA_HOME` to JDK 25 and import the project as a Gradle project in your IDE.

Use the included Gradle wrapper (`gradlew.bat` on Windows); no global Gradle
installation is needed. Versions are defined in `gradle.properties` and
`gradle/wrapper/gradle-wrapper.properties`. The project uses Fabric Loom, official
Mojang mappings, and separate common and client source sets.

```sh
./gradlew tasks --all  # List available tasks
./gradlew check       # Run configured verification (includes test)
./gradlew build       # Verify and build mod and sources JARs in build/libs/
./gradlew runGameTest # Run the isolated headless server tests
./gradlew runClient   # Launch the development client
./gradlew runAtlasTest # Opt-in real-client terrain checks (requires a display)
./gradlew runIntegrationLayoutTest # Native integration widget checks, both locales (requires a display)
./gradlew runServer   # Launch the development server
./gradlew runDatagen  # Run data generation (no providers registered yet)
python3 tools/verify_mod_jar.py # Check the built JAR and compatibility resources
```

Work on a feature branch, use Conventional Commits, and run `./gradlew build`
before committing code changes. Pull requests run the same build on GitHub Actions;
merging requires maintainer approval. See [AGENTS.md](AGENTS.md) for engineering rules.
The wrapper verifies its distribution checksum and Loom is pinned to an exact version.

The first Gradle run needs internet access to download build and game dependencies.
The development server requires accepting Minecraft's EULA in `run/eula.txt`.
`check` and `build` include the server GameTests in `src/gametest`; their world is
isolated under `build/run/gameTest/` and reset before each run, so repeated builds
do not inherit claims from a previous run. Development worlds under `run/` are untouched. `check` also runs `testKingdomNames`, a
dependency-free pure Java rule test in `src/rulesTest`. There are no JUnit tests, so
Gradle's separate `test` task reports `NO-SOURCE`. Test code is excluded from the
shipped mod JAR.

## Source layout

Packages below are relative to `dev.sakhno.regnum.nations`.

| Location | Purpose |
| --- | --- |
| `src/main/java/.../nations/RegnumNations.java` | Common mod entrypoint and identifiers |
| `src/main/java/.../nations/block/` | Obelisk block, block entity, and registration |
| `src/main/java/.../nations/kingdom/` | Kingdom model, saved registry, founding rules, and commands |
| `src/main/java/.../nations/networking/` | Bounded map/management payloads and common networking |
| `src/client/java/.../nations/client/` | Client and data generation entrypoints |
| `src/client/java/.../nations/client/ui/` | Founding, kingdom overview, and political map screens |
| `src/main/resources/` | Mod metadata and common assets/data |
| `src/client/resources/` | Client-only resources |
| `src/gametest/` | Server integration tests and test-only mod metadata |
| `src/rulesTest/` | Pure Java name-rule tests without third-party test dependencies |

Keep client UI and other client-only Minecraft references in `src/client`.
Existing template mixins remain in their common and client `mixin` packages.
Build output, IDE files, and development runtime data are excluded by `.gitignore`.

## License

CC0-1.0; see [LICENSE](LICENSE).
