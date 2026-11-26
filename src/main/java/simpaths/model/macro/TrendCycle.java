package simpaths.model.macro;

/**
 * How a Ramsey trend and a DSGE cycle combine into one level.
 *
 * <p>The trend is a percent change since the handoff quarter, {@code 100 (X_trend / X_0 - 1)}; the
 * cycle is a 100&times;log deviation from that year's trend, {@code 100 ln(X / X_trend)}. The level
 * is their product, {@code X / X_0 = (1 + trend/100) exp(cycle/100)}, so a cycle keeps its size
 * however far the trend has moved. Adding the two percentages would measure the cycle against the
 * handoff-quarter level instead and shrink it by the factor {@code 1 / (1 + trend/100)}.</p>
 */
final class TrendCycle {

    private TrendCycle() {
    }

    /**
     * The level's percent change since the handoff quarter,
     * {@code 100 ((1 + trend/100) exp(cycle/100) - 1)}.
     *
     * <p>Computed as {@code trend + c (1 + trend/100)} with {@code c = 100 (exp(cycle/100) - 1)}, so
     * a zero cycle returns the trend exactly and a zero trend returns {@code c}.</p>
     *
     * @param trendPct    percent change of the trend since the handoff quarter
     * @param cycleLogPct 100&times;log deviation of the level from the trend
     */
    static double totalPct(double trendPct, double cycleLogPct) {
        double cyclePct = 100.0 * Math.expm1(cycleLogPct / 100.0);
        return trendPct + cyclePct * (1.0 + trendPct / 100.0);
    }
}
