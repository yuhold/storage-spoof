# API 102 Hot-Reload Hook Migration Design

**Date:** 2026-08-09

## Purpose

Restore Android Settings package-stat spoofing after a libxposed API 102 in-process hot reload without weakening the Security Center display-safety gate.

The previous safety-gate change deliberately fails closed after hot reload because a new module generation does not know its host package. This design transfers only neutral host identity, recreates all generation-local state, and atomically replaces eligible old Hookers with Hookers created by the new generation.

## Branch and delivery model

This change is developed on `hot-reload-hook-migration`, stacked on `worktree-contributors-attribution` and PR #1. It will be reviewed independently. After PR #1 is merged, the hot-reload PR can be retargeted to `main`.

## Confirmed problem

libxposed API 102 creates a new module object for an accepted hot reload. The new object receives `onHotReloaded()` but does not receive the old generation's earlier `onPackageReady()` callback.

Current behavior is:

```text
old generation onHotReloading()
  -> returns true without saving host identity

new generation onHotReloaded()
  -> starts with disabled HostHookPolicy
  -> unhooks every old handle
  -> refuses to install hooks because host identity is unknown
```

This is safe for Xiaomi Security Center but causes Android Settings spoofing to remain disabled until its process restarts.

The API 102 artifact exposes the intended migration primitives:

```java
HotReloadingParam.setSavedInstanceState(Object state)
HotReloadedParam.getSavedInstanceState()
HotReloadedParam.getOldHookHandles()
HookHandle.getExecutable()
HookHandle.getId()
HookHandle.replaceHook(Hooker hooker)
HookHandle.unhook()
```

## Scope

### Included

- Save an exact supported host package name during `onHotReloading()`.
- Strictly validate saved state in the new generation.
- Recompute `HostHookPolicy` from the restored package name.
- Restore Android Settings package-stat interception after hot reload.
- Keep Xiaomi Security Center fully disabled before, during, and after hot reload.
- Replace matching old Hookers with new-generation Hookers using `HookHandle.replaceHook()`.
- Install a required Settings hook if no matching old handle exists.
- Unhook obsolete, disallowed, duplicate, or failed old handles.
- Reinitialize Preferences, reflection state, caches, and thread-local state.
- Add pure-Java tests for saved-state validation and migration planning.
- Verify real API 102 replacement behavior non-destructively on an Android 16 device.

### Excluded

- Re-enabling HyperOS package-stat or summary spoofing.
- Migrating `originalSizes`, HyperOS Fragment references, summary state, reflection fields, Preferences proxies, profile caches, or old module objects.
- Fixing nested framework-overload double application.
- Changing profile persistence, multi-user semantics, sliders, Activity state, or image handling.
- Guaranteeing seamless migration from a module version that predates this saved-state protocol. The first update from such a version has no saved host identity and therefore fails closed until the target process restarts.

## Design

### 1. Neutral saved state

Add current-generation host identity:

```java
private volatile String hostPackageName;
```

Normal package readiness remains the authority for host identity:

```text
onPackageReady(packageName)
  -> require first package callback
  -> require exact supported package name
  -> save hostPackageName
  -> resolve HostHookPolicy from packageName
  -> install only policy-authorized hook families
```

During hot reload, the old generation saves only an exact supported package-name string:

```text
onHotReloading(param)
  -> if hostPackageName is exactly supported:
       param.setSavedInstanceState(hostPackageName)
       return true
  -> otherwise:
       do not save state
       return true
```

Permitted values are:

- `com.android.settings`
- `com.miui.securitycenter`

The saved state must not contain:

- `HostHookPolicy` or any other module-defined object;
- a module, host, or framework `ClassLoader`;
- Hookers, HookHandles, Methods, Fields, lambdas, or Fragments;
- SharedPreferences or RemotePreferences proxies;
- Profile Cache entries, `originalSizes`, or ThreadLocals.

A `String` is neutral platform data and does not retain the retiring module class loader.

### 2. Strict restoration and fail-closed behavior

A pure-Java helper validates the saved object:

```text
saved state is a String and exactly supported
  -> restore package name
  -> recompute HostHookPolicy

saved state is null, wrong type, process-suffixed, or unknown
  -> no host package
  -> disabled HostHookPolicy
```

