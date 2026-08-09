package com.yuholt.storagespoof.ui;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.yuholt.storagespoof.R;
import com.yuholt.storagespoof.StorageSpoofApplication;
import com.yuholt.storagespoof.config.ProfileStore;
import com.yuholt.storagespoof.config.SpoofProfile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;

public final class MainActivity extends AppCompatActivity
        implements StorageSpoofApplication.ServiceStateListener {
    private static final int MENU_APPEARANCE = 1;
    private static final int MENU_ABOUT = 2;
    private static final long DEFAULT_APP_BYTES = 128L << 20;
    private static final long DEFAULT_DATA_BYTES = 64L << 20;
    private static final long DEFAULT_CACHE_BYTES = 16L << 20;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AppearancePreferences appearancePreferences;
    private AppAdapter adapter;
    private TextView emptyText;
    private TextView moduleStatus;
    private SharedPreferences remotePreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appearancePreferences = new AppearancePreferences(this);
        applyColors();
        super.onCreate(savedInstanceState);

        boolean customUi = AppearancePreferences.UI_CUSTOM.equals(appearancePreferences.getUiMode());
        setContentView(customUi ? R.layout.activity_main_custom : R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        moduleStatus = findViewById(R.id.module_status);
        emptyText = findViewById(R.id.empty_text);
        RecyclerView appList = findViewById(R.id.app_list);
        adapter = new AppAdapter(this, customUi, this::showProfileDialog);
        appList.setLayoutManager(new LinearLayoutManager(this));
        appList.setAdapter(adapter);

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                adapter.filter(editable.toString());
                updateEmptyState();
            }
        });

        loadApplications();
    }

    @Override
    protected void onStart() {
        super.onStart();
        StorageSpoofApplication.addServiceStateListener(this, true);
    }

    @Override
    protected void onStop() {
        StorageSpoofApplication.removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem appearance = menu.add(
                Menu.NONE,
                MENU_APPEARANCE,
                Menu.NONE,
                R.string.appearance);
        appearance.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        appearance.setIcon(android.R.drawable.ic_menu_manage);
        menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == MENU_APPEARANCE) {
            showAppearanceDialog();
            return true;
        }
        if (item.getItemId() == MENU_ABOUT) {
            startActivity(new android.content.Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceStateChanged(@Nullable XposedService service) {
        runOnUiThread(() -> {
            if (service == null) {
                remotePreferences = null;
                adapter.setPreferences(null);
                moduleStatus.setText(R.string.module_status_unavailable);
                return;
            }
            remotePreferences = service.getRemotePreferences(ProfileStore.PREFERENCES_NAME);
            adapter.setPreferences(remotePreferences);
            moduleStatus.setText(getString(
                    R.string.module_status_ready,
                    service.getFrameworkName(),
                    service.getApiVersion()));
        });
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

    private void loadApplications() {
        emptyText.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.loading_apps);
        executor.execute(() -> {
            PackageManager packageManager = getPackageManager();
            List<ApplicationInfo> applicationInfos = packageManager.getInstalledApplications(
                    PackageManager.GET_META_DATA);
            List<AppEntry> apps = new ArrayList<>(applicationInfos.size());
            for (ApplicationInfo info : applicationInfos) {
                CharSequence label = packageManager.getApplicationLabel(info);
                apps.add(new AppEntry(
                        label == null ? info.packageName : label.toString(),
                        info.packageName,
                        packageManager.getApplicationIcon(info)));
            }
            apps.sort(Comparator
                    .comparing(AppEntry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AppEntry::packageName));
            runOnUiThread(() -> {
                adapter.setApps(apps);
                updateEmptyState();
            });
        });
    }

    private void updateEmptyState() {
        emptyText.setText(R.string.no_apps);
        emptyText.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showProfileDialog(AppEntry app) {
        SharedPreferences preferences = remotePreferences;
        if (preferences == null) {
            Toast.makeText(this, R.string.remote_preferences_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_profile, null);
        TextView packageName = view.findViewById(R.id.dialog_package_name);
        TextView storageLimit = view.findViewById(R.id.storage_limit);
        MaterialSwitch enabled = view.findViewById(R.id.enabled);

        packageName.setText(app.packageName());
        SpoofProfile existing = ProfileStore.get(preferences, app.packageName());
        long maximumBytes = getInternalStorageCapacity();
        long initialAppBytes = existing == null ? DEFAULT_APP_BYTES : existing.getAppBytes();
        long initialDataBytes = existing == null ? DEFAULT_DATA_BYTES : existing.getDataBytes();
        long initialCacheBytes = existing == null ? DEFAULT_CACHE_BYTES : existing.getCacheBytes();
        storageLimit.setText(getString(R.string.storage_limit, SizeParser.format(maximumBytes)));

        SizeSliderControl appSize = new SizeSliderControl(
                view,
                R.id.app_size_slider,
                R.id.app_size_unit,
                R.id.app_size_value,
                R.id.app_unit_mb,
                R.id.app_unit_gb,
                maximumBytes,
                initialAppBytes);
        SizeSliderControl dataSize = new SizeSliderControl(
                view,
                R.id.data_size_slider,
                R.id.data_size_unit,
                R.id.data_size_value,
                R.id.data_unit_mb,
                R.id.data_unit_gb,
                maximumBytes,
                initialDataBytes);
        SizeSliderControl cacheSize = new SizeSliderControl(
                view,
                R.id.cache_size_slider,
                R.id.cache_size_unit,
                R.id.cache_size_value,
                R.id.cache_unit_mb,
                R.id.cache_unit_gb,
                maximumBytes,
                initialCacheBytes);

        if (existing == null) {
            loadRealSizeDefaults(app, maximumBytes, appSize, dataSize, cacheSize);
        }

        if (existing != null) {
            enabled.setChecked(existing.isEnabled());
        }
        setSizeControlsEnabled(enabled.isChecked(), appSize, dataSize, cacheSize);
        enabled.setOnCheckedChangeListener((button, checked) ->
                setSizeControlsEnabled(checked, appSize, dataSize, cacheSize));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(app.label())
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(existing == null ? R.string.cancel : R.string.delete_profile, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            if (existing == null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
            } else {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
                    ProfileStore.delete(preferences, app.packageName());
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.profile_deleted, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                long appBytes = appSize.getBytes();
                long dataBytes = dataSize.getBytes();
                long cacheBytes = cacheSize.getBytes();
                if (exceedsCapacity(appBytes, dataBytes, cacheBytes, maximumBytes)) {
                    Toast.makeText(
                            this,
                            getString(
                                    R.string.storage_total_exceeded,
                                    SizeParser.format(maximumBytes)),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ProfileStore.save(preferences, new SpoofProfile(
                        app.packageName(),
                        enabled.isChecked(),
                        appBytes,
                        dataBytes,
                        cacheBytes));
                adapter.notifyDataSetChanged();
                Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void loadRealSizeDefaults(
            AppEntry app,
            long maximumBytes,
            SizeSliderControl appSize,
            SizeSliderControl dataSize,
            SizeSliderControl cacheSize) {
        executor.execute(() -> {
            RealSize realSize = queryRealSize(app.packageName());
            if (realSize == null) {
                return;
            }
            runOnUiThread(() -> {
                long appBytes = Math.min(realSize.appBytes, maximumBytes);
                long dataBytes = Math.min(realSize.dataBytes, maximumBytes);
                long cacheBytes = Math.min(realSize.cacheBytes, maximumBytes);
                if (exceedsCapacity(appBytes, dataBytes, cacheBytes, maximumBytes)) {
                    return;
                }
                appSize.setBytesIfUnchanged(appBytes);
                dataSize.setBytesIfUnchanged(dataBytes);
                cacheSize.setBytesIfUnchanged(cacheBytes);
            });
        });
    }

    @Nullable
    private RealSize queryRealSize(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            StorageStatsManager manager = getSystemService(StorageStatsManager.class);
            if (manager == null) {
                return null;
            }
            StorageStats stats = manager.queryStatsForPackage(
                    info.storageUuid,
                    packageName,
                    UserHandle.getUserHandleForUid(info.uid));
            return new RealSize(
                    getAccurateAppBytes(stats),
                    stats.getDataBytes(),
                    stats.getCacheBytes());
        } catch (Exception exception) {
            return null;
        }
    }

    private static long getAccurateAppBytes(StorageStats stats) {
        try {
            Method method = stats.getClass().getMethod("getAppAccurateBytes");
            Object result = method.invoke(stats);
            if (result instanceof Long appBytes) {
                return appBytes;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to the public AOSP app byte total.
        }
        return stats.getAppBytes();
    }

    private static long getInternalStorageCapacity() {
        StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        return statFs.getTotalBytes();
    }

    private static boolean exceedsCapacity(
            long appBytes,
            long dataBytes,
            long cacheBytes,
            long maximumBytes) {
        if (appBytes > maximumBytes || dataBytes > maximumBytes || cacheBytes > maximumBytes) {
            return true;
        }
        return appBytes > maximumBytes - dataBytes
                || appBytes + dataBytes > maximumBytes - cacheBytes;
    }

    private static void setSizeControlsEnabled(
            boolean enabled,
            SizeSliderControl... controls) {
        for (SizeSliderControl control : controls) {
            control.setEnabled(enabled);
        }
    }

    private void showAppearanceDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_appearance, null);
        MaterialButtonToggleGroup uiGroup = view.findViewById(R.id.ui_mode_group);
        RadioGroup colorGroup = view.findViewById(R.id.color_mode_group);
        RadioButton monet = view.findViewById(R.id.color_monet);
        RadioButton custom = view.findViewById(R.id.color_custom);
        View customColorButton = view.findViewById(R.id.custom_color_button);
        View customColorPreviewRow = view.findViewById(R.id.custom_color_preview_row);
        View customColorPreview = view.findViewById(R.id.custom_color_preview);
        TextView customColorValue = view.findViewById(R.id.custom_color_value);
        int[] selectedColor = {appearancePreferences.getSeedColor()};

        boolean customUi = AppearancePreferences.UI_CUSTOM.equals(appearancePreferences.getUiMode());
        uiGroup.check(customUi ? R.id.ui_custom : R.id.ui_material3);
        boolean monetColor = AppearancePreferences.COLOR_MONET.equals(appearancePreferences.getColorMode());
        colorGroup.check(monetColor ? R.id.color_monet : R.id.color_custom);
        updateColorPreview(customColorPreview, customColorValue, selectedColor[0]);
        setCustomColorControlsEnabled(
                !monetColor,
                customColorButton,
                customColorPreviewRow);
        colorGroup.setOnCheckedChangeListener((group, checkedId) ->
                setCustomColorControlsEnabled(
                        checkedId == R.id.color_custom,
                        customColorButton,
                        customColorPreviewRow));
        customColorButton.setOnClickListener(button -> showColorPicker(
                selectedColor[0],
                color -> {
                    selectedColor[0] = color;
                    updateColorPreview(customColorPreview, customColorValue, color);
                }));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.appearance)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String uiMode = uiGroup.getCheckedButtonId() == R.id.ui_custom
                            ? AppearancePreferences.UI_CUSTOM
                            : AppearancePreferences.UI_MATERIAL3;
                    String colorMode = colorGroup.getCheckedRadioButtonId() == R.id.color_monet
                            ? AppearancePreferences.COLOR_MONET
                            : AppearancePreferences.COLOR_CUSTOM;
                    if (AppearancePreferences.COLOR_CUSTOM.equals(colorMode)) {
                        appearancePreferences.setSeedColor(selectedColor[0]);
                    }
                    appearancePreferences.setUiMode(uiMode);
                    appearancePreferences.setColorMode(colorMode);
                    dialog.dismiss();
                    recreate();
                }));
        dialog.show();
    }

    private void showColorPicker(int initialColor, ColorSelectionListener listener) {
        View view = getLayoutInflater().inflate(R.layout.dialog_color_picker, null);
        View preview = view.findViewById(R.id.color_preview);
        TextView value = view.findViewById(R.id.color_value);
        Slider hue = view.findViewById(R.id.color_hue_slider);
        Slider saturation = view.findViewById(R.id.color_saturation_slider);
        Slider brightness = view.findViewById(R.id.color_brightness_slider);
        float[] hsv = new float[3];
        Color.colorToHSV(initialColor, hsv);
        hue.setValue(hsv[0]);
        saturation.setValue(hsv[1] * 100.0f);
        brightness.setValue(hsv[2] * 100.0f);
        int[] selectedColor = {initialColor};
        Slider.OnChangeListener changeListener = (slider, sliderValue, fromUser) -> {
            selectedColor[0] = Color.HSVToColor(new float[]{
                    hue.getValue(),
                    saturation.getValue() / 100.0f,
                    brightness.getValue() / 100.0f
            });
            updateColorPreview(preview, value, selectedColor[0]);
        };
        hue.addOnChangeListener(changeListener);
        saturation.addOnChangeListener(changeListener);
        brightness.addOnChangeListener(changeListener);
        updateColorPreview(preview, value, initialColor);

        new AlertDialog.Builder(this)
                .setTitle(R.string.color_picker_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, (dialog, which) ->
                        listener.onColorSelected(selectedColor[0]))
                .show();
    }

    private static void setCustomColorControlsEnabled(
            boolean enabled,
            View button,
            View previewRow) {
        button.setEnabled(enabled);
        previewRow.setEnabled(enabled);
        previewRow.setAlpha(enabled ? 1.0f : 0.38f);
    }

    private static void updateColorPreview(View preview, TextView value, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(16.0f * preview.getResources().getDisplayMetrics().density);
        preview.setBackground(background);
        value.setText(AppearancePreferences.formatColor(color));
    }

    private interface ColorSelectionListener {
        void onColorSelected(int color);
    }

    private record RealSize(long appBytes, long dataBytes, long cacheBytes) {
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence value, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence value, int start, int before, int count) {
        }
    }
}
