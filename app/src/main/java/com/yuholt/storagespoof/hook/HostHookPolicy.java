package com.yuholt.storagespoof.hook;

public final class HostHookPolicy {
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";

    private static final HostHookPolicy SETTINGS = new HostHookPolicy(true, false);
    private static final HostHookPolicy SECURITY_CENTER = new HostHookPolicy(false, false);
    private static final HostHookPolicy DISABLED = new HostHookPolicy(false, false);

    private final boolean packageStatsSpoofing;
    private final boolean hyperOsSummarySpoofing;

    private HostHookPolicy(boolean packageStatsSpoofing, boolean hyperOsSummarySpoofing) {
        this.packageStatsSpoofing = packageStatsSpoofing;
        this.hyperOsSummarySpoofing = hyperOsSummarySpoofing;
    }

    public static HostHookPolicy forPackage(String packageName) {
        if (SETTINGS_PACKAGE.equals(packageName)) {
            return SETTINGS;
        }
        if (SECURITY_CENTER_PACKAGE.equals(packageName)) {
            return SECURITY_CENTER;
        }
        return DISABLED;
    }

    public static boolean isSupportedPackage(String packageName) {
        return SETTINGS_PACKAGE.equals(packageName)
                || SECURITY_CENTER_PACKAGE.equals(packageName);
    }

    public boolean packageStatsSpoofing() {
        return packageStatsSpoofing;
    }

    public boolean hyperOsSummarySpoofing() {
        return hyperOsSummarySpoofing;
    }

    public boolean hasEnabledHooks() {
        return packageStatsSpoofing || hyperOsSummarySpoofing;
    }
}
