
# Project Reigns — Agent Engineering Guide

## Project identity
Project Reigns is a multiplayer-focused Minecraft mod for kingdoms, settlements, territorial control, diplomacy, economics, and warfare.

Technology:
- Minecraft 1.21.1
- Fabric Loader and Fabric API
- Java
- Gradle and Fabric Loom
- Dedicated-server compatibility is mandatory.

The project is in early development. Do not assume that proposed gameplay mechanics are approved.

## Core engineering principles
1. The server is authoritative for all gameplay state.
2. Clients render state and submit requests; they do not decide gameplay outcomes.
3. Common code must never depend on client-only Minecraft classes.
4. Prefer small, cohesive modules over premature abstraction.
5. Preserve existing project conventions and dependency versions.
6. Do not introduce third-party libraries without a demonstrated need.
7. Design persistence explicitly. Never treat client state as persistent truth.
8. Avoid unnecessary ticking, global scans, and excessive network traffic.

## Architecture
Keep responsibilities separated:
- `kingdom`: domain models, rules, and services.
- `block`: block and block entity behavior.
- `network`: validated client-server communication.
- `client`: rendering, screens, and client-only interactions.

These are logical boundaries, not a requirement to create empty classes or interfaces.

## Fabric and Minecraft APIs
- Inspect the project's actual mappings and dependencies before coding.
- Use APIs appropriate for Minecraft 1.21.1.
- Do not copy code from incompatible Minecraft versions.
- Avoid deprecated APIs when supported alternatives exist.
- Never invent method signatures or imports.
- Consult local source attachments or verified documentation when uncertain.

## Security and multiplayer
- Validate client-originated requests on the server.
- Treat all client-supplied identifiers and coordinates as untrusted.
- Check permissions, distance, dimension, and current world state where relevant.
- Avoid exposing sensitive server state unnecessarily.
- Never use client-only behavior as a security boundary.

## Quality and testing
- Prefer incremental, compilable changes.
- Add automated tests for domain rules when practical.
- Test persistence across save/reload boundaries.
- Consider dedicated-server startup for common code changes.
- Do not claim manual gameplay tests were performed unless actually executed.
- Run relevant Gradle checks and report failures precisely.

## Workflow
Before implementation:
1. Inspect relevant files and existing patterns.
2. Identify affected common/client boundaries.
3. Present a concise implementation plan for nontrivial work.

During implementation:
1. Make the smallest coherent change.
2. Preserve unrelated code and configuration.
3. Avoid speculative future features.

After implementation:
1. Run available verification tasks.
2. Summarize changed files and behavior.
3. Identify tests not performed and remaining risks.

- Use feature branches and descriptive Conventional Commit messages.
- Preserve existing work; never force-push or rewrite history.
- Run `./gradlew build --no-daemon --console=plain` before committing code or build changes.
- `build` includes the kingdom-name rule checks and headless server GameTests;
  `test` alone has no test sources. Never claim checks passed unless executed.
- `runGameTest` resets only `build/run/gameTest/world`; keep player worlds under `run/` untouched.
- Keep generated build output, secrets, IDE files, `.agents/` and `.sober/` out of Git.
- Do not push, publish, or modify dependencies unless the user authorizes the work.
- Open pull requests with validation evidence and migration risks. Never merge without approval.
