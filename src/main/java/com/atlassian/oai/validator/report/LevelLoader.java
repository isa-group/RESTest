package com.atlassian.oai.validator.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static java.lang.String.format;

public interface LevelLoader {

    /**
     * Loads levels from system properties of the form <code>swagger.{key}={LEVEL}</code>.
     * <p>
     * For example, to set the level of the key <code>validation.response.body.missing</code>:
     * <pre>
     *     -Dswagger.validation.response.body.missing="WARN"
     * </pre>
     * To set the default level, use the property <code>swagger.defaultLevel</code>
     */
    static LevelLoader systemPropertyLoader() {
        return new PropertiesLoader(System.getProperties(), "swagger.");
    }

    /**
     * Loads levels from a properties file ".swagger-validator" in the user's current directory.
     * <p>
     * Properties should be of the form <code>{key}={LEVEL}</code>, e.g.
     * <code>validation.response.body.missing=WARN</code>.
     * <p>
     * The default level can be set with the property <code>defaultLevel</code>.
     */
    static LevelLoader currentDirectoryLoader() {
        return new PropertiesLoader(new File(System.getProperty("user.dir"), ".swagger-validator"), null);
    }

    /**
     * Loads levels from a properties file "/swagger-validator.properties" in the project's classpath.
     * <p>
     * Properties should be of the form <code>{key}={LEVEL}</code>, e.g.
     * <code>validation.response.body.missing=WARN</code>.
     * <p>
     * The default level can be set with the property <code>defaultLevel</code>.
     */
    static LevelLoader classpathLoader() {
        return new PropertiesLoader(LevelLoader.class.getClassLoader().getResource("swagger-validator.properties"), null);
    }

    /**
     * Loads default level values from the "default-levels.properties" file in the library's classpath.
     */
    static LevelLoader defaultsLoader() {
        return new PropertiesLoader(LevelLoader.class.getResource("/swagger/validation/default-levels.properties"), null);
    }

    /**
     * The default loader chain used when no other loader is specified.
     * <p>
     * Loads in the following order:
     * <ol>
     *     <li>From a properties file "swagger-validator.properties" in the project's classloader</li>
     *     <li>From a properties file ".swagger-validator" in the user's current directory</li>
     *     <li>From system properties of the form "swagger.{key}={LEVEL}"</li>
     * </ol>
     * @see #classpathLoader()
     * @see #currentDirectoryLoader()
     * @see #systemPropertyLoader()
     */
    static LevelLoader defaultLoaderChain() {
        return new ChainingLoader(classpathLoader(), currentDirectoryLoader(), systemPropertyLoader());
    }

    /**
     * Load message levels from this loader.
     *
     * @return The map of key -&gt; level that should be used to control message levels.
     */
    Map<String, ValidationReport.Level> loadLevels();

    /**
     * Load the default level from this loader (if it has been defined).
     *
     * @return the default level found in this loader, or empty() if none has been defined.
     */
    Optional<ValidationReport.Level> defaultLevel();

    /**
     * Loads levels from properties of the form <code>{prefix}{key}={LEVEL}</code>.
     * <p>
     * The {prefix} can be any String. If not provided, no prefix is applied.
     * <p>
     * To set the default level, use the property <code>{prefix}defaultLevel={LEVEL}</code>
     * <p>
     * For example, with a prefix "swagger.":
     * <pre>
     *     swagger.validation.request=ERROR
     *     swagger.validation.response=WARN
     *     swagger.defaultLevel=INFO
     * </pre>
     */
    class PropertiesLoader implements LevelLoader {
        private static final Logger log = LoggerFactory.getLogger(PropertiesLoader.class);

        private static final String VALIDATION_KEY_PREFIX = "validation";
        private static final String DEFAULT_LEVEL_KEY = "defaultLevel";

        private final Properties props;
        private final String prefix;

        public PropertiesLoader(final String sourcePath, final String prefix) {
            this(new File(sourcePath), prefix);
        }

