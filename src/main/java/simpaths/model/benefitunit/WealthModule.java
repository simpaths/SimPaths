package simpaths.model.benefitunit;

import jakarta.persistence.Transient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import simpaths.data.Parameters;
import simpaths.data.WeightedQuantileRanks;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

/**
 * Owns benefit-unit wealth construction and population-load state
 * initialisation.
 *
 * <p>The model manager decides when the completed starting population is
 * ready; all assumptions about how wealth state is constructed, bounded and
 * reported remain inside this module.</p>
 */
public final class WealthModule {

    private static Logger log = LogManager.getLogger(WealthModule.class);

    private boolean capInitialHousingResiduals = false;
    private double initialHousingResidualCapRmseMultiplier = 2.6;
    private boolean capInitialMortgageResiduals = false;
    private double initialMortgageResidualCapRmseMultiplier = 2.6;

    /**
     * Projects housing and mortgage values.
     */
    public void projectHousingValues(
            BenefitUnit benefitUnit,
            WealthNonPension wealthNonPension,
            WealthNonPension wealthNonPensionL1,
            double innovHousingIncidence,
            double innovHousingNetValue,
            double innovMortgageIncidence,
            double innovMortgageValue) {
        wealthNonPension.projectHousingWealth(
                benefitUnit, wealthNonPensionL1,
                innovHousingIncidence, innovHousingNetValue,
                innovMortgageIncidence, innovMortgageValue);
    }

    /**
     * Builds the transient non-pension wealth component from persisted level
     * fields. This is called before person-level population augmentation when
     * those calculations need access to benefit-unit wealth.
     */
    public WealthNonPension prepareBenefitUnit(BenefitUnit benefitUnit) {
        WealthNonPension wealth = new WealthNonPension(
                benefitUnit.getWealthTotValue(false),
                benefitUnit.getWealthPrptyValue(false),
                benefitUnit.getWealthMortgageDebtValue(false),
                benefitUnit.getWealthPensValue(false),
                benefitUnit.getWealthUnsecuredDebtLowValue(false),
                benefitUnit.getWealthUnsecuredDebtHighValue(false));
        benefitUnit.setWealthNonPensionForPopulationInitialization(wealth);
        return wealth;
    }

    /**
     * Assigns the common, survey-weighted private-income quintiles used by the
     * benefit-unit-aligned mortgage-amount equation. Ranking is across benefit
     * units, not within family/reference-person cells. The income definition
     * matches the modelled components retained by the revised Stata compiler:
     * earnings/self-employment, private pensions and investment/capital income.
     * Taxes and transfers are excluded, and the amount is not equivalised.
     * The amount is stored at the same time so that the annual update can
     * carry it into the fully lagged HW2b burden in the next interval.
    */
    public void assignMortgageIncomeQuintiles(Collection<BenefitUnit> benefitUnits) {
        for (BenefitUnit benefitUnit : benefitUnits) {
            benefitUnit.setWealthPrivateIncomeMonthly(
                    mortgagePrivateIncomeMonthlyForRanking(benefitUnit));
        }
        Map<BenefitUnit, Integer> incomeRanks = WeightedQuantileRanks.assign(
                benefitUnits,
                BenefitUnit::getWealthPrivateIncomeMonthly,
                BenefitUnit::getWeight,
                5);
        incomeRanks.forEach(BenefitUnit::setWealthPrivateIncomeQuintile);
    }

    static double mortgagePrivateIncomeMonthlyForRanking(BenefitUnit benefitUnit) {
        double income = 0.0;
        boolean foundAdult = false;
        Person male = benefitUnit.getMale();
        if (male != null) {
            income += mortgagePrivateIncomeMonthly(male);
            foundAdult = true;
        }
        Person female = benefitUnit.getFemale();
        if (female != null) {
            income += mortgagePrivateIncomeMonthly(female);
            foundAdult = true;
        }
        if (!foundAdult)
            throw new IllegalStateException(
                    "Benefit unit has no responsible adult for mortgage-income ranking");
        if (!Double.isFinite(income))
            throw new IllegalStateException("Benefit-unit mortgage income is not finite");
        return income;
    }

    private static double mortgagePrivateIncomeMonthly(Person person) {
        return inverseAsinhIncome(person.getYEmpPersGrossMonth(), "earnings")
                + inverseAsinhIncome(person.getYPensPersGrossMonth(), "private pension")
                + inverseAsinhIncome(person.getYCapitalPersMonth(), "capital");
    }

    private static double inverseAsinhIncome(Double transformedIncome, String component) {
        if (transformedIncome == null || !Double.isFinite(transformedIncome))
            throw new IllegalStateException(
                    "Responsible adult " + component + " income is not available");
        double income = Math.sinh(transformedIncome);
        if (!Double.isFinite(income))
            throw new IllegalStateException(
                    "Responsible adult " + component + " income is not finite in levels");
        return income;
    }

    /**
     * Rebuilds transient wealth for every final benefit unit and, when wealth
     * projection is active, reconstructs the persistent residual states using
     * this module's configured initial-state policy.
     */
    public WealthInitializationSummary initializePopulation(Collection<BenefitUnit> benefitUnits) {
        return initializePopulation(benefitUnits, Parameters.projectNonPensionWealth);
    }

