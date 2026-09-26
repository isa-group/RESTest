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
 * @param mostNames how many different names are kept at all. A guard rather than a design, against
 *     an API whose replies carry made-up property names, and well above anything a real description
 *     declares
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
 */
public record MemorySettings(
        int mostValuesUnderOneName,
        int mostNames,
        int longestValueKept,
        int longestReplyRead,
        int asDeepAsAReplyIsRead,
        boolean identifiersByResource,
        boolean identifiersByResourceFirst) {

    private static final MemorySettings DEFAULTS =
            new MemorySettings(20, 2_000, 10_000, 512 * 1024, 6, true, true);

    public MemorySettings {
        atLeastNothing(mostValuesUnderOneName, "mostValuesUnderOneName");
        atLeastNothing(mostNames, "mostNames");
        atLeastNothing(longestValueKept, "longestValueKept");
        atLeastNothing(longestReplyRead, "longestReplyRead");
        atLeastNothing(asDeepAsAReplyIsRead, "asDeepAsAReplyIsRead");
    }

    /**
     * Zero is allowed everywhere here, and means "remember none of this".
     *
     * <p>Turning the memory off is a thing an experiment asks for, and any of these at zero does
     * it. Refusing zero would make the only way to ask for it a change to the plan.
     */
    private static void atLeastNothing(int value, String what) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " cannot be negative; zero remembers none of "
                    + "them, which is the least this can do: " + value);
        }
    }

    /** What a run does when nobody has said otherwise. */
    public static MemorySettings defaults() {
        return DEFAULTS;
    }
}
