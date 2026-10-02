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
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.settings.MemorySettings;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How many names a document asks values for, and how many of the names its replies declare are
 * among them, measured over fifty real specifications.
 *
 * <p>The memory of what an API returned keeps a value only under a name some request asks for, and
 * keeps only so many names. These numbers say two things about that. How far the most demanding
 * document is from the limit, which is what makes the limit a guard rather than something a run
 * meets. And how much of what replies are declared to carry the memory now leaves out - names no
 * request will ever ask for, which before took up room as readily as the rest.
 *
 * <p>The numbers move when the corpus does. They are pinned so that a change in them is something
 * somebody decided rather than something that happened.
 */
class NamesAskedForAcrossTheCorpusTest {

    /** How far into a reply's declared shape the count goes, which is as far as a reply is read. */
    private static final int AS_DEEP_AS_A_REPLY_IS_READ =
            MemorySettings.defaults().asDeepAsAReplyIsRead();

    @Test
    @DisplayName("no document asks for anywhere near as many names as the memory may keep")
    void the_most_demanding_document_is_far_below_the_limit() {
        int most = 0;
        String which = "";
        for (Path document : TheCorpus.all()) {
            int asked = NamesTheDocumentAsksFor.in(TheCorpus.parse(document)).size();
            if (asked > most) {
                most = asked;
                which = document.getParent().getFileName().toString();
            }
        }

        assertThat(which + " " + most)
                .describedAs("the document asking for the most names, and how many")
                .isEqualTo("GitHub 394");
        assertThat(most).isLessThan(MemorySettings.defaults().mostNames() / 4);
    }

    @Test
    @DisplayName("of the names replies are declared to carry, this many are names a request asks for")
    void what_the_memory_leaves_out() {
        int declared = 0;
        int askedFor = 0;
        for (Path document : TheCorpus.all()) {
            ApiModel model = TheCorpus.parse(document);
            NamesTheDocumentAsksFor names = NamesTheDocumentAsksFor.in(model);
            Set<String> inReplies = new HashSet<>();
            for (Operation operation : model.operations()) {
                for (ResponseModel response : operation.responses()) {
                    if (response.status().startsWith("2")) {
                        response.schemaFor("application/json").ifPresent(schema ->
                                namesIn(schema, model, inReplies, new HashSet<>(), 0));
                    }
                }
            }
            declared += inReplies.size();
            askedFor += (int) inReplies.stream()
                    .filter(name -> !names.answeredBy(name).isEmpty()).count();
        }

        assertThat(askedFor + " of " + declared)
                .describedAs("names declared in 2XX replies, per document, that some request of "
                        + "the same document asks for: the rest, four in five, were kept "
                        + "before and never asked for")
                .isEqualTo("743 of 4060");
    }

    private static void namesIn(CanonicalSchema schema, ApiModel model, Set<String> into,
            Set<String> entered, int depth) {
        if (depth > AS_DEEP_AS_A_REPLY_IS_READ) {
            return;
        }
        switch (schema) {
            case SchemaReference reference -> {
                if (entered.add(reference.name())) {
                    model.resolve(reference).ifPresent(named ->
                            namesIn(named, model, into, entered, depth));
                }
            }
            case ObjectSchema object -> object.properties().forEach((name, property) -> {
                into.add(name);
                namesIn(property, model, into, entered, depth + 1);
            });
            case ArraySchema list -> namesIn(list.items(), model, into, entered, depth + 1);
            case ChoiceSchema choice -> choice.alternatives().forEach(one ->
                    namesIn(one, model, into, entered, depth + 1));
            default -> { }
        }
    }
}
