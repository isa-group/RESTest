package es.us.isa.restest.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class SchemaManager {

    private SchemaManager() {}

    public static Schema<?> generateFullyResolvedObjectSchema(Schema<?> schema, OpenAPI spec) {
        Schema<?> resolvedSchema = resolveSchema(schema, spec);
        Schema copy = new Schema();

        prePopulateSchema(resolvedSchema, copy);

        Map<String, Schema> properties = null;
        if (resolvedSchema.getProperties() != null) {
            properties = new HashMap<>();

            for (Map.Entry<String, Schema> entry: resolvedSchema.getProperties().entrySet()) {
                Schema copiedSchema;
                Schema<?> entryResolvedSchema = resolveSchema(entry.getValue(), spec);
                if (entryResolvedSchema.getType() != null) {
                    if (entryResolvedSchema.getType().equals("array"))
                        copiedSchema = generateFullyResolvedArraySchema((ArraySchema) entryResolvedSchema, spec);
                    else
                        copiedSchema = generateFullyResolvedSchema(entryResolvedSchema, spec);
                    properties.put(entry.getKey(), copiedSchema);
                }
            }
        }
        copy.setProperties(properties);

        return copy;
    }

    /**
     * No support for anyOf, oneOf. Possible support for allOf.
     * <br>
     * <br>
     * Given a schema, it generates a duplicate with all properties resolved (i.e.,
     * without "ref" attributes).
     */
    public static Schema<?> generateFullyResolvedSchema(Schema<?> schema, OpenAPI spec) {
        Schema<?> resolvedSchema = resolveSchema(schema, spec);
        if ("array".equals(resolvedSchema.getType()))
            return generateFullyResolvedArraySchema((ArraySchema) resolvedSchema, spec);
        else
            return generateFullyResolvedObjectSchema(resolvedSchema, spec);
    }

    public static ArraySchema generateFullyResolvedArraySchema(ArraySchema schema, OpenAPI spec) {
        ArraySchema resolvedSchema = (ArraySchema) resolveSchema(schema, spec);
        ArraySchema copy = new ArraySchema();

        prePopulateSchema(resolvedSchema, copy);

        Schema copiedSchema;
        Schema<?> itemsSchema = resolveSchema(resolvedSchema.getItems(), spec);
        if (itemsSchema.getType() != null) {
            if (itemsSchema.getType().equals("array"))
                copiedSchema = generateFullyResolvedArraySchema((ArraySchema) itemsSchema, spec);
            else
                copiedSchema = generateFullyResolvedSchema(itemsSchema, spec);
            copy.setItems(copiedSchema);
        }

        return copy;
    }

    public static void prePopulateSchema(Schema resolvedSchema, Schema<?> copy) {
        copy.set$ref(resolvedSchema.get$ref());
        copy.setAdditionalProperties(resolvedSchema.getAdditionalProperties());
        copy.setDefault(resolvedSchema.getDefault());
        copy.setDeprecated(resolvedSchema.getDeprecated());
        copy.setDescription(resolvedSchema.getDescription());
        copy.setDiscriminator(resolvedSchema.getDiscriminator());
        copy.setEnum(resolvedSchema.getEnum());
        copy.setExample(resolvedSchema.getExample());
        copy.setExclusiveMaximum(resolvedSchema.getExclusiveMaximum());
        copy.setExclusiveMinimum(resolvedSchema.getExclusiveMinimum());
        copy.setExtensions(resolvedSchema.getExtensions());
        copy.setExternalDocs(resolvedSchema.getExternalDocs());
        copy.setFormat(resolvedSchema.getFormat());
        copy.setMaximum(resolvedSchema.getMaximum());
        copy.setMaxItems(resolvedSchema.getMaxItems());
        copy.setMaxLength(resolvedSchema.getMaxLength());
        copy.setMaxProperties(resolvedSchema.getMaxProperties());
        copy.setMinimum(resolvedSchema.getMinimum());
        copy.setMinItems(resolvedSchema.getMinItems());
        copy.setMinLength(resolvedSchema.getMinLength());
        copy.setMinProperties(resolvedSchema.getMinProperties());
        copy.setMultipleOf(resolvedSchema.getMultipleOf());
        copy.setName(resolvedSchema.getName());
        copy.setNot(resolvedSchema.getNot());
        copy.setNullable(resolvedSchema.getNullable());
        copy.setPattern(resolvedSchema.getPattern());
        copy.setReadOnly(resolvedSchema.getReadOnly());
        copy.setRequired(resolvedSchema.getRequired());
        copy.setTitle(resolvedSchema.getTitle());
        copy.setType(resolvedSchema.getType());
        copy.setUniqueItems(resolvedSchema.getUniqueItems());
        copy.setWriteOnly(resolvedSchema.getWriteOnly());
        copy.setXml(resolvedSchema.getXml());
    }

    /**
     * Given a schema, it returns the same schema if it is already resolved (i.e.,
     * contains properties and has no ref attribute), or the resolved schema corresponding
     * to the ref attribute.
     */
    public static Schema<?> resolveSchema(Schema<?> schema, OpenAPI spec) {
        Schema resolvedSchema = schema;
        while (resolvedSchema.get$ref() != null) {
            resolvedSchema = spec.getComponents().getSchemas().get(resolvedSchema.get$ref().replace("#/components/schemas/", ""));
        }
        return resolvedSchema;
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
