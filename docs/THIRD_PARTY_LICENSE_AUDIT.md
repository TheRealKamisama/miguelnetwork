# Preliminary wstunnel dependency license audit

Date: 2026-09-05

## Scope and method

- Upstream: `erebe/wstunnel` v10.7.1
- Resolved tag commit: `d0f1e1ac3f340653a856e7aad66b886c59bf6e8e`
- Input: the upstream `Cargo.toml` files and lock-file version 4 from that commit
- Scope: a conservative dependency closure for the default `aws-lc-rs` CLI and the union of Windows/Linux target
  dependencies
- Root development-only dependencies were excluded. Transitive optional or other-platform packages may still be
  over-represented because this audit did not reproduce the upstream release build graph.

The closure contained 384 registry packages. crates.io returned a declared license expression for every package; no
metadata entry was missing.

## Result

The declared expressions are predominantly MIT and/or Apache-2.0. Other permissive terms in the conservative closure
include BSD-2-Clause, BSD-3-Clause, ISC, Zlib, Unicode-3.0, Unlicense, CC0-1.0, MIT-0, BSL-1.0,
CDLA-Permissive-2.0, the LLVM exception, and OpenSSL terms.

No dependency was found whose only declared option is GPL or LGPL. Two versions of `r-efi` declare
`MIT OR Apache-2.0 OR LGPL-2.1-or-later`, so permissive alternatives are available. This result does not force
MiguelNetwork to adopt wstunnel's BSD-3-Clause license or any crate's license as its project-wide license.

Packages that require attention beyond the dominant MIT/Apache family include:

- `aws-lc-rs`, `aws-lc-sys`, `aws-lc-fips-sys`, and `ring` (ISC/OpenSSL/BSD and combined notices);
- ICU4X data/provider crates and `unicode-ident` (Unicode-3.0);
- `webpki-root-certs` (CDLA-Permissive-2.0);
- `notify` and `dunce` (CC0-1.0 alternatives);
- Dalek, `bindgen`, `subtle`, and `zerocopy` crates (BSD variants);
- `foldhash` and TinyVec-related crates (Zlib alternatives).

## Release gate

This is a preliminary compatibility audit, not a finished binary attribution bundle. Before a public release:

1. reproduce or obtain the exact feature and target graph used for each bundled official binary;
2. generate a machine-readable SBOM containing package names, versions and checksums;
3. collect the applicable full license and notice texts, including combined native crypto notices;
4. package those materials in the Mod JAR and release archive;
5. repeat the audit whenever the pinned wstunnel version or binary changes.

Until that gate is complete, `THIRD_PARTY_NOTICES.md` correctly preserves wstunnel's own BSD-3-Clause notice but must
not be described as a complete transitive dependency attribution bundle.
