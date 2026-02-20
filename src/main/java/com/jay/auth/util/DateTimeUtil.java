package com.jay.auth.util;

import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {

    public static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private DateTimeUtil() {}
}
