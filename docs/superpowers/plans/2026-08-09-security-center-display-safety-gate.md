# Security Center Display-Safety Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep per-app storage spoofing active in Android Settings while making Xiaomi Security Center fail closed with no package-stat or summary hooks.

**Architecture:** Add a small pure-Java `HostHookPolicy` record that maps exact package identities to independent package-stat and HyperOS-summary capabilities. Route every hook-installation entry, including hot reload, through that policy; Security Center and unknown lifecycle state install nothing, while Android Settings retains its existing package-stat interceptor.

**Tech Stack:** Java 17, Android 16/SDK 36 target with compileSdk 37, libxposed API 102, JUnit 4.13.2, Gradle Android plugin.

## Global Constraints

- Keep package storage spoofing enabled in `com.android.settings`.
- Disable all storage spoofing in `com.miui.securitycenter`, including package-stat and top-summary hooks.
- Resolve policy from `PackageReadyParam.getPackageName()`, not process names or suffix stripping.
- Unknown packages and missing lifecycle policy must fail closed with both capabilities disabled.
- Preserve existing persisted profiles and configuration UI without migration.
- Leave the dormant HyperOS hook implementation in place for a later UI-terminal redesign, but make it unreachable under the current policy mapping.
- Do not fix nested overload handling, complete hot-reload state migration, summary staleness, multi-user semantics, preference concurrency, slider precision, Activity state, or image handling in this change.
- Never force the autonomous Security Center cleanup job merely to verify the fix.

---

## File Structure

- Create `app/src/main/java/com/yuholt/storagespoof/hook/HostHookPolicy.java` — pure-Java immutable capability mapping for exact host package identities.
- Create `app/src/test/java/com/yuholt/storagespoof/hook/HostHookPolicyTest.java` — regression coverage for Settings, Security Center, unknown packages, and process-suffixed strings.
- Modify `app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java` — resolve and retain the policy at package readiness, gate each hook family, fail closed during lifecycle states with no known policy, and log the Security Center safety skip.

### Task 1: Define and Test the Fail-Closed Host Policy

**Files:**
- Create: `app/src/main/java/com/yuholt/storagespoof/hook/HostHookPolicy.java`
- Create: `app/src/test/java/com/yuholt/storagespoof/hook/HostHookPolicyTest.java`

**Interfaces:**
- Produces: `HostHookPolicy.forPackage(String): HostHookPolicy`
- Produces: `HostHookPolicy.isSupportedPackage(String): boolean`
- Produces: `HostHookPolicy.packageStatsSpoofing(): boolean`
- Produces: `HostHookPolicy.hyperOsSummarySpoofing(): boolean`
- Produces: `HostHookPolicy.hasEnabledHooks(): boolean`
- Consumes: exact package names only; no Android framework types.

- [ ] **Step 1: Write the failing policy tests**

Create `app/src/test/java/com/yuholt/storagespoof/hook/HostHookPolicyTest.java`:

```java
package com.yuholt.storagespoof.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostHookPolicyTest {
    @Test
    public void settingsEnablesOnlyPackageStatsSpoofing() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.android.settings");

        assertTrue(HostHookPolicy.isSupportedPackage("com.android.settings"));
        assertTrue(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
        assertTrue(policy.hasEnabledHooks());
    }

    @Test
    public void securityCenterDisablesAllStorageHooks() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.miui.securitycenter");

        assertTrue(HostHookPolicy.isSupportedPackage("com.miui.securitycenter"));
        assertFalse(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
        assertFalse(policy.hasEnabledHooks());
    }

    @Test
    public void unknownPackageFailsClosed() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.example.unknown");

        assertFalse(HostHookPolicy.isSupportedPackage("com.example.unknown"));
        assertFalse(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
        assertFalse(policy.hasEnabledHooks());
    }

    @Test
    public void processSuffixIsNotTreatedAsPackageIdentity() {
        HostHookPolicy policy = HostHookPolicy.forPackage("com.android.settings:remote");

        assertFalse(HostHookPolicy.isSupportedPackage("com.android.settings:remote"));
        assertFalse(policy.packageStatsSpoofing());
        assertFalse(policy.hyperOsSummarySpoofing());
    }

    @Test
    public void noCurrentPolicyEnablesHyperOsSummarySpoofing() {
        String[] packages = {
                "com.android.settings",
                "com.miui.securitycenter",
                "com.example.unknown"
        };

        for (String packageName : packages) {
            assertFalse(HostHookPolicy.forPackage(packageName).hyperOsSummarySpoofing());
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the expected red state**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.yuholt.storagespoof.hook.HostHookPolicyTest
```

Expected: compilation fails because `HostHookPolicy` does not exist.

- [ ] **Step 3: Implement the minimal immutable policy**

Create `app/src/main/java/com/yuholt/storagespoof/hook/HostHookPolicy.java`:

