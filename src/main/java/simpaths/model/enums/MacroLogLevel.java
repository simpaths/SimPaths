package simpaths.model.enums;

/**
 * Verbosity of the macro module's yearly logging, selected by the {@code mm_macroLogging}
 * config key.
 *
 * <p>The levels are ordered, so {@link #VERBOSE} implies {@link #STATE}: the verbose
 * diagnostics are only written alongside the state summaries, and a single ordered key
 * cannot ask for one without the other.</p>
 */
public enum MacroLogLevel {

    /** No macro logging. */
    OFF,

    /** One state summary per simulated year. */
    STATE,

    /** State summaries plus iteration internals and sample micro logs. */
    VERBOSE;

    public boolean logsState() {
        return this != OFF;
    }

    public boolean isVerbose() {
        return this == VERBOSE;
    }

    public static MacroLogLevel fromConfigValue(Object raw) {
        return ConfigEnumValue.parse(MacroLogLevel.class, raw, OFF, "mm_macroLogging");
    }
}
