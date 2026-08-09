# Security Center Display-Safety Gate Design

**Date:** 2026-08-09

## Purpose

Restore the module's core safety guarantee: spoofed storage values must not influence real cleanup, optimization, notification, or other business decisions.

This first fix intentionally suspends all storage spoofing inside Xiaomi Security Center. Android Settings support remains enabled. HyperOS support can be restored later only through UI-terminal interception points that do not mutate shared business data.

## Confirmed problem

`StorageSpoofModule` currently hooks every `StorageStatsManager.queryStatsForPackage()` overload in both supported hosts. Within `com.miui.securitycenter`, those APIs are shared by UI and background business logic.

Confirmed non-display consumers include:

- `E9.AbstractC1943d`, which checks `com.miui.dmregservice` app and data bytes against a 20 MiB threshold and can call `clearApplicationUserData()`;
- `P5.d` and `I5.b`, which use app and data bytes to select FBO optimization candidates;
- storage models whose spoofed values affect list filtering, sorting, cleanup-menu state, and post-clean accounting.

Caller blacklists cannot establish a durable display-only guarantee because HyperOS class names and call paths can change between ROM versions. Some nominally UI-facing storage models also feed cleanup decisions.

The separate HyperOS available-space hook is synchronously scoped under `StorageFragment.A0()`, but it depends on package baselines collected by the unsafe process-wide package hook. It therefore cannot remain enabled after package interception is disabled.

## Scope

### Included

- Keep package storage spoofing enabled in `com.android.settings`.
- Disable all storage spoofing in `com.miui.securitycenter`.
- Disable HyperOS package-stat and top-summary hooks together.
- Add a testable host policy that defines which hook families each package may install.
- Add explicit logs explaining when Security Center is skipped for display safety.
- Add regression tests for host policy behavior.

### Excluded

- Restoring HyperOS UI spoofing through new UI-specific hooks.
- Fixing nested overload processing, hot reload migration, summary staleness, multi-user semantics, preference concurrency, slider precision, Activity state, or background image handling.
- Removing the dormant HyperOS implementation. It remains available for a later isolated redesign but must be unreachable through the normal installation path.
- Changing persisted profiles or the configuration UI.

## Design

### Host hook policy

Introduce a small immutable policy abstraction with two independent capabilities:

- `packageStatsSpoofing`
- `hyperOsSummarySpoofing`

The policy mapping is:

| Package | Package stats | HyperOS summary |
|---|---:|---:|
| `com.android.settings` | enabled | disabled |
| `com.miui.securitycenter` | disabled | disabled |
| any other package | disabled | disabled |

The mapping uses `PackageReadyParam.getPackageName()`, not the process name. A process suffix such as `:remote` must not independently enable a capability.

The two flags are intentionally separate even though only one combination is enabled now. This makes the dependency explicit and allows a future HyperOS UI implementation to use a different hook family without silently re-enabling process-wide package mutation.

### Installation flow

`onPackageReady()` resolves the host policy before installing hooks.

```text
unsupported package
  -> return without installation

Android Settings
  -> remember host loader
  -> install package-stat spoofing
  -> do not install HyperOS summary hooks

Xiaomi Security Center
  -> log that storage spoofing is disabled for display safety
  -> install no storage hooks
```

`installHooks()` must accept or consult the resolved policy rather than infer behavior from a mutable `hyperOsHost` boolean. This prevents future lifecycle paths, including hot reload, from accidentally enabling a hook family merely because the host is Security Center.

### Security Center behavior

Every Security Center call receives unmodified host/framework results:

```text
UI or background component
  -> StorageStatsManager.queryStatsForPackage()
  -> no module interceptor
  -> real StorageStats
```

The module also does not hook `StorageFragment.A0()` or `AppSystemDataManager.f()`. As a result it does not:

- collect Security Center `originalSizes`;
- adjust available-space summaries;
- post artificial summary refreshes;
- affect dmregservice cleanup thresholds;
- affect FBO candidate thresholds;
- affect cleanup menu branching or storage-model accounting.

### Android Settings behavior

Android Settings retains the existing package storage interception and profile application behavior. This fix does not modify the shape or values of a configured profile.

The broader question of whether any Settings-internal non-display path consumes the same API remains a separate investigation. This design only removes the confirmed Security Center violation and does not claim universal caller safety beyond the investigated host.

### Logging and failure behavior

On Security Center package readiness, emit one informational log stating that spoofing is skipped because only display-safe hosts are enabled. Do not report this as an installation error.

If Settings hook installation fails, preserve existing protective exception behavior and error logging.

No fallback may install Security Center package hooks. A missing or unknown policy must fail closed by enabling neither capability.

## Testing

### Unit tests

Extract policy resolution into code that can be tested without loading Android framework classes. Cover:

1. `com.android.settings` enables package-stat spoofing.
2. `com.android.settings` disables HyperOS summary spoofing.
3. `com.miui.securitycenter` disables package-stat spoofing.
4. `com.miui.securitycenter` disables HyperOS summary spoofing.
5. An unknown package disables both capabilities.
6. Policy lookup uses package identity; process suffixes are not treated as separate supported packages.
7. No policy permits HyperOS summary spoofing while package-stat baseline collection is absent in this release.

### Build verification

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git diff --check
```

### Device verification

On an Android 16 HyperOS device with LSPosed:

1. Restart or reload the module and both target hosts.
2. Open Android Settings application storage and confirm configured values are spoofed.
3. Confirm logs show package-stat hooks installed in Android Settings.
4. Open Security Center storage overview, app management, app storage details, and game uninstall pages.
5. Confirm all Security Center values remain real.
6. Confirm Security Center logs contain the explicit display-safety skip message.
7. Confirm no `Spoofed <package>` message originates from the Security Center process.
8. Exercise available relevant background jobs where practical and confirm no Security Center package stats are intercepted.

The autonomous cleanup job must not be deliberately forced to delete data merely for testing. Verification should establish the absence of the module interceptor from that process rather than trigger destructive behavior.

## Acceptance criteria

- Android Settings continues to install package-stat spoofing hooks.
- Xiaomi Security Center installs neither package-stat nor summary hooks.
- No runtime path can enable Security Center hooks through a permissive default or fallback.
- Unit tests enforce the host capability mapping.
- Unit tests and debug assembly pass.
- Device logs clearly distinguish Settings installation from Security Center safety skipping.
- No configuration migration is required.

## Future HyperOS restoration boundary

HyperOS spoofing must be redesigned as a separate project. It may be restored only at UI-terminal boundaries such as dedicated display models or formatted text, after proving that the selected values do not feed cleanup eligibility, optimization candidates, notifications, analytics, or post-action accounting. Process-wide mutation of shared `StorageStats` is not an acceptable restoration strategy.
