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
