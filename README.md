# Project Reigns

Multiplayer-focused Fabric mod for Minecraft 1.21.1. The first functional slice is
the Kingdom Obelisk: a placeable capital with a persistent server UUID and explicit,
server-authoritative kingdom founding. Kingdom records and capital bindings persist
in world saved data. Basic chunk claims and founder-only building protection are implemented.
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
Use City and State Obelisks, the compact `/reigns menu`, and M for the political map.
One personal city/domain is enforced. Minimal city affiliation is implemented;
full roles, diplomacy, economy and warfare are not.

See [UI design system, Living Atlas reference and screenshot steps](docs/ui-design-system.md).

See [v0.6.0 cities, migration, colors, safe deletion and manual checks](docs/cities-and-sovereignty.md).

See [v0.5.1 UX, administration, safety, and manual checks](docs/ux-and-administration.md).

See [v0.5.0 kingdom management, networking, and manual gameplay checks](docs/kingdom-management.md).

See [Kingdom Obelisk development and manual tests](docs/kingdom-obelisk.md).
See [kingdom founding, persistence, policies, and manual tests](docs/kingdom-foundation.md).

See [territory claims, protections, and manual checks](docs/territory-claims.md).

## Development

Use JDK 25 to run Gradle, matching the existing CI configuration. The mod compiles
for Java 21 and requires Java 21 or newer in Minecraft. Set `JAVA_HOME` to your JDK
and import the project as a Gradle project in your IDE.

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
./gradlew runServer   # Launch the development server
./gradlew runDatagen  # Run data generation (no providers registered yet)
```

The first Gradle run needs internet access to download build and game dependencies.
The development server requires accepting Minecraft's EULA in `run/eula.txt`.
`check` and `build` include the server GameTests in `src/gametest`; their world is
isolated under `build/run/gameTest/`. `check` also runs `testKingdomNames`, a
dependency-free pure Java rule test in `src/rulesTest`. There are no JUnit tests, so
Gradle's separate `test` task reports `NO-SOURCE`. Test code is excluded from the
shipped mod JAR.

## Source layout

Packages below are relative to `dev.sakhno.reigns`.

| Location | Purpose |
| --- | --- |
| `src/main/java/.../reigns/ProjectReigns.java` | Common mod entrypoint and identifiers |
| `src/main/java/.../reigns/block/` | Obelisk block, block entity, and registration |
| `src/main/java/.../reigns/kingdom/` | Kingdom model, saved registry, founding rules, and commands |
| `src/main/java/.../reigns/networking/` | Bounded map/management payloads and common networking |
| `src/client/java/.../reigns/client/` | Client and data generation entrypoints |
| `src/client/java/.../reigns/client/ui/` | Founding, kingdom overview, and political map screens |
| `src/main/resources/` | Mod metadata and common assets/data |
| `src/client/resources/` | Client-only resources |
| `src/gametest/` | Server integration tests and test-only mod metadata |
| `src/rulesTest/` | Pure Java name-rule tests without third-party test dependencies |

Keep client UI and other client-only Minecraft references in `src/client`.
Existing template mixins remain in their common and client `mixin` packages.
Build output, IDE files, and development runtime data are excluded by `.gitignore`.

## License

CC0-1.0; see [LICENSE](LICENSE).
