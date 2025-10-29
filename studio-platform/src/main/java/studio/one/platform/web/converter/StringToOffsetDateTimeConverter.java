package studio.one.platform.web.converter;

import org.springframework.core.convert.converter.Converter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public class StringToOffsetDateTimeConverter implements Converter<String, OffsetDateTime> {

    private static final DateTimeFormatter FLEX = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE) // yyyy-MM-dd
            .appendLiteral('T')
            .appendPattern("HH:mm") // HH:mm
            .optionalStart().appendPattern(":ss").optionalEnd() // [:ss]
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd() // [.SSS...]
            .appendOffsetId() // Z or +09:00
            .toFormatter();

    @Override
    public OffsetDateTime convert(String source) {
        if (source == null || source.isBlank())
            return null;
        return OffsetDateTime.parse(source.trim(), FLEX);
    }
}
