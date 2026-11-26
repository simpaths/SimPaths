package simpaths.model.enums;

/**
 * Which layers of the macro module run, selected by the {@code mm_macroModel} config key.
 *
 * <p>The module has two layers that are independently switchable: the Ramsey growth model,
 * which supplies the secular wage trend, and the DSGE, which supplies the business cycle
 * around it. The four constants are the only meaningful layer combinations, so one key
 * selects among them and no other combination can be configured.</p>
 *
 * <p>{@link #DSGE} is a diagnostic configuration, not a production one. The DSGE is estimated
 * on Ramsey-detrended observables and its steady state is the Ramsey path by construction, so
 * without the trend layer the cycle deviates around SimPaths' own wage-growth trend rather
 * than around the trend that identified it.</p>
 */
public enum MacroModelMode {

    /** No macro feedback. {@code mm_useMacroPathRecorder} can still record labour aggregates. */
    OFF,

    /** Ramsey trend only: the secular wage trend, no cycle. */
    RAMSEY,

    /** DSGE cycle only, on top of SimPaths' own wage trend. Diagnostic use; see the class note. */
    DSGE,

    /** Both layers: the wage SimPaths receives is the Ramsey trend plus the DSGE deviation. */
    RAMSEY_DSGE;

    public boolean isOn() {
        return this != OFF;
    }

    public boolean usesRamseyTrend() {
        return this == RAMSEY || this == RAMSEY_DSGE;
    }

    public boolean usesDsge() {
        return this == DSGE || this == RAMSEY_DSGE;
    }

    public static MacroModelMode fromConfigValue(Object raw) {
        return ConfigEnumValue.parse(MacroModelMode.class, raw, OFF, "mm_macroModel");
    }
}
