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
 * How much of what the API has already said is remembered, so it can be sent back.
 *
 * <p>A run keeps the values an API hands back - the identifier of a pet it has just created, the
 * name of an owner it has just listed - and sends them again in later requests, because a value the
 * API itself produced is one it will accept. This is the size of that memory. It is deliberately
 * small: what the memory is for is a value that is true <em>now</em>, and the oldest thing under a
 * name is the likeliest to have been deleted since.
 *
 * <p>The last two are about time rather than space. Reading a reply costs time on the thread that
 * carries announcements, and a listener that falls behind slows the whole run down, so a reply
 * larger than the tool will read is skipped rather than read slowly.
 *
 * @param mostValuesUnderOneName how many values are kept under any one name
 * @param mostNames how many different names are kept at all. A guard rather than a design: only a
 *     name some request can ask for is kept, and no real description asks for nearly this many.
 *     When it is reached, a new name makes room by letting go of the one heard of longest ago
 * @param longestValueKept how long one remembered word or number may be, written out. The numbers
 *     matter as much as the words: {@code 1e9999999} is ten characters in a reply and ten million
 *     in a web address
 * @param longestReplyRead the largest reply that is read at all. A reply this size is a listing, a
 *     dump or a file
 * @param asDeepAsAReplyIsRead how far into a reply the search for named values goes
 * @param identifiersByResource whether a gap in a web address, such as {@code {petId}} in
 *     {@code /pets/{petId}}, is also filled from the things the API returned for that address's
 *     kind of thing - the {@code id} of a pet listed at {@code /pets} - rather than only from
 *     values carrying the gap's own name. Off, only the name is matched
 * @param identifiersByResourceFirst whether, for such a gap, the things of its kind are asked
 *     before any value carrying the gap's own name. Off, the name is asked first and the things
 *     of its kind only when the name finds nothing. Means nothing while
 *     {@code identifiersByResource} is off
 * @param rememberAcceptedRequests whether the values a request carried are kept too when the API
 *     accepted it - the e-mail address and password a registration was accepted with, ready for a
 *     login - beside what replies carried. Off, only replies are learned from
 * @param pluralIdentifiers whether a gap in a web address named the way a list of identifiers is,
 *     such as {@code {ids}} in {@code /persons/{ids}} or {@code {petIds}}, is filled like the
 *     single {@code {id}} or {@code {petId}}: with the identifier of one of the things of its
 *     kind. Off, it takes only a value carrying its own name. Means nothing while
 *     {@code identifiersByResource} is off
 * @param namesByResource whether a gap named the way a thing's name is, such as
 *     {@code {productName}}, is filled with the {@code name} of one of the products the API
 *     returned, and whether a reply that is a plain list of words, such as {@code ["car", "bike"]}
 *     from {@code /products}, is kept as the names of that many products. Off, such a gap takes
 *     only a value carrying its own name, and such a list is not kept as things at all. Means
 *     nothing while {@code identifiersByResource} is off
 */
public record MemorySettings(
        int mostValuesUnderOneName,
        int mostNames,
        int longestValueKept,
        int longestReplyRead,
        int asDeepAsAReplyIsRead,
        boolean identifiersByResource,
        boolean identifiersByResourceFirst,
        boolean rememberAcceptedRequests,
        boolean pluralIdentifiers,
        boolean namesByResource) {

    private static final MemorySettings DEFAULTS =
            new MemorySettings(20, 2_000, 10_000, 512 * 1024, 6, true, true, true, true, true);

    public MemorySettings {
        atLeastNothing(mostValuesUnderOneName, "mostValuesUnderOneName");
        atLeastNothing(mostNames, "mostNames");
        atLeastNothing(longestValueKept, "longestValueKept");
        atLeastNothing(longestReplyRead, "longestReplyRead");
        atLeastNothing(asDeepAsAReplyIsRead, "asDeepAsAReplyIsRead");
    }

    /**
     * Zero is allowed everywhere here, because the smallest limit is still a limit somebody may
     * want.
     *
     * <p>What it does is not the same for all five. With no values kept under a name, no names
     * kept, or no reply read, nothing is remembered. A reply read no deeper than its top still
     * gives what its top holds, and values no longer than nothing still include true, false and
     * anything empty.
     *
     * <p>None of them is the way to run without the memory. That is the plan's to say, by leaving
     * out the source that draws on it; a plan naming a source these settings had silenced would
     * say something about the run that is not true.
     */
    private static void atLeastNothing(int value, String what) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " cannot be negative, and zero is the least "
                    + "it can be: " + value);
        }
    }

    /** What a run does when nobody has said otherwise. */
    public static MemorySettings defaults() {
        return DEFAULTS;
    }
}
