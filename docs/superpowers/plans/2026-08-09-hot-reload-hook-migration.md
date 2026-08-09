# API 102 Hot-Reload Hook Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore Android Settings storage hooks after API 102 hot reload while keeping Security Center and unknown states fully disabled.

**Architecture:** Separate pure-Java restoration and migration planning from libxposed integration. A neutral exact package-name saved state selects a `HostHookPolicy`; a deterministic executable-key planner maps old handles to `REPLACE`, `INSTALL`, `UNHOOK`, or `DISABLE`; the module applies those actions with new-generation Hookers and fails closed on invalid state or cleanup failure.

**Tech Stack:** Java 17, Android SDK 36/compileSdk 37, libxposed API 102, JUnit 4.13.2, Gradle Android plugin.

## Global Constraints

- Save only exact supported host package-name `String` state; never transfer module-defined objects, ClassLoaders, Hookers, HookHandles, reflection objects, Preferences, caches, or ThreadLocals.
- Keep package storage spoofing enabled only for `com.android.settings`.
- Keep `com.miui.securitycenter` and unknown restoration states fully disabled.
- Never authorize from process names, `:remote` suffixes, old hook count, old IDs alone, or fallback ClassLoaders.
- Use `HookHandle.replaceHook()` for matching existing hooks to avoid an intentional unhooked window.
- Unhook obsolete, disallowed, duplicate, or failed old handles; do not intentionally preserve an old-generation Hooker after replacement failure.
- Do not re-enable HyperOS package-stat or summary spoofing.
- Do not migrate `originalSizes`, HyperOS references, reflection fields, Preferences proxies, profile caches, or old module objects.
- Do not fix nested overload processing, profile persistence, multi-user semantics, sliders, Activity state, or image handling.
- Preserve existing persisted profiles and UI.
- Never trigger automatic cleanup, clear app data, uninstall packages, alter user profiles, or broaden LSPosed scope for verification.

---

## File Structure

- Create `app/src/main/java/com/yuholt/storagespoof/hook/HotReloadState.java` — pure-Java exact package-name saved-state validator.
- Create `app/src/main/java/com/yuholt/storagespoof/hook/HookMigrationPlan.java` — pure-Java descriptors/actions/planner for deterministic old-to-new hook migration.
- Create `app/src/test/java/com/yuholt/storagespoof/hook/HotReloadStateTest.java` — saved-state acceptance and fail-closed tests.
- Create `app/src/test/java/com/yuholt/storagespoof/hook/HookMigrationPlanTest.java` — replacement/install/unhook/disable planner tests.
- Modify `app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java:31-139` — host identity state, API 102 saved-state lifecycle, deterministic executable keys, Hooker factory, and migration application.

### Task 1: Add Pure-Java Hot-Reload State and Migration Planner

**Files:**
- Create: `app/src/main/java/com/yuholt/storagespoof/hook/HotReloadState.java`
- Create: `app/src/main/java/com/yuholt/storagespoof/hook/HookMigrationPlan.java`
- Create: `app/src/test/java/com/yuholt/storagespoof/hook/HotReloadStateTest.java`
- Create: `app/src/test/java/com/yuholt/storagespoof/hook/HookMigrationPlanTest.java`

**Interfaces:**
- Produces `HotReloadState.restore(Object): HotReloadState`.
- Produces `HotReloadState.packageName(): String` nullable for disabled state.
- Produces `HotReloadState.isEnabled(): boolean`.
- Produces `HookMigrationPlan.plan(String packageName, List<DesiredHook>, List<ExistingHook>): List<Action>`.
- Produces descriptors with stable executable keys and action kinds `REPLACE`, `INSTALL`, `UNHOOK`, `DISABLE`.
- Consumes `HostHookPolicy.forPackage(String)` from the safety-gate implementation.

- [ ] **Step 1: Write failing saved-state tests**

Create `HotReloadStateTest.java`:

