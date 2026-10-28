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
package io.restest.core.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Why a run stopped sending before its time was up: the API answered 401 - "who are you?" - to
 * every request of the run's opening lap, the first round that sends each operation the request it
 * is likeliest to accept.
 *
 * <p>A refusal from every operation at once is a refusal of the whole API, for want of a key or
 * because the key is wrong, and a run that carried on would only collect the same answer until its
 * time ran out. So the run stops, and the {@link RunEvent.RunFinished} that says it is over carries
 * this, for the reports to say why it ended early and to end with a number of its own.
 *
 * @param at when the run decided to stop sending
 * @param lapRequests how many requests the opening lap sent, every one of them answered 401
 */
public record Unauthorized(Instant at, long lapRequests) {

    public Unauthorized {
        Objects.requireNonNull(at, "at");
        if (lapRequests < 1) {
            throw new IllegalArgumentException("an opening lap that sent nothing refused nothing: "
                    + lapRequests);
        }
    }
}
