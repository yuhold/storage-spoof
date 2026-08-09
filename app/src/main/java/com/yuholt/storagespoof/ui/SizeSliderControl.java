package com.yuholt.storagespoof.ui;

import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;

final class SizeSliderControl {
    private static final long MEBIBYTE = 1L << 20;
    private static final long GIBIBYTE = 1L << 30;

    private final long maximumBytes;
    private final Slider slider;
    private final MaterialButtonToggleGroup unitGroup;
    private final TextView valueText;
    private final int megabyteButtonId;
    private final int gigabyteButtonId;

    private long unitBytes;

    SizeSliderControl(
            View root,
            int sliderId,
            int unitGroupId,
            int valueTextId,
            int megabyteButtonId,
            int gigabyteButtonId,
            long maximumBytes,
            long initialBytes) {
        this.maximumBytes = maximumBytes;
        this.slider = root.findViewById(sliderId);
        this.unitGroup = root.findViewById(unitGroupId);
        this.valueText = root.findViewById(valueTextId);
        this.megabyteButtonId = megabyteButtonId;
        this.gigabyteButtonId = gigabyteButtonId;

        boolean useGigabytes = initialBytes >= GIBIBYTE && initialBytes % GIBIBYTE == 0L;
        unitBytes = useGigabytes ? GIBIBYTE : MEBIBYTE;
        unitGroup.check(useGigabytes ? gigabyteButtonId : megabyteButtonId);
        configureSlider(initialBytes);

        slider.addOnChangeListener((ignored, value, fromUser) -> updateValueText());
        unitGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            long currentBytes = getBytes();
            unitBytes = checkedId == gigabyteButtonId ? GIBIBYTE : MEBIBYTE;
            configureSlider(currentBytes);
        });
    }

    long getBytes() {
        double bytes = slider.getValue() * (double) unitBytes;
        return Math.min(maximumBytes, Math.round(bytes));
    }

    void setEnabled(boolean enabled) {
        slider.setEnabled(enabled);
        unitGroup.setEnabled(enabled);
        unitGroup.findViewById(megabyteButtonId).setEnabled(enabled);
        unitGroup.findViewById(gigabyteButtonId).setEnabled(enabled);
        valueText.setEnabled(enabled);
    }

    private void configureSlider(long requestedBytes) {
        float maximumValue = Math.max(1.0f, (float) (maximumBytes / unitBytes));
        float selectedValue = Math.round(Math.min(requestedBytes, maximumBytes)
                / (double) unitBytes);
        slider.setValue(0.0f);
        slider.setValueFrom(0.0f);
        slider.setValueTo(maximumValue);
        slider.setStepSize(1.0f);
        slider.setValue(Math.max(0.0f, Math.min(selectedValue, maximumValue)));
        slider.setLabelFormatter(value -> SizeParser.format(
                Math.min(maximumBytes, Math.round(value * unitBytes))));
        updateValueText();
    }

    private void updateValueText() {
        valueText.setText(SizeParser.format(getBytes()));
    }
}
