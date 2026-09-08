# Technical validation

This guide describes the 0.2.0 validation workflow. Contributor setup and test
coverage are summarized in [`DEVELOPMENT.md`](DEVELOPMENT.md); installation and
log interpretation are in [`USER_GUIDE.md`](USER_GUIDE.md).

The primary manual acceptance path is documented in `CONFIGURATION.md`. For automated/local runs, the most useful JVM
overrides are:

```text
-Dmiguelnetwork.server.enabled=true
-Dmiguelnetwork.server.mode=STANDALONE
-Dmiguelnetwork.bindHost=127.0.0.1
-Dmiguelnetwork.public.port=35548
-Dmiguelnetwork.discovery.enabled=true
-Dmiguelnetwork.discovery.signResponses=false
```

For an external-proxy run, change the mode and configure the private listeners:

```text
-Dmiguelnetwork.server.mode=EXTERNAL_PROXY
-Dmiguelnetwork.bindHost=127.0.0.1
-Dmiguelnetwork.public.port=35549
-Dmiguelnetwork.discovery.bindHost=127.0.0.1
-Dmiguelnetwork.discovery.port=25568
-Dmiguelnetwork.discovery.advertisedTransport=WSS
```

The backend target is intentionally not supplied as an override in current deployments. MiguelNetwork reads
`server-ip`, uses the running server's actual port, and detects supported ZstdNet itself. Confirm the result in
`config/miguelnetwork/generated/detected-server.toml`.

Optional client security checks can be exercised with:

```text
-Dmiguelnetwork.discovery.verifySignatures=true
-Dmiguelnetwork.client.enforceWssDowngradeProtection=true
-Dmiguelnetwork.tls.verify=true
```

The first option requires `-Dmiguelnetwork.discovery.signResponses=true` on the server. Certificate verification should
only be disabled for an isolated test using `-Dmiguelnetwork.tls.verify=false`.

Run the full unit/integration suite with the checked-in wrapper:

```text
.\gradlew.bat clean test jar
```

On Linux/macOS use `./gradlew clean test jar`. Tests cover command construction,
signed and unsigned Discovery codecs, target address resolution, restriction
generation, process lifecycle utilities, ZstdNet version gating, and the built-in
gateway's Discovery/Upgrade split over real loopback sockets.
