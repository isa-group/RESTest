package com.atlassian.oai.validator.schema;

import com.atlassian.oai.validator.schema.format.Base64Attribute;
import com.atlassian.oai.validator.schema.format.DoubleAttribute;
import com.atlassian.oai.validator.schema.format.FloatAttribute;
import com.atlassian.oai.validator.schema.format.Int32Attribute;
import com.atlassian.oai.validator.schema.format.Int64Attribute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.keyword.syntax.checkers.AbstractSyntaxChecker;
import com.github.fge.jsonschema.core.messages.JsonSchemaSyntaxMessageBundle;
import com.github.fge.jsonschema.core.processing.Processor;
import com.github.fge.jsonschema.core.report.ListProcessingReport;
import com.github.fge.jsonschema.core.report.ListReportProvider;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.github.fge.jsonschema.format.draftv3.DateAttribute;
import com.github.fge.jsonschema.keyword.digest.AbstractDigester;
import com.github.fge.jsonschema.keyword.validator.AbstractKeywordValidator;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.Keyword;
import com.github.fge.jsonschema.library.Library;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.messages.JsonSchemaValidationBundle;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.bundle.PropertiesBundle;
import com.github.fge.msgsimple.load.MessageBundleLoader;
import com.github.fge.msgsimple.load.MessageBundles;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.atlassian.oai.validator.util.StreamUtils.stream;
import static com.github.fge.msgsimple.load.MessageBundles.getBundle;
import static java.util.stream.Collectors.toList;

/**
 * Library that extends the JSON Schema v4 and adds the additional keywords introduced by the
 * Swagger/OAI v2.0 specification.
 */
public class SwaggerV20Library {

    private SwaggerV20Library() { }

    public static final String OAI_V2_METASCHEMA_URI = "https://openapis.org/specification/versions/2.0#";

    public static final String DISCRIMINATOR_KEYWORD = "discriminator";

    static Library get() {
        // The discriminator validator holds state that may persist in the event of a runtime exception etc.
        // Re-create the library to ensure this state doesn't persist between validations.
        return DraftV4Library.get().thaw()
                .addFormatAttribute("int32", Int32Attribute.getInstance())
                .addFormatAttribute("int64", Int64Attribute.getInstance())
                .addFormatAttribute("float", FloatAttribute.getInstance())
                .addFormatAttribute("double", DoubleAttribute.getInstance())
                .addFormatAttribute("date", DateAttribute.getInstance())
                .addFormatAttribute("byte", Base64Attribute.getInstance())
                .addKeyword(
                        Keyword.newBuilder(DISCRIMINATOR_KEYWORD)
                                .withSyntaxChecker(DiscriminatorSyntaxChecker.getInstance())
                                .withDigester(DiscriminatorDigester.getInstance())
                                .withValidatorClass(DiscriminatorKeywordValidator.class)
                                .freeze())
                .freeze();
    }

    /**
     * @return A {@link JsonSchemaFactory} instance configured with the Swagger/OAI V20 metaschema library suitable
     * for use in validating Swagger/OpenAPI documents
     */
    public static JsonSchemaFactory schemaFactory() {
        return JsonSchemaFactory
                .newBuilder()
                .setValidationConfiguration(
                        ValidationConfiguration.newBuilder()
                                .setDefaultLibrary(OAI_V2_METASCHEMA_URI, SwaggerV20Library.get())
                                .setSyntaxMessages(getBundle(SyntaxBundle.class))
                                .setValidationMessages(getBundle(ValidationBundle.class))
                                .freeze())
                .setReportProvider(
                        // Only emit ERROR and above from the JSON schema validation
                        new ListReportProvider(LogLevel.ERROR, LogLevel.FATAL))
                .freeze();
    }

    /**
     * @param logLevel log level
     * @param exceptionThreshold exception threshold
     * @return A {@link JsonSchemaFactory} instance configured with the Swagger/OAI V20 metaschema library suitable
     * for use in validating Swagger/OpenAPI documents
     */
    public static JsonSchemaFactory schemaFactory(final LogLevel logLevel, final LogLevel exceptionThreshold) {
        return JsonSchemaFactory
                .newBuilder()
                .setValidationConfiguration(
                        ValidationConfiguration.newBuilder()
                                .setDefaultLibrary(OAI_V2_METASCHEMA_URI, SwaggerV20Library.get())
                                .setSyntaxMessages(getBundle(SyntaxBundle.class))
                                .setValidationMessages(getBundle(ValidationBundle.class))
                                .freeze())
                .setReportProvider(
                        // Only emit ERROR and above from the JSON schema validation
                        new ListReportProvider(logLevel, exceptionThreshold))
                .freeze();
    }

