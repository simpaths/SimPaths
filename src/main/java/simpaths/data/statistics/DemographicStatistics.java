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

    //population shares in partnerships
    @Column(name = "pr_partnered_18_29")
    private double demPartnered18to29Share;

    @Column(name = "pr_partnered_30_54")
    private double demPartnered30to54Share;

    @Column(name = "pr_partnered_55_74")
    private double demPartnered55to74Share;

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

    public double getPrPartnered18to29() {
        return demPartnered18to29Share;
    }

    public void setPrPartnered18to29(double demPartnered18to29Share) {
        this.demPartnered18to29Share = demPartnered18to29Share;
    }

    public double getPrPartnered30to54() {
        return demPartnered30to54Share;
    }

    public void setPrPartnered30to54(double demPartnered30to54Share) {
        this.demPartnered30to54Share = demPartnered30to54Share;
    }

    public double getPrPartnered55to74() {
        return demPartnered55to74Share;
    }

    public void setPrPartnered55to74(double demPartnered55to74Share) {
        this.demPartnered55to74Share = demPartnered55to74Share;
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

        setPrPartnered18to29(agg.prPartnered[0]);
        setPrPartnered30to54(agg.prPartnered[1]);
        setPrPartnered55to74(agg.prPartnered[2]);

        setAvkids18to29(agg.avkids[0]);
        setAvkids30to54(agg.avkids[1]);
        setAvkids55to74(agg.avkids[2]);

        setPopulation18to29(agg.popula[0]);
        setPopulation30to54(agg.popula[1]);
        setPopulation55to74(agg.popula[2]);
    }
}
