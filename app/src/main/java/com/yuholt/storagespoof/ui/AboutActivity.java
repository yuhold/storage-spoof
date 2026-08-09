package com.yuholt.storagespoof.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.yuholt.storagespoof.R;

public final class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppearancePreferences appearancePreferences = new AppearancePreferences(this);
        if (AppearancePreferences.COLOR_MONET.equals(appearancePreferences.getColorMode())) {
            DynamicColors.applyToActivityIfAvailable(this);
        } else {
            DynamicColors.applyToActivityIfAvailable(
                    this,
                    new DynamicColorsOptions.Builder()
                            .setContentBasedSource(appearancePreferences.getSeedColor())
                            .build());
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
    }
}
