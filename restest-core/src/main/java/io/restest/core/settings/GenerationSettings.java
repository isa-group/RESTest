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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a value the tool invents for itself is allowed to look like.
 *
 * <p>When the description of an API says a parameter is a word, or a number, or a list of things,
 * and says nothing more, somebody has to decide how long that word is and how many things are in
 * that list. These are those decisions. They are all shapes of a request rather than facts about
 * the world, which is why they are here rather than in the code: an API that refuses anything over
 * thirty characters, or one that falls over on a list of four, is answered by changing a line
 * instead of by rebuilding the tool.
 *
 * <p>The pairs are worth reading together. {@code usualLongestString} is how long an invented word
 * is when the description demands nothing; {@code longestString} is the point beyond which a length
 * the description <em>does</em> demand is declined rather than built, because inventing a word of a
 * million characters costs the run more than the request could ever be worth. The same pair exists
 * for how many items a list holds, and for how deeply one value may be nested inside another.
 *
 * @param optionalNestingDepth where optional nesting stops: below this, only what the description
 *     insists on is built
 * @param hardNestingDepth where everything stops, however insistent the description is
 * @param usualLongestString the longest word invented when the description does not demand more
 * @param longestString beyond this, a demanded length is declined rather than built
 * @param lowestNumber where an invented number starts, when the description states no bottom.
 *     Kept with its trailing zeros stripped, so {@code 1000} and {@code 1E+3} are one value
 * @param roomAboveIt how far above that it may go, when the description states no top. A width
 *     rather than a ceiling: a description that states a bottom of its own gets this much room
 *     above that bottom, so the room to move in is the same wherever the numbers begin
 * @param decimalPlaces decimal places for a number that is allowed to have them
 * @param usualMostItems the most items put in a list when the description does not demand more
 * @param mostItems beyond this, a demanded number of items is declined rather than built
 * @param optionalPropertyChance how often a property the description does not require is included
 *     anyway, between 0 and 1
 * @param optionalParameterChance how often a parameter the API does not require is included anyway,
 *     between 0 and 1
 * @param nullInOneIn how often a value that is allowed to be absent is sent as nothing at all: one
 *     time in this many. Zero never sends nothing
 * @param uniqueAttempts how many times a fresh element is attempted for a list whose items must all
 *     differ
 * @param sendableAttempts how many times a value is invented again after one that could not be put
 *     in the request
 * @param writableBodyAttempts how many bodies are drawn while looking for one that can be written
 *     as its media type
 */
public record GenerationSettings(
        int optionalNestingDepth,
        int hardNestingDepth,
        int usualLongestString,
        int longestString,
        BigDecimal lowestNumber,
        BigDecimal roomAboveIt,
        int decimalPlaces,
        int usualMostItems,
        int mostItems,
        double optionalPropertyChance,
        double optionalParameterChance,
        int nullInOneIn,
        int uniqueAttempts,
        int sendableAttempts,
        int writableBodyAttempts) {

    private static final GenerationSettings DEFAULTS = new GenerationSettings(
            4, 8, 64, 10_000, BigDecimal.ZERO, BigDecimal.valueOf(1000), 2, 4, 100,
            0.5, 0.5, 8, 8, 8, 8);

    public GenerationSettings {
        Objects.requireNonNull(lowestNumber, "lowestNumber");
        Objects.requireNonNull(roomAboveIt, "roomAboveIt");
        writable(lowestNumber, "lowestNumber");
        writable(roomAboveIt, "roomAboveIt");
        // Kept the way this tool keeps every number, so that one written 1000, another written
        // 1E+3 and a third written 1000.00 are one value rather than three. Without it, printing
        // these settings and handing the file back would produce settings that differ from the
        // ones printed - equal as numbers, unequal as values - and nothing would say why.
        lowestNumber = lowestNumber.stripTrailingZeros();
        roomAboveIt = roomAboveIt.stripTrailingZeros();
        atLeastOne(optionalNestingDepth, "optionalNestingDepth");
        atLeastOne(hardNestingDepth, "hardNestingDepth");
        atLeastOne(usualLongestString, "usualLongestString");
        atLeastOne(longestString, "longestString");
        atLeastOne(usualMostItems, "usualMostItems");
        atLeastOne(mostItems, "mostItems");
        atLeastOne(uniqueAttempts, "uniqueAttempts");
        atLeastOne(sendableAttempts, "sendableAttempts");
        atLeastOne(writableBodyAttempts, "writableBodyAttempts");
        aShare(optionalPropertyChance, "optionalPropertyChance");
        aShare(optionalParameterChance, "optionalParameterChance");
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("decimalPlaces cannot be negative; zero invents "
                    + "whole numbers where decimals are allowed: " + decimalPlaces);
        }
        if (nullInOneIn < 0) {
            throw new IllegalArgumentException("nullInOneIn cannot be negative; zero never sends "
                    + "nothing at all where nothing is allowed: " + nullInOneIn);
        }
        if (hardNestingDepth < optionalNestingDepth) {
            throw new IllegalArgumentException("hardNestingDepth (" + hardNestingDepth + ") is "
                    + "below optionalNestingDepth (" + optionalNestingDepth + "), which would stop "
                    + "generation before it stopped adding what is optional");
        }
        if (longestString < usualLongestString) {
            throw new IllegalArgumentException("longestString (" + longestString + ") is below "
                    + "usualLongestString (" + usualLongestString + "), so an ordinary invented "
                    + "word would be longer than the longest one allowed");
        }
        if (mostItems < usualMostItems) {
            throw new IllegalArgumentException("mostItems (" + mostItems + ") is below "
                    + "usualMostItems (" + usualMostItems + "), so an ordinary invented list would "
                    + "be longer than the longest one allowed");
        }
        if (roomAboveIt.signum() < 0) {
            throw new IllegalArgumentException("roomAboveIt is how far above the lowest an "
                    + "invented number may go, so it cannot be negative; zero invents the lowest "
                    + "and nothing else: " + roomAboveIt);
        }
    }

    private static void atLeastOne(int value, String what) {
        if (value < 1) {
            throw new IllegalArgumentException(what + " must be at least 1, or nothing could be "
                    + "invented at all: " + value);
        }
    }

    private static void aShare(double value, String what) {
        if (!(value >= 0) || value > 1) {
            throw new IllegalArgumentException(what + " is how often something happens, so it lies "
                    + "between 0 and 1: " + value);
        }
    }

    /** A number short enough to be written down, and therefore to be sent. */
    private static void writable(BigDecimal value, String what) {
        if (WrittenNumber.tooLongToWrite(value)) {
            throw new IllegalArgumentException(what + " takes more than " + WrittenNumber.LONGEST
                    + " characters to write down, which is longer than anything a request could "
                    + "carry");
        }
    }

    /** What a run does when nobody has said otherwise. */
    public static GenerationSettings defaults() {
        return DEFAULTS;
    }
}
