package br.gov.pb.der.netnotify.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy às HH:mm:ss");

    public static String format(LocalDateTime dateTime) {
        return dateTime.format(FORMATTER);
    }

}
