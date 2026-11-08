package simpaths.model.person;

import simpaths.data.ManagerRegressions;
import simpaths.data.Parameters;
import simpaths.data.RegressionName;
import simpaths.model.Person;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.PensionContribRate;
import simpaths.model.enums.TimeSeriesVariable;

public class PrivatePension {

    private boolean wealthPensPpMemberFlag;                            //indicator that is active member of personal pension in current year
    private boolean wealthPensOpMemberFlag;                            //indicator that is active member of occupational pension in current year
    private double wealthPensOpEeContRate;                         //contribution rate of employee to occupational pension
    private double wealthPensOpErContRate;                         //contribution rate of employer to occupational pension
    private double wealthPensPpContRate;                           //contribution rate to personal pension
    private double wealth;                               //remaining modelled private pension pot
    private boolean wealthPensAccessFlag;                         //indicator that private pension income has been accessed
    private double wealthPensLumpSumYear;                       //value of lump sum payment at time of pension access
    private double pensionIncomeAnnual;                  //private pension annuity income per year

    public PrivatePension() {
        wealthPensPpMemberFlag = false;
        wealthPensOpMemberFlag = false;
        wealthPensAccessFlag = false;
        wealthPensOpErContRate = 0.0;
        wealthPensOpEeContRate = 0.0;
        wealthPensPpContRate = 0.0;
        wealth = 0.0;
        pensionIncomeAnnual = 0.0;
        wealthPensLumpSumYear = 0.0;
    }

    public PrivatePension(PrivatePension original) {
        this.wealthPensPpMemberFlag = original.wealthPensPpMemberFlag;
        this.wealthPensOpMemberFlag = original.wealthPensOpMemberFlag;
        this.wealthPensAccessFlag = original.wealthPensAccessFlag;
        this.wealthPensOpErContRate = original.wealthPensOpErContRate;
        this.wealthPensOpEeContRate = original.wealthPensOpEeContRate;
        this.wealthPensPpContRate = original.wealthPensPpContRate;
        this.wealth = original.wealth;
        this.pensionIncomeAnnual = original.pensionIncomeAnnual;
        this.wealthPensLumpSumYear = original.wealthPensLumpSumYear;
    }


    /**************************************************************
     * DEDICATED METHODS
     **************************************************************/

    public void membership(Person person, boolean wealthPensOpMemberFlagL1, boolean wealthPensPpMemberFlagL1, double innov1, double innov2) {

        if (Les_c4.EmployedOrSelfEmployed.equals(person.getLabC4())) {

            wealthPensOpMemberFlag = ManagerRegressions.getAnnualEventFromBiennial(person, wealthPensOpMemberFlagL1, innov1, RegressionName.WealthPensionPW1a, RegressionName.WealthPensionPW1b);
            wealthPensPpMemberFlag = ManagerRegressions.getAnnualEventFromBiennial(person, wealthPensPpMemberFlagL1, innov2, RegressionName.WealthPensionPW2a, RegressionName.WealthPensionPW2b);
        } else {

            wealthPensOpMemberFlag = false;
            wealthPensPpMemberFlag = false;
        }
    }

