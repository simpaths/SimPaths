package simpaths.model.benefitunit;

import microsim.statistics.IDoubleSource;
import simpaths.data.ManagerRegressions;
import simpaths.data.Parameters;
import simpaths.data.RegressionName;
import simpaths.model.BenefitUnit;

public class WealthNonPension {

    private double inYearSavings;               // excess of disposable income to consumption in the year
    private double inYearHousingAccrual;        // accrued housing wealth in the year
    private double wealthNonPensionValue;       // total value of non-pension wealth
    WealthHousing wealthHousing;                // object to manage housing wealth
    WealthFinancial wealthFinancial;            // object to manage financial wealth


    /******************************************************
     * CONSTRUCTORS
     ******************************************************/

    public WealthNonPension() {
        // used to initialise to zero
        inYearSavings = 0.0;
        inYearHousingAccrual = 0.0;
        wealthNonPensionValue = 0.0;
        wealthHousing = new WealthHousing();
        wealthFinancial = new WealthFinancial();
    }

    public WealthNonPension(WealthNonPension original) {
        // used to generate lag objects
        inYearSavings = original.inYearSavings;
        inYearHousingAccrual = original.inYearHousingAccrual;
        wealthNonPensionValue = original.getWealthNonPensionValue();
        wealthHousing = new WealthHousing(original.wealthHousing);
        wealthFinancial = new WealthFinancial(original.wealthFinancial);
    }

    public WealthNonPension(double wealthTotValue, double wealthPrptyValue, double wealthMortgageDebtValue, double wealthPensValue,
                            double wealthUnsecuredDebtLowValue, double wealthUnsecuredDebtHighValue) {
        // used for initial population
        inYearSavings = 0.0;
        inYearHousingAccrual = 0.0;
        wealthNonPensionValue = wealthTotValue - wealthPensValue;
        wealthHousing = new WealthHousing(wealthPrptyValue,  wealthMortgageDebtValue);
        wealthFinancial = new WealthFinancial(wealthNonPensionValue - wealthHousing.getWealthNetHousing(),
                wealthUnsecuredDebtLowValue, wealthUnsecuredDebtHighValue);
    }


    /******************************************************
     * UTILITY METHODS
     ******************************************************/

    public double projectFinancialWealthIncomeAnnual(int year) {
        // used to evaluate taxable income - see BenefitUnit.setInvestmentIncomeAnnual()
        return wealthFinancial.projectIncomeAnnual(year);
    }

    public double projectHousingWealthReturnAnnual(int year) {
        return wealthHousing.projectReturnAnnual(year);
    }

    public void projectWealth(WealthNonPension wealthNonPensionL1, double disposableIncomeAnnual, double xConsumptionAnnual) {

        inYearSavings = disposableIncomeAnnual - xConsumptionAnnual;
        wealthNonPensionValue = wealthNonPensionL1.getWealthNonPensionValue() + inYearSavings + wealthNonPensionL1.getWealthHousing().getInYearAccrualNet();
    }

    public void projectHousingWealth(BenefitUnit benefitUnit,
                                     WealthNonPension wealthNonPensionL1,
                                     double innovHousingIncidence, double innovHousingNetValue,
                                     double innovMortgageIncidence, double innovMortgageValue) {

        wealthHousing.projectValues(benefitUnit, wealthNonPensionL1.getWealthHousing(), innovHousingIncidence, innovHousingNetValue,
                innovMortgageIncidence, innovMortgageValue);
    }

    public void updateNetFinancialAssetsValue() {

        wealthFinancial.setNetFinancialAssetsValue(wealthNonPensionValue - wealthHousing.getWealthNetHousing());
    }

    public void projectUnsecuredDebt(BenefitUnit benefitUnit, WealthNonPension wealthNonPensionL1,
                                     double innovUnsecuredDebtState, double innovLowDebtValue, double innovHighDebtValue) {

        wealthFinancial.projectUnsecuredDebt(benefitUnit, wealthNonPensionL1.getWealthFinancial(),
                innovUnsecuredDebtState, innovLowDebtValue, innovHighDebtValue);
    }

