package com.atlassian.oai.validator.parameter.format;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * A custom {@link DateTimeFormatter} for date and time format defined in RFC3339.  
 * @see <a href="https://tools.ietf.org/html/rfc3339#section-5.6">RFC 3339 - Section 5.6</a>
 */
public class CustomDateTimeFormatter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER;

    private CustomDateTimeFormatter() {
    }    

    static {
        final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
             .appendPattern("yyyy-MM-dd")
             .appendLiteral('T')
             .appendPattern("HH:mm:ss")
             .optionalStart()
             .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
             .optionalEnd()
             .appendOffset("+HH:mm", "Z");
        DATE_TIME_FORMATTER = builder.toFormatter();
    }
    
    public static DateTimeFormatter getRFC3339Formatter() {
        return DATE_TIME_FORMATTER;
    }
}