    public void contributionRates(Person person, double innov1, double innov2, double innov3, double innov4, double innov5) {

        if (wealthPensOpMemberFlag) {

            // consider employee contributions first
            PensionContribRate pcrDiscrete = ManagerRegressions.getEvent(person, RegressionName.WealthPensionPW1c, innov1);
            if (pcrDiscrete.equals(PensionContribRate.Other)) {

                Double val;
                double score, rmse, gauss;
                score = Parameters.getRegPW1d().getScore(person, Person.Variables.class);
                rmse = Parameters.getRMSEForRegression("PW1d");
                gauss = Parameters.getStandardNormalDistribution().inverseCumulativeProbability(innov2);
                val = Math.sinh(score + gauss * rmse);
                if (!Parameters.isFinite(val))
                    throw new RuntimeException("wealthPensOpEeContRate is not finite");
                val = Math.min(100.0, Math.max(0.0, val));
                int test = (int)Math.round(val);
                while (test == 0 || test == 3 || test == 5) {

                    // need to re-draw
                    if (innov2 > 0.5) {
                        innov2 = (innov2 - 0.5) / 0.5;
                    } else {
                        innov2 = innov2 / 0.5;
                    }
                    gauss = Parameters.getStandardNormalDistribution().inverseCumulativeProbability(innov2);
                    val = Math.sinh(score + gauss * rmse);
                    if (!Parameters.isFinite(val))
                        throw new RuntimeException("wealthPensOpEeContRate is not finite");
                    val = Math.min(100.0, Math.max(0.0, val));
                    test = (int)Math.round(val);
                }
                wealthPensOpEeContRate = val / 100.0;
            } else {
                wealthPensOpEeContRate = pcrDiscrete.getValue() / 100.0;
            }

            // consider employer contributions
            pcrDiscrete = ManagerRegressions.getEvent(person, RegressionName.WealthPensionPW1e, innov3);
            if (pcrDiscrete.equals(PensionContribRate.Other)) {

                Double val;
                double score, rmse, gauss;
                score = Parameters.getRegPW1f().getScore(person, Person.Variables.class);
                rmse = Parameters.getRMSEForRegression("PW1f");
                gauss = Parameters.getStandardNormalDistribution().inverseCumulativeProbability(innov4);
                val = Math.sinh(score + gauss * rmse);
                if (!Parameters.isFinite(val))
                    throw new RuntimeException("wealthPensOpErContRate is not finite");
                val = Math.min(100.0, Math.max(0.0, val));
                int test = (int)Math.round(val);
                while (test == 0 || test == 3 || test == 5) {

                    // need to re-draw
                    if (innov4 > 0.5) {
                        innov4 = (innov4 - 0.5) / 0.5;
                    } else {
                        innov4 = innov4 / 0.5;
                    }
                    gauss = Parameters.getStandardNormalDistribution().inverseCumulativeProbability(innov4);
                    val = Math.sinh(score + gauss * rmse);
                    if (!Parameters.isFinite(val))
                        throw new RuntimeException("wealthPensOpErContRate is not finite");
                    val = Math.min(100.0, Math.max(0.0, val));
                    test = (int)Math.round(val);
                }
                wealthPensOpErContRate = val / 100.0;
            } else {
                wealthPensOpErContRate = pcrDiscrete.getValue() / 100.0;
            }
        } else {

            wealthPensOpEeContRate = 0.0;
            wealthPensOpErContRate = 0.0;
        }
        if (wealthPensPpMemberFlag) {

            double score, rmse, gauss;
            score = Parameters.getRegPW2c().getScore(person, Person.Variables.class);
            rmse = Parameters.getRMSEForRegression("PW2c");
            gauss = Parameters.getStandardNormalDistribution().inverseCumulativeProbability(innov5);
            wealthPensPpContRate = Math.exp(score + gauss * rmse);
            if (!Parameters.isFinite(wealthPensPpContRate))
                throw new RuntimeException("wealthPensPpContRate is not finite");
            wealthPensPpContRate = Math.min(100.0, Math.max(0.0, wealthPensPpContRate)) / 100.0;
        } else {

            wealthPensPpContRate = 0.0;
        }
    }

    public void projectWealth(PrivatePension previous, double employmentIncomeAnnual, double growthRate, Les_c4 labC4) {

        // Project the single modelled pension pot.
        wealth = previous.getWealth() * (1.0 + growthRate);
        if (!Les_c4.EmployedOrSelfEmployed.equals(labC4)) {

            wealthPensOpEeContRate = 0.0;
            wealthPensOpErContRate = 0.0;
            wealthPensPpContRate = 0.0;
        } else {

            if (!wealthPensOpMemberFlag) {
                wealthPensOpEeContRate = 0.0;
                wealthPensOpErContRate = 0.0;
            }
            if (!wealthPensPpMemberFlag) {
                wealthPensPpContRate = 0.0;
            }
            wealth += (wealthPensPpContRate + wealthPensOpEeContRate + wealthPensOpErContRate) * employmentIncomeAnnual;
        }
    }

