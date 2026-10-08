# Optics

Optics is a pure Java 25 library for portal geometry and projection: frames and rigid, similarity and affine transforms, aperture shape masks with a text grammar, a sampled animation model, a portal definition and registry API, and the scan, plate, occlusion, entity and view-stream machinery Wormholes uses to show one place through another. It has no Minecraft, Bukkit or mod-loader types and depends only on fastutil. Wormholes includes it as the `optics/` submodule and ships it inside every distribution.

Build with Java 25 from the repository root: `./gradlew build` compiles and tests, `./gradlew test` runs the tests alone, and `./gradlew publishToMavenLocal` installs `art.arcane:optics:<opticsVersion>` (see `gradle.properties`) in the local Maven repository.

Documentation lives on the Volmit wiki at `/optics`: the [Optics landing page](https://github.com/VolmitSoftware/docs/blob/master/optics.md) with the [overview](https://github.com/VolmitSoftware/docs/blob/master/optics/00-overview.md), [frames and transforms](https://github.com/VolmitSoftware/docs/blob/master/optics/01-frames-and-transforms.md), [shapes](https://github.com/VolmitSoftware/docs/blob/master/optics/02-shapes.md), [animation](https://github.com/VolmitSoftware/docs/blob/master/optics/03-animation.md) and [portals](https://github.com/VolmitSoftware/docs/blob/master/optics/04-portals.md).

See [LICENSE](LICENSE).
