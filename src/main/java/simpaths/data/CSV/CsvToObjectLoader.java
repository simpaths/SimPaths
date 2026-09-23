package simpaths.data.CSV;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reads a CSV file and populates a collection whose elements are instances
 * of a caller-supplied class, one per data row.
 *
 * <p>The first line of the file is treated as a header row. Each header
 * name is matched (case-insensitively, ignoring spaces/underscores/hyphens)
 * to a declared field of the target class, and that field is populated with
 * the corresponding column's value from each subsequent row, converted to
 * the field's declared type.
 *
 * <p>The target class must have an accessible no-argument constructor.
 *
 * <p>Two entry points are provided, differing only in the collection they
 * return:
 * <ul>
 *   <li>{@link #load} returns a {@link LinkedHashSet}. Duplicate rows (per
 *       the target class's {@code equals}/{@code hashCode}) collapse into a
 *       single entry, and insertion order (i.e. file order) is preserved.
 *   <li>{@link #loadList} returns an {@link ArrayList}. Every row becomes
 *       an element, including exact duplicates, in file order.
 * </ul>
 *
 * <p>Supported field types: {@code String}, all primitive types and their
 * wrapper classes, {@code enum} types, {@link BigDecimal}, {@link LocalDate},
 * and {@link LocalDateTime}.
 *
 * <p>Example:
 * <pre>{@code
 * LinkedHashSet<Employee> uniqueEmployees =
 *     CsvToObjectLoader.load("employees.csv", Employee.class);
 *
 * ArrayList<Employee> allRows =
 *     CsvToObjectLoader.loadList("employees.csv", Employee.class);
 * }</pre>
 */
public final class CsvToObjectLoader {

    private CsvToObjectLoader() {
        // utility class; no instances
    }

    /**
     * Loads {@code filePath} as a CSV file and maps each data row onto a new
     * instance of {@code targetClass}, collecting the results into a
     * {@link LinkedHashSet} (duplicate rows, per the target class's {@code
     * equals}/{@code hashCode}, collapse into a single entry).
     *
     * @param filePath    path to the CSV file
     * @param targetClass class of object to create for each row; must have
     *                    an accessible no-argument constructor
     * @param <T>         the row object type
     * @return a LinkedHashSet containing one populated instance per unique
     *         data row, in file order
     * @throws IOException          if the file cannot be read
     * @throws CsvMappingException  if a row cannot be mapped onto the target
     *                              class (bad constructor, inaccessible
     *                              field, or unconvertible value)
     */
    public static <T> LinkedHashSet<T> load(String filePath, Class<T> targetClass) throws IOException {
        return new LinkedHashSet<>(readRows(filePath, targetClass));
    }

    /**
     * Loads {@code filePath} as a CSV file and maps each data row onto a new
     * instance of {@code targetClass}, collecting the results into an
     * {@link ArrayList}. Unlike {@link #load}, every row becomes an element
     * &mdash; exact duplicate rows are not collapsed.
     *
     * @param filePath    path to the CSV file
     * @param targetClass class of object to create for each row; must have
     *                    an accessible no-argument constructor
     * @param <T>         the row object type
     * @return an ArrayList containing one populated instance per data row,
     *         in file order
     * @throws IOException          if the file cannot be read
     * @throws CsvMappingException  if a row cannot be mapped onto the target
     *                              class (bad constructor, inaccessible
     *                              field, or unconvertible value)
     */
    public static <T> ArrayList<T> loadList(String filePath, Class<T> targetClass) throws IOException {
        return readRows(filePath, targetClass);
    }

    /**
     * Parses every data row in {@code filePath} into an instance of {@code
     * targetClass}, in file order. Shared by {@link #load} and {@link
     * #loadList}, which differ only in what collection they wrap this list
     * in.
     */
    private static <T> ArrayList<T> readRows(String filePath, Class<T> targetClass) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath must not be null");
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass must not be null");
        }

        ArrayList<T> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return results; // empty file: no header, no rows
            }

            String[] headers = splitCsvLine(headerLine);
            trimAll(headers);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue; // skip blank lines
                }

                String[] values = splitCsvLine(line);
                trimAll(values);

                T instance = createInstance(targetClass, headers, values, lineNumber, filePath);
                results.add(instance);
            }
        }

        return results;
    }

    private static <T> T createInstance(Class<T> targetClass, String[] headers, String[] values,
                                        int lineNumber, String filePath) {
        T instance;
        try {
            java.lang.reflect.Constructor<T> constructor = targetClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new CsvMappingException(
                    "Unable to instantiate " + targetClass.getName()
                            + ". It must have an accessible no-argument constructor.", e);
        }

        int columnCount = Math.min(headers.length, values.length);
        for (int i = 0; i < columnCount; i++) {
            setField(instance, targetClass, headers[i], values[i], lineNumber, filePath);
        }

        return instance;
    }

    private static <T> void setField(T instance, Class<T> targetClass, String columnName,
                                     String rawValue, int lineNumber, String filePath) {
        Field field = findField(targetClass, columnName);
        if (field == null) {
            // No field on the target class matches this column; skip it.
            return;
        }

        field.setAccessible(true);
        try {
            Object convertedValue = convertValue(rawValue, field.getType());
            field.set(instance, convertedValue);
        } catch (IllegalAccessException e) {
            throw new CsvMappingException(
                    "Unable to set field '" + field.getName() + "' on " + targetClass.getName()
                            + " (row " + lineNumber + " of " + filePath + ").", e);
        } catch (RuntimeException e) {
            throw new CsvMappingException(
                    "Unable to convert value '" + rawValue + "' for field '" + field.getName()
                            + "' of type " + field.getType().getSimpleName()
                            + " (row " + lineNumber + " of " + filePath + ").", e);
        }
    }

    /**
     * Finds a declared field (searching up the class hierarchy) whose name
     * matches the given CSV column name, ignoring case, spaces, underscores,
     * and hyphens. This lets a column like "first_name" or "First Name"
     * match a field named {@code firstName}.
     */
    private static Field findField(Class<?> targetClass, String columnName) {
        String normalizedColumn = normalize(columnName);
        for (Class<?> current = targetClass; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue;
                }
                if (normalize(field.getName()).equals(normalizedColumn)) {
                    return field;
                }
            }
        }
        return null;
    }

    private static String normalize(String name) {
        return name.replaceAll("[\\s_-]+", "").toLowerCase();
    }

    private static Object convertValue(String rawValue, Class<?> fieldType) {
        if (rawValue == null || rawValue.isEmpty()) {
            return fieldType.isPrimitive() ? defaultPrimitiveValue(fieldType) : null;
        }

        if (fieldType == String.class) {
            return rawValue;
        } else if (fieldType == int.class || fieldType == Integer.class) {
            return Integer.parseInt(rawValue);
        } else if (fieldType == long.class || fieldType == Long.class) {
            return Long.parseLong(rawValue);
        } else if (fieldType == double.class || fieldType == Double.class) {
            return Double.parseDouble(rawValue);
        } else if (fieldType == float.class || fieldType == Float.class) {
            return Float.parseFloat(rawValue);
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return Boolean.parseBoolean(rawValue);
        } else if (fieldType == short.class || fieldType == Short.class) {
            return Short.parseShort(rawValue);
        } else if (fieldType == byte.class || fieldType == Byte.class) {
            return Byte.parseByte(rawValue);
        } else if (fieldType == char.class || fieldType == Character.class) {
            return rawValue.charAt(0);
        } else if (fieldType.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf((Class<Enum>) fieldType, rawValue);
            return enumValue;
        } else if (fieldType == BigDecimal.class) {
            return new BigDecimal(rawValue);
        } else if (fieldType == LocalDate.class) {
            return LocalDate.parse(rawValue);
        } else if (fieldType == LocalDateTime.class) {
            return LocalDateTime.parse(rawValue);
        }

        throw new IllegalArgumentException("Unsupported field type: " + fieldType.getName());
    }

    private static Object defaultPrimitiveValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return " ";
        return null;
    }

    private static void trimAll(String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                values[i] = values[i].trim();
            }
        }
    }

    /**
     * Splits a single CSV line on commas. Supports double-quoted fields
     * (so a quoted field may itself contain commas) and "" as an escaped
     * quote inside a quoted field. This is a lightweight parser intended
     * for well-formed CSV, not a full RFC 4180 implementation.
     */
    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }
}
