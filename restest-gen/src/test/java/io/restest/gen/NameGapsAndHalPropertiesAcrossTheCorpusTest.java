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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How many gaps in web addresses are named for several identifiers or for a thing's name, and how
 * many request bodies declare HAL's own properties, measured over the corpus of real
 * specifications.
 *
 * <p>The three numbers say how far each change can reach on documents other than the ones it was
 * found on. A gap named for several identifiers, such as {@code {ids}}, is in none of them: it was
 * found on an API the corpus does not hold. A gap named for a thing's name - {@code {productName}},
 * {@code {topic_name}}, or {@code {name}} on its own - is in five of them. And no request body in
 * the corpus declares {@code _links} or {@code _embedded}, so taking them out costs no document a
 * property it asks for.
 *
 * <p>The numbers move when the corpus does. They are pinned so that a change in them is something
 * somebody decided rather than something that happened.
 */
class NameGapsAndHalPropertiesAcrossTheCorpusTest {

    /** How far into a shape the count goes, which is further than any body is ever built. */
    private static final int AS_DEEP_AS_IT_GOES = 16;

    @Test
    @DisplayName("gaps named for several identifiers are in no document, gaps named for a name in "
            + "five")
    void gaps_named_for_identifiers_and_names() {
        int documents = 0;
        int gaps = 0;
        int plural = 0;
        Map<String, Integer> namesByApi = new TreeMap<>();
        for (Path document : TheCorpus.all()) {
            documents++;
            ApiModel model = TheCorpus.parse(document);
            String api = document.getParent().getFileName().toString();
            for (Operation operation : model.operations()) {
                for (Parameter parameter : operation.parameters()) {
                    if (parameter.location() != ParameterLocation.PATH) {
                        continue;
                    }
                    gaps++;
                    if (ObservedValues.looksLikeSeveralIdentifiers(parameter.name())) {
                        plural++;
                    }
                    if (ObservedValues.looksLikeAName(parameter.name())
                            || parameter.name().equalsIgnoreCase(ObservedValues.A_THINGS_NAME)) {
                        namesByApi.merge(api, 1, Integer::sum);
                    }
                }
            }
        }

        assertThat(documents).isEqualTo(46);
        assertThat(gaps)
                .describedAs("gaps in the addresses of the whole corpus, counting each gap once "
                        + "for every operation whose address has it")
                .isEqualTo(1_838);
        assertThat(plural)
                .describedAs("gaps named for several identifiers, such as {ids} or {petIds}")
                .isZero();
        assertThat(namesByApi.values().stream().mapToInt(Integer::intValue).sum())
                .describedAs("gaps named for a thing's name, such as {productName}, or {name} on "
                        + "its own")
                .isEqualTo(106);
        assertThat(namesByApi)
                .describedAs("and where they are")
                .containsOnlyKeys("FeaturesService", "GitHub", "Restcountries", "flight-search",
                        "kafka-rest-proxy");
    }

    @Test
    @DisplayName("no request body in the corpus declares _links or _embedded")
    void no_body_declares_hal_s_own_properties() {
        int bodies = 0;
        List<String> declaring = new ArrayList<>();
        for (Path document : TheCorpus.all()) {
            ApiModel model = TheCorpus.parse(document);
            for (Operation operation : model.operations()) {
                if (operation.requestBody().isEmpty()) {
                    continue;
                }
                bodies++;
                for (String mediaType : operation.requestBody().get().mediaTypes()) {
                    Set<String> names = new LinkedHashSet<>();
                    operation.requestBody().get().schemaFor(mediaType).ifPresent(schema ->
                            names(schema, model, 0, new LinkedHashSet<>(), names));
                    if (names.stream().anyMatch(HalProperties.RESERVED::contains)) {
                        declaring.add(model.title() + " " + operation.id().value());
                    }
                }
            }
        }

        assertThat(bodies).describedAs("operations that take a body").isEqualTo(340);
        assertThat(declaring)
                .describedAs("so taking those two out costs no document a property it asks for")
                .isEmpty();
    }

    /** Every property name a shape declares, at any depth. */
    private static void names(CanonicalSchema schema, ApiModel model, int depth,
            Set<String> following, Set<String> into) {
        CanonicalSchema here = schema;
        if (here instanceof SchemaReference reference) {
            here = following.add(reference.name()) ? model.resolve(reference).orElse(null) : null;
        }
        if (here == null || depth > AS_DEEP_AS_IT_GOES) {
            return;
        }
        switch (here) {
            case ObjectSchema object -> {
                into.addAll(object.properties().keySet());
                object.properties().values().forEach(property -> names(property, model,
                        depth + 1, new LinkedHashSet<>(following), into));
                object.additionalProperties().ifPresent(property -> names(property, model,
                        depth + 1, new LinkedHashSet<>(following), into));
            }
            case ArraySchema list ->
                    names(list.items(), model, depth + 1, new LinkedHashSet<>(following), into);
            case ChoiceSchema choice -> choice.alternatives().forEach(alternative ->
                    names(alternative, model, depth + 1, new LinkedHashSet<>(following), into));
            default -> { }
        }
    }
}
