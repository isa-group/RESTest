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

import java.util.Objects;

/**
 * One check saying it could not judge a reply, and why.
 *
 * <p>Where a {@link Finding} says "this reply is wrong", this says "this reply was not looked at in
 * this way": the shape of its body was not compared with the document, say, because the document
 * gives none. It is not a fault and never changes how a run ends; it is what lets a run that found
 * nothing wrong say how much it could not check, rather than seem to have checked it all.
 *
 * @param kind which kind of fault the check would have reported, which is how a report tells the
 *     checks apart: every check reports one kind
 * @param what what the check looks at, as a report writes it before "of 12 replies": {@code the
 *     shape}, {@code the status code}, {@code the content type}
 * @param why why it could not
 */
public record CouldNotJudge(FaultCategory kind, String what, WhyNotJudged why) {

    public CouldNotJudge {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(why, "why");
        if (what.isBlank()) {
            throw new IllegalArgumentException("a check that could not judge a reply says what it "
                    + "would have looked at");
        }
    }
}
