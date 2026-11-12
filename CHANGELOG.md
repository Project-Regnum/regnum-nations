# Changelog

## 0.8.5 — Integration, migration and stability

- Integrate PR #15 territory protection (`a87f62f`), #14 kingdom administration
  (`42929b1`) and #13 diplomacy (`f6cc91f`) from main `f14cd92`.
- Reconcile independent schema-6 formats into tested schema 7. Load schemas 1–5,
  administration/diplomacy v6 and combined records; preserve identities, municipal
  and political data, extension fields and unreadable NBT. Backup restore remains
  mandatory for downgrade.
- Use one server capability policy: rulers/chancellors manage diplomacy; role or
  membership revocation invalidates their pending requests. Transferred rulers
  gain authority immediately; historical founders have no independent privilege.
- Invalidate open political/citizen views after authoritative changes, preserve
  both English/Ukrainian navigation paths and clear stale confirmations.
- Preserve territory hardening independently of diplomacy. Alliances grant no
  building, interaction or cross-city machine permissions.
- Fix the obsolete obelisk inventory-model assertion and check packaged animated
  world/static item resources without removing compatibility checks.
- Run rules/GameTests and opt-in native UI layout checks on Java 21 while retaining
  existing build dependencies and the JDK 25 Gradle runtime.
- Add 15 integration GameTests, handwritten branch-schema fixtures and native UI
  layout/snapshot checks. See the [audit](docs/integration-audit-v0.8.5.md) for exact
  executed results, restart evidence, limitations and the manual multiplayer list.

No economy, warfare, allied building rights or other gameplay features are added.
