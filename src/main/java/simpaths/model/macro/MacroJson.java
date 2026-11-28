package simpaths.model.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, whitespace-tolerant reader for the JSON artefacts written by the
 * MATLAB export chain ({@code model_info.json}, {@code growth_params_*.json},
 * {@code growth_terminal_state.json}).
 *
 * <p><b>Why this class exists.</b> {@link DSGEModel}, {@link RamseyTrendModel}
 * and {@link MacroModelManager} all read these files through this one class.
 * The producers differ: MATLAB's {@code jsonencode(..., 'PrettyPrint', true)}
 * writes a space after the colon, while the exporter's hand-rolled
 * pretty-printer does not. A reader that matched the literal {@code "key":}
 * would silently return its default for {@code "key": value}, so a change of
 * writer would shift a parameter to 0.0 with nothing failing — the
 * silent-agreement failure mode catalogued in
 * {@code input_processing/macromodel/docs/Integration_Contracts.md}.</p>
 *
 * <p>Everything here tolerates arbitrary whitespace around keys, colons and
 * values. This is deliberately not a general JSON parser: it does not handle
 * escaped quotes inside keys or unicode escapes, because the artefacts it reads
 * are machine-generated with a fixed vocabulary. What it does guarantee is that
 * all three consumers read the same file the same way.</p>
 *
 * <p>Lookups are not scoped to a nesting level: {@link #object(String, String)}
 * exists so a caller can narrow to a sub-object first when a key name repeats.</p>
 */
public final class MacroJson {

    private MacroJson() {
    }

    /** True when the document contains the key at all (at any nesting level). */
    public static boolean has(String json, String key) {
        return json != null && json.contains("\"" + key + "\"");
    }

    /**
     * Index of the first character of the value for {@code key}, or -1.
     * Tolerates any whitespace between the key, the colon and the value.
     */
    private static int valueStart(String json, String key) {
        if (json == null) {
            return -1;
        }
        String quoted = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int keyIdx = json.indexOf(quoted, from);
            if (keyIdx < 0) {
                return -1;
            }
            int i = keyIdx + quoted.length();
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i < json.length() && json.charAt(i) == ':') {
                i++;
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                return i;
            }
            // The match was a value, not a key (e.g. an element of a name array).
            from = keyIdx + quoted.length();
        }
    }

    /** String value, or {@code defaultValue} when absent or not a string. */
    public static String string(String json, String key, String defaultValue) {
        int start = valueStart(json, key);
        if (start < 0 || start >= json.length() || json.charAt(start) != '"') {
            return defaultValue;
        }
        start++;
        int end = json.indexOf('"', start);
        return end < 0 ? defaultValue : json.substring(start, end);
    }

    /** Integer value, or {@code defaultValue} when absent or unparseable. */
    public static int integer(String json, String key, int defaultValue) {
        int start = valueStart(json, key);
        if (start < 0) {
            return defaultValue;
        }
        StringBuilder sb = new StringBuilder();
        while (start < json.length()
                && (Character.isDigit(json.charAt(start)) || json.charAt(start) == '-')) {
            sb.append(json.charAt(start++));
        }
        if (sb.length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Double value, or {@code defaultValue} when absent or unparseable.
     * Accepts scientific notation and numbers written as JSON strings
     * ({@code "phi": "1.35"}), which the MATLAB writers have both produced.
     */
    public static double number(String json, String key, double defaultValue) {
        int start = valueStart(json, key);
        if (start < 0 || start >= json.length()) {
            return defaultValue;
        }
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            if (end < 0) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(json.substring(start, end).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        StringBuilder sb = new StringBuilder();
        while (start < json.length()) {
            char c = json.charAt(start);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
                sb.append(c);
                start++;
            } else {
                break;
            }
        }
        if (sb.length() == 0) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(sb.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Boolean value, or {@code defaultValue} when absent or not a boolean. */
    public static boolean bool(String json, String key, boolean defaultValue) {
        int start = valueStart(json, key);
        if (start < 0) {
            return defaultValue;
        }
        if (json.startsWith("true", start)) {
            return true;
        }
        if (json.startsWith("false", start)) {
            return false;
        }
        return defaultValue;
    }

    /** Elements of a flat array of strings; empty array when absent. */
    public static String[] stringArray(String json, String key) {
        int start = valueStart(json, key);
        if (start < 0 || start >= json.length() || json.charAt(start) != '[') {
            return new String[0];
        }
        int end = json.indexOf(']', start);
        if (end < 0) {
            return new String[0];
        }
        List<String> elements = new ArrayList<>();
        for (String part : json.substring(start + 1, end).split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                elements.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return elements.toArray(new String[0]);
    }

    /**
     * A flat object of string→int mappings, e.g. {@code "state_idx_in_s"}.
     * Entries whose value is not an integer are skipped; the returned map keeps
     * document order.
     */
    public static Map<String, Integer> intMap(String json, String key) {
        Map<String, Integer> map = new LinkedHashMap<>();
        String body = object(json, key);
        if (body == null) {
            return map;
        }
        for (String entry : body.split(",")) {
            String trimmed = entry.trim();
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0) {
                continue;
            }
            String k = trimmed.substring(0, colonIdx).trim().replace("\"", "");
            String v = trimmed.substring(colonIdx + 1).trim();
            try {
                map.put(k, Integer.parseInt(v));
            } catch (NumberFormatException e) {
                // Not an integer entry — skip it, as the previous readers did.
            }
        }
        return map;
    }

    /**
     * The raw text inside the braces of the object at {@code key}, or null.
     * Brace matching is string- and escape-aware so a brace inside a comment
     * string does not truncate the object.
     */
    public static String object(String json, String key) {
        int start = valueStart(json, key);
        if (start < 0 || start >= json.length() || json.charAt(start) != '{') {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        return null;
    }
}
