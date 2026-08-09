# Release process

Official builds are signed with one persistent Android signing key. Keep the
keystore and passwords outside Git; losing the key prevents future versions
from updating existing installations.

## One-time local setup

Generate a PKCS12 keystore outside the repository:

```bash
keytool -genkeypair \
  -keystore "$HOME/.android/storage-spoof-release.p12" \
  -storetype PKCS12 \
  -alias storage-spoof \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=yuholt, O=StorageSpoof"
```

Set these environment variables before running a release build:

```text
ANDROID_SIGNING_STORE_FILE
ANDROID_SIGNING_STORE_PASSWORD
ANDROID_SIGNING_KEY_ALIAS
ANDROID_SIGNING_KEY_PASSWORD
```

Then run:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

If the four variables are absent, Gradle can still produce an unsigned Release
APK for source verification, but it is not an official distributable build.

## GitHub Actions secrets

Configure these repository secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_SIGNING_STORE_PASSWORD
ANDROID_SIGNING_KEY_ALIAS
ANDROID_SIGNING_KEY_PASSWORD
```

`ANDROID_KEYSTORE_BASE64` is the Base64 encoding of the same local keystore.
Base64 is only an encoding; secrecy is provided by GitHub Actions secrets.

Pushing a tag matching `v*` runs `.github/workflows/release.yml`. The workflow
checks that the tag agrees with `versionName`, builds and verifies the signed
APK, and publishes:

- `StorageSpoof-<version>-universal.apk`
- `StorageSpoof-<version>-arm64-v8a.apk`
- `StorageSpoof-<version>-checksums.txt`

StorageSpoof currently contains no native `.so` libraries. Consequently, the
`arm64-v8a` file is a clearly named compatibility copy of the Universal APK,
not a smaller binary. It is usable on arm64 devices but is not artificially
restricted to that ABI. This avoids pretending that a Java-only APK has a real
ABI-specific payload.

The official signing certificate SHA-256 fingerprint is:

```text
5F:73:C4:3C:1A:01:A1:43:D7:95:39:8C:6A:61:B4:A2:95:B1:D6:86:B1:E6:6B:AB:E1:06:76:D7:4D:B4:62:05
```