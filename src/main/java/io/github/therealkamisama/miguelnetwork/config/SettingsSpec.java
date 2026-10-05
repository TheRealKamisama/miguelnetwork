package io.github.therealkamisama.miguelnetwork.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Startup-only TOML settings. Existing files are never rewritten or corrected silently. */
public final class SettingsSpec {
    private final Map<String, ConfigValue<?>> values;

    private SettingsSpec(Map<String, ConfigValue<?>> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public synchronized void load(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            if (!Files.exists(path)) {
                CommentedConfig defaults = CommentedConfig.inMemory();
                values.forEach((key, value) -> {
                    defaults.set(key, value.defaultValue instanceof Enum<?> e ? e.name() : value.defaultValue);
                    defaults.setComment(key, value.comment);
                });
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                    new TomlWriter().write(defaults, writer);
                }
            }
            try (CommentedFileConfig config = CommentedFileConfig.of(path)) {
                config.load();
                Map<ConfigValue<?>, Object> loaded = new LinkedHashMap<>();
                values.forEach((key, value) -> {
                    Object raw = config.contains(key) ? config.get(key) : value.defaultValue;
                    try {
                        loaded.put(value, value.parse.apply(raw));
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException("Invalid setting " + key + " in " + path, exception);
                    }
                });
                loaded.forEach(ConfigValue::setParsed);
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load MiguelNetwork settings from " + path, exception);
        }
    }

    public static class ConfigValue<T> {
        private final T defaultValue;
        private final Function<Object, T> parse;
        private String comment = "";
        private volatile T value;
        private ConfigValue(T defaultValue, Function<Object, T> parse) {
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.parse = parse;
        }
        public T get() { return value; }
        @SuppressWarnings("unchecked")
        private void setParsed(Object parsed) { value = (T) parsed; }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        private BooleanValue(boolean fallback) {
            super(fallback, raw -> {
                if (raw instanceof Boolean result) return result;
                throw new IllegalArgumentException("Expected a boolean");
            });
        }
    }
    public static final class IntValue extends ConfigValue<Integer> {
        private IntValue(int fallback, int min, int max) { super(fallback, raw -> (int) integer(raw, min, max)); }
    }
    public static final class LongValue extends ConfigValue<Long> {
        private LongValue(long fallback, long min, long max) { super(fallback, raw -> integer(raw, min, max)); }
    }
    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        private EnumValue(E fallback) {
            super(fallback, raw -> {
                if (fallback.getDeclaringClass().isInstance(raw)) return fallback.getDeclaringClass().cast(raw);
                if (raw instanceof String text) return Enum.valueOf(fallback.getDeclaringClass(), text);
                throw new IllegalArgumentException("Expected " + fallback.getDeclaringClass().getSimpleName());
            });
        }
    }
    private static long integer(Object raw, long min, long max) {
        if (!(raw instanceof Integer || raw instanceof Long)) throw new IllegalArgumentException("Expected an integer");
        long result = ((Number) raw).longValue();
        if (result < min || result > max) throw new IllegalArgumentException("Expected range " + min + ".." + max);
        return result;
    }

    public static final class Builder {
        private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
        private final List<String> sections = new ArrayList<>();
        private String comment = "";
        public Builder comment(String... lines) { comment = String.join("\n", lines); return this; }
        public Builder push(String name) { sections.add(name); return this; }
        public Builder pop() { sections.remove(sections.size() - 1); return this; }
        public SettingsSpec build() { return new SettingsSpec(values); }
        public BooleanValue define(String key, boolean fallback) { return add(key, new BooleanValue(fallback)); }
        public ConfigValue<String> define(String key, String fallback) { return define(key, fallback, raw -> true); }
        public ConfigValue<String> define(String key, String fallback, Predicate<Object> valid) {
            return add(key, new ConfigValue<>(fallback, raw -> {
                if (raw instanceof String text && valid.test(text)) return text;
                throw new IllegalArgumentException("Expected a valid string");
            }));
        }
        public IntValue defineInRange(String key, int fallback, int min, int max) { return add(key, new IntValue(fallback, min, max)); }
        public LongValue defineInRange(String key, long fallback, long min, long max) { return add(key, new LongValue(fallback, min, max)); }
        public <E extends Enum<E>> EnumValue<E> defineEnum(String key, E fallback) { return add(key, new EnumValue<>(fallback)); }
        private <V extends ConfigValue<?>> V add(String key, V value) {
            String path = sections.isEmpty() ? key : String.join(".", sections) + "." + key;
            ((ConfigValue<?>) value).comment = comment;
            comment = "";
            if (values.putIfAbsent(path, value) != null) throw new IllegalArgumentException("Duplicate setting " + path);
            return value;
        }
    }
}
