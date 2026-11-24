# Release 1.0.0 verification — 2026-10-08

Build environment: Java 25 and Paper API `26.2.build.129-stable`. The same JAR was verified on:

- Paper `26.2-132-ver/26.2@19ebc4a`;
- Folia `26.2-7-ver/26.2.x@14b7fee`.

All 8 JUnit tests passed. The following 8 integration checks passed on each isolated server:

1. Enable the release JAR.
2. Perform a real manual ground launch: consume one rocket and receive a client `set_cooldown` packet for the `minecraft:firework_rocket` group with a duration of 40 ticks.
3. Reject launches after hand and hotbar-slot changes during the cooldown without consuming blocked rockets.
4. Perform a real elytra boost: accept one rocket and preserve subsequent rockets until the cooldown expires.
5. Allow another launch after the cooldown expires.
6. Respect a boost cancelled by another plugin without consuming a rocket or starting a new cooldown.
7. Confirm that an explicitly granted `nordfireworks.bypass` permission prevents a new cooldown.
8. Reload successfully without handler or region-ownership errors.

The test installations listened only on loopback. They used a new synthetic world, a test client, and the companion plugin from `test-support`; no production worlds, authentication, configuration, or servers were accessed. Both test installations shut down normally. The companion plugin is not present in the release JAR.

The verification did not include a 1,000-player load test, human visual inspection, or Minecraft versions other than 26.2. Receipt of the vanilla cooldown packet was verified programmatically.