    public double projectInPaymentPension(int year, double pensionIncomePerYearL1) {

        wealthPensAccessFlag = true;
        wealthPensPpMemberFlag = false;
        wealthPensOpMemberFlag = false;
        wealthPensOpErContRate = 0.0;
        wealthPensOpEeContRate = 0.0;
        wealthPensPpContRate = 0.0;
        double inflationIndexL1 = Parameters.getTimeSeriesValue(year-1, TimeSeriesVariable.Inflation);
        double inflationIndex = Parameters.getTimeSeriesValue(year, TimeSeriesVariable.Inflation);
        pensionIncomeAnnual = pensionIncomePerYearL1 * inflationIndexL1 / inflationIndex;
        return pensionIncomeAnnual / 12.0;
    }

    public double projectPensionAccess(int age, Gender male, int year, PrivatePension previous) {

        wealthPensAccessFlag = true;
        wealthPensPpMemberFlag = false;
        wealthPensOpMemberFlag = false;
        wealthPensOpErContRate = 0.0;
        wealthPensOpEeContRate = 0.0;
        wealthPensPpContRate = 0.0;
        double pot = previous.getWealth();
        wealthPensLumpSumYear = Math.min(pot * Parameters.pensionLumpSumShare, Parameters.pensionLumpSumCap);
        Double annuityPrice = Parameters.annuityRates.getAnnuityRateByGenderAgeYear(male, age, year);
        if (annuityPrice == null)
            throw new RuntimeException("Annuity price not found for " + male + " " + age + " " + year);
        pensionIncomeAnnual = (pot - wealthPensLumpSumYear) / annuityPrice;
        // The modelled pot is exhausted by the lump sum and annuity purchase.
        wealth = 0.0;
        return pensionIncomeAnnual / 12.0;
    }


    /**************************************************************
     * GETTERS AND SETTERS
     **************************************************************/

    public double getWealthPensLumpSumYear() {
        return wealthPensLumpSumYear;
    }

    public void setWealthPensLumpSumYear(double wealthPensLumpSumYear) {
        this.wealthPensLumpSumYear = wealthPensLumpSumYear;
    }

    public boolean isWealthPensAccessFlag() {
        return wealthPensAccessFlag;
    }

    public void setWealthPensAccessFlag(boolean wealthPensAccessFlag) {
        this.wealthPensAccessFlag = wealthPensAccessFlag;
    }

    public double getPensionIncomeAnnual() {
        return pensionIncomeAnnual;
    }

    public void setPensionIncomeAnnual(double pensionIncomeAnnual) {
        this.pensionIncomeAnnual = pensionIncomeAnnual;
    }

    public boolean isWealthPensPpMemberFlag() {
        return wealthPensPpMemberFlag;
    }

    public void setWealthPensPpMemberFlag(boolean wealthPensPpMemberFlag) {
        this.wealthPensPpMemberFlag = wealthPensPpMemberFlag;
    }

    public boolean isWealthPensOpMemberFlag() {
        return wealthPensOpMemberFlag;
    }

    public void setWealthPensOpMemberFlag(boolean wealthPensOpMemberFlag) {
        this.wealthPensOpMemberFlag = wealthPensOpMemberFlag;
    }

    public double getWealthPensOpErContRate() {
        return wealthPensOpErContRate;
    }

    public void setWealthPensOpErContRate(double wealthPensOpErContRate) {

        this.wealthPensOpErContRate = wealthPensOpErContRate;
        wealthPensOpMemberFlag = (wealthPensOpEeContRate + wealthPensOpErContRate > 0.001);
    }

    public double getWealthPensOpEeContRate() {
        return wealthPensOpEeContRate;
    }

    public void setWealthPensOpEeContRate(double wealthPensOpEeContRate) {

        this.wealthPensOpEeContRate = wealthPensOpEeContRate;
        wealthPensOpMemberFlag = (wealthPensOpEeContRate + wealthPensOpErContRate > 0.001);
    }

    public double getWealthPensPpContRate() {
        return wealthPensPpContRate;
    }

    public void setWealthPensPpContRate(double wealthPensPpContRate) {

        this.wealthPensPpContRate = wealthPensPpContRate;
        wealthPensPpMemberFlag = (wealthPensPpContRate > 0.001);
    }

    public double getWealth() {
        return wealth;
    }

    public void setWealth(double wealth) {
        this.wealth = wealth;
    }

}
