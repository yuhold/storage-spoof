package com.yuholt.storagespoof.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.ColorInt;

public final class AppearancePreferences {
    public static final String UI_CUSTOM = "custom";
    public static final String UI_MATERIAL3 = "material3";
    public static final String COLOR_MONET = "monet";
    public static final String COLOR_CUSTOM = "custom";

    private static final String PREFERENCES_NAME = "appearance";
    private static final String KEY_UI_MODE = "ui_mode";
    private static final String KEY_COLOR_MODE = "color_mode";
    private static final String KEY_SEED_COLOR = "seed_color";
    private static final int DEFAULT_SEED_COLOR = 0xFF6750A4;

    private final SharedPreferences preferences;

    public AppearancePreferences(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public String getUiMode() {
        return preferences.getString(KEY_UI_MODE, UI_CUSTOM);
    }

    public void setUiMode(String mode) {
        preferences.edit().putString(KEY_UI_MODE, mode).apply();
    }

    public String getColorMode() {
        return preferences.getString(KEY_COLOR_MODE, COLOR_MONET);
    }

    public void setColorMode(String mode) {
        preferences.edit().putString(KEY_COLOR_MODE, mode).apply();
    }

    @ColorInt
    public int getSeedColor() {
        return preferences.getInt(KEY_SEED_COLOR, DEFAULT_SEED_COLOR);
    }

    public void setSeedColor(@ColorInt int color) {
        preferences.edit().putInt(KEY_SEED_COLOR, color | 0xFF000000).apply();
    }

    public static String formatColor(@ColorInt int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
