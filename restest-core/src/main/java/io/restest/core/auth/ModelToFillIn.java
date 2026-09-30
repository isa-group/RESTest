/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.core.auth;

import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.BodyContent;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The API as the part of RESTest that invents values should see it when a key fills some of its
 * inputs: those inputs are simply not there.
 *
 * <p>An operation may declare an input under the name its key goes by - LanguageTool asks for
 * {@code apiKey} as a field of the form it takes, the pet shop for {@code api_key} as a header. Left
 * in, the part that invents values would make something up for it, a change to a request could pick
 * it, and the key added as the request leaves would land beside the made-up value or on top of it.
 * Taken out here, nothing is invented for it and nothing can change it, and every other input is
 * filled exactly as before.
 *
 * <p>For a field of a form, what is taken out is the property of the form's shape, the requirement
 * that it be there, and the property inside every sample of a whole form the document writes down;
 * and the smallest and largest number of fields the form allows go down by the fields taken out,
 * since the key's field is added back to every request. A form whose shape is written once and
 * referred to by name gets a copy of that shape without the field, so the name is no longer attached
 * to it.
 */
final class ModelToFillIn {

    /** The one kind of body a key can be a field of: a web form. */
    static final String FORM = "application/x-www-form-urlencoded";

    /** How far a chain of named shapes is followed before it is taken to go round in a circle. */
    private static final int REFERENCES_FOLLOWED = 32;

    private ModelToFillIn() {
    }

    /** The model with every input the trims name left out, and every other operation as it was. */
    static ApiModel without(ApiModel model, Map<OperationId, CredentialPlan.Trim> trims) {
        List<Operation> operations = new ArrayList<>(model.operations().size());
        boolean changed = false;
        for (Operation operation : model.operations()) {
            CredentialPlan.Trim trim = trims.get(operation.id());
            Operation trimmed = trim == null ? operation : without(operation, trim, model);
            changed |= trimmed != operation;
            operations.add(trimmed);
        }
        return changed ? model.withOperations(operations) : model;
    }

    private static Operation without(Operation operation, CredentialPlan.Trim trim,
            ApiModel model) {
        Operation trimmed = operation;
        if (!trim.parameters().isEmpty()) {
            List<Parameter> kept = operation.parameters().stream()
                    .filter(parameter -> !trim.parameters()
                            .contains(parameter.location() + " " + parameter.name()))
                    .toList();
            trimmed = trimmed.withParameters(kept);
        }
        if (!trim.formFields().isEmpty() && operation.requestBody().isPresent()) {
            RequestBodyModel body = operation.requestBody().get();
            Map<String, BodyContent> content = new LinkedHashMap<>(body.content());
            BodyContent form = content.get(FORM);
            if (form != null) {
                content.put(FORM, new BodyContent(
                        without(form.schema(), trim.formFields(), model, 0),
                        form.examples().stream()
                                .map(example -> without(example, trim.formFields()))
                                .toList()));
                trimmed = trimmed.withRequestBody(
                        new RequestBodyModel(body.required(), content, body.description()));
            }
        }
        return trimmed;
    }

    /**
     * The names of the fields a form this operation accepts may have, where it accepts one: the
     * properties of the form's shape, the shape it names followed, and each of the shapes a choice
     * offers.
     */
    static Set<String> formFields(Operation operation, ApiModel model) {
        Optional<CanonicalSchema> shape = operation.requestBody()
                .map(RequestBodyModel::content)
                .map(content -> content.get(FORM))
                .map(BodyContent::schema);
        Set<String> fields = new LinkedHashSet<>();
        shape.ifPresent(schema -> collectFields(schema, model, fields, new HashSet<>(), 0));
        return fields;
    }

    private static void collectFields(CanonicalSchema schema, ApiModel model, Set<String> fields,
            Set<String> followed, int depth) {
        if (depth > REFERENCES_FOLLOWED) {
            return;
        }
        switch (schema) {
            case ObjectSchema object -> fields.addAll(object.properties().keySet());
            case SchemaReference reference -> {
                if (followed.add(reference.name())) {
                    model.resolve(reference).ifPresent(resolved ->
                            collectFields(resolved, model, fields, followed, depth + 1));
                }
            }
            case ChoiceSchema choice -> choice.alternatives().forEach(alternative ->
                    collectFields(alternative, model, fields, followed, depth + 1));
            default -> {
                // Any other shape is not a form with fields to name.
            }
        }
    }

    private static CanonicalSchema without(CanonicalSchema schema, Set<String> fields,
            ApiModel model, int depth) {
        if (depth > REFERENCES_FOLLOWED) {
            return schema;
        }
        return switch (schema) {
            case ObjectSchema object -> without(object, fields);
            case SchemaReference reference -> model.resolve(reference)
                    .map(resolved -> without(resolved, fields, model, depth + 1))
                    .orElse(schema);
            case ChoiceSchema choice -> new ChoiceSchema(without(choice.metadata(), fields),
                    choice.alternatives().stream()
                            .map(alternative -> without(alternative, fields, model, depth + 1))
                            .toList());
            default -> schema;
        };
    }

    private static ObjectSchema without(ObjectSchema object, Set<String> fields) {
        Map<String, CanonicalSchema> properties = new LinkedHashMap<>(object.properties());
        int removed = 0;
        for (String field : fields) {
            if (properties.remove(field) != null) {
                removed++;
            }
        }
        Set<String> required = new LinkedHashSet<>(object.required());
        required.removeAll(fields);
        int fewer = removed;
        return new ObjectSchema(without(object.metadata(), fields), properties, required,
                object.additionalProperties(),
                object.minProperties().map(fewest -> Math.max(0, fewest - fewer)),
                object.maxProperties().map(most -> Math.max(0, most - fewer)));
    }

    /** The samples, default and allowed values a shape writes down, each without the fields. */
    private static SchemaMetadata without(SchemaMetadata metadata, Set<String> fields) {
        SchemaMetadata trimmed = metadata
                .withEnumeration(metadata.enumeration().stream()
                        .map(value -> without(value, fields)).toList())
                .withExamples(metadata.examples().stream()
                        .map(value -> without(value, fields)).toList());
        return metadata.defaultValue()
                .map(value -> trimmed.withDefault(without(value, fields)))
                .orElse(trimmed);
    }

    /** A whole form written down, without the fields; anything that is not an object as it is. */
    private static JsonValue without(JsonValue value, Set<String> fields) {
        if (!(value instanceof JsonValue.JsonObject object)
                || fields.stream().noneMatch(field -> object.members().containsKey(field))) {
            return value;
        }
        Map<String, JsonValue> members = new LinkedHashMap<>(object.members());
        fields.forEach(members::remove);
        return JsonValue.object(members);
    }
}