    private static boolean arrayNodeContains(final JsonNode requiredProperties, final String element) {
        return stream(requiredProperties.elements()).anyMatch(e -> e.textValue().equals(element));
    }

    /**
     * Syntax checker for the <code>discriminator</code> keyword introduced by the Swagger/OpenAPI specification.
     *
     * @see <a href="http://swagger.io/specification/#composition-and-inheritance--polymorphism--83">Swagger specification</a>
     */
    public static class DiscriminatorSyntaxChecker extends AbstractSyntaxChecker {

        private static final DiscriminatorSyntaxChecker INSTANCE = new DiscriminatorSyntaxChecker();

        static DiscriminatorSyntaxChecker getInstance() {
            return INSTANCE;
        }

        DiscriminatorSyntaxChecker() {
            super(DISCRIMINATOR_KEYWORD, NodeType.STRING);
        }

        @Override
        protected void checkValue(final Collection<JsonPointer> pointers,
                                  final MessageBundle bundle,
                                  final ProcessingReport report,
                                  final SchemaTree tree) throws ProcessingException {

            final String discriminatorFieldName = getNode(tree).textValue();
            if (discriminatorFieldName.isEmpty()) {
                report.error(msg(tree, bundle, "err.swaggerv2.discriminator.empty"));
                return;
            }

            final JsonNode properties = tree.getNode().get("properties");
            final List<String> propertyNames = stream(properties.fieldNames()).collect(toList());
            if (!properties.has(discriminatorFieldName)) {
                report.error(msg(tree, bundle, "err.swaggerv2.discriminator.noProperty")
                        .putArgument("fieldName", discriminatorFieldName)
                        .putArgument("properties", propertyNames)
                );
                return;
            }

            final JsonNode property = properties.get(discriminatorFieldName);
            if (!property.has("type") ||
                    !property.get("type").textValue().equalsIgnoreCase("string")) {
                report.error(msg(tree, bundle, "err.swaggerv2.discriminator.wrongType")
                        .putArgument("fieldName", discriminatorFieldName)
                );
                return;
            }

            final JsonNode requiredProperties = tree.getNode().get("required");
            if (requiredProperties == null ||
                    !requiredProperties.isArray() ||
                    requiredProperties.size() == 0 ||
                    !arrayNodeContains(requiredProperties, discriminatorFieldName)) {
                report.error(msg(tree, bundle, "err.swaggerv2.discriminator.notRequired")
                        .putArgument("fieldName", discriminatorFieldName)
                );
                return;
            }
        }

        private ProcessingMessage msg(final SchemaTree tree, final MessageBundle bundle, final String key) {
            return newMsg(tree, bundle, key).put("key", key);
        }
    }

    /**
     * Digester for the <code>discriminator</code> keyword introduced by the Swagger/OpenAPI specification.
     */
    public static class DiscriminatorDigester extends AbstractDigester {

        private static final DiscriminatorDigester INSTANCE = new DiscriminatorDigester();

        public static DiscriminatorDigester getInstance() {
            return INSTANCE;
        }

        private DiscriminatorDigester() {
            super(DISCRIMINATOR_KEYWORD, NodeType.OBJECT);
        }

        @Override
        public JsonNode digest(final JsonNode schema) {
            final ObjectNode ret = FACTORY.objectNode();
            ret.put(keyword, schema.get(keyword).textValue());
            return ret;
        }
    }

    /**
     * Keyword validator for the <code>discriminator</code> keyword introduced by the Swagger/OpenAPI specification.
     *
     * @see <a href="http://swagger.io/specification/#composition-and-inheritance--polymorphism--83">Swagger specification</a>
     */
    public static class DiscriminatorKeywordValidator extends AbstractKeywordValidator {

        private final ThreadLocal<Set<ObjectNode>> visitedNodes = ThreadLocal.withInitial(HashSet::new);

        private final String fieldName;

        public DiscriminatorKeywordValidator(final JsonNode digest) {
            super(DISCRIMINATOR_KEYWORD);
            this.fieldName = digest.get(keyword).textValue();
        }

