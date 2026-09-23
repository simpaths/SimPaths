package simpaths.model.benefitunit;

import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;
import simpaths.model.annotations.UpdateManager;
import simpaths.model.enums.Gender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaggedMortgageIncomeRegressorTest {

    @Test
    void hw2bBurdenUsesPriorBenefitUnitIncomeRatherThanCurrentIncome() {
        BenefitUnit benefitUnit = singleAdultUnit(1L, 1_000.0);
        benefitUnit.setWealthNonPensionForPopulationInitialization(
                new WealthNonPension(150_000.0, 250_000.0, 120_000.0,
                        0.0, 0.0, 0.0));

        WealthModule module = new WealthModule();
        module.assignMortgageIncomeQuintiles(List.of(benefitUnit));
        UpdateManager.applyAnnotations(benefitUnit);

        Person adult = benefitUnit.getFemale();
        adult.setYEmpPersGrossMonth(Parameters.asinh(10_000.0));
        module.assignMortgageIncomeQuintiles(List.of(benefitUnit));

        assertEquals(1_000.0, benefitUnit.getWealthPrivateIncomeMonthlyL1(), 1.0e-10);
        assertEquals(10_000.0, benefitUnit.getWealthPrivateIncomeMonthly(), 1.0e-10);
        assertEquals(
                Parameters.asinh(120_000.0 / 12_000.0),
                benefitUnit.getDoubleValue(
                        BenefitUnit.Variables.AsinhLagMortgageDebtToLagAnnualPrivateIncome),
                1.0e-10);

        benefitUnit.setWealthNonPensionForPopulationInitialization(
                new WealthNonPension(80_000.0, 140_000.0, 60_000.0,
                        0.0, 0.0, 0.0));
        UpdateManager.applyAnnotations(benefitUnit);
        adult.setYEmpPersGrossMonth(Parameters.asinh(20_000.0));
        module.assignMortgageIncomeQuintiles(List.of(benefitUnit));

        assertEquals(10_000.0, benefitUnit.getWealthPrivateIncomeMonthlyL1(), 1.0e-10);
        assertEquals(
                Parameters.asinh(60_000.0 / 120_000.0),
                benefitUnit.getDoubleValue(
                        BenefitUnit.Variables.AsinhLagMortgageDebtToLagAnnualPrivateIncome),
                1.0e-10);
    }

    @Test
    void hw2bBurdenFloorsLaggedAnnualPrivateIncomeAtOnePound() {
        BenefitUnit benefitUnit = singleAdultUnit(2L, 0.0);
        benefitUnit.setWealthNonPensionForPopulationInitialization(
                new WealthNonPension(90.0, 100.0, 10.0,
                        0.0, 0.0, 0.0));

        WealthModule module = new WealthModule();
        module.assignMortgageIncomeQuintiles(List.of(benefitUnit));
        UpdateManager.applyAnnotations(benefitUnit);

        assertEquals(
                Parameters.asinh(10.0),
                benefitUnit.getDoubleValue(
                        BenefitUnit.Variables.AsinhLagMortgageDebtToLagAnnualPrivateIncome),
                1.0e-10);
    }

    private static BenefitUnit singleAdultUnit(long id, double earningsMonthly) {
        BenefitUnit benefitUnit = new BenefitUnit(id, id);
        Person adult = new Person(1_000L + id);
        adult.setDemMaleFlag(Gender.Female);
        adult.setDemAge(40);
        adult.setYEmpPersGrossMonth(Parameters.asinh(earningsMonthly));
        adult.setYPensPersGrossMonth(Parameters.asinh(0.0));
        adult.setyCapitalPersMonth(Parameters.asinh(0.0));
        adult.setWgt(1.0);
        benefitUnit.getMembers().add(adult);
        return benefitUnit;
    }
}