    /**
     * Reconstructs the persistent wealth residual states represented by the
     * starting population. Persistence terms are suppressed while evaluating
     * the structural scores because there is no earlier simulated state at
     * population load.
     *
     * <p>Housing and mortgage states can be bounded independently by the
     * supplied policy. The high-cost-debt state retains its existing uncapped
     * treatment.</p>
     */
    public ResidualInitializationResult initializeResidualStates(
            BenefitUnit benefitUnit, InitialWealthResidualPolicy policy) {

        IDoubleSource zeroPersistenceSource = variableID -> {
            if (BenefitUnit.Variables.HousingPersistence.equals(variableID) ||
                    BenefitUnit.Variables.MortgagePersistence.equals(variableID) ||
                    BenefitUnit.Variables.HighCostDebtPersistence.equals(variableID)) {
                return 0.0;
            }
            return benefitUnit.getDoubleValue(variableID);
        };

        boolean housingInitialized = false;
        double housingRawResidual = Double.NaN;
        double housingStoredResidual = Double.NaN;
        if (wealthHousing.isHomeOwner()) {
            double score = ManagerRegressions.getLinearRegression(RegressionName.WealthHousingHW1c)
                    .getScore(zeroPersistenceSource, BenefitUnit.Variables.class);
            housingRawResidual = Parameters.asinh(wealthHousing.getWealthNetHousing()) - score;
            housingStoredResidual = policy.applyHousingCap(
                    housingRawResidual, ManagerRegressions.getRmse(RegressionName.WealthHousingHW1c));
            wealthHousing.setWealthNetInnovation(housingStoredResidual);
            housingInitialized = true;
        }

        boolean mortgageInitialized = false;
        double mortgageRawResidual = Double.NaN;
        double mortgageStoredResidual = Double.NaN;
        if (wealthHousing.isMortgageHolder()) {
            double score = ManagerRegressions.getLinearRegression(RegressionName.WealthHousingHW2c)
                    .getScore(zeroPersistenceSource, BenefitUnit.Variables.class);
            mortgageRawResidual = Math.log(getWealthMortgageDebtValue()) - score;
            mortgageStoredResidual = policy.applyMortgageCap(
                    mortgageRawResidual, ManagerRegressions.getRmse(RegressionName.WealthHousingHW2c));
            wealthHousing.setWealthMortgageDebtInnovation(mortgageStoredResidual);
            mortgageInitialized = true;
        }

        boolean highCostDebtInitialized = false;
        if (wealthFinancial.hasHighCostDebt()) {
            double score = ManagerRegressions.getLinearRegression(RegressionName.WealthFinancialFW2c)
                    .getScore(zeroPersistenceSource, BenefitUnit.Variables.class);
            double residual = Parameters.asinh(wealthFinancial.getWealthUnsecuredDebtHighValue()) - score;
            wealthFinancial.setWealthUnsecuredDebtHighInnovation(residual);
            highCostDebtInitialized = true;
        }

        return new ResidualInitializationResult(
                housingInitialized, housingRawResidual, housingStoredResidual,
                mortgageInitialized, mortgageRawResidual, mortgageStoredResidual,
                highCostDebtInitialized);
    }

    public record ResidualInitializationResult(
            boolean housingInitialized,
            double housingRawResidual,
            double housingStoredResidual,
            boolean mortgageInitialized,
            double mortgageRawResidual,
            double mortgageStoredResidual,
            boolean highCostDebtInitialized) {

        public boolean housingCapped() {
            return housingInitialized && housingRawResidual != housingStoredResidual;
        }

        public boolean mortgageCapped() {
            return mortgageInitialized && mortgageRawResidual != mortgageStoredResidual;
        }
    }


    /**************************************************************
     * GETTERS AND SETTERS
     **************************************************************/

    public double getInYearAccrualTotal() {

        return wealthHousing.getInYearAccrualNet() + inYearSavings;
    }

    public WealthHousing getWealthHousing() {return wealthHousing;}

    public WealthFinancial getWealthFinancial() {return wealthFinancial;}

    public double getWealthNonPensionValue() {
        return updateWealthNonPensionValue();
    }

    public double getWealthNonPensionValueDirect() {
        return wealthNonPensionValue;
    }

    public double getInYearSavings() {
        return inYearSavings;
    }

    public void setInYearSavings(double val) {
        inYearSavings = val;
    }

    public void setWealthFinancialValue(double val) {
        wealthFinancial.setNetFinancialAssetsValue(val);
        updateWealthNonPensionValue();
    }

    public double updateWealthNonPensionValue() {
        return wealthNonPensionValue = wealthHousing.getWealthNetHousing() + wealthFinancial.getValue();
    }

    public double getWealthPrptyValue() {
        return wealthHousing.getWealthPrptyValue();
    }

    public double  getWealthMortgageDebtValue() {
        return wealthHousing.getWealthMortgageDebtValue();
    }

    public boolean isHomeOwner() {
        return wealthHousing.isHomeOwner();
    }
}
