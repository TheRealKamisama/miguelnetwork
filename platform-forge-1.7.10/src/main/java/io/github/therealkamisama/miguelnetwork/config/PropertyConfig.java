package io.github.therealkamisama.miguelnetwork.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;

/** Small platform-local configuration schema. Existing files are read, never rewritten. */
public final class PropertyConfig {
    private final Map<String, ConfigValue<?>> values;

    private PropertyConfig(Map<String, ConfigValue<?>> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public void load(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        // Validate the entire document before applying any value.
        Map<ConfigValue<?>, Object> parsed = new LinkedHashMap<>();
        try {
            for (var entry : values.entrySet()) {
                ConfigValue<?> value = entry.getValue();
                String text = properties.getProperty(entry.getKey());
                parsed.put(value, text == null ? value.defaultValue : value.parse(text));
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid MiguelNetwork configuration: " + file.getFileName(), exception);
        }
        parsed.forEach(ConfigValue::assign);
        if (!Files.exists(file)) {
            values.forEach((key, value) -> properties.setProperty(key, value.defaultValue.toString()));
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                properties.store(writer, "MiguelNetwork Forge 1.7.10; UTF-8 Java properties; restart to apply changes");
            }
        }
    }

    public static class ConfigValue<T> {
        private final T defaultValue;
        private final Function<String, T> parser;
        private final Predicate<T> validator;
        private volatile T value;

        ConfigValue(T defaultValue, Function<String, T> parser, Predicate<T> validator) {
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.parser = parser;
            this.validator = validator;
        }

        public T get() { return value; }

        private T parse(String text) {
            T parsed = parser.apply(text.trim());
            if (!validator.test(parsed)) throw new IllegalArgumentException("Invalid value: " + text);
            return parsed;
        }

        @SuppressWarnings("unchecked")
        private void assign(Object parsed) { value = (T) parsed; }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(boolean value) {
            super(value, text -> {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Expected true or false");
                }
                return Boolean.parseBoolean(text);
            }, ignored -> true);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        IntValue(int value, int min, int max) { super(value, Integer::parseInt, v -> v >= min && v <= max); }
    }

    public static final class LongValue extends ConfigValue<Long> {
        LongValue(long value, long min, long max) { super(value, Long::parseLong, v -> v >= min && v <= max); }
    }

    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        EnumValue(E value) {
            super(value, text -> Enum.valueOf(value.getDeclaringClass(), text.toUpperCase(java.util.Locale.ROOT)), v -> true);
        }
    }

    public static final class Builder {
        private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
        private final ArrayDeque<String> sections = new ArrayDeque<>();

        public Builder comment(String... comments) { return this; }
        public Builder push(String section) { sections.addLast(section); return this; }
        public Builder pop() { sections.removeLast(); return this; }
        public BooleanValue define(String key, boolean value) { return add(key, new BooleanValue(value)); }
        public ConfigValue<String> define(String key, String value) { return define(key, value, ignored -> true); }
        public ConfigValue<String> define(String key, String value, Predicate<Object> validator) {
            return add(key, new ConfigValue<>(value, Function.identity(), validator::test));
        }
        public IntValue defineInRange(String key, int value, int min, int max) {
            return add(key, new IntValue(value, min, max));
        }
        public LongValue defineInRange(String key, long value, long min, long max) {
            return add(key, new LongValue(value, min, max));
        }
        public <E extends Enum<E>> EnumValue<E> defineEnum(String key, E value) {
            return add(key, new EnumValue<>(value));
        }
        private <T extends ConfigValue<?>> T add(String key, T value) {
            String fullKey = sections.isEmpty() ? key : String.join(".", sections) + "." + key;
            if (values.putIfAbsent(fullKey, value) != null) throw new IllegalArgumentException("Duplicate key " + fullKey);
            return value;
        }
        public PropertyConfig build() { return new PropertyConfig(values); }
    }
}
