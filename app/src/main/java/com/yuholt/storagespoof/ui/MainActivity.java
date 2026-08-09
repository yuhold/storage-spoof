package com.yuholt.storagespoof.ui;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.slider.Slider;
import com.yuholt.storagespoof.R;
import com.yuholt.storagespoof.StorageSpoofApplication;
import com.yuholt.storagespoof.config.ProfileStore;
import com.yuholt.storagespoof.config.SpoofProfile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;

public final class MainActivity extends AppCompatActivity
        implements StorageSpoofApplication.ServiceStateListener {
    private static final int MENU_APPEARANCE = 1;
    private static final int MENU_ABOUT = 2;
    private static final int MENU_READINESS = 3;
    private static final String LIST_PREFERENCES_NAME = "app_list";
    private static final String KEY_SHOW_SYSTEM_APPS = "show_system_apps";
    private static final String KEY_FILTER_MODE = "filter_mode";
    private static final String KEY_SORT_MODE = "sort_mode";
    private static final long DEFAULT_APP_BYTES = 128L << 20;
    private static final long DEFAULT_DATA_BYTES = 64L << 20;
    private static final long DEFAULT_CACHE_BYTES = 16L << 20;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<PickVisualMediaRequest> backgroundPicker =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    this::onBackgroundSelected);

    private AppearancePreferences appearancePreferences;
    private SharedPreferences listPreferences;
    private AppAdapter adapter;
    private TextView emptyText;
    private TextView moduleStatus;
    private TextView listSummary;
    private SharedPreferences remotePreferences;
    private AlertDialog appearanceDialog;
    private TextView appearanceBackgroundStatus;
    private TextView appearanceBackgroundBrightnessValue;
    private Uri pendingBackgroundUri;
    private int pendingBackgroundBrightness;
    private boolean removeBackground;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appearancePreferences = new AppearancePreferences(this);
        listPreferences = getSharedPreferences(LIST_PREFERENCES_NAME, MODE_PRIVATE);
        applyColors();
        super.onCreate(savedInstanceState);

        boolean customUi = AppearancePreferences.UI_CUSTOM.equals(appearancePreferences.getUiMode());
        setContentView(customUi ? R.layout.activity_main_custom : R.layout.activity_main);
        applyBackgroundImage();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        configureNavigation(toolbar);

        moduleStatus = findViewById(R.id.module_status);
        listSummary = findViewById(R.id.list_summary);
        emptyText = findViewById(R.id.empty_text);
        RecyclerView appList = findViewById(R.id.app_list);
        adapter = new AppAdapter(this, customUi, this::showProfileDialog);
        appList.setLayoutManager(new LinearLayoutManager(this));
        appList.setAdapter(adapter);
        configureListControls(customUi);

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                adapter.filter(editable.toString());
                updateListState();
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

    private void configureNavigation(MaterialToolbar toolbar) {
        View homePage = findViewById(R.id.page_home);
        View appsPage = findViewById(R.id.page_apps);
        View settingsPage = findViewById(R.id.page_settings);
        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(item -> {
            int selectedId = item.getItemId();
            boolean home = selectedId == R.id.navigation_home;
            boolean apps = selectedId == R.id.navigation_apps;
            homePage.setVisibility(home ? View.VISIBLE : View.GONE);
            appsPage.setVisibility(apps ? View.VISIBLE : View.GONE);
            settingsPage.setVisibility(
                    selectedId == R.id.navigation_settings
                            ? View.VISIBLE
                            : View.GONE);
            toolbar.setTitle(home
                    ? R.string.navigation_home
                    : apps
                            ? R.string.navigation_apps
                            : R.string.navigation_settings);
            return true;
        });
        findViewById(R.id.home_open_apps).setOnClickListener(button ->
                navigation.setSelectedItemId(R.id.navigation_apps));
        findViewById(R.id.home_readiness).setOnClickListener(button ->
                startActivity(new Intent(this, LauncherActivity.class)
                        .putExtra(LauncherActivity.EXTRA_FORCE_SHOW, true)));
        findViewById(R.id.home_about).setOnClickListener(button ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.settings_appearance).setOnClickListener(button ->
                showAppearanceDialog());
        findViewById(R.id.settings_readiness).setOnClickListener(button ->
                startActivity(new Intent(this, LauncherActivity.class)
                        .putExtra(LauncherActivity.EXTRA_FORCE_SHOW, true)));
        findViewById(R.id.settings_about).setOnClickListener(button ->
                startActivity(new Intent(this, AboutActivity.class)));
        navigation.setSelectedItemId(R.id.navigation_home);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == MENU_APPEARANCE) {
            showAppearanceDialog();
            return true;
        }
        if (item.getItemId() == MENU_READINESS) {
            startActivity(new android.content.Intent(this, LauncherActivity.class)
                    .putExtra(LauncherActivity.EXTRA_FORCE_SHOW, true));
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
                updateListState();
                moduleStatus.setText(R.string.module_status_unavailable);
                return;
            }
            remotePreferences = service.getRemotePreferences(ProfileStore.PREFERENCES_NAME);
            adapter.setPreferences(remotePreferences);
            updateListState();
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
                        packageManager.getApplicationIcon(info),
                        (info.flags & (ApplicationInfo.FLAG_SYSTEM
                                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0));
            }
            runOnUiThread(() -> {
                adapter.setApps(apps);
                updateListState();
            });
        });
    }

    private void configureListControls(boolean customUi) {
        boolean showSystemApps = listPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false);
        String filterMode = listPreferences.getString(
                KEY_FILTER_MODE,
                AppListPolicy.FILTER_ALL);
        String sortMode = listPreferences.getString(
                KEY_SORT_MODE,
                AppListPolicy.SORT_DEFAULT);

        MaterialSwitch showSystem = findViewById(R.id.show_system_apps);
        showSystem.setChecked(showSystemApps);
        adapter.setShowSystemApps(showSystemApps);
        showSystem.setOnCheckedChangeListener((button, checked) -> {
            listPreferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, checked).apply();
            adapter.setShowSystemApps(checked);
            updateListState();
        });

        ChipGroup filterGroup = findViewById(R.id.filter_group);
        filterGroup.check(switch (filterMode) {
            case AppListPolicy.FILTER_CONFIGURED -> R.id.filter_configured;
            case AppListPolicy.FILTER_UNCONFIGURED -> R.id.filter_unconfigured;
            default -> R.id.filter_all;
        });
        adapter.setFilterMode(filterMode);
        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = checkedIds.isEmpty() ? R.id.filter_all : checkedIds.get(0);
            String selected = checkedId == R.id.filter_configured
                    ? AppListPolicy.FILTER_CONFIGURED
                    : checkedId == R.id.filter_unconfigured
                            ? AppListPolicy.FILTER_UNCONFIGURED
                            : AppListPolicy.FILTER_ALL;
            listPreferences.edit().putString(KEY_FILTER_MODE, selected).apply();
            adapter.setFilterMode(selected);
            updateListState();
        });

        String[] sortLabels = {
                getString(R.string.sort_default),
                getString(R.string.sort_name)
        };
        AutoCompleteTextView sort = findViewById(R.id.sort_mode);
        sort.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                sortLabels));
        sort.setText(
                AppListPolicy.SORT_NAME.equals(sortMode) ? sortLabels[1] : sortLabels[0],
                false);
        adapter.setSortMode(sortMode);
        sort.setOnItemClickListener((parent, view, position, id) -> {
            String selected = position == 1
                    ? AppListPolicy.SORT_NAME
                    : AppListPolicy.SORT_DEFAULT;
            listPreferences.edit().putString(KEY_SORT_MODE, selected).apply();
            adapter.setSortMode(selected);
            updateVisibleSortChoice(selected);
            updateListState();
        });
        configureVisibleSortControls(sortMode, sort, sortLabels);
        configureListOptions(customUi, showSystemApps, filterMode, sortMode, sortLabels);
    }

    private void configureVisibleSortControls(
            String sortMode,
            AutoCompleteTextView hiddenSort,
            String[] sortLabels) {
        ChipGroup group = findViewById(R.id.custom_sort_group);
        updateVisibleSortChoice(sortMode);
        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            int checkedId = checkedIds.isEmpty()
                    ? R.id.custom_sort_default
                    : checkedIds.get(0);
            String selected = checkedId == R.id.custom_sort_name
                    ? AppListPolicy.SORT_NAME
                    : AppListPolicy.SORT_DEFAULT;
            listPreferences.edit().putString(KEY_SORT_MODE, selected).apply();
            hiddenSort.setText(
                    AppListPolicy.SORT_NAME.equals(selected)
                            ? sortLabels[1]
                            : sortLabels[0],
                    false);
            adapter.setSortMode(selected);
            updateListState();
        });
    }

    private void updateVisibleSortChoice(String sortMode) {
        ChipGroup group = findViewById(R.id.custom_sort_group);
        if (group != null) {
            group.check(AppListPolicy.SORT_NAME.equals(sortMode)
                    ? R.id.custom_sort_name
                    : R.id.custom_sort_default);
        }
    }

    private void configureListOptions(
            boolean customUi,
            boolean showSystemApps,
            String filterMode,
            String sortMode,
            String[] sortLabels) {
        View optionsTrigger;
        if (customUi) {
            optionsTrigger = findViewById(R.id.custom_list_options);
        } else {
            TextInputLayout searchContainer = findViewById(R.id.search_container);
            searchContainer.setEndIconOnClickListener(button -> showListOptions(
                    showSystemApps,
                    filterMode,
                    sortMode,
                    sortLabels));
            return;
        }
        optionsTrigger.setOnClickListener(button -> showListOptions(
                showSystemApps,
                filterMode,
                sortMode,
                sortLabels));
    }

    private void showListOptions(
            boolean showSystemApps,
            String filterMode,
            String sortMode,
            String[] sortLabels) {
        View view = getLayoutInflater().inflate(
                R.layout.bottom_sheet_list_options,
                findViewById(android.R.id.content),
                false);
        ChipGroup filter = view.findViewById(R.id.sheet_filter_group);
        MaterialSwitch showSystem = view.findViewById(R.id.sheet_show_system_apps);
        AutoCompleteTextView sort = view.findViewById(R.id.sheet_sort_mode);
        String currentFilter = listPreferences.getString(
                KEY_FILTER_MODE,
                filterMode);
        String currentSort = listPreferences.getString(
                KEY_SORT_MODE,
                sortMode);
        filter.check(switch (currentFilter) {
            case AppListPolicy.FILTER_CONFIGURED -> R.id.sheet_filter_configured;
            case AppListPolicy.FILTER_UNCONFIGURED -> R.id.sheet_filter_unconfigured;
            default -> R.id.sheet_filter_all;
        });
        showSystem.setChecked(listPreferences.getBoolean(
                KEY_SHOW_SYSTEM_APPS,
                showSystemApps));
        sort.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                sortLabels));
        sort.setText(
                AppListPolicy.SORT_NAME.equals(currentSort)
                        ? sortLabels[1]
                        : sortLabels[0],
                false);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(view);
        filter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = checkedIds.isEmpty()
                    ? R.id.sheet_filter_all
                    : checkedIds.get(0);
            String selected = checkedId == R.id.sheet_filter_configured
                    ? AppListPolicy.FILTER_CONFIGURED
                    : checkedId == R.id.sheet_filter_unconfigured
                            ? AppListPolicy.FILTER_UNCONFIGURED
                            : AppListPolicy.FILTER_ALL;
            listPreferences.edit().putString(KEY_FILTER_MODE, selected).apply();
            ChipGroup inlineFilter = findViewById(R.id.filter_group);
            inlineFilter.check(switch (selected) {
                case AppListPolicy.FILTER_CONFIGURED -> R.id.filter_configured;
                case AppListPolicy.FILTER_UNCONFIGURED -> R.id.filter_unconfigured;
                default -> R.id.filter_all;
            });
        });
        showSystem.setOnCheckedChangeListener((buttonView, checked) -> {
            listPreferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, checked).apply();
            ((MaterialSwitch) findViewById(R.id.show_system_apps)).setChecked(checked);
        });
        sort.setOnItemClickListener((parent, item, position, id) -> {
            String selected = position == 1
                    ? AppListPolicy.SORT_NAME
                    : AppListPolicy.SORT_DEFAULT;
            listPreferences.edit().putString(KEY_SORT_MODE, selected).apply();
            ((AutoCompleteTextView) findViewById(R.id.sort_mode)).setText(
                    position == 1 ? sortLabels[1] : sortLabels[0],
                    false);
            adapter.setSortMode(selected);
            updateVisibleSortChoice(selected);
            updateListState();
        });
        dialog.show();
    }

    private void updateListState() {
        emptyText.setText(R.string.no_apps);
        emptyText.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
        listSummary.setText(getString(
                R.string.list_summary,
                adapter.getVisibleCount(),
                adapter.getConfiguredCount()));
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
                    adapter.refreshProfiles();
                    updateListState();
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
                adapter.refreshProfiles();
                updateListState();
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
        MaterialButtonToggleGroup themeGroup = view.findViewById(R.id.theme_mode_group);
        RadioGroup colorGroup = view.findViewById(R.id.color_mode_group);
        RadioButton monet = view.findViewById(R.id.color_monet);
        RadioButton custom = view.findViewById(R.id.color_custom);
        View customColorButton = view.findViewById(R.id.custom_color_button);
        View customColorPreviewRow = view.findViewById(R.id.custom_color_preview_row);
        View customColorPreview = view.findViewById(R.id.custom_color_preview);
        TextView customColorValue = view.findViewById(R.id.custom_color_value);
        appearanceBackgroundStatus = view.findViewById(R.id.background_status);
        appearanceBackgroundBrightnessValue =
                view.findViewById(R.id.background_brightness_value);
        Slider backgroundBrightness =
                view.findViewById(R.id.background_brightness_slider);
        View chooseBackground = view.findViewById(R.id.choose_background);
        View removeBackgroundButton = view.findViewById(R.id.remove_background);
        int[] selectedColor = {appearancePreferences.getSeedColor()};
        pendingBackgroundUri = null;
        pendingBackgroundBrightness = appearancePreferences.getBackgroundBrightness();
        removeBackground = false;

        boolean customUi = AppearancePreferences.UI_CUSTOM.equals(appearancePreferences.getUiMode());
        uiGroup.check(customUi ? R.id.ui_custom : R.id.ui_material3);
        themeGroup.check(switch (appearancePreferences.getThemeMode()) {
            case AppearancePreferences.THEME_LIGHT -> R.id.theme_light;
            case AppearancePreferences.THEME_DARK -> R.id.theme_dark;
            default -> R.id.theme_system;
        });
        boolean monetColor = AppearancePreferences.COLOR_MONET.equals(appearancePreferences.getColorMode());
        colorGroup.check(monetColor ? R.id.color_monet : R.id.color_custom);
        updateColorPreview(customColorPreview, customColorValue, selectedColor[0]);
        setCustomColorControlsEnabled(
                !monetColor,
                customColorButton,
                customColorPreviewRow);
        backgroundBrightness.setValue(pendingBackgroundBrightness);
        updateBackgroundBrightnessPreview(pendingBackgroundBrightness);
        backgroundBrightness.addOnChangeListener((slider, value, fromUser) -> {
            pendingBackgroundBrightness = Math.round(value);
            updateBackgroundBrightnessPreview(pendingBackgroundBrightness);
        });
        updateBackgroundStatus();
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
        chooseBackground.setOnClickListener(button -> backgroundPicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        removeBackgroundButton.setOnClickListener(button -> {
            pendingBackgroundUri = null;
            removeBackground = true;
            updateBackgroundStatus();
            hideBackgroundImage();
        });

        appearanceDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.appearance)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, null)
                .create();
        appearanceDialog.setOnDismissListener(ignored -> {
            appearanceDialog = null;
            appearanceBackgroundStatus = null;
            appearanceBackgroundBrightnessValue = null;
            pendingBackgroundUri = null;
            pendingBackgroundBrightness = 0;
            removeBackground = false;
            applyBackgroundImage();
        });
        appearanceDialog.setOnShowListener(ignored ->
                appearanceDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(button -> {
                            String uiMode = uiGroup.getCheckedButtonId() == R.id.ui_custom
                                    ? AppearancePreferences.UI_CUSTOM
                                    : AppearancePreferences.UI_MATERIAL3;
                            String colorMode = colorGroup.getCheckedRadioButtonId() == R.id.color_monet
                                    ? AppearancePreferences.COLOR_MONET
                                    : AppearancePreferences.COLOR_CUSTOM;
                            int checkedThemeId = themeGroup.getCheckedButtonId();
                            String themeMode = checkedThemeId == R.id.theme_light
                                    ? AppearancePreferences.THEME_LIGHT
                                    : checkedThemeId == R.id.theme_dark
                                            ? AppearancePreferences.THEME_DARK
                                            : AppearancePreferences.THEME_SYSTEM;
                            if (AppearancePreferences.COLOR_CUSTOM.equals(colorMode)) {
                                appearancePreferences.setSeedColor(selectedColor[0]);
                            }
                            if (removeBackground) {
                                appearancePreferences.setBackgroundUri("");
                            } else if (pendingBackgroundUri != null) {
                                appearancePreferences.setBackgroundUri(pendingBackgroundUri.toString());
                            }
                            appearancePreferences.setBackgroundBrightness(
                                    pendingBackgroundBrightness);
                            appearancePreferences.setUiMode(uiMode);
                            appearancePreferences.setColorMode(colorMode);
                            appearancePreferences.setThemeMode(themeMode);
                            StorageSpoofApplication.applyThemeMode(themeMode);
                            appearanceDialog.dismiss();
                            recreate();
                        }));
        appearanceDialog.show();
    }

    private void onBackgroundSelected(@Nullable Uri uri) {
        if (uri == null || appearanceDialog == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some picker providers grant durable access without an explicit persist call.
        }
        pendingBackgroundUri = uri;
        removeBackground = false;
        updateBackgroundStatus();
        showBackgroundPreview(uri);
    }

    private void updateBackgroundStatus() {
        if (appearanceBackgroundStatus == null) {
            return;
        }
        boolean selected = !removeBackground
                && (pendingBackgroundUri != null
                || !appearancePreferences.getBackgroundUri().isBlank());
        appearanceBackgroundStatus.setText(selected
                ? R.string.background_selected
                : R.string.background_none);
    }

    private void applyBackgroundImage() {
        String value = appearancePreferences.getBackgroundUri();
        if (value.isBlank()) {
            hideBackgroundImage();
            return;
        }
        showBackgroundPreview(Uri.parse(value));
        updateBackgroundScrim(appearancePreferences.getBackgroundBrightness());
    }

    private void showBackgroundPreview(Uri uri) {
        ImageView background = findViewById(R.id.background_image);
        View scrim = findViewById(R.id.background_scrim);
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(
                    getContentResolver(),
                    uri);
            Drawable drawable = ImageDecoder.decodeDrawable(source);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            background.setAdjustViewBounds(false);
            background.setImageDrawable(drawable);
            background.setVisibility(View.VISIBLE);
            scrim.setVisibility(View.VISIBLE);
            updateBackgroundScrim(appearanceDialog != null
                    ? pendingBackgroundBrightness
                    : appearancePreferences.getBackgroundBrightness());
        } catch (Exception exception) {
            if (appearanceDialog == null) {
                appearancePreferences.setBackgroundUri("");
            }
            hideBackgroundImage();
            Toast.makeText(
                    this,
                    R.string.background_load_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void hideBackgroundImage() {
        ImageView background = findViewById(R.id.background_image);
        View scrim = findViewById(R.id.background_scrim);
        background.setImageDrawable(null);
        background.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
    }

    private void updateBackgroundBrightnessPreview(int brightness) {
        if (appearanceBackgroundBrightnessValue != null) {
            appearanceBackgroundBrightnessValue.setText(getString(
                    R.string.background_brightness,
                    brightness));
        }
        updateBackgroundScrim(brightness);
    }

    private void updateBackgroundScrim(int brightness) {
        View scrim = findViewById(R.id.background_scrim);
        ImageView background = findViewById(R.id.background_image);
        if (scrim == null || background == null
                || background.getVisibility() != View.VISIBLE) {
            return;
        }
        float requestedAlpha = 1.0f
                - Math.max(0, Math.min(100, brightness)) / 100.0f;
        scrim.setAlpha(Math.max(0.18f, requestedAlpha));
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
