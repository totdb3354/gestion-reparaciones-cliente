package com.reparaciones.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FechaUtils {

    private static final ZoneId UTC    = ZoneId.of("UTC");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Convierte una fecha UTC del servidor a hora de Madrid y la formatea. */
    public static String formatear(LocalDateTime utc, DateTimeFormatter fmt) {
        if (utc == null) return "";
        return utc.atZone(UTC).withZoneSameInstant(MADRID).format(fmt);
    }

    /** Convierte una fecha UTC del servidor a LocalDate en hora de Madrid. */
    public static LocalDate toLocalDate(LocalDateTime utc) {
        if (utc == null) return null;
        return utc.atZone(UTC).withZoneSameInstant(MADRID).toLocalDate();
    }
}