```java
package com.yuholt.storagespoof.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HotReloadStateTest {
    @Test
    public void acceptsSettingsPackage() {
        HotReloadState state = HotReloadState.restore("com.android.settings");
        assertTrue(state.isEnabled());
        assertEquals("com.android.settings", state.packageName());
    }

    @Test
    public void acceptsSecurityCenterIdentityButDisablesHooks() {
        HotReloadState state = HotReloadState.restore("com.miui.securitycenter");
        assertFalse(state.isEnabled());
        assertEquals("com.miui.securitycenter", state.packageName());
    }

    @Test
    public void rejectsNullState() {
        HotReloadState state = HotReloadState.restore(null);
        assertFalse(state.isEnabled());
        assertNull(state.packageName());
    }

    @Test
    public void rejectsWrongStateType() {
        HotReloadState state = HotReloadState.restore(Integer.valueOf(1));
        assertFalse(state.isEnabled());
        assertNull(state.packageName());
    }

    @Test
    public void rejectsUnknownAndProcessSuffixedNames() {
        assertFalse(HotReloadState.restore("com.example.app").isEnabled());
        assertFalse(HotReloadState.restore("com.android.settings:remote").isEnabled());
    }
}
```

- [ ] **Step 2: Run the saved-state test and verify red**

Run:

```bash
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:testDebugUnitTest --tests com.yuholt.storagespoof.hook.HotReloadStateTest
```

Expected: compilation fails because `HotReloadState` does not exist.

- [ ] **Step 3: Implement strict saved-state validation**

Create `HotReloadState.java`:

```java
package com.yuholt.storagespoof.hook;

public final class HotReloadState {
    private final String packageName;
    private final boolean enabled;

    private HotReloadState(String packageName, boolean enabled) {
        this.packageName = packageName;
        this.enabled = enabled;
    }

    public static HotReloadState restore(Object savedState) {
        if (!(savedState instanceof String packageName)
                || !HostHookPolicy.isSupportedPackage(packageName)) {
            return new HotReloadState(null, false);
        }
        return new HotReloadState(
                packageName,
                HostHookPolicy.forPackage(packageName).hasEnabledHooks());
    }

    public String packageName() {
        return packageName;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
```

- [ ] **Step 4: Run saved-state tests green**

Run:

```bash
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:testDebugUnitTest --tests com.yuholt.storagespoof.hook.HotReloadStateTest
```

Expected: five tests pass.

- [ ] **Step 5: Write failing migration planner tests**

Create planner value types as nested immutable records or top-level package-private types in `HookMigrationPlan.java`. The public test-facing API must support executable-key-only descriptors, avoiding Android/libxposed types:

```java
HookMigrationPlan.DesiredHook desired(String key)
HookMigrationPlan.ExistingHook existing(String id, String key)
List<HookMigrationPlan.Action> plan(
        String packageName,
        List<HookMigrationPlan.DesiredHook> desired,
        List<HookMigrationPlan.ExistingHook> existing)
```

Create `HookMigrationPlanTest.java` with these tests:

```java
package com.yuholt.storagespoof.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class HookMigrationPlanTest {
    @Test
    public void settingsReplacesMatchingHooks() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("uuid"),
                        HookMigrationPlan.desired("string")),
                List.of(HookMigrationPlan.existing("old-uuid", "uuid"),
                        HookMigrationPlan.existing("old-string", "string")));

        assertEquals(2, actions.size());
        assertTrue(actions.stream().allMatch(action ->
                action.kind() == HookMigrationPlan.ActionKind.REPLACE));
    }

    @Test
    public void missingDesiredHookIsInstalled() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("uuid"),
                        HookMigrationPlan.desired("string")),
                List.of(HookMigrationPlan.existing("old-uuid", "uuid")));

        assertEquals(2, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.REPLACE, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.INSTALL, actions.get(1).kind());
    }

    @Test
    public void duplicateAndObsoleteHooksAreUnhooked() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("uuid")),
                List.of(HookMigrationPlan.existing("first", "uuid"),
                        HookMigrationPlan.existing("duplicate", "uuid"),
                        HookMigrationPlan.existing("obsolete", "old")));

        assertEquals(3, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.REPLACE, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(1).kind());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(2).kind());
    }

    @Test
    public void disabledHostsOnlyUnhookAndDisable() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                "com.miui.securitycenter",
                List.of(HookMigrationPlan.desired("uuid")),
                List.of(HookMigrationPlan.existing("old", "uuid")));

        assertEquals(2, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.DISABLE, actions.get(1).kind());
    }

    @Test
    public void unknownHostsOnlyUnhookAndDisable() {
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                null,
                List.of(HookMigrationPlan.desired("uuid")),
                List.of(HookMigrationPlan.existing("old", "uuid")));

        assertEquals(2, actions.size());
        assertEquals(HookMigrationPlan.ActionKind.UNHOOK, actions.get(0).kind());
        assertEquals(HookMigrationPlan.ActionKind.DISABLE, actions.get(1).kind());
    }

    @Test
    public void actionOrderingIsDeterministic() {
        List<HookMigrationPlan.Action> first = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("b"), HookMigrationPlan.desired("a")),
                List.of(HookMigrationPlan.existing("old-b", "b"),
                        HookMigrationPlan.existing("old-a", "a")));
        List<HookMigrationPlan.Action> second = HookMigrationPlan.plan(
                "com.android.settings",
                List.of(HookMigrationPlan.desired("b"), HookMigrationPlan.desired("a")),
                List.of(HookMigrationPlan.existing("old-b", "b"),
                        HookMigrationPlan.existing("old-a", "a")));

        assertEquals(first, second);
    }
}
```

