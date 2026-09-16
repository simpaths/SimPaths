package simpaths.data.filters;

import java.util.function.Predicate;

import simpaths.model.BenefitUnit;
import simpaths.model.enums.Occupancy;
import simpaths.model.enums.Region;

/// Collection of [BenefitUnit] filters.
public class BenefitUnitFilters {
    BenefitUnitFilters() {}

    /// Filter by region.
    public static Predicate<BenefitUnit> region(Region region) {
        return b -> b.getRegion() == region;
    }

    /// Filter by occupancy.
    public static Predicate<BenefitUnit> occupancy(Occupancy occupancy) {
        return b -> b.getOccupancy() == occupancy;
    }

    /// Filter benefit units with a non-negative disposable income.
    public static Predicate<BenefitUnit> validIncome() {
        return b -> b.getEquivalisedDisposableIncomeYearly() >= 0.0;
    }
}
