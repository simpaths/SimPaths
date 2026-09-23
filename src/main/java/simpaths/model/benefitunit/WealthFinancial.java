package simpaths.model.benefitunit;

import microsim.statistics.regression.LinearRegression;
import simpaths.data.ManagerRegressions;
import simpaths.data.Parameters;
import simpaths.data.RegressionName;
import simpaths.model.BenefitUnit;
import simpaths.model.enums.TimeVaryingRate;
import simpaths.model.enums.UnsecuredDebtState;

public class WealthFinancial {

    private double wealthFinancialAssetsValue;              // value of financial assets before unsecured debts
    private double wealthUnsecuredDebtLowValue;             // low-cost unsecured debt
    private double wealthUnsecuredDebtHighValue;            // high-cost unsecured debt
    private double wealthUnsecuredDebtLowInnovation;        // persistent residual state for low-cost unsecured debt
    private double wealthUnsecuredDebtHighInnovation;       // persistent residual state for high-cost unsecured debt
    private double yWealthFinancialReturnYear;              // net return to financial assets/debts during year
    private UnsecuredDebtState unsecuredDebtState;


    /******************************************************
     * CONSTRUCTORS
     ******************************************************/

    public WealthFinancial() {
        wealthFinancialAssetsValue = 0.0;
        wealthUnsecuredDebtLowValue = 0.0;
        wealthUnsecuredDebtHighValue = 0.0;
        wealthUnsecuredDebtLowInnovation = 0.0;
        wealthUnsecuredDebtHighInnovation = 0.0;
        yWealthFinancialReturnYear = 0.0;
        unsecuredDebtState = UnsecuredDebtState.None;
    }

    public WealthFinancial(WealthFinancial original) {
        wealthFinancialAssetsValue = original.wealthFinancialAssetsValue;
        wealthUnsecuredDebtLowValue = original.wealthUnsecuredDebtLowValue;
        wealthUnsecuredDebtHighValue = original.wealthUnsecuredDebtHighValue;
        wealthUnsecuredDebtLowInnovation = original.wealthUnsecuredDebtLowInnovation;
        wealthUnsecuredDebtHighInnovation = original.wealthUnsecuredDebtHighInnovation;
        yWealthFinancialReturnYear = original.yWealthFinancialReturnYear;
        unsecuredDebtState = original.unsecuredDebtState;
    }

    public WealthFinancial(double netFinancialWealth) {
        this();
        setNetFinancialAssetsValue(netFinancialWealth);
    }

    public WealthFinancial(double netFinancialWealth, double lowCostDebt, double highCostDebt) {
        this();
        wealthUnsecuredDebtLowValue = positiveOrZero(lowCostDebt);
        wealthUnsecuredDebtHighValue = positiveOrZero(highCostDebt);
        unsecuredDebtState = getUnsecuredDebtStateFromValues();
        setNetFinancialAssetsValue(netFinancialWealth);
    }


    /******************************************************
     * UTILITY METHODS
     ******************************************************/

    public double projectIncomeAnnual(int year) {
        // projects returns - see BenefitUnit.setInvestmentIncomeAnnual()

        double returnsAssets, costsLowDebt = 0.0, costsHighDebt = 0.0;
        if (Parameters.projectLowCostDebt) {
            costsLowDebt = wealthUnsecuredDebtLowValue * Parameters.getTimeSeriesRate(year, TimeVaryingRate.RealDebtCostLow);
        }
        if (Parameters.projectHighCostDebt) {
            costsHighDebt = wealthUnsecuredDebtHighValue * Parameters.getTimeSeriesRate(year, TimeVaryingRate.RealDebtCostHigh);
        }
        if (wealthFinancialAssetsValue > 0.0) {
            returnsAssets = wealthFinancialAssetsValue * Parameters.getTimeSeriesRate(year, TimeVaryingRate.RealSavingReturn);
        } else {
            returnsAssets = wealthFinancialAssetsValue * Parameters.getTimeSeriesRate(year, TimeVaryingRate.RealDebtCostLow);
        }
        yWealthFinancialReturnYear = returnsAssets - costsLowDebt - costsHighDebt;
        return yWealthFinancialReturnYear;
    }

