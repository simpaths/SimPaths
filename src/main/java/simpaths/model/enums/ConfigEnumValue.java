package simpaths.model.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Parses YAML and command-line configuration values into the macro module's mode enums.
 *
 * <p>SnakeYAML applies the YAML 1.1 boolean resolver, so {@code off}, {@code no} and
 * {@code false} (in any capitalisation) are converted to {@link Boolean} before this code
 * sees them: an unquoted {@code mm_macroModel: off} arrives as {@code Boolean.FALSE}, not as
 * the string "off". Mapping {@code false} onto each enum's disabled constant keeps the
 * natural spelling working without forcing quotes into every config file. {@code true} is
 * rejected, because it does not identify which of the enabled modes was meant.</p>
 */
public final class ConfigEnumValue {

    private ConfigEnumValue() {
    }

    /**
     * @param type          the enum to parse into
     * @param raw           the value as delivered by SnakeYAML or the CLI parser
     * @param disabledValue the constant that {@code false} maps to
     * @param key           the configuration key, used in error messages
     */
    public static <E extends Enum<E>> E parse(Class<E> type, Object raw, E disabledValue, String key) {
        if (raw == null) {
            throw new IllegalArgumentException(key + " must not be null; expected one of " + valueList(type));
        }
        if (raw instanceof Boolean flag) {
            if (flag) {
                throw new IllegalArgumentException(key + " is a mode, not a switch: 'true' does not say which"
                        + " mode was meant. Use one of " + valueList(type) + ".");
            }
            return disabledValue;
        }
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        String token = raw.toString().trim().replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(token)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown value '" + raw + "' for " + key
                + "; expected one of " + valueList(type) + ".");
    }

    /** Lower-case constant names, the spelling the config files use. */
    public static String valueList(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(c -> c.name().toLowerCase())
                .collect(Collectors.joining(" | "));
    }
}
