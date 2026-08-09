package com.yuholt.storagespoof.ui;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

public record AppEntry(
        @NonNull String label,
        @NonNull String packageName,
        @NonNull Drawable icon) {
}
