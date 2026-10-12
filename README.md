# Optics

Optics is a pure Java 25 library for portal geometry and projection: frames and rigid, similarity and affine transforms, aperture shape masks with a text grammar, a sampled animation model, a portal definition and registry API, and the scan, plate, occlusion, entity and view-stream machinery Wormholes uses to show one place through another. It has no Minecraft, Bukkit or mod-loader types and depends only on fastutil. Wormholes includes it as the `optics/` submodule and ships it inside every distribution.

Build with Java 25 from the repository root: `./gradlew build` compiles and tests, `./gradlew test` runs the tests alone, and `./gradlew publishToMavenLocal` installs `art.arcane:optics:<opticsVersion>` (see `gradle.properties`) in the local Maven repository.

Documentation lives on the Volmit wiki at `/optics`. The [Optics manual](https://github.com/VolmitSoftware/docs/blob/master/optics.md) covers transforms, shapes, animation, portals, crossing, projection, world views, entities, fidelity, ClientView and host interfaces. The [demonstrations](https://github.com/VolmitSoftware/docs/blob/master/optics/12-demonstrations.md) describe the standalone Paper demo plugin and its first-person renderer comparisons.

See [LICENSE](LICENSE).