- [ ] **Step 6: Run planner tests and verify red**

Run:

```bash
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:testDebugUnitTest --tests com.yuholt.storagespoof.hook.HookMigrationPlanTest
```

Expected: compilation fails because the planner API does not exist.

- [ ] **Step 7: Implement deterministic planner**

Implement `HookMigrationPlan.java` with:

- immutable `DesiredHook(String executableKey)` and `ExistingHook(String id, String executableKey)` records;
- `ActionKind { REPLACE, INSTALL, UNHOOK, DISABLE }`;
- immutable `Action(ActionKind kind, ExistingHook existing, DesiredHook desired)`;
- factories `desired(String)`, `existing(String, String)`;
- `plan(...)` that sorts desired and existing descriptors by executable key then ID, preserves one old handle for each desired key, emits `REPLACE` or `INSTALL`, emits `UNHOOK` for duplicates/obsolete old handles, and emits one final `DISABLE` for a disabled/unknown policy.

A disabled policy must emit no `INSTALL` or `REPLACE`; it must emit `UNHOOK` for every existing descriptor and then `DISABLE`.

- [ ] **Step 8: Run planner and complete unit suites green**

Run:

```bash
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:testDebugUnitTest --tests com.yuholt.storagespoof.hook.HookMigrationPlanTest
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:testDebugUnitTest
```

Expected: planner tests and all existing tests pass.

- [ ] **Step 9: Commit pure-Java planning unit**

```bash
git add app/src/main/java/com/yuholt/storagespoof/hook/HotReloadState.java app/src/main/java/com/yuholt/storagespoof/hook/HookMigrationPlan.java app/src/test/java/com/yuholt/storagespoof/hook/HotReloadStateTest.java app/src/test/java/com/yuholt/storagespoof/hook/HookMigrationPlanTest.java
git commit -m "Add hot reload migration planner"
```

### Task 2: Integrate API 102 State and Hook Replacement

**Files:**
- Modify: `app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java:31-139`
- Test: existing pure-Java planner/state tests from Task 1; no new Android framework test dependency.

**Interfaces:**
- Consumes `HotReloadState.restore(Object)` and `HookMigrationPlan.plan(...)` from Task 1.
- Produces `onHotReloading()` that saves only supported package-name `String` state.
- Produces `onHotReloaded()` that restores policy, resets generation-local state, migrates old handles, and fails closed.
- Produces a deterministic executable-key function for `Executable` and a shared current-generation package Hooker factory.

- [ ] **Step 1: Add host identity and neutral state saving**

Add:

```java
private volatile String hostPackageName;
```

In `onPackageReady()`, after exact supported-package validation and before policy resolution, assign:

```java
hostPackageName = packageName;
```

Replace `onHotReloading()` with:

```java
@Override
public boolean onHotReloading(@NonNull HotReloadingParam param) {
    String packageName = hostPackageName;
    if (HostHookPolicy.isSupportedPackage(packageName)) {
        param.setSavedInstanceState(packageName);
        log(Log.INFO, TAG, "Saved hot-reload host identity: " + packageName);
    } else {
        log(Log.WARN, TAG, "No supported host identity available for hot reload");
    }
    return true;
}
```

No other state may be saved.

- [ ] **Step 2: Extract generation-local reset**

Extract the existing reset block from `onHotReloaded()` into:

