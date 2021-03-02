package es.us.isa.restest.util;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

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
}
