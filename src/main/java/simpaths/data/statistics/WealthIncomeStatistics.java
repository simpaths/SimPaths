package simpaths.data.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import microsim.data.db.PanelEntityKey;

@Entity
public class WealthIncomeStatistics {

	@Id
	private PanelEntityKey key = new PanelEntityKey(1L);

	@Column(name = "Gini_coefficient_individual_market_income_nationally")
	private double statYMktNatGini;

	@Column(name = "Gini_coefficient_equivalised_household_disposable_income_nationally")
	private double statYHhDispEquivNatGini;

	@Column(name = "Median_equivalised_household_disposable_income")
	private double yHhDispEquivP50;
	
	//Percentiles of ydses:
	@Column(name = "Ydses_p20")
	private double yHhQuintilesC5P20;
	
	@Column(name = "Ydses_p40")
	private double yHhQuintilesC5P40;
	
	@Column(name = "Ydses_p60")
	private double yHhQuintilesC5P60;
	
	@Column(name = "Ydses_p80")
	private double yHhQuintilesC5P80;

	//Percentiles of gross labour income:
	@Column(name = "Gross_Labour_Income_p20")
	private double yLabP20;

	@Column(name = "Gross_Labour_Income_p40")
	private double yLabP40;

	@Column(name = "Gross_Labour_Income_p60")
	private double yLabP60;

	@Column(name = "Gross_Labour_Income_p80")
	private double yLabP80;

	//Equivalised disposable income is reported as the income median yHhDispEquivP50;
	//the legacy edi_p50 alias is intentionally removed to match the codebook naming rules.

	//Percentiles of SIndex:
	@Column(name = "statSIndexP50")
	private double statSIndexP50;

	//employment income, averaged over workers rather than over population, weekly and not equivalised
	@Column(name = "labourIncome_perWorker_weekly_18_29")
	private double statYLabWeeklyPerWorker18to29Avg;

	@Column(name = "labourIncome_perWorker_weekly_30_54")
	private double statYLabWeeklyPerWorker30to54Avg;

	@Column(name = "labourIncome_perWorker_weekly_55_74")
	private double statYLabWeeklyPerWorker55to74Avg;

	//investment income
	@Column(name = "investmentIncome_18_29")
	private double statYInvest18to29Avg;

	@Column(name = "investmentIncome_30_54")
	private double statYInvest30to54Avg;

	@Column(name = "investmentIncome_55_74")
	private double statYInvest55to74Avg;

	//pension income
	@Column(name = "pensionIncome_18_29")
	private double statYPens18to29Avg;

	@Column(name = "pensionIncome_30_54")
	private double statYPens30to54Avg;

	@Column(name = "pensionIncome_55_74")
	private double statYPens55to74Avg;

	//investment losses
	@Column(name = "investmentLosses_18_29")
	private double statInvestLoss18to29Avg;

	@Column(name = "investmentLosses_30_54")
	private double statInvestLoss30to54Avg;

	@Column(name = "investmentLosses_55_74")
	private double statInvestLoss55to74Avg;

	//disposable income gross of investment losses
	@Column(name = "dispInc_grossLosses_18_29")
	private double statYDispGrossOfLosses18to29Avg;

	@Column(name = "dispInc_grossLosses_30_54")
	private double statYDispGrossOfLosses30to54Avg;

	@Column(name = "dispInc_grossLosses_55_74")
	private double statYDispGrossOfLosses55to74Avg;

	//wealth
	@Column(name = "wealth_18_29")
	private double wealth18to29Avg;

	@Column(name = "wealth_30_54")
	private double wealth30to54Avg;

	@Column(name = "wealth_55_74")
	private double wealth55to74Avg;

	////	Risk-of-poverty threshold is set at 60% of the national median equivalised household disposable income.
//	@Column(name = "Risk_of_poverty_threshold")
//	private double riskOfPovertyThreshold;
	
	public void setGiniPersonalGrossEarningsNational(double statYMktNatGini) {
		this.statYMktNatGini = statYMktNatGini;
	}
	
	public void setGiniEquivalisedHouseholdDisposableIncomeNational(double statYHhDispEquivNatGini) {
		this.statYHhDispEquivNatGini = statYHhDispEquivNatGini;
	}

	public double getMedianEquivalisedHouseholdDisposableIncome() {
		return yHhDispEquivP50;
	}

	public void setMedianEquivalisedHouseholdDisposableIncome(double yHhDispEquivP50) {
		this.yHhDispEquivP50 = yHhDispEquivP50;
	}
	
