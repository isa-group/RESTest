package com.atlassian.oai.validator.model;

import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Character.toLowerCase;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;

public class ApiPathImpl extends NormalisedPathImpl implements ApiPath {

    private static final String PARAM_REGEX = "\\{(.*?)}";
    private static final Pattern PARAM_PATTERN = compile(PARAM_REGEX);

    private static final char PARAM_START = '{';
    private static final char PARAM_END = '}';

    public ApiPathImpl(@Nonnull final String path, @Nullable final String apiPrefix) {
        super(path, apiPrefix);
    }

    @Override
    public boolean matches(final NormalisedPath requestPath) {
        if (this.numberOfParts() != requestPath.numberOfParts()) {
            return false;
        }
        for (int i = 0; i < this.numberOfParts(); i++) {
            if (!this.partMatches(i, requestPath.part(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean partMatches(final int index, @Nonnull final String requestPathPart) {
        requireNonNull(requestPathPart, "A request path part is required");
        final String template = part(index);
        final Pattern templatePattern = compile(quote(template).replaceAll(PARAM_REGEX, "\\\\E(.*?)\\\\Q"), CASE_INSENSITIVE);
        return templatePattern.matcher(requestPathPart).matches();
    }

    @Override
    public boolean hasParams(final int index) {
        final String part = part(index);
        return PARAM_PATTERN.matcher(part).find();
    }

    @Override
    public List<String> paramNames(final int index) {
        final String part = part(index);
        final Matcher matcher = PARAM_PATTERN.matcher(part);
        final List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    @Override
    public Map<String, Optional<String>> paramValues(final int index, final String requestPathPart) {
        final List<String> paramNames = paramNames(index);
        if (paramNames.isEmpty()) {
            return emptyMap();
        }

        final String template = part(index);

        // This is a shortcut for the most common case where a single path param occupies the whole path part
        // e.g. /foo/{param}/bar
        // Avoids the need to scan strings etc.
        if (paramNames.size() == 1
                && template.indexOf(PARAM_START) == 0
                && template.indexOf(PARAM_END) == template.length() - 1) {
            return ImmutableMap.of(paramNames.get(0), of(requestPathPart));
        }

        // Using a scanning approach rather than regexes etc. because we want to get any matches
        // and then fill remaining with empties so we can validate on them later.
        // This is harder to do with regexes...

        final Map<String, Optional<String>> result = new HashMap<>();

        int templateScanner = 0;
        int requestScanner = 0;
        int paramValueStart;
        int paramIndex = 0;

        while (templateScanner < template.length() && requestScanner < requestPathPart.length()) {
            if (template.charAt(templateScanner) == PARAM_START) {
                paramValueStart = requestScanner;

                // Scan ahead in the template and find the terminal character
                while (templateScanner < template.length() && template.charAt(templateScanner) != PARAM_END) {
                    templateScanner++;
                }
                if (templateScanner == template.length() || template.charAt(templateScanner) != PARAM_END) {
                    // We must have reached the end without finding a close char
                    break;
                }
                if (templateScanner == template.length() - 1) {
                    // Close char is the last char - value goes to end of string
                    result.put(paramNames.get(paramIndex++), Optional.of(requestPathPart.substring(paramValueStart)));
                    break;
                }

                final char terminal = toLowerCase(template.charAt(++templateScanner));

                // Scan ahead in the request to find the terminal char
                while (requestScanner < requestPathPart.length() &&
                        toLowerCase(requestPathPart.charAt(requestScanner)) != terminal) {
                    requestScanner++;
                }
                if (toLowerCase(requestPathPart.charAt(requestScanner)) == terminal) {
                    // Found the terminal - construct the param value
                    result.put(paramNames.get(paramIndex++), Optional.of(requestPathPart.substring(paramValueStart, requestScanner)));
                } else {
                    // Must have reached the end without finding a terminal - no match
                    break;
                }
            } else {
                if (toLowerCase(template.charAt(templateScanner)) != toLowerCase(requestPathPart.charAt(requestScanner))) {
                    // Templates differ - no match
                    break;
                }
                templateScanner++;
                requestScanner++;
            }
        }
        while (paramIndex < paramNames.size()) {
            result.put(paramNames.get(paramIndex++), empty());
        }
        return result;
    }

}
