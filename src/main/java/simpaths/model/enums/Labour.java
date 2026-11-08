package simpaths.model.enums;

import microsim.statistics.regression.IntegerValuedEnum;
import org.apache.commons.math3.distribution.NormalDistribution;
import simpaths.data.Parameters;
import simpaths.data.UkhlsHoursDistribution;
import simpaths.model.Person;

/**
 * An enumeration representing different categories of weekly labour supply (work hours) provided by persons.
 */

public enum Labour implements IntegerValuedEnum {

    //Represents hours of work per week that a Person will supply to firms
    //Representative hours are used to score the labour-supply choice
    //Realised hours are drawn within [minBound, maxBound] according to distributionOption after the labour-supply choice
    ZERO(0, 0, 5, DistributionOption.UNIFORM), // Note: ZERO always returns 0 continuous hours but maxBound is specified as 5 here to remain consistent with the discretization used in the data
    TEN(10, 6, 15, DistributionOption.OBSERVED_LHW),
    TWENTY(20, 16, 25, DistributionOption.OBSERVED_LHW),
    THIRTY(30, 26, 35, DistributionOption.OBSERVED_LHW),
    THIRTY_EIGHT(38, 36, 40, DistributionOption.OBSERVED_LHW),
    FORTY_FIVE(45, 41, 49, DistributionOption.OBSERVED_LHW),
    FIFTY_FIVE(55, 50, 126, DistributionOption.OBSERVED_LHW);

    /** How realised (non-representative) hours are drawn within a bracket when continuous hours are on. */
    public enum DistributionOption {
        LOGNORMAL,      // sampled from the calibrated log-normal distribution
        UNIFORM,        // each whole hour in [minBound, maxBound] is equally likely
        OBSERVED_LHW    // drawn from the UKHLS within-band distribution of observed hours (UkhlsHoursDistribution.draw); falls back to UNIFORM if unavailable
    }

    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution();
    private static final double FIFTY_FIVE_LOG_NORMAL_MU = 4.052454;
    private static final double FIFTY_FIVE_LOG_NORMAL_SD = 0.1668883;
    private static final int FIFTY_FIVE_MIN_CONTINUOUS_HOURS = 50;
    private static final double CDF_EPSILON = 1.e-9;

    private final int hours;
    private final int minBound;
    private final int maxBound;
    private final DistributionOption distributionOption;

    Labour(int hours, int minBound, int maxBound, DistributionOption distributionOption) {
        this.hours = hours;
        this.minBound = minBound;
        this.maxBound = maxBound;
        this.distributionOption = distributionOption;
    }

    @Override
    public int getValue() {return hours;}

    /** This bracket's representative hours, used to score the labour-supply choice. */
    public int getRepresentativeHours() {
        return hours;
    }

	/**
	 * Converts hours worked (int) to the corresponding labour category.
	 *
	 * @param hoursWorked The hours worked.
	 * @return The labour category.
	 */
    public static Labour convertHoursToLabour(int hoursWorked) {
        return convertHoursToLabourInternal(hoursWorked);
    }

	/**
	 * Converts hours worked (double) to the corresponding labour category.
	 *
	 * @param hoursWorked The hours worked.
	 * @return The labour category.
	 */
    public static Labour convertHoursToLabour(double hoursWorked) {
        return convertHoursToLabourInternal((int) hoursWorked);
    }

    private static Labour convertHoursToLabourInternal(int hoursWorked) {
        if (hoursWorked <= 5) {
            return Labour.ZERO;
        } else if (hoursWorked <= 15) {
            return Labour.TEN;
        } else if (hoursWorked <= 25) {
            return Labour.TWENTY;
        } else if (hoursWorked <= 35) {
            return Labour.THIRTY;
        } else if (hoursWorked <= 40) {
            return Labour.THIRTY_EIGHT;
        } else if (hoursWorked <= 49) {
            return Labour.FORTY_FIVE;
        } else {
            return Labour.FIFTY_FIVE;
        }
    }

    /** Each whole hour in [minBound, maxBound] gets an equal share of the unit interval; min() guards a draw of exactly 1. */
    private int uniformHours(double draw) {
        return Math.min(minBound + (int) (draw * (maxBound - minBound + 1)), maxBound);
    }

	/**
	 * Gets the hours of work based on the labour category and the person's draw from the uniform distribution.
	 * While Parameters.useRepresentativeHoursInUtility is true and the person's labour-supply choice is being
	 * evaluated (Person.isEvaluatingLabourChoice), returns this bracket's representative hours, so the choice is
	 * scored consistently across persons regardless of their individually drawn hours.
	 * Otherwise, if discretized hours are used, returns the representative hours. If continuous hours are used,
	 * returns realised hours drawn according to distributionOption, within minBound and maxBound.
	 * Note: ZERO category always returns 0 hours of work, irrespective of any of the above.
	 *
	 * @param person The person for whom the hours are calculated.
	 * @return The calculated hours of work.
	 */
    public int getHours(Person person) {
        // There were some cases in BenefitUnit where person can be null but hours of work still needed to be obtained where individual is a single adult and labour key composed of two values needs to be defined.
        // I replaced that with a 0. value, instead of converting a ZERO labour key to hours, so person should never be null.
        // However, added a check for null persons which should result in a default number of hours returned in such cases.
        if (this == Labour.ZERO || person == null) {
            return hours;
        }

        if (Parameters.useRepresentativeHoursInUtility && person.isEvaluatingLabourChoice()) {
            return hours;
        }

        if (!Parameters.USE_CONTINUOUS_LABOUR_SUPPLY_HOURS) {
            return hours;
        }

        // Continuous hours are based on person's randomly drawn value. This can be considered person's "type", for example, a person always works hours in the bottom 10% of a (uniformly distributed) labour supply bracket.
        double draw = person.getLabourSupplySingleDraw();

        return switch (distributionOption) {
            case OBSERVED_LHW -> {
                UkhlsHoursDistribution observed = Parameters.getUkhlsHoursDistribution();
                if (observed != null) {
                    int drawnHours = observed.draw(person.getUkhlsLhwSheet(), minBound, maxBound, draw);
                    if (drawnHours > 0) yield drawnHours;
                }
                // no observed hours for this band/sheet: fall back to a uniform draw within the bracket
                yield uniformHours(draw);
            }
            case LOGNORMAL -> {
                double boundedCdf = Math.max(CDF_EPSILON, Math.min(1.0 - CDF_EPSILON, draw));
                double gauss = STANDARD_NORMAL.inverseCumulativeProbability(boundedCdf);
                int drawnHours = (int) Math.round(Math.exp(FIFTY_FIVE_LOG_NORMAL_MU + FIFTY_FIVE_LOG_NORMAL_SD * gauss));
                yield Math.max(FIFTY_FIVE_MIN_CONTINUOUS_HOURS, Math.min(drawnHours, maxBound));
            }
            case UNIFORM -> uniformHours(draw);
        };
    }

}