	public double getYHhQuintilesC5P20() {
		return yHhQuintilesC5P20;
	}

	public void setYHhQuintilesC5P20(double yHhQuintilesC5P20) {
		this.yHhQuintilesC5P20 = yHhQuintilesC5P20;
	}

	public double getYHhQuintilesC5P40() {
		return yHhQuintilesC5P40;
	}

	public void setYHhQuintilesC5P40(double yHhQuintilesC5P40) {
		this.yHhQuintilesC5P40 = yHhQuintilesC5P40;
	}

	public double getYHhQuintilesC5P60() {
		return yHhQuintilesC5P60;
	}

	public void setYHhQuintilesC5P60(double yHhQuintilesC5P60) {
		this.yHhQuintilesC5P60 = yHhQuintilesC5P60;
	}

	public double getYHhQuintilesC5P80() {
		return yHhQuintilesC5P80;
	}

	public void setYHhQuintilesC5P80(double yHhQuintilesC5P80) {
		this.yHhQuintilesC5P80 = yHhQuintilesC5P80;
	}

	public double getStatSIndexP50() {
		return statSIndexP50;
	}

	public void setStatSIndexP50(double statSIndexP50) {
		this.statSIndexP50 = statSIndexP50;
	}

	public double getYLabP20() {
		return yLabP20;
	}

	public void setYLabP20(double yLabP20) {
		this.yLabP20 = yLabP20;
	}

	public double getYLabP40() {
		return yLabP40;
	}

	public void setYLabP40(double yLabP40) {
		this.yLabP40 = yLabP40;
	}

	public double getYLabP60() {
		return yLabP60;
	}

	public void setYLabP60(double yLabP60) {
		this.yLabP60 = yLabP60;
	}

	public double getYLabP80() {
		return yLabP80;
	}

	public void setYLabP80(double yLabP80) {
		this.yLabP80 = yLabP80;
	}

	public double getLabourIncomeWeeklyPerWorker18to29() {
		return statYLabWeeklyPerWorker18to29Avg;
	}

	public void setLabourIncomeWeeklyPerWorker18to29(double statYLabWeeklyPerWorker18to29Avg) {
		this.statYLabWeeklyPerWorker18to29Avg = statYLabWeeklyPerWorker18to29Avg;
	}

	public double getLabourIncomeWeeklyPerWorker30to54() {
		return statYLabWeeklyPerWorker30to54Avg;
	}

	public void setLabourIncomeWeeklyPerWorker30to54(double statYLabWeeklyPerWorker30to54Avg) {
		this.statYLabWeeklyPerWorker30to54Avg = statYLabWeeklyPerWorker30to54Avg;
	}

	public double getLabourIncomeWeeklyPerWorker55to74() {
		return statYLabWeeklyPerWorker55to74Avg;
	}

	public void setLabourIncomeWeeklyPerWorker55to74(double statYLabWeeklyPerWorker55to74Avg) {
		this.statYLabWeeklyPerWorker55to74Avg = statYLabWeeklyPerWorker55to74Avg;
	}

	public double getInvestmentIncome18to29() {
		return statYInvest18to29Avg;
	}

	public void setInvestmentIncome18to29(double statYInvest18to29Avg) {
		this.statYInvest18to29Avg = statYInvest18to29Avg;
	}

	public double getInvestmentIncome30to54() {
		return statYInvest30to54Avg;
	}

	public void setInvestmentIncome30to54(double statYInvest30to54Avg) {
		this.statYInvest30to54Avg = statYInvest30to54Avg;
	}

	public double getInvestmentIncome55to74() {
		return statYInvest55to74Avg;
	}

	public void setInvestmentIncome55to74(double statYInvest55to74Avg) {
		this.statYInvest55to74Avg = statYInvest55to74Avg;
	}

	public double getPensionIncome18to29() {
		return statYPens18to29Avg;
	}

	public void setPensionIncome18to29(double statYPens18to29Avg) {
		this.statYPens18to29Avg = statYPens18to29Avg;
	}

	public double getPensionIncome30to54() {
		return statYPens30to54Avg;
	}

	public void setPensionIncome30to54(double statYPens30to54Avg) {
		this.statYPens30to54Avg = statYPens30to54Avg;
	}

	public double getPensionIncome55to74() {
		return statYPens55to74Avg;
	}

