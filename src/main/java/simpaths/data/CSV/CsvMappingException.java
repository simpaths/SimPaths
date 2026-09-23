package simpaths.data.CSV;

/**
 * Thrown when a CSV row cannot be mapped onto an instance of the target class
 * (e.g. missing no-arg constructor, inaccessible field, or a value that
 * cannot be converted to the field's declared type).
 */
public class CsvMappingException extends RuntimeException {

    public CsvMappingException(String message) {
        super(message);
    }

    public CsvMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
