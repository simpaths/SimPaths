package simpaths.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;
import simpaths.model.Person.DoublesVariables;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Occupancy;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BenefitReceiptPropagationTest {

    private BenefitUnit bu;
    private Person p1;
    private Person p2;

    @BeforeEach
    void setUp() {
        // Minimal, in-memory setup: a BU with two adult members
        // Note: We only need fields required for UC/LB propagation and readout
        Parameters.calculatePartnershipDifferentials("UK");
        bu = new BenefitUnit(1L, 1234L);
        bu.setI_demOccupancy(Occupancy.Couple);

        p1 = new Person(100L, 2000L);
        p2 = new Person(101L, 2001L);


        p1.setDemAge(30);
        p2.setDemAge(28);

        p1.setDemMaleFlag(Gender.Male);
        p2.setDemMaleFlag(Gender.Female);

        // Wire the association both ways
        setBenefitUnit(p1, bu);
        setBenefitUnit(p2, bu);
        addMember(bu, p1);
        addMember(bu, p2);
    }


    private void setBenefitUnit(Person person, BenefitUnit benefitUnit) {
        try {
            var f = Person.class.getDeclaredField("benefitUnit");
            f.setAccessible(true);
            f.set(person, benefitUnit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to associate Person to BenefitUnit in test", e);
        }
    }

    private void addMember(BenefitUnit benefitUnit, Person person) {
        try {
            var f = BenefitUnit.class.getDeclaredField("members");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Person> members = (Set<Person>) f.get(benefitUnit);
            if (members == null) {
                members = new LinkedHashSet<>();
                f.set(benefitUnit, members);
            }
            members.add(person);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add member to BenefitUnit in test", e);
        }
    }

    @Test
    @DisplayName("UC: amount > 0 and yBenUCReceivedFlag = 1 propagate correctly to BU and Persons")
    void ucAmountAndOnOffCoherence() {
        // Simulate donor-imputed results
        setUniversalCreditMonthly(bu, 120.0);
        bu.setReceivedBenefitsUC(1);                   // donor on/off
        bu.setReceivedBenefitsNonUC(0);       // ensure legacy off here


        // Propagate receipt flags to persons
        bu.onEvent(BenefitUnit.Processes.ReceivesBenefitsUC);

        // BU assertions
        assertEquals(120.0, getUniversalCreditMonthly(bu), 1e-9, "BU UC amount should match donor-imputed value");
        assertEquals(1, getReceivedUC(bu), "BU receivedUC flag should match donor-imputed flag");

        // Person-level flags used by downstream modules
        assertEquals(1.0, p1.getDoubleValue(DoublesVariables.D_Econ_benefits_UC), 1e-9, "Person UC indicator should be 1.0");
        assertEquals(1.0, p2.getDoubleValue(DoublesVariables.D_Econ_benefits_UC), 1e-9, "Person UC indicator should be 1.0");
    }

    @Test
    @DisplayName("Non-UC benefits: amount > 0 and receivedNonUCBenefit = 1 propagate correctly to BU and Persons")
    void nonUCAmountAndOnOffCoherence() {
        // Simulate donor-imputed results
        setBenefitNonUCMonthly(bu, 250.0);
        bu.setReceivedBenefitsNonUC(1);       // donor on/off
        bu.setReceivedBenefitsUC(0);                   // ensure UC off here

        // Propagate receipt flags to persons
        bu.onEvent(BenefitUnit.Processes.ReceivesBenefitsUC);

        // BU assertions
        assertEquals(250.0, getBenefitNonUCMonthly(bu), 1e-9, "BU legacy amount should match donor-imputed value");
        assertEquals(1, getReceivedNonUC(bu), "BU receivedLegacyBenefit flag should match donor-imputed flag");

        // Person-level flags used by downstream modules
        assertEquals(1.0, p1.getDoubleValue(DoublesVariables.D_Econ_benefits_NonUC), 1e-9, "Person NonUC indicator should be 1.0");
        assertEquals(1.0, p2.getDoubleValue(DoublesVariables.D_Econ_benefits_NonUC), 1e-9, "Person NonUC indicator should be 1.0");
    }

    @Test
    @DisplayName("UC: zero amount with receivedUC = 0 implies off at BU and Person level")
    void ucZeroImpliesOff() {
        setUniversalCreditMonthly(bu, 0.0);
        bu.setReceivedBenefitsUC(0);
        bu.setReceivedBenefitsNonUC(0);

        bu.onEvent(BenefitUnit.Processes.ReceivesBenefitsUC);

        assertEquals(0.0, getUniversalCreditMonthly(bu), 1e-9, "BU UC amount should be zero");
        assertEquals(0, getReceivedUC(bu), "BU receivedUC flag should be 0");

        assertEquals(0.0, p1.getDoubleValue(DoublesVariables.D_Econ_benefits_UC), 1e-9, "Person UC indicator should be 0.0");
        assertEquals(0.0, p2.getDoubleValue(DoublesVariables.D_Econ_benefits_UC), 1e-9, "Person UC indicator should be 0.0");
    }

    @Test
    @DisplayName("UC take-up consistency: BU.uc_takeup is set and reflected in IDoubleSource correctly")
    void ucTakeupConsistency() {
        // Toggle take-up on
        bu.setUC_takeup(1);

        // Assert raw field
        assertEquals(1, bu.getUC_takeup(), "UC take-up raw field should be 1");

        // If there is an IDoubleSource mapping for UC take-up regressors, assert that too
        // Note: We guard this with try/catch in case enums/mapping differ by build
        try {
            var regressorsEnum = Class.forName("simpaths.model.BenefitUnit$Regressors");
            Object ucReg = Enum.valueOf((Class<Enum>) regressorsEnum, "UC_TakeUp");
            double mapped = bu.getDoubleValue((Enum<?>) ucReg);
            assertEquals(1.0, mapped, 1e-9, "IDoubleSource mapping for UC_TakeUp should reflect raw value");
        } catch (ClassNotFoundException e) {
            // If no such mapping exists in this build, we still validate the raw field
        }
    }

    // Small accessors to avoid relying on full public API beyond what's needed for tests

    private void setUniversalCreditMonthly(BenefitUnit unit, double val) {
        try {
            var f = BenefitUnit.class.getDeclaredField("universalCreditMonthly");
            f.setAccessible(true);
            f.set(unit, val);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set UC amount on BenefitUnit in test", e);
        }
    }

    private double getUniversalCreditMonthly(BenefitUnit unit) {
        try {
            var f = BenefitUnit.class.getDeclaredField("universalCreditMonthly");
            f.setAccessible(true);
            Object v = f.get(unit);
            return v == null ? 0.0 : (Double) v;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get UC amount from BenefitUnit in test", e);
        }
    }

    private void setBenefitNonUCMonthly(BenefitUnit unit, double val) {
        try {
            var f = BenefitUnit.class.getDeclaredField("nonUCMonthly");
            f.setAccessible(true);
            f.set(unit, val);
            var f2 = BenefitUnit.class.getDeclaredField("yBenAmountMonth");
            f2.setAccessible(true);
            f2.set(unit, val);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set nonUC benefit amount on BenefitUnit in test", e);
        }
    }

    private double getBenefitNonUCMonthly(BenefitUnit unit) {
        try {
            var f = BenefitUnit.class.getDeclaredField("nonUCMonthly");
            f.setAccessible(true);
            Object v = f.get(unit);
            return v == null ? 0.0 : (Double) v;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get nonUC benefit amount from BenefitUnit in test", e);
        }
    }

    private int getReceivedUC(BenefitUnit unit) {
        try {
            var f = BenefitUnit.class.getDeclaredField("yBenUCReceivedFlag");
            f.setAccessible(true);
            Object v = f.get(unit);
            return v == null ? 0 : (Integer) v;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get receivedUC flag from BenefitUnit in test", e);
        }
    }

    private int getReceivedNonUC(BenefitUnit unit) {
        try {
            var f = BenefitUnit.class.getDeclaredField("yBenNonUCReceivedFlag");
            f.setAccessible(true);
            Object v = f.get(unit);
            return v == null ? 0 : (Integer) v;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get yBenNonUCReceivedFlag flag from BenefitUnit in test", e);
        }
    }
}