    public void projectUnsecuredDebt(BenefitUnit benefitUnit, WealthFinancial wealthFinancialL1,
                                     double innovUnsecuredDebtState,
                                     double innovLowDebtValue,
                                     double innovHighDebtValue) {

        double netFinancialWealth = getValue();
        projectUnsecuredDebtState(benefitUnit, wealthFinancialL1, innovUnsecuredDebtState);

        wealthUnsecuredDebtLowValue = 0.0;
        wealthUnsecuredDebtHighValue = 0.0;
        wealthUnsecuredDebtLowInnovation = 0.0;
        wealthUnsecuredDebtHighInnovation = 0.0;

        // low-cost debt
        if (unsecuredDebtState.hasLowCostDebt()) {
            // not currently supported

            throw new RuntimeException("unsecured debt projection not supported for low-cost debt");
        }

        // high-cost debt
        if (unsecuredDebtState.hasHighCostDebt()) {

            boolean continuing = wealthFinancialL1.hasHighCostDebt();
            LinearRegression regressionModel = continuing ? Parameters.getRegFW2d() : Parameters.getRegFW2c();
            double score = regressionModel.getScore(benefitUnit, BenefitUnit.Variables.class);
            String regression = continuing ? "FW2d" : "FW2c";
            wealthUnsecuredDebtHighInnovation = getInnovation(regression, innovHighDebtValue);
            double transformedValue = score + wealthUnsecuredDebtHighInnovation;
            wealthUnsecuredDebtHighValue = Math.max(0.0, Math.sinh(transformedValue));
            if (!Parameters.isFinite(wealthUnsecuredDebtHighValue))
                throw new RuntimeException("projection for high-cost unsecured debt value is not finite"
                        + " for benefit unit " + benefitUnit.getId()
                        + ", regression " + regression
                        + ", netFinancialWealth " + netFinancialWealth
                        + ", score " + score
                        + ", randomDraw " + innovHighDebtValue
                        + ", shock " + wealthUnsecuredDebtHighInnovation
                        + ", transformed value " + transformedValue);
        }

        // assets
        wealthFinancialAssetsValue = netFinancialWealth + wealthUnsecuredDebtLowValue + wealthUnsecuredDebtHighValue;
    }

    private void projectUnsecuredDebtState(BenefitUnit benefitUnit, WealthFinancial wealthFinancialL1, double randomDraw) {

        if (Parameters.projectLowCostDebt && Parameters.projectHighCostDebt) {

            throw new RuntimeException("unsecured debt projection not supported for both low-cost and high-cost debt");
            /*
            UnsecuredDebtState lagState = wealthFinancialL1.getUnsecuredDebtState();
            UnsecuredDebtState[] states = UnsecuredDebtState.values();
            double[][] biennialTransition = new double[states.length][states.length];
            for (UnsecuredDebtState assumedLagState : states) {
                IDoubleSource source = new CounterfactualDebtStateSource(benefitUnit, assumedLagState);
                Map<UnsecuredDebtState, Double> probabilities = ManagerRegressions.getProbabilities(
                        source, BenefitUnit.Variables.class, RegressionName.WealthFinancialFW2a);
                for (UnsecuredDebtState currentState : states) {
                    biennialTransition[assumedLagState.getValue()][currentState.getValue()] = probabilities.get(currentState);
                }
            }
            double[][] annualTransition = TransitionMatrixAnnualiser.annualiseBiennial(biennialTransition);

            double cumulative = 0.0;
            for (UnsecuredDebtState state : states) {
                cumulative += annualTransition[lagState.getValue()][state.getValue()];
                if (randomDraw <= cumulative) {
                    unsecuredDebtState = state;
                    break;
                }
            }
             */
        } else if (Parameters.projectHighCostDebt || Parameters.projectLowCostDebt) {

            boolean debtHolder = ManagerRegressions.getAnnualEventFromBiennial(benefitUnit, wealthFinancialL1.hasDebt(), randomDraw, RegressionName.WealthFinancialFW2a, RegressionName.WealthFinancialFW2b);
            setDebt(debtHolder);
        } else {

            unsecuredDebtState =  UnsecuredDebtState.None;
        }
    }

