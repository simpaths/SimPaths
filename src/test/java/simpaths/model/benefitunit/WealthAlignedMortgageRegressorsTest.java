package simpaths.model.benefitunit;

import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;
import simpaths.model.enums.Gender;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WealthAlignedMortgageRegressorsTest {

    @Test
    void assignsCommonWeightedBenefitUnitPrivateIncomeQuintiles() {
        List<BenefitUnit> benefitUnits = new ArrayList<>();
        for (int quintile = 1; quintile <= 5; quintile++) {
            benefitUnits.add(singleAdultUnit(
                    quintile, 40, 100.0 * quintile, 0.0, 0.0, 1.0));
        }

        new WealthModule().assignMortgageIncomeQuintiles(benefitUnits);

        for (int quintile = 1; quintile <= 5; quintile++) {
            BenefitUnit benefitUnit = benefitUnits.get(quintile - 1);
            assertEquals(quintile, benefitUnit.getWealthPrivateIncomeQuintile());
            for (int requested = 2; requested <= 5; requested++) {
                BenefitUnit.Variables variable = BenefitUnit.Variables.valueOf(
                        "WealthPrivateIncomeQuintile" + requested);
                assertEquals(requested == quintile ? 1.0 : 0.0,
                        benefitUnit.getDoubleValue(variable));
            }
        }
    }

    @Test
    void sumsModelledMortgageIncomeComponentsInLevels() {
        BenefitUnit benefitUnit = new BenefitUnit(100L, 100L);
        addAdult(benefitUnit, 101L, Gender.Male, 39,
                60.0, 25.0, 15.0, 1.0);
        addAdult(benefitUnit, 102L, Gender.Female, 64,
                30.0, 10.0, 10.0, 1.0);

        assertEquals(150.0,
                WealthModule.mortgagePrivateIncomeMonthlyForRanking(benefitUnit), 1.0e-10);
    }

    private static BenefitUnit singleAdultUnit(
            long id, int age, double earnings, double pensionIncome,
            double capitalIncome, double weight) {
        BenefitUnit benefitUnit = new BenefitUnit(id, id);
        addAdult(benefitUnit, 1_000L + id, Gender.Female, age,
                earnings, pensionIncome, capitalIncome, weight);
        return benefitUnit;
    }

    private static void addAdult(
            BenefitUnit benefitUnit, long id, Gender gender, int age,
            double earnings, double pensionIncome, double capitalIncome,
            double weight) {
        Person person = new Person(id);
        person.setDemMaleFlag(gender);
        person.setDemAge(age);
        person.setYEmpPersGrossMonth(Parameters.asinh(earnings));
        person.setYPensPersGrossMonth(Parameters.asinh(pensionIncome));
        person.setyCapitalPersMonth(Parameters.asinh(capitalIncome));
        person.setWgt(weight);
        benefitUnit.getMembers().add(person);
    }
}