    WealthInitializationSummary initializePopulation(
            Collection<BenefitUnit> benefitUnits, boolean initializeResidualStates) {

        InitialWealthResidualPolicy policy = new InitialWealthResidualPolicy(
                capInitialHousingResiduals,
                initialHousingResidualCapRmseMultiplier,
                capInitialMortgageResiduals,
                initialMortgageResidualCapRmseMultiplier);
        int homeowners = 0;
        int housingCappedLower = 0;
        int housingCappedUpper = 0;
        int mortgageHolders = 0;
        int mortgageCappedLower = 0;
        int mortgageCappedUpper = 0;
        int highCostDebtHolders = 0;
        double maxAbsoluteHousingResidualBefore = 0.0;
        double maxAbsoluteHousingResidualAfter = 0.0;
        double maxAbsoluteMortgageResidualBefore = 0.0;
        double maxAbsoluteMortgageResidualAfter = 0.0;

        for (BenefitUnit benefitUnit : benefitUnits) {
            WealthNonPension wealth = prepareBenefitUnit(benefitUnit);
            if (!initializeResidualStates) {
                continue;
            }

            WealthNonPension.ResidualInitializationResult result =
                    wealth.initializeResidualStates(benefitUnit, policy);

            if (result.housingInitialized()) {
                homeowners++;
                maxAbsoluteHousingResidualBefore = Math.max(
                        maxAbsoluteHousingResidualBefore, Math.abs(result.housingRawResidual()));
                maxAbsoluteHousingResidualAfter = Math.max(
                        maxAbsoluteHousingResidualAfter, Math.abs(result.housingStoredResidual()));
                if (result.housingCapped()) {
                    if (result.housingRawResidual() < result.housingStoredResidual()) {
                        housingCappedLower++;
                    } else {
                        housingCappedUpper++;
                    }
                }
            }
            if (result.mortgageInitialized()) {
                mortgageHolders++;
                maxAbsoluteMortgageResidualBefore = Math.max(
                        maxAbsoluteMortgageResidualBefore, Math.abs(result.mortgageRawResidual()));
                maxAbsoluteMortgageResidualAfter = Math.max(
                        maxAbsoluteMortgageResidualAfter, Math.abs(result.mortgageStoredResidual()));
                if (result.mortgageCapped()) {
                    if (result.mortgageRawResidual() < result.mortgageStoredResidual()) {
                        mortgageCappedLower++;
                    } else {
                        mortgageCappedUpper++;
                    }
                }
            }
            if (result.highCostDebtInitialized()) {
                highCostDebtHolders++;
            }
        }

        WealthInitializationSummary summary = new WealthInitializationSummary(
                benefitUnits.size(), homeowners, housingCappedLower, housingCappedUpper,
                mortgageHolders, mortgageCappedLower, mortgageCappedUpper, highCostDebtHolders,
                maxAbsoluteHousingResidualBefore, maxAbsoluteHousingResidualAfter,
                maxAbsoluteMortgageResidualBefore, maxAbsoluteMortgageResidualAfter);
        log.info(summary);
        System.out.println(summary);
        return summary;
    }

    /** Writes this module's effective settings to the archived run options. */
    public void writeRunParameters(PrintWriter writer) {
        writer.println("capInitialHousingResiduals: " + capInitialHousingResiduals);
        writer.println("initialHousingResidualCapRmseMultiplier: " +
                initialHousingResidualCapRmseMultiplier);
        writer.println("capInitialMortgageResiduals: " + capInitialMortgageResiduals);
        writer.println("initialMortgageResidualCapRmseMultiplier: " +
                initialMortgageResidualCapRmseMultiplier);
    }

    public boolean isCapInitialHousingResiduals() {
        return capInitialHousingResiduals;
    }

    public void setCapInitialHousingResiduals(boolean capInitialHousingResiduals) {
        this.capInitialHousingResiduals = capInitialHousingResiduals;
    }

    public double getInitialHousingResidualCapRmseMultiplier() {
        return initialHousingResidualCapRmseMultiplier;
    }

    public void setInitialHousingResidualCapRmseMultiplier(double multiplier) {
        this.initialHousingResidualCapRmseMultiplier = multiplier;
    }

    public boolean isCapInitialMortgageResiduals() {
        return capInitialMortgageResiduals;
    }

    public void setCapInitialMortgageResiduals(boolean capInitialMortgageResiduals) {
        this.capInitialMortgageResiduals = capInitialMortgageResiduals;
    }

    public double getInitialMortgageResidualCapRmseMultiplier() {
        return initialMortgageResidualCapRmseMultiplier;
    }

    public void setInitialMortgageResidualCapRmseMultiplier(double multiplier) {
        this.initialMortgageResidualCapRmseMultiplier = multiplier;
    }

    public record WealthInitializationSummary(
            int benefitUnits,
            int homeowners,
            int housingCappedLower,
            int housingCappedUpper,
            int mortgageHolders,
            int mortgageCappedLower,
            int mortgageCappedUpper,
            int highCostDebtHolders,
            double maxAbsoluteHousingResidualBefore,
            double maxAbsoluteHousingResidualAfter,
            double maxAbsoluteMortgageResidualBefore,
            double maxAbsoluteMortgageResidualAfter) {

        @Override
        public String toString() {
            return "Initial wealth residual states: benefitUnits=" + benefitUnits +
                    ", homeowners=" + homeowners +
                    ", housing capped lower/upper=" + housingCappedLower + "/" + housingCappedUpper +
                    ", mortgageHolders=" + mortgageHolders +
                    ", mortgage capped lower/upper=" + mortgageCappedLower + "/" + mortgageCappedUpper +
                    ", highCostDebtHolders=" + highCostDebtHolders +
                    ", max |housing residual| before/after=" +
                    maxAbsoluteHousingResidualBefore + "/" + maxAbsoluteHousingResidualAfter +
                    ", max |mortgage residual| before/after=" +
                    maxAbsoluteMortgageResidualBefore + "/" + maxAbsoluteMortgageResidualAfter;
        }
    }
}
