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

/**
 * Where a run's evidence is kept: every request RESTest sent, every reply it got back, and enough
 * about each one to look at it again long after the run ended.
 *
 * <p>A run is kept in a single SQLite file - a database that is one ordinary file rather than a
 * server somebody has to install - so a finished run can be copied, attached to a bug report, or
 * opened by any tool that reads SQLite. Everything else in RESTest asks this module through the
 * small interface in {@code io.restest.core.store} and never learns that a database is involved.
 *
 * <p>One third-party library is needed and it is confined here: the SQLite driver. Turning a value
 * into JSON text, which this module also has to do, is done through types RESTest owns, so nothing
 * built on this module depends on a JSON library either.
 */
module io.restest.store {
    requires io.restest.core;
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    exports io.restest.store;
}
