package simpaths.data.filters;

import java.util.function.Predicate;

import simpaths.data.Parameters;
import simpaths.model.Person;
import simpaths.model.enums.Education;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Indicator;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.Region;

/// Collection of [Person] filters.
public class Filters {
    private Filters() {
    }

    /// Filter the requested gender.
    public static Predicate<Person> gender(Gender gender) {
        return p -> p.getDemMaleFlag() == gender;
    }

    /// Filter male persons.
    public static Predicate<Person> male() {
        return gender(Gender.Male);
    }

    /// Filter female persons.
    public static Predicate<Person> female() {
        return gender(Gender.Female);
    }

    /// Filter persons strictly younger than given age.
    public static Predicate<Person> youngerStrict(int age) {
        return p -> p.getDemAge() < age;
    }

    /// Filter persons younger than given age, that age included.
    public static Predicate<Person> youngerOr(int age) {
        return p -> p.getDemAge() <= age;
    }

    /// Filter persons older than given age, that age included.
    public static Predicate<Person> olderOr(int age) {
        return p -> p.getDemAge() >= age;
    }

    /// Filter given age range (both ends are included).
    public static Predicate<Person> ageRange(int from, int to) {
        return p -> p.getDemAge() >= from && p.getDemAge() <= to;
    }

    /// Filter persons with at least one child in the given age range (both
    /// ends included).
    public static Predicate<Person> hasChildInAgeRange(int from, int to) {
        return p -> p.getBenefitUnit().getIndicatorChildren(from, to) == Indicator.True;
    }

    /// Filter children (i.e. strictly younger than
    /// [Parameters#AGE_TO_BECOME_RESPONSIBLE]).
    public static Predicate<Person> child() {
        return youngerStrict(Parameters.AGE_TO_BECOME_RESPONSIBLE);
    }

    /// Filter by region.
    public static Predicate<Person> region(Region region) {
        return p -> p.getRegion() == region;
    }

    /// Filter by education.
    public static Predicate<Person> education(Education education) {
        return p -> p.getEduHighestC4() == education;
    }

    /// Filter by employment status.
    public static Predicate<Person> employment(Les_c4 status) {
        return p -> p.getLabC4() == status;
    }

    /// Filter by employment history.
    public static Predicate<Person> employmentHistory(Les_c4 employmentLag1) {
        return p -> p.getLabC4L1() == employmentLag1;
    }

    /// Filter persons without a partner.
    public static Predicate<Person> single() {
        return p -> p.getPartner() == null;
    }

    /// Filter women susceptible to becoming pregnant.
    public static Predicate<Person> fertile() {
        var filter = Filters.female()
                .and(Filters.ageRange(Parameters.MIN_AGE_MATERNITY, Parameters.MAX_AGE_MATERNITY));
        if (!Parameters.FLAG_SINGLE_MOTHERS) {
            filter = filter.and(Filters.single().negate());
        }
        return filter;
    }

    /// Filter persons with a non-negative disposable income.
    public static Predicate<Person> validIncome() {
        return p -> p.getBenefitUnit().getEquivalisedDisposableIncomeYearly() >= 0.0;
    }

    /// Filter persons by minimum yearly gross earnings (included).
    public static Predicate<Person> grossEarningsYearlyAtLeast(double value) {
        return p -> p.getGrossEarningsYearly() >= value;
    }

    /// Filter persons with long term disability.
    public static Predicate<Person> hasLongTermDisability() {
        return p -> p.getHealthDsblLongtermFlag() == Indicator.True;
    }

    /// Filter persons who are "flexible in labour supply".
    ///
    /// The conditions are the following:
    /// - of working age
    /// - not a student nor retired
    /// - not disabled
    public static Predicate<Person> flexibleLabourSupply() {
        var toExclude = Filters
                .employment(Les_c4.Student)
                .or(Filters.employment(Les_c4.Retired))
                .or(Filters.hasLongTermDisability());
        return Filters
                .ageRange(Parameters.MIN_AGE_FLEXIBLE_LABOUR_SUPPLY, Parameters.MAX_AGE_FLEXIBLE_LABOUR_SUPPLY)
                .and(toExclude.negate());
    }
}
