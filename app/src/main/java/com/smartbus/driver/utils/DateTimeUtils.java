package com.smartbus.driver.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class DateTimeUtils {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter DISPLAY_SHORT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter[] PARSERS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private DateTimeUtils() {
    }

    public static String formatDisplay(String raw) {
        LocalDateTime dateTime = parse(raw);
        if (dateTime == null) {
            return raw != null && !raw.isEmpty() ? raw : "—";
        }
        return dateTime.format(DISPLAY);
    }

    public static String formatShort(String raw) {
        LocalDateTime dateTime = parse(raw);
        if (dateTime == null) {
            return raw != null && !raw.isEmpty() ? raw : "—";
        }
        return dateTime.format(DISPLAY_SHORT);
    }

    public static String formatRange(String startedAt, String endedAt) {
        return formatDisplay(startedAt) + " → " + formatDisplay(endedAt);
    }

    public static String formatDuration(String startedAt, String endedAt) {
        LocalDateTime start = parse(startedAt);
        LocalDateTime end = parse(endedAt);
        if (start == null || end == null) {
            return "—";
        }
        long minutes = Math.max(0, Duration.between(start, end).toMinutes());
        long hours = minutes / 60;
        long remain = minutes % 60;
        if (hours > 0) {
            return hours + " giờ " + remain + " phút";
        }
        return remain + " phút";
    }

    public static String formatEtaFromMeters(double meters) {
        // Ước lượng ~25 km/h trong nội đô.
        int minutes = Math.max(1, (int) Math.ceil(meters / 416.67));
        int distance = (int) Math.round(meters);
        return "Còn ~" + minutes + " phút (" + distance + " m)";
    }

    private static LocalDateTime parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through to other formats.
        }
        for (DateTimeFormatter formatter : PARSERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next parser.
            }
        }
        return null;
    }
}
