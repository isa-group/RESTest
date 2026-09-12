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
package io.restest.core.store;

import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import java.util.List;
import java.util.Optional;

/**
 * Where everything a run did to an API is kept, so that it can be looked at afterwards.
 *
 * <p>A test run is an experiment, and an experiment nobody can re-examine is worth very little. So
 * every attempt is written down in full - the request as it was sent, the reply as it came back, how
 * long it took, which test it belonged to, and where each value in it came from - and it stays there
 * when the run ends. That is what lets a person ask "show me everything that returned a server error"
 * hours later, what lets a new way of judging replies be tried against a run that already happened
 * without touching the API again, and what makes a report something that can be regenerated rather
 * than something that had to be captured while the run was alive.
 *
 * <p>A store is opened for a run and used until it ends. It is safe to use from several threads at
 * once, because requests are sent from many at once. Two stores can be open in the same program
 * without either seeing the other's interactions.
 *
 * <p>Everything here can throw {@link InteractionStoreException}, and nothing else does.
 */
public interface InteractionStore extends AutoCloseable {

    /**
     * Writes one interaction down.
     *
     * <p>Recording the same interaction twice is not an error: the second one replaces the first,
     * which is what makes a re-run of the same attempt after a crash produce one row rather than two.
     *
     * <p>The writing happens on the calling thread and is finished when this returns, so a caller
     * that must not wait for a disk - the loop that decides what to send next, above all - should
     * hand the interaction to something else to record rather than call this from there.
     *
     * @param interaction what was tried, sent, and came back. Never {@code null}
     */
    void record(Interaction interaction);

    /**
     * Every interaction matching the question, oldest first.
     *
     * @param query what to look for; {@link InteractionQuery#all()} for everything
     * @return the matching interactions, exactly as they were recorded
     */
    List<Interaction> find(InteractionQuery query);

    /**
     * How many interactions match the question.
     *
     * <p>Separate from {@link #find} because counting a hundred thousand interactions should not
     * mean building a hundred thousand of them in memory first.
     *
     * @param query what to count; {@link InteractionQuery#all()} for everything
     * @return how many there are
     */
    long count(InteractionQuery query);

    /**
     * One interaction by its identifier.
     *
     * @param id the identifier the interaction was recorded under
     * @return the interaction, or empty if this store has never seen it
     */
    Optional<Interaction> byId(InteractionId id);

    /**
     * Finishes writing and releases the file.
     *
     * <p>Closing twice is harmless. Everything recorded before closing is on disk afterwards, which
     * is the promise the whole interface rests on.
     */
    @Override
    void close();
}
