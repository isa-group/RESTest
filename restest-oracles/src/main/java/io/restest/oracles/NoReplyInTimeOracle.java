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
package io.restest.oracles;

import io.restest.core.execution.Interaction;
import io.restest.core.model.ApiModel;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import io.restest.core.oracle.WfcFault;
import java.util.List;
import java.util.Locale;

/**
 * Reports a request the API had on an open connection and never answered before RESTest stopped
 * waiting.
 *
 * <p>RESTest waits a set time for each reply - half a minute unless told otherwise - and then gives
 * up. Most requests that end without a reply say nothing about the API: nothing was listening, or the
 * connection was refused or cut. This one does. The connection was open, the request went out, and
 * not even the first line of a reply came back. Usually it means the code handling the request is
 * waiting on something that does not answer - a database, another service, a lock - with no limit
 * of its own; and since what was sent is what made it wait, it may be a way to tie the API up on
 * purpose, which is why the catalogue files it among the security problems.
 *
 * <p>It is weaker evidence than a crash. The API's surroundings can be the slow part rather than its
 * code, the run's own pace can be what it could not keep up with, and a connection kept open between
 * requests can be dropped by something in between without a word, so that a request sent on it
 * reaches nobody. Whoever reads the fault decides which, and a run against an API known to be slow
 * can wait longer, or switch this rule off.
 *
 * <p>A reply whose first line did arrive is not reported, however long the rest of it took: that
 * API answered, and an endpoint that sends a reply bit by bit for as long as it is listened to is
 * doing what it is for. A connection that could not be made in time is not reported either, since
 * the request never reached the API.
 */
public final class NoReplyInTimeOracle implements Oracle {

    @Override
    public String name() {
        return "no-reply-in-time";
    }

    @Override
    public String description() {
        return "reports a request that got no reply, on a connection open to the API, before "
                + "RESTest stopped waiting";
    }

    @Override
    public List<Finding> judge(Interaction interaction, ApiModel api) {
        if (interaction.timedOutWaitingForAReply()) {
            return List.of(Finding.of(WfcFault.SECURITY_CALL_TIMEOUT, interaction,
                    "no reply within " + seconds(interaction) + ": a connection to the API was "
                            + "open, and no status line came back before RESTest stopped waiting"));
        }
        return List.of();
    }

    /** How long the attempt took, as {@code 30.0s}. */
    private static String seconds(Interaction interaction) {
        return String.format(Locale.ROOT, "%.1fs", interaction.elapsed().toMillis() / 1000.0);
    }
}
