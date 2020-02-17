package com.atlassian.oai.validator.report;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.max;

/**
 * Resolves the {@link ValidationReport.Level} for a given message key.
 * <p>
 * Levels are specified hierarchically - if a level is not found for the given key it
 * will inherit the level of its parent key. If no level is found for any parent key the
 * {@link #defaultLevel} will be returned.
 * <p>
 * For example:
 * <pre>
 *     validation.request=ERROR
 *     validation.request.body=WARN
 *
 *     getLevel("validation.request.body.missing") == WARN
 *     getLevel("validation.request.parameter.query.missing") == ERROR
 * </pre>
 *
 * @see #create()
 * @see #defaultResolver()
 */
public class LevelResolver {

    private final ValidationReport.Level defaultLevel;
    private final Map<String, ValidationReport.Level> levels = new HashMap<>();

    /**
     * Create a new {@link LevelResolver} instance using a builder to obtain configuration.
     *
     * @return a new builder to use for creating {@link LevelResolver} instances.
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Create a new default {@link LevelResolver}.
     * <p>
     * This resolver will load levels using the {@link LevelLoader#defaultLoaderChain()} and apply a default
     * level of {@link ValidationReport.Level#ERROR}.
     *
     * @return a new {@link LevelResolver} with default configuration.
     */
    public static LevelResolver defaultResolver() {
        return new Builder().build();
    }

    /**
     * Create a new instance with the given key -> level mappings and default level.
     *
     * @param levels The mapping of message key -> level to apply to messages.
     *               If <code>null</code>, no mappings will be applied.
     * @param defaultLevel The default level to apply to message keys for which no mapping is provided.
     *                     If <code>null</code> will default to {@link ValidationReport.Level#ERROR}.
     *
     */
    private LevelResolver(@Nullable final Map<String, ValidationReport.Level> levels,
                         @Nullable final ValidationReport.Level defaultLevel) {
        if (levels != null) {
            this.levels.putAll(levels);
        }
        this.defaultLevel = defaultLevel == null ? ValidationReport.Level.ERROR : defaultLevel;
    }

    /**
     * Gets the {@link ValidationReport.Level} for the given message key.
     * <p>
     * Levels are specified hierarchically - if a level is not found for the given key it
     * will inherit the level of its parent key. If no level is found for any parent key the
     * {@link #defaultLevel} will be returned.
     * <p>
     * For example:
     * <pre>
     *     validation.request=ERROR
     *     validation.request.body=WARN
     *
     *     getLevel("validation.request.body.missing") == WARN
     *     getLevel("validation.request.parameter.query.missing") == ERROR
     * </pre>
     *
     * @param key the message key to resolve e.g. <code>"validation.request.body.missing"</code>
     *
     * @return The level to use for the given message key
     */
    @Nonnull
    public ValidationReport.Level getLevel(@Nullable final String key) {
        if (key == null || key.isEmpty()) {
            return defaultLevel;
        }

        if (levels.containsKey(key)) {
            return levels.get(key);
        }

        final String parentKey = key.substring(0, max(0, key.lastIndexOf('.')));
        final ValidationReport.Level result = getLevel(parentKey);
        levels.put(key, result);
        return result;
    }

    /**
     * A builder for creating {@link LevelResolver} instances.
     */
    public static class Builder {

        private ValidationReport.Level defaultLevel;
        private Map<String, ValidationReport.Level> levels = new HashMap<>();
        private LevelLoader loader;
        private boolean useDefaultLoader = true;

        /**
         * Set or override the {@link LevelLoader} strategy used to load message levels.
         * <p>
         * By default, the {@link LevelLoader#defaultLoaderChain()} is used. If this needs to be overridden,
         * set it to <code>null</code> here to avoid any loading, or replace it with your own implementation.
         *
         * @param loader The loader to use to load initial message levels.
         *
         * @return this builder instance.
         */
        public Builder withLoader(final LevelLoader loader) {
            this.loader = loader;
            this.useDefaultLoader = false;
            return this;
        }

        /**
         * Set the default level to use for any message which does not have an explicit mapping defined.
         * <p>
         * Note that any default level set by the loader configured in {@link #withLoader(LevelLoader)}
         * will override this value.
         *
         * @param defaultLevel the default level to use for any message which does not have an explicit mapping defined
         *
         * @return this builder instance.
         */
        public Builder withDefaultLevel(final ValidationReport.Level defaultLevel) {
            this.defaultLevel = defaultLevel;
            return this;
        }

        /**
         * Set mappings of message key -&gt; level to use in the {@link LevelResolver}.
         * <p>
         * Note that any mappings loaded from the the loader configured in {@link #withLoader(LevelLoader)}
         * will override those set with this method.
         *
         * @param levels mappings of message key -&gt; level to use in the {@link LevelResolver}.
         *
         * @return this builder instance.
         */
        public Builder withLevels(final Map<String, ValidationReport.Level> levels) {
            this.levels.putAll(levels);
            return this;
        }

        /**
         * Add a mapping of message key -&gt; level to use in the {@link LevelResolver}.
         * <p>
         * Note that any mappings loaded from the the loader configured in {@link #withLoader(LevelLoader)}
         * will override those set with this method.
         *
         * @param key The message key
         * @param level The level to associate with the key
         *
         * @return this builder instance.
         */
        public Builder withLevel(final String key, final ValidationReport.Level level) {
            this.levels.put(key, level);
            return this;
        }

        /**
         * Build and return a new {@link LevelResolver} instance created from the configuration collected
         * in this builder.
         *
         * @return The new {@link LevelResolver} instance.
         */
        public LevelResolver build() {
            final Map<String, ValidationReport.Level> levels = new HashMap<>();
            if (useDefaultLoader) {
                this.loader = LevelLoader.defaultLoaderChain();
            }
            ValidationReport.Level defaultLevel = this.defaultLevel;
            levels.putAll(LevelLoader.defaultsLoader().loadLevels());
            levels.putAll(this.levels);
            if (loader != null) {
                levels.putAll(loader.loadLevels());
                defaultLevel = loader.defaultLevel().orElse(this.defaultLevel);
            }

            return new LevelResolver(levels, defaultLevel);
        }
    }

}