        public PropertiesLoader(final File source, final String prefix) {
            this.prefix = prefix;
            this.props = new Properties();
            if (!source.exists()) {
                log.debug("Levels file {} does not exist. Skipping.", source);
                return;
            }
            try {
                props.load(new FileReader(source));
            } catch (final IOException e) {
                log.warn(format("Unable to load levels file %s", source.getAbsolutePath()), e);
            }
        }

        public PropertiesLoader(final URL source, final String prefix) {
            this.prefix = prefix;
            this.props = new Properties();
            if (source == null) {
                return;
            }
            try {
                props.load(new InputStreamReader(source.openStream()));
            } catch (final IOException e) {
                log.warn(format("Unable to load levels file %s", source.toExternalForm()), e);
            }
        }

        public PropertiesLoader(final Properties source, final String prefix) {
            this.prefix = prefix;
            this.props = source;
        }

        @Override
        public Map<String, ValidationReport.Level> loadLevels() {
            final Map<String, ValidationReport.Level> result = new HashMap<>();
            props.stringPropertyNames().forEach(n -> {
                if (!n.startsWith(toPropertyName(VALIDATION_KEY_PREFIX))) {
                    return;
                }
                final String key = fromPropertyName(n);
                final String value = props.getProperty(n).toUpperCase();
                try {
                    result.put(key, ValidationReport.Level.valueOf(value));
                } catch (final Exception e) {
                    log.warn("Unable to load level {} from property with value '{}'.", key, value);
                }
            });
            return result;
        }

        @Override
        public Optional<ValidationReport.Level> defaultLevel() {
            if (!props.containsKey(toPropertyName(DEFAULT_LEVEL_KEY))) {
                return Optional.empty();
            }
            final String value = props.getProperty(toPropertyName(DEFAULT_LEVEL_KEY));
            try {
                return Optional.of(ValidationReport.Level.valueOf(value.toUpperCase()));
            } catch (final Exception e) {
                log.warn("Unable to load the default level from property '{}'.", value);
                return Optional.empty();
            }
        }

        private String toPropertyName(final String keyName) {
            if (prefix == null || prefix.isEmpty()) {
                return keyName;
            }
            return prefix + keyName;
        }

        private String fromPropertyName(final String propertyName) {
            if (prefix == null || prefix.isEmpty()) {
                return propertyName;
            }
            return propertyName.replace(prefix, "");
        }
    }

    /**
     * A loader that chains multiple loaders together. Can be used to develop loading strategies to
     * load levels from multiple locations.
     * <p>
     * Loaders will be used in the order they are provided in. The result is that keys loaded from later loaders
     * will replace those loaded from earlier loaders.
     * <p>
     * Note that this mechanism only loads and overwrites individual keys. It will not apply any hierarchical
     * rules to the loaded keys.
     * <p>
     * For example:
     * <pre>
     *     l1 {
     *         a.b.c = ERROR
     *         a.b = IGNORE
     *     }
     *
     *     l2 {
     *         a.b = WARN
     *     }
     *
     *     chain(l1, l2) {
     *         a.b.c = ERROR
     *         a.b = WARN
     *     }
     * </pre>
     */
    class ChainingLoader implements LevelLoader {

        private final LevelLoader[] loaders;

        public ChainingLoader(final LevelLoader... loaders) {
            this.loaders = loaders;
        }

        @Override
        public Map<String, ValidationReport.Level> loadLevels() {
            final Map<String, ValidationReport.Level> result = new HashMap<>();
            for (LevelLoader l : loaders) {
                result.putAll(l.loadLevels());
            }
            return result;
        }

        @Override
        public Optional<ValidationReport.Level> defaultLevel() {
            for (int i = loaders.length - 1; i >= 0; i--) {
                final Optional<ValidationReport.Level> level = loaders[i].defaultLevel();
                if (level.isPresent()) {
                    return level;
                }
            }
            return Optional.empty();
        }
    }
}
