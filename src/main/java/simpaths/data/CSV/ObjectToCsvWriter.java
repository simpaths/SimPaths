package simpaths.data.CSV;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Writes a {@link List} of objects to a CSV file, one row per element, in
 * list order.
 *
 * <p>This is the mirror image of {@link CsvToObjectLoader}: given a target
 * class, it reflects over that class's declared instance fields (walking up
 * the class hierarchy, base class first, the same way {@code
 * CsvToObjectLoader} walks it when matching columns) and writes one header
 * row of field names followed by one data row per element of the list, in
 * that list's order &mdash; the natural counterpart to {@link
 * CsvToObjectLoader#loadList}, which returns rows as an {@link ArrayList}.
 *
 * <p>Field values are converted with {@link Object#toString()} ({@code
 * null} becomes an empty field), <b>except</b> when a field's declared type
 * is not one of the "simple" types {@code CsvToObjectLoader} natively
 * understands (a primitive, its wrapper, {@code String}, an {@code enum},
 * {@link BigDecimal}, {@link LocalDate}, or {@link LocalDateTime}). For such
 * a "complex" attribute, if its runtime value's class declares an {@code
 * int}/{@code Integer} field named {@code id} (case-insensitive, searched up
 * the value's own class hierarchy), that id is written instead of the
 * object's {@code toString()} &mdash; e.g. a {@code Department department}
 * field holding a {@code Department} with {@code id = 7} writes {@code 7},
 * not {@code Department@6bc7c054} or a hand-written {@code toString()}. If
 * no such {@code id} field is found, the value's own {@code toString()} is
 * used as a fallback.
 *
 * <p>Any value containing a comma, double quote, or newline is wrapped in
 * double quotes with embedded quotes doubled, matching standard CSV quoting
 * and the quoting {@link CsvToObjectLoader} already understands when
 * reading the file back &mdash; so a round trip through {@code write} and
 * then {@code load}/{@code loadList} reproduces the original field values
 * (for simple-typed fields; a complex field round-trips only as the id, not
 * the original object, since that's all the CSV row captures).
 *
 * <p>Example:
 * <pre>{@code
 * ArrayList<Employee> employees = CsvToObjectLoader.loadList("in.csv", Employee.class);
 * ObjectToCsvWriter.write("out.csv", employees, Employee.class);
 * }</pre>
 *
 * <p>{@link Collection}-accepting overloads are also provided for callers
 * still working with a {@link java.util.LinkedHashSet} or other {@code
 * Collection}; those write rows in that collection's own iteration order.
 */
public final class ObjectToCsvWriter {

    private ObjectToCsvWriter() {
        // utility class; no instances
    }

    /**
     * Writes {@code data} to {@code filePath} as CSV, one row per element in
     * list order, using the declared instance fields of {@code targetClass}
     * as columns.
     *
     * @param filePath    path of the CSV file to create/overwrite
     * @param data        the objects to write, in the order they should
     *                    appear as rows; may be empty (a header-only file
     *                    is written) but not null
     * @param targetClass the class whose fields define the CSV columns
     * @param <T>         the row object type
     * @throws IOException if the file cannot be written
     * @throws CsvMappingException if a field cannot be read via reflection
     */
    public static <T> void write(String filePath, List<T> data, Class<T> targetClass) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath must not be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass must not be null");
        }

        List<Field> fields = collectFields(targetClass);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(buildLine(fieldNames(fields)));
            writer.newLine();

            for (T item : data) {
                writer.write(buildLine(fieldValues(item, fields, targetClass)));
                writer.newLine();
            }
        }
    }

    /**
     * Convenience overload that infers the target class from the first
     * element of {@code data}. {@code data} must be non-null and non-empty,
     * since an empty list carries no runtime type to infer from.
     *
     * @throws IllegalArgumentException if {@code data} is null or empty
     */
    public static <T> void write(String filePath, List<T> data) throws IOException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot infer the target class from a null/empty list; "
                            + "use write(filePath, data, targetClass) instead.");
        }

        @SuppressWarnings("unchecked")
        Class<T> inferredClass = (Class<T>) data.iterator().next().getClass();
        write(filePath, data, inferredClass);
    }

    /**
     * {@link Collection} counterpart to {@link #write(String, List, Class)}
     * for callers passing a non-{@code List} collection (e.g. a {@link
     * java.util.LinkedHashSet}). Rows are written in that collection's own
     * iteration order. A {@code List} argument resolves to the more
     * specific overload above instead of this one.
     *
     * @throws IOException if the file cannot be written
     * @throws CsvMappingException if a field cannot be read via reflection
     */
    public static <T> void write(String filePath, Collection<T> data, Class<T> targetClass) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        write(filePath, data instanceof List ? (List<T>) data : new ArrayList<>(data), targetClass);
    }

    /**
     * {@link Collection} counterpart to {@link #write(String, List)} that
     * also infers the target class from the first element.
     *
     * @throws IllegalArgumentException if {@code data} is null or empty
     */
    public static <T> void write(String filePath, Collection<T> data) throws IOException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot infer the target class from a null/empty collection; "
                            + "use write(filePath, data, targetClass) instead.");
        }
        write(filePath, data instanceof List ? (List<T>) data : new ArrayList<>(data));
    }

    /**
     * Collects the non-static, non-synthetic declared fields of {@code
     * targetClass}, walking up the class hierarchy (excluding {@code
     * Object}) so that base-class fields come first and the most-derived
     * class's own fields come last. This produces a stable, predictable
     * column order.
     */
    private static List<Field> collectFields(Class<?> targetClass) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = targetClass; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(current);
        }

        List<Field> fields = new ArrayList<>();
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            for (Field field : hierarchy.get(i).getDeclaredFields()) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static List<String> fieldNames(List<Field> fields) {
        List<String> names = new ArrayList<>(fields.size());
        for (Field field : fields) {
            names.add(field.getName());
        }
        return names;
    }

    private static <T> List<String> fieldValues(T item, List<Field> fields, Class<T> targetClass) {
        List<String> values = new ArrayList<>(fields.size());
        for (Field field : fields) {
            field.setAccessible(true);
            Object rawValue;
            try {
                rawValue = field.get(item);
            } catch (IllegalAccessException e) {
                throw new CsvMappingException(
                        "Unable to read field '" + field.getName() + "' on " + targetClass.getName() + ".", e);
            }
            values.add(valueToCsvString(rawValue, field, targetClass));
        }
        return values;
    }

    /**
     * Converts one field's value to the string that goes in the CSV cell.
     * {@code null} becomes an empty string. A value whose field is a
     * "simple" type (see {@link #isSimpleType}) is converted with {@code
     * toString()}. A "complex" value is instead represented by its own
     * {@code id} field, if it has an {@code int}/{@code Integer} one (see
     * {@link #findIdField}); otherwise it falls back to {@code toString()}.
     */
    private static String valueToCsvString(Object rawValue, Field field, Class<?> targetClass) {
        if (rawValue == null) {
            return "";
        }

        if (isSimpleType(field.getType())) {
            return rawValue.toString();
        }

        Field idField = findIdField(rawValue.getClass());
        if (idField == null) {
            // No int/Integer "id" field on the referenced object; fall back
            // to its own toString().
            return rawValue.toString();
        }

        idField.setAccessible(true);
        try {
            Object idValue = idField.get(rawValue);
            return idValue == null ? "" : idValue.toString();
        } catch (IllegalAccessException e) {
            throw new CsvMappingException(
                    "Unable to read 'id' field on " + rawValue.getClass().getName()
                            + " (referenced by field '" + field.getName() + "' of " + targetClass.getName() + ").", e);
        }
    }

    /**
     * True for the field types {@code CsvToObjectLoader} natively parses
     * from a plain CSV cell: primitives and their wrappers (covering {@code
     * int}/{@code Integer}, {@code boolean}/{@code Boolean}, etc.), {@code
     * String}, {@code enum} constants, {@link BigDecimal}, {@link
     * LocalDate}, and {@link LocalDateTime}. Anything else is treated as a
     * "complex" attribute for id-substitution purposes.
     */
    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || Number.class.isAssignableFrom(type) // Integer, Long, Double, Float, Short, Byte, BigDecimal, ...
                || type == Boolean.class
                || type == Character.class
                || type.isEnum()
                || type == LocalDate.class
                || type == LocalDateTime.class;
    }

    /**
     * Searches {@code valueClass} and its superclasses (excluding {@code
     * Object}) for a non-static, non-synthetic field named {@code id}
     * (case-insensitive) whose type is {@code int} or {@code Integer}.
     * Returns {@code null} if none is found.
     */
    private static Field findIdField(Class<?> valueClass) {
        for (Class<?> current = valueClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field candidate : current.getDeclaredFields()) {
                if (candidate.isSynthetic() || Modifier.isStatic(candidate.getModifiers())) {
                    continue;
                }
                if (candidate.getName().equalsIgnoreCase("id")
                        && (candidate.getType() == int.class || candidate.getType() == Integer.class)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String buildLine(List<String> rawFields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < rawFields.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escapeCsvField(rawFields.get(i)));
        }
        return line.toString();
    }

    /**
     * Quotes a field if it contains a comma, double quote, or newline,
     * doubling any embedded double quotes &mdash; the same convention
     * {@link CsvToObjectLoader}'s line parser already understands.
     */
    private static String escapeCsvField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
