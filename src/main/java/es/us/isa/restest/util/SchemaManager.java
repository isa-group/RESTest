package es.us.isa.restest.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class SchemaManager {

    private SchemaManager() {}

    public static Schema copySchema(Schema schema) {
        Schema copy = new Schema();
        copy.set$ref(schema.get$ref());
        copy.setAdditionalProperties(schema.getAdditionalProperties());
        copy.setDefault(schema.getDefault());
        copy.setDeprecated(schema.getDeprecated());
        copy.setDescription(schema.getDescription());
        copy.setDiscriminator(schema.getDiscriminator());
        copy.setEnum(schema.getEnum());
        copy.setExample(schema.getExample());
        copy.setExclusiveMaximum(schema.getExclusiveMaximum());
        copy.setExclusiveMinimum(schema.getExclusiveMinimum());
        copy.setExtensions(schema.getExtensions());
        copy.setExternalDocs(schema.getExternalDocs());
        copy.setFormat(schema.getFormat());
        copy.setMaximum(schema.getMaximum());
        copy.setMaxItems(schema.getMaxItems());
        copy.setMaxLength(schema.getMaxLength());
        copy.setMaxProperties(schema.getMaxProperties());
        copy.setMinimum(schema.getMinimum());
        copy.setMinItems(schema.getMinItems());
        copy.setMinLength(schema.getMinLength());
        copy.setMinProperties(schema.getMinProperties());
        copy.setMultipleOf(schema.getMultipleOf());
        copy.setName(schema.getName());
        copy.setNot(schema.getNot());
        copy.setNullable(schema.getNullable());
        copy.setPattern(schema.getPattern());
        copy.setReadOnly(schema.getReadOnly());
        copy.setRequired(schema.getRequired());
        copy.setTitle(schema.getTitle());
        copy.setType(schema.getType());
        copy.setUniqueItems(schema.getUniqueItems());
        copy.setWriteOnly(schema.getWriteOnly());
        copy.setXml(schema.getXml());

        Map<String, Schema> properties = null;
        if (schema.getProperties() != null) {
            properties = new HashMap<>();

            for (Object o: schema.getProperties().entrySet()) {
                Map.Entry<String, Schema> entry = (Map.Entry<String, Schema>) o;
                Schema copiedSchema;
                if (entry.getValue().getType().equals("array")) {
                    copiedSchema = copyArraySchema((ArraySchema) entry.getValue());
                } else {
                    copiedSchema = copySchema(entry.getValue());
                }
                properties.put(entry.getKey(), copiedSchema);
            }
        }

        copy.setProperties(properties);

        return copy;
    }

    public static ArraySchema copyArraySchema(ArraySchema schema) {
        ArraySchema copy = new ArraySchema();
        Schema itemsCopy;
        if (schema.getItems().getType().equals("array")) {
            itemsCopy = copyArraySchema((ArraySchema) schema.getItems());
        } else {
            itemsCopy = copySchema(schema.getItems());
        }

        copy.setItems(itemsCopy);
        return copy;
    }

    public static JsonNode createValueNode(Object value, ObjectMapper mapper) {
        JsonNode node = null;

        if (value instanceof Number) {
            Number n = (Number) value;

            if (n instanceof Integer || n instanceof Long) {
                node = mapper.getNodeFactory().numberNode(n.longValue());
            } else if (n instanceof BigInteger) {
                node = mapper.getNodeFactory().numberNode((BigInteger) n);
            } else if (n instanceof Double || n instanceof Float) {
                node = mapper.getNodeFactory().numberNode(n.doubleValue());
            } else if (n instanceof BigDecimal) {
                node = mapper.getNodeFactory().numberNode((BigDecimal) n);
            }
        } else if (value instanceof Boolean) {
            node = mapper.getNodeFactory().booleanNode((Boolean) value);
        } else {
            node = mapper.getNodeFactory().textNode((String) value);
        }

        return node;
    }
}
