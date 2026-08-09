package com.yuholt.storagespoof.ui;

import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.yuholt.storagespoof.R;

public final class AboutActivity extends AppCompatActivity {
    private AppearancePreferences appearancePreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appearancePreferences = new AppearancePreferences(this);
        applyColors();
        super.onCreate(savedInstanceState);
        boolean customUi = AppearancePreferences.UI_CUSTOM.equals(
                appearancePreferences.getUiMode());
        setContentView(customUi
                ? R.layout.activity_about_custom
                : R.layout.activity_about);
        applyBackgroundImage();
    }

    private void applyColors() {
        if (AppearancePreferences.COLOR_MONET.equals(appearancePreferences.getColorMode())) {
            DynamicColors.applyToActivityIfAvailable(this);
            return;
        }
        DynamicColors.applyToActivityIfAvailable(
                this,
                new DynamicColorsOptions.Builder()
                        .setContentBasedSource(appearancePreferences.getSeedColor())
                        .build());
    }

    private void applyBackgroundImage() {
        String value = appearancePreferences.getBackgroundUri();
        if (value.isBlank()) {
            return;
        }
        ImageView background = findViewById(R.id.background_image);
        View scrim = findViewById(R.id.background_scrim);
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(
                    getContentResolver(),
                    Uri.parse(value));
            Drawable drawable = ImageDecoder.decodeDrawable(source);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            background.setAdjustViewBounds(false);
            background.setImageDrawable(drawable);
            background.setVisibility(View.VISIBLE);
            scrim.setVisibility(View.VISIBLE);
            int brightness = appearancePreferences.getBackgroundBrightness();
            float requestedAlpha = 1.0f
                    - Math.max(0, Math.min(100, brightness)) / 100.0f;
            scrim.setAlpha(Math.max(0.18f, requestedAlpha));
        } catch (Exception exception) {
            appearancePreferences.setBackgroundUri("");
            background.setImageDrawable(null);
            background.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            Toast.makeText(
                    this,
                    R.string.background_load_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
