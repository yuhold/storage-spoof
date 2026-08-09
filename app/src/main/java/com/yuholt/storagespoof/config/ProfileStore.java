package com.yuholt.storagespoof.config;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProfileStore {
    public static final String PREFERENCES_NAME = "spoof_profiles";
    public static final String KEY_PACKAGES = "packages";
    public static final String SUFFIX_ENABLED = ".enabled";
    public static final String SUFFIX_APP_BYTES = ".app_bytes";
    public static final String SUFFIX_DATA_BYTES = ".data_bytes";
    public static final String SUFFIX_CACHE_BYTES = ".cache_bytes";

    private ProfileStore() {
    }

    public static List<SpoofProfile> getAll(SharedPreferences preferences) {
        Set<String> packageNames = preferences.getStringSet(KEY_PACKAGES, Collections.emptySet());
        List<SpoofProfile> profiles = new ArrayList<>(packageNames.size());
        for (String packageName : packageNames) {
            SpoofProfile profile = get(preferences, packageName);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        profiles.sort((left, right) -> left.getPackageName().compareToIgnoreCase(right.getPackageName()));
        return profiles;
    }

    @Nullable
    public static SpoofProfile get(SharedPreferences preferences, String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        Set<String> packageNames = preferences.getStringSet(KEY_PACKAGES, Collections.emptySet());
        if (!packageNames.contains(packageName)) {
            return null;
        }
        return new SpoofProfile(
                packageName,
                preferences.getBoolean(key(packageName, SUFFIX_ENABLED), true),
                preferences.getLong(key(packageName, SUFFIX_APP_BYTES), 0L),
                preferences.getLong(key(packageName, SUFFIX_DATA_BYTES), 0L),
                preferences.getLong(key(packageName, SUFFIX_CACHE_BYTES), 0L));
    }

    public static boolean save(SharedPreferences preferences, SpoofProfile profile) {
        Set<String> packageNames = new HashSet<>(
                preferences.getStringSet(KEY_PACKAGES, Collections.emptySet()));
        packageNames.add(profile.getPackageName());
        SharedPreferences.Editor editor = preferences.edit();
        if (editor == null) {
            return false;
        }
        return editor
                .putStringSet(KEY_PACKAGES, packageNames)
                .putBoolean(key(profile.getPackageName(), SUFFIX_ENABLED), profile.isEnabled())
                .putLong(key(profile.getPackageName(), SUFFIX_APP_BYTES), profile.getAppBytes())
                .putLong(key(profile.getPackageName(), SUFFIX_DATA_BYTES), profile.getDataBytes())
                .putLong(key(profile.getPackageName(), SUFFIX_CACHE_BYTES), profile.getCacheBytes())
                .commit();
    }

    public static boolean delete(SharedPreferences preferences, String packageName) {
        Set<String> packageNames = new HashSet<>(
                preferences.getStringSet(KEY_PACKAGES, Collections.emptySet()));
        packageNames.remove(packageName);
        SharedPreferences.Editor editor = preferences.edit();
        if (editor == null) {
            return false;
        }
        return editor
                .putStringSet(KEY_PACKAGES, packageNames)
                .remove(key(packageName, SUFFIX_ENABLED))
                .remove(key(packageName, SUFFIX_APP_BYTES))
                .remove(key(packageName, SUFFIX_DATA_BYTES))
                .remove(key(packageName, SUFFIX_CACHE_BYTES))
                .commit();
    }

    private static String key(String packageName, String suffix) {
        return packageName + suffix;
    }
}
