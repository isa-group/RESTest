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
package io.restest.arch;

import io.restest.core.json.JsonException;
import io.restest.core.json.JsonValue;
import io.restest.core.json.YamlText;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The blocks of YAML a page of the documentation shows, as they are written there.
 *
 * <p>The pages show files somebody is meant to copy - a dictionary, a plan, a file of settings -
 * and the tests that check those pages against the tool read them out of the page here, so that
 * every one of them reads a page the same way. Each block comes with the label the page gives it,
 * which {@link DocumentedSwitchesTest} matches against its tables, and the heading it sits under.
 */
final class YamlBlocks {

    /** The label a page gives a file: the words in bold before it, up to a comma or a colon. */
    private static final Pattern LABEL = Pattern.compile("^\\*\\*([^,:*]+)");

    private YamlBlocks() {
    }

    /** A block of YAML on a page, with the label before it, if any, and the heading above it. */
    record Block(String text, String label, String heading) {
    }

    /**
     * Every block of YAML on a page, in the order the page shows them.
     *
     * @param page the page's text, with its lines ended the Unix way
     * @return the blocks
     */
    static List<Block> on(String page) {
        List<Block> found = new ArrayList<>();
        String heading = "";
        String label = "";
        StringBuilder inside = null;
        for (String line : page.lines().toList()) {
            if (inside != null) {
                if (line.equals("```")) {
                    found.add(new Block(inside.toString(), label, heading));
                    inside = null;
                    label = "";
                } else {
                    inside.append(line).append('\n');
                }
            } else if (line.equals("```yaml")) {
                inside = new StringBuilder();
            } else if (line.startsWith("## ")) {
                heading = line.substring("## ".length()).trim();
                label = "";
            } else {
                Matcher labelled = LABEL.matcher(line);
                if (labelled.find()) {
                    label = labelled.group(1).trim();
                }
            }
        }
        return found;
    }

    /**
     * The members a block holds at its top, which is what says what kind of file it is: a plan
     * has {@code strategies}, a dictionary {@code keyedBy}, and a whole file of either holds its
     * {@code version}. A piece of a file that is not an object, or not YAML at all, holds none.
     *
     * @param block a block's text
     * @return the names of its members
     */
    static Set<String> members(String block) {
        try {
            return YamlText.read(block, "a block of a page") instanceof JsonValue.JsonObject object
                    ? object.members().keySet()
                    : Set.of();
        } catch (JsonException notAFile) {
            return Set.of();
        }
    }
}
