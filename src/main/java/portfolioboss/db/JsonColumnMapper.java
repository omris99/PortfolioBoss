package portfolioboss.db;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * How Hibernate writes a record into a {@code JSONB} column ({@code stock_analysis.result}) and reads it back, named in
 * {@code application.properties} ({@code hibernate.type.json_format_mapper}). Left to itself Hibernate picks one of the
 * two Jacksons on the classpath — the Anthropic SDK brings the older one — with that version's settings, and stores a
 * date as {@code [2026, 10, 5]}. This one is the newer Jackson, the API's, with its own defaults: {@code "2026-10-05"},
 * whichever Jackson the libraries bring next. Hibernate's own class for it can't be extended ({@code final}), so this
 * one hands every call to it.
 */
public class JsonColumnMapper implements FormatMapper {

    private final FormatMapper jacksonMapper = new Jackson3JsonFormatMapper(JsonMapper.builder().build());

    /** Hibernate creates it from its name in {@code application.properties}. */
    public JsonColumnMapper() {
    }

    @Override
    public <T> T fromString(CharSequence json, JavaType<T> javaType, WrapperOptions options) {
        return jacksonMapper.fromString(json, javaType, options);
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions options) {
        return jacksonMapper.toString(value, javaType, options);
    }
}
