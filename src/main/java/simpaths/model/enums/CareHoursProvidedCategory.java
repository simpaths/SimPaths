package simpaths.model.enums;

import microsim.statistics.regression.IntegerValuedEnum;

/**
 * Ordered categories for weekly informal-care hours provided.
 *
 * <p>The integer values match the S3c/S3d ordered-logit outcome categories.
 * Representative hours reproduce the values assigned in the source-data
 * preparation and regression-estimation scripts.</p>
 */
public enum CareHoursProvidedCategory implements IntegerValuedEnum {

    Hours2(1, 2.0),
    Hours7(2, 7.0),
    Hours14_5(3, 14.5),
    Hours27(4, 27.0),
    Hours42(5, 42.0),
    Hours74_5(6, 74.5),
    Hours120(7, 120.0);

    private final int value;
    private final double representativeHours;

    CareHoursProvidedCategory(int value, double representativeHours) {
        this.value = value;
        this.representativeHours = representativeHours;
    }

    @Override
    public int getValue() {
        return value;
    }

    public double getRepresentativeHours() {
        return representativeHours;
    }
}