The new generation must never infer authorization from:

- process names;
- a `:remote` suffix;
- old hook count;
- old Hook IDs alone;
- an available fallback class loader.

Unknown state causes every old module HookHandle to be unhooked and no new hook to be installed.

This means the first hot update from the current pre-protocol implementation will intentionally disable Settings hooks until Settings restarts. Subsequent hot reloads performed between protocol-aware generations restore Settings immediately.

### 3. Generation-local reset

Before installing or replacing any Hooker, `onHotReloaded()` obtains a new RemotePreferences proxy and resets all state owned by the new generation:

- clear `profileCache`;
- clear `originalSizes` rather than migrating possibly spoof-contaminated baselines;
- reset reflection lookup flags and all reflected `Field` values;
- clear HyperOS method and Fragment references;
- remove summary ThreadLocal values for the reload callback thread;
- set `hooksInstalled` to false.

Security Center summary state remains absent because no current host policy enables it.

### 4. Stable desired-hook identity

Hook migration must not depend on `Class.getDeclaredMethods()` iteration order or on a fixed number of overloads.

Each desired package hook is identified by a deterministic executable key derived from:

```text
declaring class name
method name
ordered parameter type names
return type name
```

For example:

```text
android.app.usage.StorageStatsManager#queryStatsForPackage(
  java.util.UUID,java.lang.String,android.os.UserHandle
)->android.app.usage.StorageStats
```

Newly installed hook IDs use a stable prefix plus a deterministic digest or encoded executable key. Migration from the existing index-based IDs remains possible because an old handle's `getExecutable()` is authoritative; the old ID is used only to recognize module-owned storage hooks and for diagnostics.

The migration matcher uses both:

- `HookHandle.getExecutable()` to identify the actual intercepted method;
- `HookHandle.getId()` to classify known package and dormant HyperOS hook families.

Executable identity, not old list ordering, determines replacement.

### 5. Pure-Java migration plan

Introduce a small planner that accepts:

- restored host package name;
- desired executable keys under the recomputed policy;
- existing old-hook descriptors containing ID and executable key.

It returns ordered actions:

- `REPLACE(existing, desired)` — desired executable already has one eligible old handle;
- `INSTALL(desired)` — desired executable has no eligible old handle;
- `UNHOOK(existing)` — old handle is disallowed, obsolete, duplicate, or belongs to a disabled hook family;
- `DISABLE` — restoration did not authorize any hook family.

Rules:

1. Android Settings desires every supported `queryStatsForPackage()` method returning `StorageStats`.
2. Xiaomi Security Center desires no hooks.
3. Unknown restoration state desires no hooks.
4. The first eligible old handle for a desired executable is replaced.
5. Duplicate old handles for that executable are unhooked.
6. Old HyperOS summary handles are always unhooked because no current policy authorizes them.
7. Missing desired executables are installed through the normal policy-gated builder.
8. No action may install a hook when policy capabilities are disabled.

The planner contains no Android or libxposed types, allowing deterministic JUnit coverage.

### 6. New-generation Hooker creation

Extract package interception into a factory method that receives an `Executable` or `Method`, validates its signature, computes package/user argument indices, and returns a Hooker created by the current module generation.

The Hooker captures only:

- immutable argument indices;
- the new generation's `StorageSpoofModule` instance through method invocation;
- no object belonging to the retired generation.

Both new registration and `replaceHook()` use the same factory so behavior cannot drift between cold installation and hot migration.

### 7. Applying the migration plan

For each action:

#### `REPLACE`

```text
create new-generation Hooker
  -> oldHandle.replaceHook(newHooker)
  -> retain returned/current handle as active
```

`replaceHook()` preserves the existing executable, ID, priority, and exception mode while atomically changing the Hooker. This avoids the unhooked window created by the previous unhook-all/reinstall flow.

#### `INSTALL`

Use the normal `hook(method)` builder with:

- deterministic ID;
- `ExceptionMode.PROTECTIVE`;
- the same new-generation Hooker factory.

#### `UNHOOK`

Call `unhook()` and do not reuse the handle.

### 8. Per-hook failure handling

Each action is isolated so one failed overload does not prevent migration of another.

