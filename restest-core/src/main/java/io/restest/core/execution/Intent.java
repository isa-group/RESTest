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
package io.restest.core.execution;

/**
 * What RESTest expected when it built a {@link TestCase}: that the API would accept it, that the
 * API would turn it away, or nothing in particular.
 *
 * <p>A test is more than a request. The same {@code 400 Bad Request} is the right answer to a
 * request that left out something the API insists on, and the wrong answer to a request that did
 * everything the documentation asks. So each test case says what its builder expected, and anything
 * that later judges the reply - a rule that looks for faults, or a person reading a stored run -
 * can tell which answer would have been the right one.
 *
 * <p>It is a statement about the request, made when the request was built, and it stays true
 * whatever the API answers. Which rules read it, and what they conclude, is decided elsewhere.
 * When the request is one that was made by changing a single thing in a request the API had
 * already accepted, {@link Mutation} says what that one thing was; when it is one step of a series
 * built around a thing the run created itself, {@link SequenceStep} says which series and what came
 * before it.
 */
public enum Intent {

    /**
     * Every value in it came from something that knows the API, so it is expected to be accepted.
     *
     * <p>Nothing builds a request with this intent yet. Telling it apart from {@link #UNKNOWN}
     * needs to know where every value inside a body came from, and a body records one origin for
     * the whole of it; claiming this for such a body would sometimes be claiming something false.
     */
    ACCEPTABLE,

    /**
     * The API is expected to turn it away, for a reason the test case names.
     *
     * <p>One of two reasons. Either one thing in a request the API had accepted was changed so that
     * it breaks what the documentation says, and a {@link Mutation} says what was changed. Or the
     * request asks about a thing the API itself said, a moment before, that it had deleted, and a
     * {@link SequenceStep} names that exchange. Either way, an acceptance afterwards can be put down
     * to that one reason.
     */
    REFUSAL_EXPECTED,

    /**
     * Nothing in particular is expected.
     *
     * <p>The honest intent of most requests. A value invented to fit what the documentation
     * describes may still be refused for a rule it never wrote down, and a thing a series deletes a
     * second time may be answered that it is not there, or with a success: either answer is a fair
     * one.
     */
    UNKNOWN,

    /**
     * Built from values chosen to be awkward throughout, to see whether the API falls over.
     *
     * <p>Neither answer is expected: many APIs accept an empty word or a zero, and a refusal
     * cannot be put down to any one of the values, since all of them were awkward at once. What
     * such a request is looking for is the third answer, the API failing.
     */
    PUSHING
}