	public void setPensionIncome55to74(double statYPens55to74Avg) {
		this.statYPens55to74Avg = statYPens55to74Avg;
	}

	public double getInvestmentLosses18to29() {
		return statInvestLoss18to29Avg;
	}

	public void setInvestmentLosses18to29(double statInvestLoss18to29Avg) {
		this.statInvestLoss18to29Avg = statInvestLoss18to29Avg;
	}

	public double getInvestmentLosses30to54() {
		return statInvestLoss30to54Avg;
	}

	public void setInvestmentLosses30to54(double statInvestLoss30to54Avg) {
		this.statInvestLoss30to54Avg = statInvestLoss30to54Avg;
	}

	public double getInvestmentLosses55to74() {
		return statInvestLoss55to74Avg;
	}

	public void setInvestmentLosses55to74(double statInvestLoss55to74Avg) {
		this.statInvestLoss55to74Avg = statInvestLoss55to74Avg;
	}

	public double getDispIncomeGrossOfLosses18to29() {
		return statYDispGrossOfLosses18to29Avg;
	}

	public void setDispIncomeGrossOfLosses18to29(double statYDispGrossOfLosses18to29Avg) {
		this.statYDispGrossOfLosses18to29Avg = statYDispGrossOfLosses18to29Avg;
	}

	public double getDispIncomeGrossOfLosses30to54() {
		return statYDispGrossOfLosses30to54Avg;
	}

	public void setDispIncomeGrossOfLosses30to54(double statYDispGrossOfLosses30to54Avg) {
		this.statYDispGrossOfLosses30to54Avg = statYDispGrossOfLosses30to54Avg;
	}

	public double getDispIncomeGrossOfLosses55to74() {
		return statYDispGrossOfLosses55to74Avg;
	}

	public void setDispIncomeGrossOfLosses55to74(double statYDispGrossOfLosses55to74Avg) {
		this.statYDispGrossOfLosses55to74Avg = statYDispGrossOfLosses55to74Avg;
	}

	public double getWealth18to29() {
		return wealth18to29Avg;
	}

	public void setWealth18to29(double wealth18to29Avg) {
		this.wealth18to29Avg = wealth18to29Avg;
	}

	public double getWealth30to54() {
		return wealth30to54Avg;
	}

	public void setWealth30to54(double wealth30to54Avg) {
		this.wealth30to54Avg = wealth30to54Avg;
	}

	public double getWealth55to74() {
		return wealth55to74Avg;
	}

	public void setWealth55to74(double wealth55to74Avg) {
		this.wealth55to74Avg = wealth55to74Avg;
	}

	/**
	 *
	 * POPULATE THE INCOME, WEALTH AND CONSUMPTION STATISTICS REPORTED BY AGE BAND
	 *
	 * The distributional statistics held by this class are set by SimPathsCollector as
	 * its own calculation events fire; this method covers only the age-band aggregates.
	 *
	 */
	public void update(AgeBandAggregates agg) {

		setLabourIncomeWeeklyPerWorker18to29(agg.labInc[0]);
		setLabourIncomeWeeklyPerWorker30to54(agg.labInc[1]);
		setLabourIncomeWeeklyPerWorker55to74(agg.labInc[2]);

		setInvestmentIncome18to29(agg.invInc[0]);
		setInvestmentIncome30to54(agg.invInc[1]);
		setInvestmentIncome55to74(agg.invInc[2]);

		setPensionIncome18to29(agg.penInc[0]);
		setPensionIncome30to54(agg.penInc[1]);
		setPensionIncome55to74(agg.penInc[2]);

		setInvestmentLosses18to29(agg.invLosses[0]);
		setInvestmentLosses30to54(agg.invLosses[1]);
		setInvestmentLosses55to74(agg.invLosses[2]);

		setDispIncomeGrossOfLosses18to29(agg.grossDisInc[0]);
		setDispIncomeGrossOfLosses30to54(agg.grossDisInc[1]);
		setDispIncomeGrossOfLosses55to74(agg.grossDisInc[2]);

		setWealth18to29(agg.wealth[0]);
		setWealth30to54(agg.wealth[1]);
		setWealth55to74(agg.wealth[2]);
	}

//	public double getRiskOfPovertyThreshold() {
//		return riskOfPovertyThreshold;
//	}
//
//	public void setRiskOfPovertyThreshold(double riskOfPovertyThreshold) {
//		this.riskOfPovertyThreshold = riskOfPovertyThreshold;
//	}

}
