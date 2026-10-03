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
package io.restest.core.oracle;

import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.model.OperationId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing an oracle decided was wrong, together with the evidence for it.
 *
 * <p>A finding is what a person reads after a run: what kind of fault it is, which operation it
 * happened on, a line saying what went wrong, and - when there is more to say - the particular
 * points of disagreement, such as which field of a reply did not match its declared shape.
 *
 * <p>It carries the whole {@link Interaction} rather than a reference to one, so a finding is
 * complete on its own. A report can print the exact request that caused it, and the {@code curl}
 * command that reproduces it, without having to go and look anything up. Findings are rare next to
 * requests, so keeping the evidence beside the verdict costs little.
 *
 * @param category  which kind of fault this is, by its catalogue number
 * @param context  an optional word telling apart two findings of the same kind, where a catalogue
 *                 entry covers more than one way a thing can go wrong. Empty here; the oracles that
 *                 need it arrive later
 * @param interaction the attempt that produced it: the request sent and the reply received
 * @param summary  one line, in plain words, saying what is wrong
 * @param details  the particular disagreements, if there are any to list
 */
public record Finding(
        FaultCategory category,
        Optional<String> context,
        Interaction interaction,
        String summary,
        List<String> details) {

    public Finding {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(interaction, "interaction");
        Objects.requireNonNull(summary, "summary");
        if (summary.isBlank()) {
            throw new IllegalArgumentException("a finding must say what is wrong; a blank summary "
                    + "is a report nobody can act on");
        }
        context.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("a finding's context either says something or "
                        + "is absent; a blank one only looks like it says something");
            }
        });
        details = List.copyOf(details);
    }

    /** A finding with nothing further to list beyond its one-line summary. */
    public static Finding of(FaultCategory category, Interaction interaction, String summary) {
        return new Finding(category, Optional.empty(), interaction, summary, List.of());
    }

    /** The same finding, with the particular points of disagreement listed. */
    public Finding withDetails(List<String> value) {
        return new Finding(category, context, interaction, summary, value);
    }

    /** The same finding, distinguished from others of its kind by one word. */
    public Finding withContext(String value) {
        return new Finding(category, Optional.of(Objects.requireNonNull(value, "value")),
                interaction, summary, details);
    }

    /** The operation this happened on. */
    public OperationId operation() {
        return interaction.testCase().operation();
    }

    /** The attempt this happened on, by its identifier - the same one the stored run uses. */
    public InteractionId interactionId() {
        return interaction.id();
    }

    /** How the catalogue writes this fault: {@code F100:HTTP Status 500}. */
    public String label() {
        return category.label();
    }
}
