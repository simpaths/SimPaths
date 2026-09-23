package simpaths.model.enums;

import microsim.statistics.regression.IntegerValuedEnum;

public enum UnsecuredDebtState implements IntegerValuedEnum {

    None(0, false, false),
    LowCostOnly(1, true, false),
    HighCostOnly(2, false, true),
    Mixed(3, true, true);

    private final int value;
    private final boolean lowCostDebt;
    private final boolean highCostDebt;

    UnsecuredDebtState(int value, boolean lowCostDebt, boolean highCostDebt) {
        this.value = value;
        this.lowCostDebt = lowCostDebt;
        this.highCostDebt = highCostDebt;
    }

    public int getValue() {
        return value;
    }

    public boolean hasLowCostDebt() {
        return lowCostDebt;
    }

    public boolean hasHighCostDebt() {
        return highCostDebt;
    }

    public static UnsecuredDebtState fromValue(int value) {
        for (UnsecuredDebtState state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unsupported unsecured debt state " + value);
    }
}