        @Override
        public void validate(final Processor<FullData, FullData> processor,
                             final ProcessingReport report,
                             final MessageBundle bundle,
                             final FullData data) throws ProcessingException {

            if (visitedNodes.get().contains(data.getSchema().getNode())) {
                // We have already validated the discriminator of this node.
                // We need to bail out to avoid a validation loop.
                visitedNodes.get().remove(data.getSchema().getNode());
                return;
            }

            final JsonNode discriminatorNode = data.getInstance().getNode().get(fieldName);
            if (discriminatorNode == null) {
                report.error(
                        msg(data, bundle, "err.swaggerv2.discriminator.missing")
                                .putArgument("discriminatorField", fieldName)
                );
                return;
            }
            if (!discriminatorNode.isTextual()) {
                report.error(
                        msg(data, bundle, "err.swaggerv2.discriminator.nonText")
                                .putArgument("discriminatorField", fieldName)
                );
                return;
            }
            if (discriminatorNode.textValue().isEmpty()) {
                report.error(
                        msg(data, bundle, "err.swaggerv2.discriminator.missing")
                                .putArgument("discriminatorField", fieldName)
                );
                return;
            }

            // Valid 'subclasses' should use allOf to reference the parent schema definition
            final SchemaTree schemaTree = data.getSchema();
            final String parentDefinitionRef = "#" + schemaTree.getPointer().toString();
            final Map<String, JsonNode> validDiscriminatorValues = new HashMap<>();
            data.getSchema().getBaseNode().get("definitions").fields().forEachRemaining(e -> {
                final JsonNode def = e.getValue();
                if (!def.has("allOf")) {
                    return;
                }

                def.get("allOf").forEach(n -> {
                    if (n.has("$ref") && n.get("$ref").textValue().equals(parentDefinitionRef)) {
                        validDiscriminatorValues.put(e.getKey(), def);
                    }
                });

            });

            if (!validDiscriminatorValues.containsKey(discriminatorNode.textValue())) {
                report.error(
                        msg(data, bundle, "err.swaggerv2.discriminator.invalid")
                                .putArgument("discriminatorField", fieldName)
                                .putArgument("value", discriminatorNode.textValue())
                                .putArgument("allowedValues", validDiscriminatorValues.keySet())
                );
            }

            final ListProcessingReport subReport = new ListProcessingReport(report.getLogLevel(), LogLevel.FATAL);
            final JsonPointer ptr = JsonPointer.of("definitions", discriminatorNode.textValue());
            final FullData newData = data.withSchema(schemaTree.setPointer(ptr));

            // Mark the node to ensure we don't get in a validation loop
            visitedNodes.get().add((ObjectNode) schemaTree.getNode());

            // Validate against the sub-schema
            processor.process(subReport, newData);

            if (!subReport.isSuccess()) {
                report.error(msg(data, bundle, "err.swaggerv2.discriminator.fail")
                        .putArgument("schema", ptr.toString())
                        .put("report", subReport.asJson()));
            }

        }

        @Override
        public String toString() {
            return keyword;
        }

        private ProcessingMessage msg(final FullData data, final MessageBundle bundle, final String key) {
            return newMsg(data, bundle, key).put("key", key);
        }
    }

    /**
     * Message bundle loader that appends messages for the Swagger V20 extensions to the standard
     * JSON Schema syntax bundle.
     */
    public static class SyntaxBundle implements MessageBundleLoader {

        private static final String PATH = "swagger/validation/schema-validation.properties";

        @Override
        public MessageBundle getBundle() {
            return MessageBundles
                    .getBundle(JsonSchemaSyntaxMessageBundle.class)
                    .thaw()
                    .appendBundle(PropertiesBundle.forPath(PATH))
                    .freeze();
        }
    }

    /**
     * Message bundle loader that appends messages for the Swagger V20 extensions to the standard
     * JSON Schema validation message bundle.
     */
    public static class ValidationBundle implements MessageBundleLoader {

        private static final String PATH = "swagger/validation/schema-validation.properties";

        @Override
        public MessageBundle getBundle() {
            return MessageBundles
                    .getBundle(JsonSchemaValidationBundle.class)
                    .thaw()
                    .appendBundle(PropertiesBundle.forPath(PATH))
                    .freeze();
        }
    }
}
