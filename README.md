# Project Reigns

Fabric mod development scaffold for Minecraft 1.21.1. Gameplay is not implemented yet.

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
./gradlew runClient   # Launch the development client
./gradlew runServer   # Launch the development server
./gradlew runDatagen  # Run data generation (no providers registered yet)
```

The first Gradle run needs internet access to download build and game dependencies.
The development server requires accepting Minecraft's EULA in `run/eula.txt`.
There are currently no automated tests; Gradle's `test` task reports `NO-SOURCE`.

## Source layout

Packages below are relative to `dev.sakhno.reigns`. Reserved packages contain only
`package-info.java` documentation until features are added.

| Location | Purpose |
| --- | --- |
| `src/main/java/.../reigns/ProjectReigns.java` | Common mod entrypoint and identifiers |
| `src/main/java/.../reigns/block/` | Future blocks and block registration |
| `src/main/java/.../reigns/kingdom/` | Future kingdom domain logic |
| `src/main/java/.../reigns/networking/` | Future payloads and common/server networking |
| `src/client/java/.../reigns/client/` | Client and data generation entrypoints |
| `src/client/java/.../reigns/client/ui/` | Future client screens and UI |
| `src/main/resources/` | Mod metadata and common assets/data |
| `src/client/resources/` | Client-only resources |

Keep client UI and other client-only Minecraft references in `src/client`.
Existing template mixins remain in their common and client `mixin` packages.
Build output, IDE files, and development runtime data are excluded by `.gitignore`.

## License

CC0-1.0; see [LICENSE](LICENSE).