```java
private void resetGenerationState() {
    preferences = getRemotePreferences(ProfileStore.PREFERENCES_NAME);
    profileCache.clear();
    originalSizes.clear();
    fieldLookupAttempted = false;
    appBytesField = null;
    dataBytesField = null;
    cacheBytesField = null;
    apkBytesField = null;
    libBytesField = null;
    dmBytesField = null;
    dexoptBytesField = null;
    curProfBytesField = null;
    refProfBytesField = null;
    hyperOsSummaryMethod = null;
    hyperOsStorageFragment = null;
    storageSummaryDepth.remove();
    storageSummaryTotal.remove();
    hooksInstalled = false;
}
```

The method must not restore any old-generation object.

- [ ] **Step 3: Add deterministic executable-key and package Hooker factory helpers**

Add:

```java
private static String executableKey(Executable executable) {
    StringBuilder key = new StringBuilder(executable.getDeclaringClass().getName())
            .append('#').append(executable.getName()).append('(');
    Class<?>[] parameterTypes = executable.getParameterTypes();
    for (int i = 0; i < parameterTypes.length; i++) {
        if (i > 0) {
            key.append(',');
        }
        key.append(parameterTypes[i].getName());
    }
    return key.append(')').append("->").append(
            executable instanceof Method method
                    ? method.getReturnType().getName()
                    : "<init>").toString();
}

private XposedInterface.Hooker createPackageStorageHooker(Method method) {
    int packageNameIndex = findPackageNameIndex(method);
    int userIndex = findUserHandleIndex(method);
    return chain -> {
        Object result = chain.proceed();
        if (result instanceof StorageStats stats) {
            Object[] args = chain.getArgs().toArray();
            applyProfile(
                    findPackageName(args, packageNameIndex),
                    findUserHandle(args, userIndex),
                    stats);
        }
        return result;
    };
}
```

Use the actual imported libxposed `Hooker` type used by the API 102 artifact; do not introduce a duplicate interface. Ensure the factory captures only primitive indices and the current module instance.

- [ ] **Step 4: Make cold package-hook installation use the shared factory and stable keys**

Change `installPackageStorageHooks()` to:

- collect all eligible `Method` objects first;
- sort them by `executableKey(method)`;
- assign a stable ID such as `storage-spoof-package-` plus a deterministic key-safe digest/string;
- call `hook(method).setId(id).setExceptionMode(PROTECTIVE).intercept(createPackageStorageHooker(method))`;
- log the executable key and argument indices.

Do not rely on reflection declaration order. The hook behavior must remain unchanged for cold Settings installation.

- [ ] **Step 5: Add desired-method collection and old-handle descriptors**

Add helpers that:

- load `StorageStatsManager` with the retained Settings `hostClassLoader`;
- collect eligible `queryStatsForPackage()` methods returning `StorageStats`;
- sort methods by `executableKey()`;
- map each method to a `HookMigrationPlan.DesiredHook` plus the `Method` needed for registration;
- convert each `param.getOldHookHandles()` handle to `ExistingHook` using `getId()` and `getExecutable()`;
- classify handles with IDs beginning `storage-spoof-package-` as package hooks; include old HyperOS IDs so disabled policies unhook them; ignore non-module handles only if their ID is not module-owned.

The converted old descriptor key must use `getExecutable()` rather than old ID numbering.

- [ ] **Step 6: Apply migration actions with replacement, installation, and unhook failure handling**

Implement `migrateHooks(HotReloadedParam param, HostHookPolicy policy, ClassLoader classLoader)`:

- when policy is disabled, call `unhook()` on every module-owned old handle, log the Security Center/unknown skip, and leave `hooksInstalled=false`;
- when Settings is enabled, generate desired sorted methods and plan actions;
- `REPLACE`: create the new-generation Hooker and call `oldHandle.replaceHook(newHooker)`;
- `INSTALL`: register through the same builder/factory used by cold install;
- `UNHOOK`: call `unhook()`;
- `DISABLE`: keep disabled;
- catch each action failure independently and log it;
- on `replaceHook()` failure, try to unhook the old handle before optionally attempting one policy-gated normal install for that method;
- set `hooksInstalled` true only if at least one desired package hook is active; never set it true for Security Center or unknown policy.

Do not blindly call `param.getOldHookHandles().forEach(HookHandle::unhook)` in the new implementation; migration owns each module handle exactly once.

- [ ] **Step 7: Replace `onHotReloaded()` with strict restoration flow**

Use this order:

