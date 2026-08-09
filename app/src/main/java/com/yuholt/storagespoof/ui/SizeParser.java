package com.yuholt.storagespoof.ui;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SizeParser {
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)\\s*(B|KB|KIB|MB|MIB|GB|GIB|TB|TIB)?$",
            Pattern.CASE_INSENSITIVE);

    private SizeParser() {
    }

    public static String format(long bytes) {
        if (bytes == 0L) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        if (Math.rint(value) == value) {
            return String.format(Locale.ROOT, "%.0f %s", value, units[unitIndex]);
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }

    public static long parse(String input) {
        String normalized = input == null ? "" : input.trim();
        Matcher matcher = SIZE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid size");
        }

        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        long multiplier = switch (unit == null ? "B" : unit.toUpperCase(Locale.ROOT)) {
            case "KB", "KIB" -> 1L << 10;
            case "MB", "MIB" -> 1L << 20;
            case "GB", "GIB" -> 1L << 30;
            case "TB", "TIB" -> 1L << 40;
            default -> 1L;
        };
        double bytes = value * multiplier;
        if (!Double.isFinite(bytes) || bytes > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Size is too large");
        }
        return Math.round(bytes);
    }
}
