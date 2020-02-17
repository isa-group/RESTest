package com.atlassian.oai.validator.schema;

import com.atlassian.oai.validator.parameter.format.CustomDateTimeFormatter;
import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ListProcessingReport;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.properties.DateProperty;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.StringProperty;
import io.swagger.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.atlassian.oai.validator.schema.SwaggerV20Library.OAI_V2_METASCHEMA_URI;
import static com.atlassian.oai.validator.schema.SwaggerV20Library.schemaFactory;
import static com.atlassian.oai.validator.util.StringUtils.capitalise;
import static com.atlassian.oai.validator.util.StringUtils.quote;
import static com.atlassian.oai.validator.util.StringUtils.requireNonEmpty;
import static java.util.Objects.requireNonNull;

/**
 * Validate a value against the schema defined in a Swagger/OpenAPI specification.
 * <p>
 * Supports validation of properties and request/response bodies, and supports schema references.
 */
public class SchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

    public static final String ADDITIONAL_PROPERTIES_KEY = "validation.schema.additionalProperties";
    public static final String INVALID_JSON_KEY = "validation.schema.invalidJson";
    public static final String UNKNOWN_ERROR_KEY = "validation.schema.unknownError";

    private static final String ADDITIONAL_PROPERTIES_FIELD = "additionalProperties";
    private static final String DISCRIMINATOR_FIELD = "discriminator";
    private static final String DEFINITIONS_FIELD = "definitions";
    private static final String ALLOF_FIELD = "allOf";
    private static final String SCHEMA_REF_FIELD = "$schema";

    private final Swagger api;
    private JsonNode definitions;
    private boolean definitionsContainAllOf;
    private final MessageResolver messages;

    /**
     * Build a new validator with no API specification.
     * <p>
     * This will not perform any validation of $ref references that reference local definitions.
     *
     * @param messages The message resolver to use
     */
    public SchemaValidator(@Nonnull final MessageResolver messages) {
        this(null, messages);
    }

    /**
     * Build a new validator for the given API specification.
     *
     * @param api      The API to build the validator for. If provided, is used to retrieve schema definitions
     *                 for use in references.
     * @param messages The message resolver to use.
     */
    public SchemaValidator(@Nullable final Swagger api, @Nonnull final MessageResolver messages) {
        this.api = api;
        this.messages = requireNonNull(messages, "A message resolver is required");
    }

    /**
     * Validate the given value against the given property schema. If the schema is null then any json is valid.
     *
     * @param value  The value to validate
     * @param schema The property schema to validate the value against
     * @return A validation report containing accumulated validation errors
     */
    @Nonnull
    public ValidationReport validate(@Nonnull final String value, @Nullable final Property schema) {
        return doValidate(value, schema);
    }

    /**
     * Validate the given value against the given model schema. If the schema is null then any json is valid.
     *
     * @param value  The value to validate
     * @param schema The model schema to validate the value against
     * @return A validation report containing accumulated validation errors
     */
    @Nonnull
    public ValidationReport validate(@Nonnull final String value, @Nullable final Model schema) {
        return doValidate(value, schema);
    }

    @Nonnull
    private ValidationReport doValidate(@Nonnull final String value, @Nullable final Object schema) {
        requireNonEmpty(value, "A value is required");

        if (schema == null) {
            return ValidationReport.empty();
        }

        try {
            final JsonNode schemaObject, content;
            try {
                schemaObject = readSchema(schema);
                content = readContent(value, schema);

                checkForKnownGotchasAndLogMessage(schemaObject);
            } catch (final JsonParseException e) {
                return ValidationReport.singleton(messages.get(INVALID_JSON_KEY, e.getMessage()));
            }

            final ListProcessingReport processingReport;
            try {
                processingReport = (ListProcessingReport) schemaFactory().getJsonSchema(schemaObject)
                        .validate(content, true);
            } catch (final ProcessingException e) {
                return getProcessingMessage(e.getProcessingMessage(), "processingError");
            }

            if ((processingReport != null) && !processingReport.isSuccess()) {
                return StreamSupport.stream(processingReport.spliterator(), false)
                        .map(pm -> getProcessingMessage(pm, null))
                        .reduce(ValidationReport.empty(), ValidationReport::merge);
            }
            return ValidationReport.empty();
        } catch (final Exception e) {
            return ValidationReport.singleton(messages.get(UNKNOWN_ERROR_KEY, e.getMessage()));
        }
    }

    private JsonNode readSchema(@Nonnull final Object schema) throws IOException {
        final JsonNode schemaObject = Json.mapper().readTree(Json.pretty(schema));
        setupSchemaDefinitionRefs(schemaObject);
        return schemaObject;
    }

    private void setupSchemaDefinitionRefs(final JsonNode schemaObject) throws IOException {
        final ObjectNode objectNode = (ObjectNode) schemaObject;

        objectNode.put(SCHEMA_REF_FIELD, OAI_V2_METASCHEMA_URI);
        if (additionalPropertiesValidationEnabled()) {
            objectNode.set(ADDITIONAL_PROPERTIES_FIELD, BooleanNode.getFalse());
        }

        if (api != null) {
            if (this.definitions == null) {
                this.definitions = api.getDefinitions() == null ?
                        Json.mapper().createObjectNode() :
                        Json.mapper().readTree(Json.pretty(api.getDefinitions()));
                this.definitions.forEach(n -> {
                    if (additionalPropertiesValidationEnabled()) {
                        // Explicitly disable additionalProperties
                        // Calling code can choose what level to emit this failure at using
                        // validation.schema.additionalProperties
                        if (!n.has(ADDITIONAL_PROPERTIES_FIELD) && !n.has(DISCRIMINATOR_FIELD)) {
                            ((ObjectNode) n).set(ADDITIONAL_PROPERTIES_FIELD, BooleanNode.getFalse());
                        }
                    }
                    if (n.has(ALLOF_FIELD)) {
                        this.definitionsContainAllOf = true;
                    }
                });
            }
            objectNode.set(DEFINITIONS_FIELD, this.definitions);
        }
    }

    private JsonNode readContent(@Nonnull final String value, @Nonnull final Object schema) throws IOException {
        String normalisedValue = value;
        if (schema instanceof StringProperty
                || schema instanceof DateProperty) {
            normalisedValue = quote(value);
        } else if (schema instanceof DateTimeProperty) {
            normalisedValue = quote(normaliseDateTime(value));
        }
        return removeNullValuesFromTree(Json.mapper().readTree(normalisedValue));
    }

    private String normaliseDateTime(final String dateTime) {
        // Re-format DateTime since Schema validator doesn't accept some valid RFC3339 date-times and throws:
        // ERROR - String "1996-12-19T16:39:57-08:00" is invalid against requested date format(s)
        // [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,12}Z]: []
        String formatedDateTime = dateTime;
        try {
            final LocalDateTime rfc3339dt = LocalDateTime.parse(dateTime, CustomDateTimeFormatter.getRFC3339Formatter());
            formatedDateTime = rfc3339dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
            //CHECKSTYLE:OFF EmptyCatchBlock
        } catch (final DateTimeParseException e) {
            // Could not parse to RFC3339 format. Schema validator will throw the appropriate error
        }
        //CHECKSTYLE:ON
        return formatedDateTime;
    }

    /**
     * Removes null values from the given <code>JsonNode</code> and its sub-nodes.
     * <p>
     * Traverses arrays and objects.
     */
    private JsonNode removeNullValuesFromTree(final JsonNode node) {
        final JsonNode result = node.deepCopy();

        final Deque<JsonNode> toClean = new ArrayDeque<>();
        toClean.push(result);

        while (!toClean.isEmpty()) {
            final JsonNode n = toClean.pop();
            if (n.isObject()) {
                final Iterator<Map.Entry<String, JsonNode>> fields = n.fields();
                while (fields.hasNext()) {
                    final JsonNode field = fields.next().getValue();
                    if (field.isNull()) {
                        fields.remove();
                    } else if (field.isObject() || field.isArray()) {
                        toClean.push(field);
                    }
                }
            } else if (n.isArray()) {
                final Iterator<JsonNode> elements = n.elements();
                while (elements.hasNext()) {
                    final JsonNode e = elements.next();
                    if (e.isNull()) {
                        elements.remove();
                    } else if (e.isObject() || e.isArray()) {
                        toClean.push(e);
                    }
                }
            }
        }

        return result;
    }

    private boolean additionalPropertiesValidationEnabled() {
        return !messages.isIgnored(ADDITIONAL_PROPERTIES_KEY);
    }

    private void checkForKnownGotchasAndLogMessage(final JsonNode schemaObject) {
        if (additionalPropertiesValidationEnabled() && (schemaObject.has(ALLOF_FIELD) || definitionsContainAllOf)) {
            log.info("Note: Schema uses the 'allOf' keyword. " +
                    "Validation of 'additionalProperties' may fail with unexpected errors. " +
                    "See the project README FAQ for more information.");
        }
    }

    private ValidationReport getProcessingMessage(final ProcessingMessage pm,
                                                  final String keywordOverride) {
        final JsonNode processingMessage = pm.asJson();
        final String validationKeyword = keywordOverride != null ? keywordOverride : processingMessage.get("keyword").textValue();
        final String pointer = processingMessage.has("instance") ? processingMessage.get("instance").get("pointer").textValue() : "";

        final List<String> subReports = new ArrayList<>();
        if (processingMessage.has("reports")) {
            final JsonNode reports = processingMessage.get("reports");
            reports.fields().forEachRemaining(field -> {
                field.getValue().elements().forEachRemaining(report -> {
                    subReports.add(field.getKey() + ": " + capitalise(report.get("message").textValue()));
                });
            });
        }

        final String message =
                (pointer.isEmpty() ? "" : "[Path '" + pointer + "'] ")
                        + capitalise(pm.getMessage());

        return ValidationReport.singleton(
                messages.create("validation.schema." + validationKeyword, message, subReports.toArray(new String[0]))
        );
    }

}