```java
package com.yuholt.storagespoof.hook;

record HostHookPolicy(
        boolean packageStatsSpoofing,
        boolean hyperOsSummarySpoofing) {
    static final String SETTINGS_PACKAGE = "com.android.settings";
    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";

    private static final HostHookPolicy SETTINGS = new HostHookPolicy(true, false);
    private static final HostHookPolicy DISABLED = new HostHookPolicy(false, false);

    static HostHookPolicy forPackage(String packageName) {
        return SETTINGS_PACKAGE.equals(packageName) ? SETTINGS : DISABLED;
    }

    static boolean isSupportedPackage(String packageName) {
        return SETTINGS_PACKAGE.equals(packageName)
                || SECURITY_CENTER_PACKAGE.equals(packageName);
    }

    boolean hasEnabledHooks() {
        return packageStatsSpoofing || hyperOsSummarySpoofing;
    }
}
```

This deliberately maps Security Center and unknown packages to the same disabled capability set while `isSupportedPackage()` lets the module distinguish a recognized safety-skipped host from an unrelated package.

- [ ] **Step 4: Run the focused test and verify green**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.yuholt.storagespoof.hook.HostHookPolicyTest
```

Expected: all five `HostHookPolicyTest` methods pass.

- [ ] **Step 5: Run the complete unit-test suite**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all existing and new unit tests pass with zero failures.

- [ ] **Step 6: Commit the policy unit**

```bash
git add app/src/main/java/com/yuholt/storagespoof/hook/HostHookPolicy.java app/src/test/java/com/yuholt/storagespoof/hook/HostHookPolicyTest.java
git commit -m "Add fail-closed host hook policy"
```

### Task 2: Route Hook Installation Through the Host Policy

**Files:**
- Modify: `app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java:26-35`
- Modify: `app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java:54-58`
- Modify: `app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java:68-75`
- Modify: `app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java:82-126`
- Test: `app/src/test/java/com/yuholt/storagespoof/hook/HostHookPolicyTest.java`

**Interfaces:**
- Consumes: `HostHookPolicy.forPackage(String)`, `isSupportedPackage(String)`, `hasEnabledHooks()`, `packageStatsSpoofing()`, and `hyperOsSummarySpoofing()` from Task 1.
- Produces: `StorageSpoofModule` installs package hooks only when `packageStatsSpoofing()` is true and HyperOS summary hooks only when `hyperOsSummarySpoofing()` is true.
- Produces: missing policy during hot reload leaves all hooks disabled rather than falling back to process-wide package interception.

- [ ] **Step 1: Replace package constants and host boolean with fail-closed policy state**

In `StorageSpoofModule.java`, remove:

```java
import java.util.Set;
```

Remove:

```java
private static final Set<String> SUPPORTED_PACKAGES = Set.of(
        "com.android.settings",
        "com.miui.securitycenter"
);
private static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";
```

Replace:

```java
private volatile boolean hyperOsHost;
```

with:

```java
private volatile HostHookPolicy hostPolicy = HostHookPolicy.forPackage(null);
```

The null lookup returns the disabled policy, so a fresh module generation cannot install hooks before a package identity is known.

- [ ] **Step 2: Resolve policy in `onPackageReady()` and skip Security Center explicitly**

Replace `onPackageReady()` with:

```java
@Override
public void onPackageReady(@NonNull PackageReadyParam param) {
    String packageName = param.getPackageName();
    if (!param.isFirstPackage() || !HostHookPolicy.isSupportedPackage(packageName)) {
        return;
    }

    HostHookPolicy policy = HostHookPolicy.forPackage(packageName);
    hostPolicy = policy;
    hostClassLoader = param.getClassLoader();
    if (!policy.hasEnabledHooks()) {
        log(Log.INFO, TAG, "Storage spoofing disabled in " + packageName
                + " to keep Security Center business logic unmodified");
        return;
    }
    installHooks(hostClassLoader, policy);
}
```

This log is informational, not an installation failure. Exact package matching means no suffix stripping can accidentally authorize another identity.

- [ ] **Step 3: Make hot reload fail closed when the new generation lacks host policy**

At the end of `onHotReloaded()`, replace:

```java
hooksInstalled = false;
installHooks(hostClassLoader != null
        ? hostClassLoader
        : StorageStatsManager.class.getClassLoader());
```

with:

```java
hooksInstalled = false;
HostHookPolicy policy = hostPolicy;
if (!policy.hasEnabledHooks()) {
    log(Log.WARN, TAG, "Storage hooks remain disabled after hot reload until "
            + "the target process restarts and reports its package identity");
    return;
}
installHooks(hostClassLoader != null
        ? hostClassLoader
        : StorageStatsManager.class.getClassLoader(), policy);
