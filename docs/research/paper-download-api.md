# PaperMC downloads API evidence

Access date: 2026-11-20 (UTC). Source: [PaperMC Downloads Service](https://docs.papermc.io/misc/downloads-service/)
and the linked [Fill API documentation](https://fill.papermc.io/swagger-ui/index.html#/).

## Why this source

The compatibility harness must obtain official server software without scraping a web page.
PaperMC documents Fill as its downloads service for automated access and warns that unstable
builds and auto-updaters are not recommended. The harness therefore pins a `STABLE` build,
requires an HTTPS download URL, and verifies the checksum supplied by the API before use.

## Request requirements

PaperMC requires a non-generic `User-Agent` that identifies the software and includes a contact
URL or email address. DeployMC uses:

```text
DeployMC-compat-harness/0.1.0 (https://github.com/woxlo1/DeployMC)
```

## REST endpoints used

The documented v3 REST shape used by the harness is:

```text
GET https://fill.papermc.io/v3/projects/paper
GET https://fill.papermc.io/v3/projects/paper/versions/{version}/builds
```

The versions response groups versions; the builds response is a list. The harness selects the
entry whose build `id` equals the pinned `build`, requires `channel == "STABLE"`, and reads
`downloads["server:default"]`. That object supplies `url`, `size`, `name`, and
`checksums.sha256`.

## Observed response for the pinned entry

On 2026-11-20 the builds response for Paper 26.3 contained build 256 with these relevant fields:

```json
{
  "id": 256,
  "time": "2026-11-19T04:49:17Z",
  "channel": "STABLE",
  "downloads": {
    "server:default": {
      "name": "paper-26.3-256.jar",
      "checksums": {
        "sha256": "c86baf83b1fb0eb0d572f39484c242210896c6adf9cc9342d5c16c4de70c9bdc"
      },
      "size": 55305120,
      "url": "https://fill-data.papermc.io/v1/objects/c86baf83b1fb0eb0d572f39484c242210896c6adf9cc9342d5c16c4de70c9bdc/paper-26.3-256.jar"
    }
  }
}
```

The checksum in this document is evidence for the pinned artifact. The harness does not trust a
checksum copied into source: it reads the checksum from the API response on every run, rejects a
non-64-hex value, and compares it with the downloaded bytes and with the cache file name.

## Runtime Java requirement

Access date: 2026-11-20 (UTC). Source: [Installing or updating Java](https://docs.papermc.io/misc/java-install/),
last updated 2026-07-16 according to the page. PaperMC's guide installs Java 25, and its
verification section expects output beginning with `openjdk 25`. A local Paper 26.3 launch with
Java 17 failed before plugin loading with:

```text
Minecraft 26.1 and newer requires running the server with Java 25 or above.
```

For the local Windows smoke run, the harness used Temurin 25.0.5+8 from the official
[Adoptium API](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse):

```text
release_name: jdk-25.0.5+8
package: OpenJDK25U-jdk_x64_windows_hotspot_25.0.5_8.zip
sha256: 636bdc13dc649ddc7d5921c5c7d23cca6e44fd19d662b45cc9c02135a6c96c42
```

The downloaded ZIP hash matched the API checksum, and `java -version` reported
`openjdk version "25.0.5" 2026-10-20 LTS`. CI uses Temurin 25 only for the Paper process and JDK 21
for Gradle; Java modules still compile with `--release 8`.

## Caching and failure behavior

- The cache path is the verified SHA-256 digest plus `.jar`.
- A cached file is re-hashed before use; a mismatch fails the run.
- A partial download is removed on failure.
- A size mismatch or checksum mismatch fails the run before the server is started.

## Limitations

- A verified download proves artifact integrity from the official API, not that Paper or the
  plugin supports a production deployment. Support claims still require the matrix rules in
  [support matrix](../compatibility/support-matrix.md).
- The observed response is time-sensitive. A future API change must be re-verified against the
  official documentation before changing the harness.
- The harness pins an exact Minecraft version and build. It does not silently follow "latest",
  which would make the matrix nondeterministic.
