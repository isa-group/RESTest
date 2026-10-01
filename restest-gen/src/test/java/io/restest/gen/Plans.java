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
package io.restest.gen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Plans the generator's tests ask for by how much of a run they spend pushing at the API, from all
 * of it to none.
 *
 * <p>A plan says how a run divides its time between ways of building a request. Each plan here is
 * the one RESTest carries, read from the file it ships, cut down to its two plainest strategies -
 * the one that builds requests meant to work and the one that pushes - and given a new division of
 * the run between them. Taking them from the file rather than writing them out again means a test
 * of how the generator follows such a plan is also a test of the plan RESTest actually ships.
 */
final class Plans {

    private Plans() {
    }

    /** The plan RESTest carries with nothing left but its strategy that pushes at the API. */
    static Campaign onlyPushing() {
        return pushing(Campaign.WHOLE);
    }

    /** The plan RESTest carries with nothing left but its strategy that builds requests to work. */
    static Campaign neverPushing() {
        return pushing(0);
    }

    /**
     * The plan RESTest carries, spending this much of its time pushing at the API and the rest
     * building requests meant to work. A strategy given none of the time is left out, as a plan
     * would leave it out.
     *
     * @param share how much of the run pushes, out of a hundred
     */
    static Campaign pushing(int share) {
        Campaign shipped;
        try {
            shipped = Campaigns.shipped();
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(cannotRead);
        }
        Campaign.PlannedStrategy ordinary = shipped.strategies().stream()
                .filter(way -> !way.pushesAtTheApi() && !way.mutatesAccepted()
                        && !way.sendsSequences())
                .findFirst().orElseThrow();
        Campaign.PlannedStrategy pushes = shipped.strategies().stream()
                .filter(Campaign.PlannedStrategy::pushesAtTheApi)
                .findFirst().orElseThrow();
        List<Campaign.PlannedStrategy> kept = new ArrayList<>();
        if (share < Campaign.WHOLE) {
            kept.add(new Campaign.PlannedStrategy(ordinary.name(), Campaign.WHOLE - share,
                    ordinary.sources()));
        }
        if (share > 0) {
            kept.add(new Campaign.PlannedStrategy(pushes.name(), share, pushes.sources()));
        }
        return new Campaign(kept, shipped.operations());
    }
}
