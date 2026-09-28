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
package io.restest.core.settings;

/**
 * Which series of requests a run may send around a thing it created itself.
 *
 * <p>Some faults only show across several requests: a thing the API said it deleted is still
 * there when it is read again, a second deletion of the same thing makes the API fail, something
 * can still be added under a thing that no longer exists, the same creation sent twice breaks the
 * API, repeating a change gives a different result, or merely reading a thing changes it. To look
 * for them, a run creates something of its own and sends a short, fixed series of requests about
 * it, each built once the answer to the one before has arrived. Every series asks one question, and
 * each has a switch here, so that an experiment can measure what one of them is worth by turning it
 * off and nothing else.
 *
 * <p>Whether a run sends series at all is the plan's to say, not these settings': a plan names a
 * way of building requests that sends them, and gives it its share of the run. These say which
 * series it may send; with all of them off, that share is built the ordinary way.
 *
 * @param readAfterDelete create a thing, read it, delete it, and read it and what hangs from it
 *     again: is a deleted thing gone for whoever reads it?
 * @param deleteTwice create a thing and delete it twice: is a second deletion answered calmly?
 * @param writeUnderDeleted create a thing, delete it, and add or change something under it: can
 *     something still be written under a thing that no longer exists?
 * @param putTwice create a thing and send it the same replacement twice, reading it after each: is
 *     a replacement idempotent, and does it replace the whole thing?
 * @param safeGet create a thing, read it, send other reads, and read it again: does reading change
 *     anything?
 * @param createTwice send the same creation twice: does creating the same thing twice break
 *     anything?
 */
public record SequenceSettings(
        boolean readAfterDelete,
        boolean deleteTwice,
        boolean writeUnderDeleted,
        boolean putTwice,
        boolean safeGet,
        boolean createTwice) {

    private static final SequenceSettings DEFAULTS =
            new SequenceSettings(true, true, true, true, true, true);

    /** What a run does when nobody has said otherwise: every series may be sent. */
    public static SequenceSettings defaults() {
        return DEFAULTS;
    }

    /** Whether any series may be sent at all. */
    public boolean anyOn() {
        return readAfterDelete || deleteTwice || writeUnderDeleted || putTwice || safeGet
                || createTwice;
    }

    /** Every series switched off, so that none is ever sent. */
    public static SequenceSettings noneSent() {
        return new SequenceSettings(false, false, false, false, false, false);
    }
}
