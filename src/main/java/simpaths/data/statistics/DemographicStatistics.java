package simpaths.data.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import microsim.data.db.PanelEntityKey;

/**
 *
 * DEMOGRAPHIC STATISTICS BY AGE BAND
 *
 * One row per simulated year, covering the whole population in each of the three age
 * bands 18-29, 30-54 and 55-74.
 *
 * Health, labour and income statistics reported over the same age bands live in
 * HealthStatistics, LabourStatistics and WealthIncomeStatistics respectively.
 *
 */
@Entity
public class DemographicStatistics {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    //population shares in cohabiting relationships
    @Column(name = "pr_married_18_29")
    private double demMarried18to29Share;

    @Column(name = "pr_married_30_54")
    private double demMarried30to54Share;

    @Column(name = "pr_married_55_74")
    private double demMarried55to74Share;

    //average dependent children
    @Column(name = "avkids_18_29")
    private double demNChild18to29Avg;

    @Column(name = "avkids_30_54")
    private double demNChild30to54Avg;

    @Column(name = "avkids_55_74")
    private double demNChild55to74Avg;

    //population counts, the denominator for the age-band statistics reported elsewhere
    @Column(name= "population_18_29")
    private double demPop18to29N;

    @Column(name= "population_30_54")
    private double demPop30to54N;

    @Column(name= "population_55_74")
    private double demPop55to74N;

    public double getPopulation18to29() {
        return demPop18to29N;
    }

    public void setPopulation18to29(double demPop18to29N) {
        this.demPop18to29N = demPop18to29N;
    }

    public double getPopulation30to54() {
        return demPop30to54N;
    }

    public void setPopulation30to54(double demPop30to54N) {
        this.demPop30to54N = demPop30to54N;
    }

    public double getPopulation55to74() {
        return demPop55to74N;
    }

    public void setPopulation55to74(double demPop55to74N) {
        this.demPop55to74N = demPop55to74N;
    }

    public double getPrMarried18to29() {
        return demMarried18to29Share;
    }

    public void setPrMarried18to29(double demMarried18to29Share) {
        this.demMarried18to29Share = demMarried18to29Share;
    }

    public double getPrMarried30to54() {
        return demMarried30to54Share;
    }

    public void setPrMarried30to54(double demMarried30to54Share) {
        this.demMarried30to54Share = demMarried30to54Share;
    }

    public double getPrMarried55to74() {
        return demMarried55to74Share;
    }

    public void setPrMarried55to74(double demMarried55to74Share) {
        this.demMarried55to74Share = demMarried55to74Share;
    }

    public double getAvkids18to29() {
        return demNChild18to29Avg;
    }

    public void setAvkids18to29(double demNChild18to29Avg) {
        this.demNChild18to29Avg = demNChild18to29Avg;
    }

    public double getAvkids30to54() {
        return demNChild30to54Avg;
    }

    public void setAvkids30to54(double demNChild30to54Avg) {
        this.demNChild30to54Avg = demNChild30to54Avg;
    }

    public double getAvkids55to74() {
        return demNChild55to74Avg;
    }

    public void setAvkids55to74(double demNChild55to74Avg) {
        this.demNChild55to74Avg = demNChild55to74Avg;
    }

    public void update(AgeBandAggregates agg) {

        setPrMarried18to29(agg.prMarr[0]);
        setPrMarried30to54(agg.prMarr[1]);
        setPrMarried55to74(agg.prMarr[2]);

        setAvkids18to29(agg.avkids[0]);
        setAvkids30to54(agg.avkids[1]);
        setAvkids55to74(agg.avkids[2]);

        setPopulation18to29(agg.popula[0]);
        setPopulation30to54(agg.popula[1]);
        setPopulation55to74(agg.popula[2]);
    }
}
