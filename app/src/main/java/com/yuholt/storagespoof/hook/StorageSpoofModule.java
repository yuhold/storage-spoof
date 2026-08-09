package com.yuholt.storagespoof.hook;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.yuholt.storagespoof.config.ProfileStore;
import com.yuholt.storagespoof.config.SpoofProfile;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class StorageSpoofModule extends XposedModule {
    private static final String TAG = "StorageSpoof";
    private static final long CACHE_TTL_MILLIS = 2_000L;
    private static final String HYPER_OS_STORAGE_FRAGMENT =
            "com.miui.optimizecenter.storage.StorageFragment";
    private static final String HYPER_OS_DATA_MANAGER =
            "com.miui.optimizecenter.storage.AppSystemDataManager";

    private final Map<String, CacheEntry> profileCache = new ConcurrentHashMap<>();
    private final Map<PackageUserKey, OriginalSize> originalSizes = new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> storageSummaryDepth =
            ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<Long> storageSummaryTotal = new ThreadLocal<>();

    private volatile SharedPreferences preferences;
    private volatile Field appBytesField;
    private volatile Field dataBytesField;
    private volatile Field cacheBytesField;
    private volatile Field apkBytesField;
    private volatile Field libBytesField;
    private volatile Field dmBytesField;
    private volatile Field dexoptBytesField;
    private volatile Field curProfBytesField;
    private volatile Field refProfBytesField;
    private volatile boolean fieldLookupAttempted;
    private volatile boolean hooksInstalled;
    private volatile String hostPackageName;
    private volatile HostHookPolicy hostPolicy = HostHookPolicy.forPackage(null);
    private volatile ClassLoader hostClassLoader;
    private volatile Method hyperOsSummaryMethod;
    private volatile Object hyperOsStorageFragment;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName()
                + " using " + getFrameworkName() + " API " + getApiVersion());
        preferences = getRemotePreferences(ProfileStore.PREFERENCES_NAME);
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!param.isFirstPackage() || !HostHookPolicy.isSupportedPackage(packageName)) {
            return;
        }

        hostPackageName = packageName;
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

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        resetGenerationState();
        HotReloadState state = HotReloadState.restore(param.getSavedInstanceState());
        hostPackageName = state.packageName();
        hostPolicy = HostHookPolicy.forPackage(hostPackageName);
        ClassLoader loader = state.isEnabled()
                ? (hostClassLoader != null
                        ? hostClassLoader
                        : StorageStatsManager.class.getClassLoader())
                : null;
        migrateHooks(param, hostPolicy, loader);
    }

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

    private synchronized void installHooks(
            ClassLoader classLoader,
            HostHookPolicy policy) {
        if (hooksInstalled || !policy.hasEnabledHooks()) {
            return;
        }
        int installedCount = 0;
        try {
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
            hooksInstalled = installedCount > 0;
            log(Log.ERROR, TAG, "Failed to resolve storage hooks after installing "
                    + installedCount + " hook(s)", throwable);
        }
    }

    private int installPackageStorageHooks(Class<?> managerClass) {
        int installedCount = 0;
        for (Method method : collectPackageStorageMethods(managerClass)) {
            try {
                installPackageStorageHook(method);
                installedCount++;
            } catch (Throwable throwable) {
                log(Log.ERROR, TAG, "Failed to install cold package hook for "
                        + executableKey(method), throwable);
            }
        }
        return installedCount;
    }

    private XposedInterface.HookHandle installPackageStorageHook(Method method) {
        String key = executableKey(method);
        String hookId = packageHookId(key);
        XposedInterface.HookHandle handle = hook(method)
                .setId(hookId)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(createPackageStorageHooker(method));
        log(Log.INFO, TAG, "Hooked " + key + " as " + hookId
                + ", package argument=" + findPackageNameIndex(method)
                + ", user argument=" + findUserHandleIndex(method));
        return handle;
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

    private static List<Method> collectPackageStorageMethods(Class<?> managerClass) {
        List<Method> methods = new ArrayList<>();
        for (Method method : managerClass.getDeclaredMethods()) {
            if ("queryStatsForPackage".equals(method.getName())
                    && StorageStats.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        methods.sort(Comparator.comparing(StorageSpoofModule::executableKey));
        return methods;
    }

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

    private static String packageHookId(String executableKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(executableKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("storage-spoof-package-");
            for (int index = 0; index < 12; index++) {
                id.append(String.format("%02x", digest[index]));
            }
            return id.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 unavailable", impossible);
        }
    }

    private void migrateHooks(
            HotReloadedParam param,
            HostHookPolicy policy,
            ClassLoader classLoader) {
        List<HookMigrationPlan.ExistingHook> existingHooks = new ArrayList<>();
        Map<HookMigrationPlan.ExistingHook, XposedInterface.HookHandle> handlesByDescriptor =
                new IdentityHashMap<>();
        boolean cleanupComplete = true;
        int handleIndex = 0;
        for (XposedInterface.HookHandle handle : param.getOldHookHandles()) {
            String id = null;
            boolean idReadable = true;
            try {
                id = handle.getId();
            } catch (Throwable throwable) {
                idReadable = false;
                log(Log.ERROR, TAG, "Failed to inspect old hook ID; using conservative ownership "
                        + "classification", throwable);
            }

            Executable executable = null;
            try {
                executable = handle.getExecutable();
            } catch (Throwable throwable) {
                if (!idReadable || id == null || isModuleOwnedHookId(id)) {
                    cleanupComplete &= unhookOldHandle(handle,
                            "unreadable module-owned hook");
                    log(Log.ERROR, TAG, "Failed to inspect module-owned old hook executable: "
                            + printableHookId(id), throwable);
                } else {
                    log(Log.ERROR, TAG, "Failed to inspect identifiable non-module hook executable; "
                            + "leaving it alone: " + printableHookId(id), throwable);
                }
                handleIndex++;
                continue;
            }

            boolean storageExecutable = isPackageStorageExecutable(executable);
            boolean moduleOwned = HookMigrationPlan.isModuleOwnedStorageHandle(
                    id, idReadable, storageExecutable) || (idReadable && isModuleOwnedHookId(id));
            if (!moduleOwned) {
                handleIndex++;
                continue;
            }
            if (executable == null) {
                cleanupComplete &= unhookOldHandle(handle,
                        "module-owned hook with no executable");
                log(Log.ERROR, TAG, "Module-owned old hook has no executable: "
                        + printableHookId(id));
                handleIndex++;
                continue;
            }

            String executableKey = executableKey(executable);
            String descriptorId = id != null
                    ? id
                    : "storage-spoof-package-unknown-" + packageHookId(executableKey)
                            + "-" + handleIndex;
            HookMigrationPlan.ExistingHook descriptor = HookMigrationPlan.existing(
                    descriptorId, executableKey);
            existingHooks.add(descriptor);
            handlesByDescriptor.put(descriptor, handle);
            handleIndex++;
        }

        if (!policy.hasEnabledHooks() || hostPackageName == null) {
            boolean descriptorCleanupComplete = unhookDescriptors(existingHooks,
                    handlesByDescriptor, "disabled host");
            cleanupComplete &= descriptorCleanupComplete;
            hooksInstalled = false;
            if (cleanupComplete) {
                log(Log.WARN, TAG, "Storage hooks disabled after hot reload for host identity: "
                        + hostPackageName);
            } else {
                log(Log.ERROR, TAG, "FAIL-CLOSED cleanup incomplete after hot reload for host "
                        + hostPackageName);
            }
            return;
        }

        List<DesiredPackageHook> desiredHooks;
        try {
            Class<?> managerClass = Class.forName(
                    "android.app.usage.StorageStatsManager", false, classLoader);
            desiredHooks = collectPackageStorageMethods(managerClass).stream()
                    .map(method -> new DesiredPackageHook(
                            HookMigrationPlan.desired(executableKey(method)), method))
                    .toList();
        } catch (Throwable throwable) {
            boolean descriptorCleanupComplete = unhookDescriptors(existingHooks,
                    handlesByDescriptor, "desired hook resolution failure");
            cleanupComplete &= descriptorCleanupComplete;
            hooksInstalled = false;
            if (cleanupComplete) {
                log(Log.ERROR, TAG, "Failed to resolve desired storage hooks; disabled", throwable);
            } else {
                log(Log.ERROR, TAG, "FAIL-CLOSED cleanup incomplete after desired hook resolution "
                        + "failure", throwable);
            }
            return;
        }

        Map<String, Method> methodsByKey = new HashMap<>();
        List<HookMigrationPlan.DesiredHook> desiredDescriptors = new ArrayList<>();
        for (DesiredPackageHook desired : desiredHooks) {
            desiredDescriptors.add(desired.descriptor());
            methodsByKey.put(desired.descriptor().executableKey(), desired.method());
        }

        int activeDesiredHooks = 0;
        List<HookMigrationPlan.Action> actions = HookMigrationPlan.plan(
                hostPackageName, desiredDescriptors, existingHooks);
        for (HookMigrationPlan.Action action : actions) {
            try {
                switch (action.kind()) {
                    case REPLACE -> {
                        Method method = methodsByKey.get(action.desired().executableKey());
                        XposedInterface.HookHandle oldHandle = handlesByDescriptor.get(
                                action.existing());
                        try {
                            oldHandle.replaceHook(createPackageStorageHooker(method));
                            activeDesiredHooks++;
                            log(Log.INFO, TAG, "Replaced hot-reload hook for "
                                    + action.desired().executableKey());
                        } catch (Throwable replacementFailure) {
                            log(Log.ERROR, TAG, "Failed to replace hot-reload hook for "
                                    + action.desired().executableKey(), replacementFailure);
                            if (unhookOldHandle(oldHandle, action.existing().id())
                                    && installPackageStorageHookSafely(method)) {
                                activeDesiredHooks++;
                            }
                        }
                    }
                    case INSTALL -> {
                        Method method = methodsByKey.get(action.desired().executableKey());
                        if (installPackageStorageHookSafely(method)) {
                            activeDesiredHooks++;
                        }
                    }
                    case UNHOOK -> cleanupComplete &= unhookOldHandle(
                            handlesByDescriptor.get(action.existing()), action.existing().id());
                    case DISABLE -> {
                        // The disabled policy is handled before desired hooks are resolved.
                    }
                }
            } catch (Throwable throwable) {
                log(Log.ERROR, TAG, "Failed hot-reload migration action " + action.kind(),
                        throwable);
            }
        }
        hooksInstalled = activeDesiredHooks > 0 && cleanupComplete;
        if (!cleanupComplete) {
            log(Log.ERROR, TAG, "FAIL-CLOSED migration cleanup incomplete; old module hooks "
                    + "may remain active");
        }
        log(Log.INFO, TAG, "Hot reload activated " + activeDesiredHooks
                + " package storage hook(s)");
    }

    private boolean installPackageStorageHookSafely(Method method) {
        if (!hostPolicy.packageStatsSpoofing()
                || !"com.android.settings".equals(hostPackageName)
                || method == null) {
            return false;
        }
        try {
            installPackageStorageHook(method);
            return true;
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to install hot-reload hook for "
                    + (method == null ? "<unknown>" : executableKey(method)), throwable);
            return false;
        }
    }

    private boolean unhookOldHandle(XposedInterface.HookHandle handle, String description) {
        if (handle == null) {
            log(Log.ERROR, TAG, "Missing old hook handle for " + description);
            return false;
        }
        try {
            handle.unhook();
            log(Log.INFO, TAG, "Unhooked old module hook: " + description);
            return true;
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to unhook old module hook: " + description, throwable);
            return false;
        }
    }

    private boolean unhookDescriptors(
            List<HookMigrationPlan.ExistingHook> descriptors,
            Map<HookMigrationPlan.ExistingHook, XposedInterface.HookHandle> handlesByDescriptor,
            String reason) {
        boolean complete = true;
        for (HookMigrationPlan.ExistingHook descriptor : descriptors) {
            if (!unhookOldHandle(handlesByDescriptor.get(descriptor),
                    descriptor.id() + " (" + reason + ")")) {
                complete = false;
            }
        }
        return complete;
    }

    private static boolean isPackageStorageExecutable(Executable executable) {
        return executable instanceof Method method
                && "queryStatsForPackage".equals(method.getName())
                && StorageStats.class.isAssignableFrom(method.getReturnType());
    }

    private static String printableHookId(String id) {
        return id == null ? "<missing>" : id;
    }
    private static boolean isModuleOwnedHookId(String id) {
        return id != null && (id.startsWith("storage-spoof-package-")
                || "storage-spoof-hyperos-summary".equals(id)
                || "storage-spoof-hyperos-available".equals(id));
    }

    private int installHyperOsStorageSummaryHooks(ClassLoader classLoader) {
        try {
            Class<?> fragmentClass = Class.forName(
                    HYPER_OS_STORAGE_FRAGMENT,
                    false,
                    classLoader);
            Class<?> dataManagerClass = Class.forName(
                    HYPER_OS_DATA_MANAGER,
                    false,
                    classLoader);
            Method summaryMethod = fragmentClass.getDeclaredMethod("A0", Boolean.class);
            Method availableBytesMethod = dataManagerClass.getDeclaredMethod("f");
            summaryMethod.setAccessible(true);
            availableBytesMethod.setAccessible(true);

            hyperOsSummaryMethod = summaryMethod;
            hook(summaryMethod)
                    .setId("storage-spoof-hyperos-summary")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        hyperOsStorageFragment = chain.getThisObject();
                        storageSummaryTotal.set(readHyperOsStorageTotal(
                                chain.getThisObject()));
                        enterStorageSummary();
                        try {
                            return chain.proceed();
                        } finally {
                            exitStorageSummary();
                        }
                    });
            hook(availableBytesMethod)
                    .setId("storage-spoof-hyperos-available")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (!(result instanceof Long realAvailable)
                                || storageSummaryDepth.get() <= 0) {
                            return result;
                        }
                        long delta = calculateStorageDelta();
                        long adjustedAvailable = subtractClamped(realAvailable, delta);
                        Long total = storageSummaryTotal.get();
                        if (total != null && total >= 0L) {
                            adjustedAvailable = Math.min(adjustedAvailable, total);
                        }
                        log(Log.INFO, TAG, "Adjusted HyperOS storage summary: available="
                                + realAvailable + ", delta=" + delta
                                + ", adjusted=" + adjustedAvailable);
                        return adjustedAvailable;
                    });
            log(Log.INFO, TAG, "Installed HyperOS storage summary hooks");
            return 2;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            log(Log.WARN, TAG, "HyperOS storage summary layout is unavailable", exception);
            return 0;
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to install HyperOS storage summary hooks", throwable);
            return 0;
        }
    }

    private void applyProfile(String packageName, UserHandle user, StorageStats stats) {
        if (packageName == null) {
            return;
        }
        PackageUserKey key = new PackageUserKey(packageName, user);
        SpoofProfile profile = loadProfile(packageName);
        if (profile == null || !profile.isEnabled()) {
            originalSizes.remove(key);
            return;
        }

        try {
            ensureFields(stats.getClass());
            if (appBytesField == null || dataBytesField == null || cacheBytesField == null) {
                return;
            }
            long originalAppBytes = readAccurateAppBytes(stats);
            long originalDataBytes = dataBytesField.getLong(stats);
            OriginalSize previous = originalSizes.put(key, new OriginalSize(
                    originalAppBytes,
                    originalDataBytes));
            if (previous == null) {
                requestHyperOsSummaryRefresh();
            }
            appBytesField.setLong(stats, profile.getAppBytes());
            dataBytesField.setLong(stats, profile.getDataBytes());
            cacheBytesField.setLong(stats, profile.getCacheBytes());
            applyAccurateAppBytes(stats, profile.getAppBytes());
            log(Log.INFO, TAG, "Spoofed " + packageName
                    + ": app=" + profile.getAppBytes()
                    + ", data=" + profile.getDataBytes()
                    + ", cache=" + profile.getCacheBytes());
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to modify stats for " + packageName, throwable);
        }
    }

    private SpoofProfile loadProfile(String packageName) {
        long now = SystemClock.elapsedRealtime();
        CacheEntry cached = profileCache.get(packageName);
        if (cached != null && now - cached.loadedAtMillis < CACHE_TTL_MILLIS) {
            return cached.profile;
        }

        SharedPreferences currentPreferences = preferences;
        SpoofProfile profile = currentPreferences == null
                ? null
                : ProfileStore.get(currentPreferences, packageName);
        profileCache.put(packageName, new CacheEntry(profile, now));
        return profile;
    }

    private synchronized void ensureFields(Class<?> statsClass) {
        if (fieldLookupAttempted) {
            return;
        }
        fieldLookupAttempted = true;
        appBytesField = findLongField(statsClass, "appBytes", "mAppBytes", "codeBytes", "mCodeBytes");
        dataBytesField = findLongField(statsClass, "dataBytes", "mDataBytes");
        cacheBytesField = findLongField(statsClass, "cacheBytes", "mCacheBytes");
        apkBytesField = findLongField(statsClass, "apkBytes", "mApkBytes");
        libBytesField = findLongField(statsClass, "libBytes", "mLibBytes");
        dmBytesField = findLongField(statsClass, "dmBytes", "mDmBytes");
        dexoptBytesField = findLongField(statsClass, "dexoptBytes", "mDexoptBytes");
        curProfBytesField = findLongField(statsClass, "curProfBytes", "mCurProfBytes");
        refProfBytesField = findLongField(statsClass, "refProfBytes", "mRefProfBytes");
        if (appBytesField == null || dataBytesField == null || cacheBytesField == null) {
            log(Log.ERROR, TAG, "Unsupported StorageStats field layout");
        }
    }

    private static Field findLongField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                if (field.getType() == long.class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Try the next known AOSP or vendor field name.
            }
        }
        return null;
    }

    private long readAccurateAppBytes(StorageStats stats) throws IllegalAccessException {
        Field[] fields = {
                apkBytesField,
                libBytesField,
                dmBytesField,
                dexoptBytesField,
                curProfBytesField,
                refProfBytesField
        };
        long accurateBytes = 0L;
        boolean accurateLayoutAvailable = false;
        for (Field field : fields) {
            if (field != null) {
                accurateLayoutAvailable = true;
                accurateBytes = addClamped(accurateBytes, field.getLong(stats));
            }
        }
        return accurateLayoutAvailable ? accurateBytes : appBytesField.getLong(stats);
    }

    private void applyAccurateAppBytes(StorageStats stats, long appBytes)
            throws IllegalAccessException {
        Field[] fields = {
                apkBytesField,
                libBytesField,
                dmBytesField,
                dexoptBytesField,
                curProfBytesField,
                refProfBytesField
        };
        boolean accurateLayoutAvailable = false;
        for (Field field : fields) {
            if (field != null) {
                accurateLayoutAvailable = true;
                field.setLong(stats, 0L);
            }
        }
        if (accurateLayoutAvailable) {
            Field primaryField = apkBytesField != null ? apkBytesField : firstAvailableField(fields);
            primaryField.setLong(stats, appBytes);
        }
    }

    private static Field firstAvailableField(Field[] fields) {
        for (Field field : fields) {
            if (field != null) {
                return field;
            }
        }
        throw new IllegalStateException("No accurate app size field is available");
    }

    private long readHyperOsStorageTotal(Object fragment) {
        if (fragment == null) {
            return Long.MAX_VALUE;
        }
        try {
            Field viewModelField = fragment.getClass().getDeclaredField("f27686i");
            viewModelField.setAccessible(true);
            Object viewModel = viewModelField.get(fragment);
            Method storageInfoMethod = viewModel.getClass().getDeclaredMethod("i");
            storageInfoMethod.setAccessible(true);
            Object storageInfo = storageInfoMethod.invoke(viewModel);
            Method totalMethod = storageInfo.getClass().getDeclaredMethod("m");
            totalMethod.setAccessible(true);
            return (Long) totalMethod.invoke(storageInfo);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "Failed to read HyperOS total storage", throwable);
            return Long.MAX_VALUE;
        }
    }

    private void requestHyperOsSummaryRefresh() {
        Method summaryMethod = hyperOsSummaryMethod;
        Object fragment = hyperOsStorageFragment;
        if (summaryMethod == null || fragment == null) {
            return;
        }
        try {
            Method getViewMethod = fragment.getClass().getMethod("getView");
            Object currentView = getViewMethod.invoke(fragment);
            if (!(currentView instanceof View view)) {
                return;
            }
            view.post(() -> {
                try {
                    Object attachedView = getViewMethod.invoke(fragment);
                    if (attachedView != null) {
                        summaryMethod.invoke(fragment, Boolean.TRUE);
                    }
                } catch (Throwable throwable) {
                    log(Log.WARN, TAG, "Failed to refresh HyperOS storage summary", throwable);
                }
            });
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "Failed to schedule HyperOS storage summary refresh", throwable);
        }
    }

    private void enterStorageSummary() {
        storageSummaryDepth.set(storageSummaryDepth.get() + 1);
    }

    private void exitStorageSummary() {
        int depth = storageSummaryDepth.get() - 1;
        if (depth <= 0) {
            storageSummaryDepth.remove();
            storageSummaryTotal.remove();
        } else {
            storageSummaryDepth.set(depth);
        }
    }

    private long calculateStorageDelta() {
        SharedPreferences currentPreferences = preferences;
        if (currentPreferences == null) {
            return 0L;
        }
        long delta = 0L;
        for (Map.Entry<PackageUserKey, OriginalSize> entry : originalSizes.entrySet()) {
            SpoofProfile profile = ProfileStore.get(
                    currentPreferences,
                    entry.getKey().packageName);
            if (profile == null || !profile.isEnabled()) {
                continue;
            }
            OriginalSize original = entry.getValue();
            long spoofTotal = addClamped(profile.getAppBytes(), profile.getDataBytes());
            long originalTotal = addClamped(original.appBytes, original.dataBytes);
            delta = addSignedClamped(delta, subtractSignedClamped(spoofTotal, originalTotal));
        }
        return delta;
    }

    private static long addClamped(long left, long right) {
        if (left >= Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long subtractSignedClamped(long left, long right) {
        return left >= right ? left - right : -(right - left);
    }

    private static long addSignedClamped(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static long subtractClamped(long value, long delta) {
        if (delta >= 0L) {
            return delta >= value ? 0L : value - delta;
        }
        long increase = delta == Long.MIN_VALUE ? Long.MAX_VALUE : -delta;
        return value >= Long.MAX_VALUE - increase ? Long.MAX_VALUE : value + increase;
    }

    private static int findUserHandleIndex(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (UserHandle.class.isAssignableFrom(parameterTypes[i])) {
                return i;
            }
        }
        return -1;
    }

    private static UserHandle findUserHandle(Object[] args, int userIndex) {
        if (userIndex >= 0
                && userIndex < args.length
                && args[userIndex] instanceof UserHandle userHandle) {
            return userHandle;
        }
        return null;
    }

    private static int findPackageNameIndex(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int firstStringIndex = -1;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] != String.class) {
                continue;
            }
            if (firstStringIndex == -1) {
                firstStringIndex = i;
            } else {
                return i;
            }
        }
        return firstStringIndex;
    }

    private static String findPackageName(Object[] args, int packageNameIndex) {
        if (packageNameIndex >= 0
                && packageNameIndex < args.length
                && args[packageNameIndex] instanceof String packageName) {
            return packageName;
        }
        return null;
    }

    private record DesiredPackageHook(
            HookMigrationPlan.DesiredHook descriptor,
            Method method) {
    }

    private record CacheEntry(SpoofProfile profile, long loadedAtMillis) {
    }

    private record PackageUserKey(String packageName, UserHandle user) {
    }

    private record OriginalSize(long appBytes, long dataBytes) {
    }
}