    /*

    private static final class CounterfactualDebtStateSource implements IDoubleSource {

        private final BenefitUnit benefitUnit;
        private final UnsecuredDebtState assumedLagState;

        private CounterfactualDebtStateSource(BenefitUnit benefitUnit, UnsecuredDebtState assumedLagState) {
            this.benefitUnit = benefitUnit;
            this.assumedLagState = assumedLagState;
        }

        @Override
        public double getDoubleValue(Enum<?> variableID) {
            if (BenefitUnit.Variables.LagUnsecuredDebtLowCostOnly.equals(variableID)) {
                return UnsecuredDebtState.LowCostOnly.equals(assumedLagState) ? 1.0 : 0.0;
            }
            if (BenefitUnit.Variables.LagUnsecuredDebtHighCostOnly.equals(variableID)) {
                return UnsecuredDebtState.HighCostOnly.equals(assumedLagState) ? 1.0 : 0.0;
            }
            if (BenefitUnit.Variables.LagUnsecuredDebtMixed.equals(variableID)) {
                return UnsecuredDebtState.Mixed.equals(assumedLagState) ? 1.0 : 0.0;
            }
            return benefitUnit.getDoubleValue(variableID);
        }
    }

     */
    private double getInnovation(String regression, double randomDraw) {
        double rmse = Parameters.getRMSEForRegression(regression);
        double gauss = Parameters.getStandardNormalDistribution().inverseCumulativeProbability(randomDraw);
        return gauss * rmse;
    }

    private double positiveOrZero(double value) {
        return (Parameters.isFinite(value) && value > 0.0) ? value : 0.0;
    }

    private UnsecuredDebtState getUnsecuredDebtStateFromValues() {
        boolean hasLowDebt = wealthUnsecuredDebtLowValue > 0.0;
        boolean hasHighDebt = wealthUnsecuredDebtHighValue > 0.0;
        if (hasLowDebt && hasHighDebt) return UnsecuredDebtState.Mixed;
        if (hasLowDebt) return UnsecuredDebtState.LowCostOnly;
        if (hasHighDebt) return UnsecuredDebtState.HighCostOnly;
        return UnsecuredDebtState.None;
    }


    /**************************************************************
     * GETTERS AND SETTERS
     **************************************************************/

    public double getValue() {
        return wealthFinancialAssetsValue - wealthUnsecuredDebtLowValue - wealthUnsecuredDebtHighValue;
    }

    public void setNetFinancialAssetsValue(double netFinancialWealthValue) {
        wealthFinancialAssetsValue = netFinancialWealthValue + wealthUnsecuredDebtLowValue + wealthUnsecuredDebtHighValue;
    }

    public double getWealthFinancialAssetsValue() {
        return wealthFinancialAssetsValue;
    }

    public double getWealthUnsecuredDebtLowValue() {
        return wealthUnsecuredDebtLowValue;
    }

    public double getWealthUnsecuredDebtHighValue() {
        return wealthUnsecuredDebtHighValue;
    }

    public double getWealthUnsecuredDebtLowInnovation() {
        return wealthUnsecuredDebtLowInnovation;
    }

    public double getWealthUnsecuredDebtHighInnovation() {
        return wealthUnsecuredDebtHighInnovation;
    }

    public void setWealthUnsecuredDebtLowInnovation(double value) {
        wealthUnsecuredDebtLowInnovation = value;
    }

    public void setWealthUnsecuredDebtHighInnovation(double value) {
        wealthUnsecuredDebtHighInnovation = value;
    }

    public UnsecuredDebtState getUnsecuredDebtState() {
        return unsecuredDebtState;
    }

    public boolean hasLowCostDebt() {
        return unsecuredDebtState.hasLowCostDebt();
    }

    public boolean hasHighCostDebt() {
        return unsecuredDebtState.hasHighCostDebt();
    }

    public boolean hasMixedDebt() {
        return unsecuredDebtState == UnsecuredDebtState.Mixed;
    }

    public boolean hasDebt() {

        if (Parameters.projectLowCostDebt && unsecuredDebtState.hasLowCostDebt())
            return true;
        else
            return (Parameters.projectHighCostDebt && unsecuredDebtState.hasHighCostDebt());
    }

    private void setDebt(boolean debtHolder) {

        if (debtHolder) {

            if (Parameters.projectLowCostDebt) {
                unsecuredDebtState = UnsecuredDebtState.LowCostOnly;
            } else if (Parameters.projectHighCostDebt) {
                unsecuredDebtState = UnsecuredDebtState.HighCostOnly;
            } else {
                unsecuredDebtState = UnsecuredDebtState.None;
            }
        } else {

            unsecuredDebtState = UnsecuredDebtState.None;
        }
    }
}