```text
REPLACE succeeds
  -> count the returned handle active

REPLACE fails
  -> try to unhook the old handle
  -> log both replacement and cleanup failures if necessary
  -> optionally perform one normal INSTALL for that desired executable

INSTALL fails
  -> log failure
  -> leave that executable uncovered

UNHOOK fails
  -> log a high-severity error
  -> never treat the old handle as successfully migrated
```

A replacement failure must not deliberately leave an old-generation Hooker active. If cleanup also fails, report the unresolved condition prominently; do not claim successful migration.

After processing all actions:

- `hooksInstalled` is true only if at least one policy-authorized desired hook is active;
- a disabled policy always leaves `hooksInstalled` false;
- logs report replaced, newly installed, unhooked, and failed counts separately.

### 9. Host behavior matrix

| Restored state | Desired behavior |
|---|---|
| `com.android.settings` | Replace matching package hooks; install missing package hooks; remove obsolete/HyperOS handles |
| `com.miui.securitycenter` | Unhook every old storage handle; install nothing; log display-safety skip |
| `null` | Unhook every old handle; install nothing; log failed-closed restoration |
| wrong saved-state type | Same as null |
| unknown or process-suffixed string | Same as null |

## Testing

### Unit tests

Add tests for saved-state restoration:

1. exact Settings package is accepted;
2. exact Security Center package is accepted but resolves to disabled policy;
3. `null` is rejected;
4. non-String state is rejected;
5. unknown package is rejected;
6. process-suffixed string is rejected.

Add planner tests:

1. Settings plus two matching old hooks yields two `REPLACE` actions;
2. Settings with one old hook missing yields one `REPLACE` and one `INSTALL`;
3. Settings with duplicate old hooks yields one `REPLACE` and one `UNHOOK` for the duplicate;
4. Security Center with old package and summary hooks yields only `UNHOOK` actions;
5. unknown restoration state with old hooks yields only `UNHOOK` actions and `DISABLE`;
6. an obsolete old executable is unhooked;
7. a desired executable is installed only when package-stat capability is enabled;
8. action ordering is deterministic;
9. no current plan creates a HyperOS summary `INSTALL` action.

Package Hooker integration remains covered by compilation and device verification because libxposed interfaces are compile-only Android APIs and are not practical plain-JVM fakes in the current test setup.

### Build verification

Run:

```bash
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:testDebugUnitTest
ANDROID_HOME=C:/Users/Yuholt/.android-sdk ./gradlew :app:assembleDebug
git diff --check
```

### Non-destructive device verification

On the connected Android 16 HyperOS device:

1. Install a protocol-aware debug build and restart Settings once to seed saved host state.
2. Confirm Settings initially installs all expected package hooks.
3. Install the next protocol-aware build or trigger API 102 hot reload without restarting Settings.
4. Confirm logs report package Hooker replacements and any necessary installs.
5. Confirm Settings still emits spoof logs for an existing enabled profile when such a profile is available.
6. Confirm Security Center main and remote processes continue to log the display-safety skip.
7. Confirm Security Center emits no package-hook, spoof, or summary-adjustment logs.
8. Exercise invalid saved-state behavior through unit tests; do not weaken runtime state or forge unsafe framework data on the device.
9. Do not trigger automatic cleanup, clear app data, uninstall packages, alter user profiles, or broaden LSPosed scope.

If no enabled profile exists, Hook installation/replacement logs are sufficient for the lifecycle migration property; visible configured-value comparison remains a clearly recorded manual follow-up.

## Acceptance criteria

- A protocol-aware Android Settings generation saves exact host identity before reload.
- The next generation restores Settings policy and replaces existing package Hookers with new-generation Hookers.
- Missing desired Settings hooks are installed through a policy-gated path.
- Security Center and all unknown restoration states install no storage hooks.
- No retired module-defined object is transferred through saved instance state.
- Old HyperOS and disallowed handles are unhooked.
- Replacement failures do not intentionally preserve old-generation Hookers.
- Unit tests cover restoration validation and migration decisions.
- Unit tests and debug assembly pass.
- Device logs demonstrate Settings replacement and continuing Security Center fail-closed behavior without destructive testing.

## Follow-up boundary

This change restores lifecycle continuity only. Nested overload deduplication, profile cache coherence, configuration write safety, multi-user semantics, and UI reliability remain separate fixes and must not be folded into this implementation.
