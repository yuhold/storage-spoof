package com.yuholt.storagespoof.config;

import java.util.Objects;

public final class SpoofProfile {
    private final String packageName;
    private final boolean enabled;
    private final long appBytes;
    private final long dataBytes;
    private final long cacheBytes;

    public SpoofProfile(
            String packageName,
            boolean enabled,
            long appBytes,
            long dataBytes,
            long cacheBytes) {
        this.packageName = Objects.requireNonNull(packageName);
        this.enabled = enabled;
        this.appBytes = requireNonNegative(appBytes, "appBytes");
        this.dataBytes = requireNonNegative(dataBytes, "dataBytes");
        this.cacheBytes = requireNonNegative(cacheBytes, "cacheBytes");
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getAppBytes() {
        return appBytes;
    }

    public long getDataBytes() {
        return dataBytes;
    }

    public long getCacheBytes() {
        return cacheBytes;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