```text
resetGenerationState()
HotReloadState state = HotReloadState.restore(param.getSavedInstanceState())
hostPackageName = state.packageName()
hostPolicy = HostHookPolicy.forPackage(hostPackageName)
ClassLoader loader = hostClassLoader != null
        ? hostClassLoader
        : StorageStatsManager.class.getClassLoader()
migrateHooks(param, hostPolicy, loader)
```

If state is disabled or package name is null, do not install hooks. The fallback classloader may be used only after a valid Settings package identity has been restored; it must never authorize a host.

- [ ] **Step 8: Run all unit tests and compile debug APK**

Run:

```bash
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:testDebugUnitTest
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:assembleDebug
```

Expected: all unit tests pass and debug APK assembles successfully.

- [ ] **Step 9: Check integration diff and commit**

Run:

```bash
git diff --check
git diff --stat
rg -n "setSavedInstanceState|getSavedInstanceState|replaceHook|HookHandle::unhook|installHooks|installPackageStorageHooks" app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java
```

Confirm there is no unconditional old-handle unhook loop left in `onHotReloaded()`, no HyperOS policy enablement, and no saved state other than the package-name string.

Commit:

```bash
git add app/src/main/java/com/yuholt/storagespoof/hook/StorageSpoofModule.java
 git commit -m "Restore Settings hooks across hot reload"
```

### Task 3: Non-Destructive API 102 Device Verification

**Files:**
- No source files modified.
- Create only an ignored verification report under `.superpowers/sdd/...` if using the SDD workflow.

**Interfaces:**
- Consumes debug APK and connected Android 16 device.
- Produces logs proving replacement/restoration and continued Security Center disablement.

- [ ] **Step 1: Build and install the debug APK**

Run:

```bash
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:assembleDebug
MSYS_NO_PATHCONV=1 C:/Users/Yuholt/.android-sdk/platform-tools/adb.exe -s 230e4b30 install -r app/build/outputs/apk/debug/app-debug.apk
```

Do not change LSPosed scope or user profiles.

- [ ] **Step 2: Seed a protocol-aware generation**

Force-stop and relaunch Settings and Security Center once. Capture logs showing:

```text
com.android.settings ... Installed ... storage hook(s)
com.miui.securitycenter ... Storage spoofing disabled ...
```

This seeds the old generation's host identity and confirms the safety gate remains active.

- [ ] **Step 3: Trigger API 102 hot reload without destructive actions**

Use the existing authorized module hot-reload mechanism or install the next protocol-aware module generation without force-stopping Settings if the framework supports it. Do not clear data, uninstall packages, invoke cleanup jobs, or alter LSPosed scope.

- [ ] **Step 4: Inspect replacement and fail-closed logs**

Expected Settings evidence:

```text
Saved hot-reload host identity: com.android.settings
replaced=...
installed=...
unhooked=...
```

Expected Security Center evidence:

```text
Storage spoofing disabled in com.miui.securitycenter ...
```

No Security Center `Hooked ... queryStatsForPackage`, `Spoofed ...`, or `Adjusted HyperOS storage summary` lines may appear after the relevant restart/reload boundary.

- [ ] **Step 5: Verify configured values only if an enabled profile exists**

If an enabled profile is already present, confirm Settings still logs and displays its configured values after hot reload. If no profile exists, record the visible-value comparison as blocked; do not create or mutate profiles solely for this test.

- [ ] **Step 6: Record limitations accurately**

A framework/device that cannot trigger API 102 hot reload must be reported as a runtime limitation. Do not claim replacement based solely on cold-start installation logs. Unit tests and compilation still verify planner and integration contracts.

### Final Verification Checklist

- [ ] `HotReloadStateTest` passes.
- [ ] `HookMigrationPlanTest` passes.
- [ ] Full `:app:testDebugUnitTest` passes.
- [ ] `:app:assembleDebug` succeeds.
- [ ] `git diff --check` passes.
- [ ] Settings saves exact package identity and restores package hooks after protocol-aware hot reload.
- [ ] Security Center remains disabled for main, remote, and invalid restoration state.
- [ ] Matching old handles use `replaceHook()`; missing desired handles use policy-gated install.
- [ ] Obsolete, duplicate, disallowed, and failed old handles are unhooked.
- [ ] No old-generation module object is saved or captured by new Hookers.
- [ ] Device evidence is non-destructive and accurately reports any unavailable hot-reload trigger.
