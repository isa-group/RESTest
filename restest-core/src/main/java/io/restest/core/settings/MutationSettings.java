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
 * Which changes a run may make to requests the API has already accepted, and how large they are.
 *
 * <p>A run can take a request the API accepted and send it again with exactly one thing different,
 * to see what the API does when only that one thing is wrong. Every change breaks something the
 * API's documentation states: a value it says it needs left out, a number one past the largest it
 * allows, a word where it wants a number. The documentation says those should be turned away.
 *
 * <p>Some changes are to the body as a whole rather than to one value in it: a list where an object
 * belongs, no bytes at all, text that is not JSON, the right body under the wrong media type. An API
 * reads and checks a body before any of its own code runs, and that reading is code too, with
 * failures of its own.
 *
 * <p>Every kind of change has a switch of its own, so that an experiment can measure what one of
 * them is worth by turning it off and nothing else, and one switch turns them all off at once. A
 * kind of change is made only when its own switch and that one are both on.
 *
 * <p>Whether a run changes accepted requests at all is the plan's to say, not these settings': a
 * plan names a way of building requests that works this way, and gives it its share of the run.
 * These settings say how it behaves; with every change off, that share is built the ordinary way.
 *
 * @param violations whether accepted requests are changed at all
 * @param dropRequired leave out a parameter, or a property of the body, that the documentation says
 *     is required
 * @param wrongLocation send a required parameter somewhere other than where it was declared - in a
 *     header instead of the query string, say
 * @param wrongType send a value of another kind: a word where a number is declared
 * @param outsideABound send a value one step past a limit the documentation states: one below the
 *     smallest number allowed, one character longer than the longest word
 * @param breakAnEnumeration send a value that is not on the closed list the documentation states
 * @param breakAPattern send a word the stated spelling rule refuses
 * @param breakAFormat send a word that looks like the kind of word the documentation names but is
 *     not one: {@code 2021-02-30} where it says {@code date}, {@code a@b.} where it says
 *     {@code email}
 * @param sendNull send nothing at all, written as {@code null}, for a property of the body that
 *     may not be null
 * @param sendEmpty send an empty word, list or object where the documentation forbids one
 * @param oversize send a word of {@code oversizedLength} characters, or a list of
 *     {@code oversizedItems} items, where that is beyond the longest the documentation allows
 * @param wrongRoot send the whole body as another kind of thing than the documentation declares - a
 *     list holding it where an object is declared, say
 * @param emptyBody send a body of no bytes at all where the documentation says a body is required
 * @param notJson send a body that is not JSON: the accepted one cut off halfway or broken in one of
 *     the ways a reader of JSON stops at, or plain words
 * @param wrongContentType send the accepted body unchanged, under a media type the documentation
 *     does not offer for it
 * @param beyondItsWidth send a number past the largest or smallest the kind of number its
 *     documentation names can hold: 2147483648 where it says {@code int32}
 * @param acceptedKept how many of the requests each operation accepted are kept to be changed; the
 *     most recent ones, since an older one may refer to something deleted since
 * @param oversizedLength how many characters an oversized word has
 * @param oversizedItems how many items an oversized list has
 */
public record MutationSettings(
        boolean violations,
        boolean dropRequired,
        boolean wrongLocation,
        boolean wrongType,
        boolean outsideABound,
        boolean breakAnEnumeration,
        boolean breakAPattern,
        boolean breakAFormat,
        boolean sendNull,
        boolean sendEmpty,
        boolean oversize,
        boolean wrongRoot,
        boolean emptyBody,
        boolean notJson,
        boolean wrongContentType,
        boolean beyondItsWidth,
        int acceptedKept,
        int oversizedLength,
        int oversizedItems) {

    private static final MutationSettings DEFAULTS = new MutationSettings(
            true,
            true, true, true, true, true, true, true, true, true, true,
            true, true, true, true, true,
            16, 10_000, 1_000);

    public MutationSettings {
        atLeastOne(acceptedKept, "acceptedKept");
        atLeastOne(oversizedLength, "oversizedLength");
        atLeastOne(oversizedItems, "oversizedItems");
    }

    /** What a run does when nobody has said otherwise: every kind of change. */
    public static MutationSettings defaults() {
        return DEFAULTS;
    }

    /** These settings with every change off, so that nothing accepted is ever changed. */
    public MutationSettings withNothingChanged() {
        return new MutationSettings(false, dropRequired, wrongLocation, wrongType, outsideABound,
                breakAnEnumeration, breakAPattern, breakAFormat, sendNull, sendEmpty, oversize,
                wrongRoot,
                emptyBody, notJson, wrongContentType, beyondItsWidth, acceptedKept,
                oversizedLength, oversizedItems);
    }

    private static void atLeastOne(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1: " + value);
        }
    }
}