```

This does not implement complete API 102 generation-state migration. It closes the unsafe fallback: when a new generation has no restored package identity, it unhooks old handles and installs nothing. A later dedicated hot-reload fix will restore Settings coverage without weakening this gate.

- [ ] **Step 4: Gate each hook family inside `installHooks()`**

Change the signature:

```java
private synchronized void installHooks(
        ClassLoader classLoader,
        HostHookPolicy policy) {
```

Replace its body with:

```java
if (hooksInstalled || !policy.hasEnabledHooks()) {
    return;
}
try {
    int installedCount = 0;
    if (policy.packageStatsSpoofing()) {
        Class<?> managerClass = Class.forName(
                "android.app.usage.StorageStatsManager",
                false,
                classLoader);
        installedCount += installPackageStorageHooks(managerClass);
    }
    if (policy.hyperOsSummarySpoofing()) {
        installedCount += installHyperOsStorageSummaryHooks(classLoader);
    }
    hooksInstalled = installedCount > 0;
    log(Log.INFO, TAG, "Installed " + installedCount + " storage hook(s)");
} catch (Throwable throwable) {
    log(Log.ERROR, TAG, "Failed to install storage hooks", throwable);
}
```

Remove the old `hyperOsHost` condition. The dormant HyperOS methods remain compiled but no current policy can reach them.

- [ ] **Step 5: Verify there is no policy-bypassing install call**

Run:

```bash
rg -n "installHooks\(" app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java
rg -n "hyperOsHost|SUPPORTED_PACKAGES|SECURITY_CENTER_PACKAGE" app/src/main/java/com/yuholt/storagespoof/hook
```

Expected:

- exactly two `installHooks(...)` callers, both passing a `HostHookPolicy`, plus the method declaration;
- no matches for the removed `hyperOsHost` or `SUPPORTED_PACKAGES` symbols;
- `SECURITY_CENTER_PACKAGE` appears only in `HostHookPolicy.java`.

- [ ] **Step 6: Run focused and complete unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.yuholt.storagespoof.hook.HostHookPolicyTest
./gradlew :app:testDebugUnitTest
```

Expected: both commands exit successfully with zero test failures.

- [ ] **Step 7: Compile the debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; this verifies the libxposed overrides and Android references still compile after the installation-flow change.

- [ ] **Step 8: Check the final diff for scope and whitespace errors**

Run:

```bash
git diff --check
git diff --stat
git status --short
```

Expected:

- `git diff --check` emits no errors;
- implementation changes are limited to `HostHookPolicy.java`, `HostHookPolicyTest.java`, and `StorageSpoofModule.java` since the prior plan commit;
- no configuration, UI, or profile persistence file is modified.

- [ ] **Step 9: Commit the integration**

```bash
git add app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java
git commit -m "Disable Security Center storage hooks"
```

### Task 3: Perform Non-Destructive Runtime Verification

**Files:**
- No source files modified.
- Inspect runtime logs and UI only.

**Interfaces:**
- Consumes: debug APK produced by Task 2.
- Produces: evidence that Settings remains hooked while Security Center has no module storage interception.

- [ ] **Step 1: Install the debug APK and activate its existing LSPosed scope**

Use the project's normal device workflow to install the APK from:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Restart both `com.android.settings` and `com.miui.securitycenter` after installation so each process reports package readiness. Do not rely on in-process hot reload for this verification because full hot-reload migration is explicitly outside this fix.

- [ ] **Step 2: Capture logs while opening Android Settings app storage**

Open an app with an enabled spoof profile in Android Settings. Confirm:

```text
Installed <positive number> storage hook(s)
Spoofed <configured package>: app=..., data=..., cache=...
```

Confirm the displayed app/data/cache values match the configured profile.

- [ ] **Step 3: Capture logs while opening Security Center storage pages**

Open:

- storage overview;
- app management;
- app storage details;
- game uninstall.

Confirm Security Center logs contain exactly the safety intent:

```text
Storage spoofing disabled in com.miui.securitycenter to keep Security Center business logic unmodified
```

Confirm the Security Center process emits neither:

```text
Hooked public ... queryStatsForPackage ...
Installed HyperOS storage summary hooks
Spoofed <package> ...
Adjusted HyperOS storage summary ...
```

- [ ] **Step 4: Confirm Security Center displays real values without destructive testing**

Compare Security Center's visible values with unspoofed system/package values where practical. Do not invoke or force `E9.AbstractC1943d` cleanup. The absence of package and summary hook installation in the Security Center process is the safety evidence for background paths.

- [ ] **Step 5: Record any device-only compatibility result without broadening scope**

If runtime verification differs from the expected log behavior, preserve the logs and stop. Do not add caller blacklists or broaden the `AppSystemDataManager.f()` hook. Return to root-cause investigation before changing the design.

## Final Verification Checklist

- [ ] `HostHookPolicyTest` passes.
- [ ] Full `:app:testDebugUnitTest` passes with zero failures.
- [ ] `:app:assembleDebug` succeeds.
- [ ] `git diff --check` reports no errors.
- [ ] Android Settings installs package-stat hooks and displays configured spoofed values.
- [ ] Security Center logs the display-safety skip and installs no storage hooks.
- [ ] Security Center displays real values.
- [ ] No destructive Security Center cleanup path was forced during testing.
- [ ] No files outside the three planned implementation/test files were modified.
