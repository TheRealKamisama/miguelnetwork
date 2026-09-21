# Third-party notices

MiguelNetwork itself is licensed under the Apache License 2.0. Third-party components retain their own licenses.

NightConfig core and TOML 3.6.4 are included under GNU LGPL v3.0.
Upstream: https://github.com/TheElectronWill/Night-Config.
Maven coordinates: com.electronwill.night-config:core:3.6.4 and com.electronwill.night-config:toml:3.6.4.
Their bytecode is relocated from com.electronwill.nightconfig to io.github.therealkamisama.miguelnetwork.internal.nightconfig.
See META-INF/licenses/NightConfig-LGPL-3.0.txt and NightConfig-GPL-3.0.txt for the complete license terms.
Corresponding upstream sources with copyright headers are in META-INF/sources/nightconfig/.
The public MiguelNetwork source/build at https://github.com/TheRealKamisama/miguelnetwork provides the relocation and
relinking recipe: replace the bundled dependencies and run the checked-in wrapper build with Java 25.
Reverse engineering for debugging modifications to this library is permitted; no extra LGPL restriction is imposed.

MiguelNetwork includes unmodified executable builds from erebe/wstunnel v10.7.1.

wstunnel is distributed under the BSD 3-Clause License. See
`META-INF/licenses/wstunnel-BSD-3-Clause.txt` for the complete license text.
