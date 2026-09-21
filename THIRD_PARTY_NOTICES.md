# Third-party notices

MiguelNetwork itself is licensed under the Apache License 2.0. The components below retain their own licenses.

## NightConfig 3.6.4

This Cleanroom distribution includes NightConfig core and TOML, licensed under GNU LGPL v3.0.
Upstream: https://github.com/TheElectronWill/Night-Config; Maven coordinates:
`com.electronwill.night-config:core:3.6.4` and `com.electronwill.night-config:toml:3.6.4`.

The library bytecode is relocated from `com.electronwill.nightconfig` to
`io.github.therealkamisama.miguelnetwork.internal.nightconfig` to avoid mod-classpath conflicts.
The complete LGPL/GPL texts are included under `META-INF/licenses/NightConfig-*.txt`.
The corresponding upstream source JARs are included under `META-INF/sources/nightconfig/`, retaining their copyright headers.
The public MiguelNetwork source and Gradle build provide the relocation/relinking recipe; replace the bundled dependency
with a modified build and run `gradlew build` to recombine it. Reverse engineering for debugging modifications to this
library is permitted. No extra restriction is imposed on the rights granted by the LGPL.

## wstunnel

MiguelNetwork includes unmodified executable builds from
[erebe/wstunnel](https://github.com/erebe/wstunnel), version 10.7.1.

Copyright (c) 2016-2024, Erèbe - Romain Gerard.

wstunnel is distributed under the BSD 3-Clause License. The complete license text is included at
`META-INF/licenses/wstunnel-BSD-3-Clause.txt` in the MiguelNetwork JAR.

A preliminary transitive dependency review is recorded in `docs/THIRD_PARTY_LICENSE_AUDIT.md`. A target-accurate SBOM
and complete transitive attribution bundle remain a release requirement.
