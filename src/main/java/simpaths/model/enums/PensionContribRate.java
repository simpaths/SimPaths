package simpaths.model.enums;

import microsim.statistics.regression.IntegerValuedEnum;

public enum PensionContribRate implements IntegerValuedEnum {
    Other(-1),
    Zero(0),
    Three(3),
    Five(5);

    private final int value;
    PensionContribRate(int val) { value = val; }

    @Override
    public int getValue() {return value;}
}
