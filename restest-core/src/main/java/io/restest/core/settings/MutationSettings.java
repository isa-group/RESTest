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
 * to see what the API does when only that one thing is wrong. The changes come in two families,
 * each switched on and off as a whole. The first breaks something the API's documentation states: a
 * value it says it needs left out, a number one past the largest it allows, a word where it wants a
 * number. The documentation says those should be turned away. The second goes where the
 * documentation says nothing - a name ten thousand characters long where no longest length is given,
 * an empty word where nothing says a word may not be empty - and there nobody can say in advance
 * which answer is right, which is exactly why it is worth asking.
 *
 * <p>Some changes are to the body as a whole rather than to one value in it: a list where an object
 * belongs, no bytes at all, text that is not JSON, the right body under the wrong media type, a
 * member nested ten thousand levels deep. An API reads and checks a body before any of its own code
 * runs, and that reading is code too, with failures of its own. Those changes belong to the same two
 * families, by the same rule.
 *
 * <p>Only the first family is on unless somebody says otherwise. The second found nothing the
 * first had not when it was measured, and cost time on an API slow to answer a very long word;
 * it is one setting away for whoever wants to try it on their own API.
 *
 * <p>Inside each family every kind of change has a switch of its own, so that an experiment can
 * measure what one of them is worth by turning it off and nothing else. A kind of change is made
 * only when its own switch and its family's are both on.
 *
 * <p>Whether a run changes accepted requests at all is the plan's to say, not these settings': a
 * plan names a way of building requests that works this way, and gives it its share of the run.
 * These settings say how it behaves; with both families off, that share is built the ordinary way.
 *
 * @param violations whether changes that break what the documentation states are made
 * @param probes whether changes the documentation does not rule on are made
 * @param dropRequired leave out a parameter, or a property of the body, that the documentation says
 *     is required
 * @param wrongLocation send a required parameter somewhere other than where it was declared - in a
 *     header instead of the query string, say
 * @param wrongType send a value of another kind: a word where a number is declared
 * @param outsideABound send a value one step past a limit the documentation states: one below the
 *     smallest number allowed, one character longer than the longest word
 * @param breakAnEnumeration send a value that is not on the closed list the documentation states
 * @param breakAPattern send a word the stated spelling rule refuses
 * @param sendNull send nothing at all, written as {@code null}, for a property of the body that
 *     may not be null
 * @param sendEmpty send an empty word, list or object where the documentation forbids one
 * @param oversize send a word of {@code oversizedLength} characters, or a list of
 *     {@code oversizedItems} items, where that is beyond the longest the documentation allows
 * @param oversizeWithNoLimit the same, where the documentation states no longest length at all
 * @param emptyWithNoRule send an empty word, list or object where nothing in the documentation
 *     forbids one
 * @param wrongRoot send the whole body as another kind of thing than the documentation declares - a
 *     list holding it where an object is declared, say
 * @param emptyBody send a body of no bytes at all where the documentation says a body is required
 * @param notJson send a body that is not JSON: the accepted one cut off halfway, or plain words
 * @param wrongContentType send the accepted body unchanged, under a media type the documentation
 *     does not offer for it
 * @param beyondItsWidth send a number past the largest or smallest the kind of number its
 *     documentation names can hold: 2147483648 where it says {@code int32}
 * @param deepNesting add a member the documentation does not declare, holding lists nested
 *     {@code nestingDepth} levels deep, to a body that allows members it does not declare
 * @param extremeNumber send the largest or smallest number a common kind of number can hold, the
 *     one nearest to nothing, or just past them, where nothing in the documentation rules it out
 * @param acceptedKept how many of the requests each operation accepted are kept to be changed; the
 *     most recent ones, since an older one may refer to something deleted since
 * @param oversizedLength how many characters an oversized word has
 * @param oversizedItems how many items an oversized list has
 * @param nestingDepth how many levels deep a member added by {@code deepNesting} is nested, at
 *     most a million
 */
public record MutationSettings(
        boolean violations,
        boolean probes,
        boolean dropRequired,
        boolean wrongLocation,
        boolean wrongType,
        boolean outsideABound,
        boolean breakAnEnumeration,
        boolean breakAPattern,
        boolean sendNull,
        boolean sendEmpty,
        boolean oversize,
        boolean oversizeWithNoLimit,
        boolean emptyWithNoRule,
        boolean wrongRoot,
        boolean emptyBody,
        boolean notJson,
        boolean wrongContentType,
        boolean beyondItsWidth,
        boolean deepNesting,
        boolean extremeNumber,
        int acceptedKept,
        int oversizedLength,
        int oversizedItems,
        int nestingDepth) {

    // Probes off. Measured against five APIs restarted before every run, five seeds, a minute
    // each, they found nothing the violations had not, and on one API they cost a third of the
    // requests: a word ten thousand characters long takes longer to answer. They stay a line away
    // for the experiment that measures them over a longer run. Ten thousand levels is past the
    // depth the common JSON readers stop at - a thousand, two hundred and fifty-five - and past the
    // depth at which a reader that calls itself for every level runs out of room.
    // A safeguard, not a tuning number: two characters a level, so a million levels is a body of
    // two megabytes, sent again every time the change is drawn, and much beyond it the text stops
    // fitting in memory at all.
    private static final int DEEPEST = 1_000_000;

    private static final MutationSettings DEFAULTS = new MutationSettings(
            true, false,
            true, true, true, true, true, true, true, true, true,
            true, true,
            true, true, true, true, true,
            true, true,
            16, 10_000, 1_000, 10_000);

    public MutationSettings {
        atLeastOne(acceptedKept, "acceptedKept");
        atLeastOne(oversizedLength, "oversizedLength");
        atLeastOne(oversizedItems, "oversizedItems");
        atLeastOne(nestingDepth, "nestingDepth");
        if (nestingDepth > DEEPEST) {
            throw new IllegalArgumentException("nestingDepth must be at most " + DEEPEST + ": "
                    + nestingDepth);
        }
    }

    /**
     * What a run does when nobody has said otherwise: every kind of change that breaks what the
     * documentation states, and none of those it does not rule on, which are one setting away.
     */
    public static MutationSettings defaults() {
        return DEFAULTS;
    }

    /**
     * Whether either family is on. A family on with every kind of change in it off still changes
     * nothing; which kinds belong to which family is decided where the changes are made.
     */
    public boolean anyFamilyOn() {
        return violations || probes;
    }

    /** These settings with both families off, so that nothing accepted is ever changed. */
    public MutationSettings withNothingChanged() {
        return new MutationSettings(false, false, dropRequired, wrongLocation, wrongType,
                outsideABound, breakAnEnumeration, breakAPattern, sendNull, sendEmpty, oversize,
                oversizeWithNoLimit, emptyWithNoRule, wrongRoot, emptyBody, notJson,
                wrongContentType, beyondItsWidth, deepNesting, extremeNumber, acceptedKept,
                oversizedLength, oversizedItems, nestingDepth);
    }

    private static void atLeastOne(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1: " + value);
        }
    }
}
