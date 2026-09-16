package simpaths.data.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import microsim.FilteredCollection;
import microsim.data.db.PanelEntityKey;
import microsim.dev.statistics.CrossSection;
import microsim.dev.statistics.Stats;
import simpaths.data.filters.Filters;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Gender;

/**
 *
 * SUBJECTIVE WELLBEING AND HEALTH-RELATED QUALITY OF LIFE, BY GENDER
 *
 * Written once per gender group per simulated year (Total, Male, Female), so the
 * output is stacked long rather than wide. All measures cover ages 25 to 64.
 *
 * Population-wide health statistics reported by age band live in HealthStatistics.
 *
 */
@Entity
public class WellbeingByGender {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    @Column(name = "gender")
    private String demSex;

    // mental health numeric
    @Column(name = "dhm_mean")
    private double healthWbScore0to36Avg;

    @Column(name = "dhm_median")
    private double healthWbScore0to36P50;

    @Column(name = "dhm_p_10")
    private double healthWbScore0to36P10;

    @Column(name = "dhm_p_90")
    private double healthWbScore0to36P90;

    @Column(name = "dhm_p_25")
    private double healthWbScore0to36P25;

    @Column(name = "dhm_p_75")
    private double healthWbScore0to36P75;

    // MCS score numeric
    @Column(name = "dhe_mcs_mean")
    private double healthMentalMcsAvg;

    @Column(name = "dhe_mcs_median")
    private double healthMentalMcsP50;

    @Column(name = "dhe_mcs_p_10")
    private double healthMentalMcsP10;

    @Column(name = "dhe_mcs_p_90")
    private double healthMentalMcsP90;

    @Column(name = "dhe_mcs_p_25")
    private double healthMentalMcsP25;

    @Column(name = "dhe_mcs_p_75")
    private double healthMentalMcsP75;

    // PCS score numeric
    @Column(name = "dhe_pcs_mean")
    private double healthPhysicalPcsAvg;

    @Column(name = "dhe_pcs_median")
    private double healthPhysicalPcsP50;

    @Column(name = "dhe_pcs_p_10")
    private double healthPhysicalPcsP10;

    @Column(name = "dhe_pcs_p_90")
    private double healthPhysicalPcsP90;

    @Column(name = "dhe_pcs_p_25")
    private double healthPhysicalPcsP25;

    @Column(name = "dhe_pcs_p_75")
    private double healthPhysicalPcsP75;

    // Life Satisfaction numeric
    @Column(name = "dls_mean")
    private double demLifeSatScore0to10Avg;

    @Column(name = "dls_median")
    private double demLifeSatScore0to10P50;

    @Column(name = "dls_p_10")
    private double demLifeSatScore0to10P10;

    @Column(name = "dls_p_90")
    private double demLifeSatScore0to10P90;

    @Column(name = "dls_p_25")
    private double demLifeSatScore0to10P25;

    @Column(name = "dls_p_75")
    private double demLifeSatScore0to10P75;

    @Column(name = "qualys")
    private double healthLifeYearQualAdj;

    @Column(name = "wellbys")
    private double healthLifeYearWbAdj;

    //N
    @Column(name = "N")
    private int healthNObsSubGroup;


    public void setGender(String demSex) {
        this.demSex = demSex;
    }

    /**
     *
     * ENTITY ID FOR A GENDER GROUP
     *
     * Three rows are written per simulated year, one per gender group. The entity key is
     * part of the database primary key alongside simulation time and run, so the groups
     * need distinct ids to be persisted as separate records rather than overwriting
     * one another.
     *
     * @param gender_s the gender group being reported
     * @return the id identifying that group
     *
     */
    private static long genderKeyId(String gender_s) {

        if ("Male".equals(gender_s))
            return 2L;
        if ("Female".equals(gender_s))
            return 3L;
        return 1L;      // Total
    }

    public void setHealthWbScore0to36Avg(double healthWbScore0to36Avg) {
        this.healthWbScore0to36Avg = healthWbScore0to36Avg;
    }

    public void setHealthWbScore0to36P50(double healthWbScore0to36P50) {
        this.healthWbScore0to36P50 = healthWbScore0to36P50;
    }

    public void setHealthWbScore0to36P10(double healthWbScore0to36P10) {
        this.healthWbScore0to36P10 = healthWbScore0to36P10;
    }

    public void setHealthWbScore0to36P90(double healthWbScore0to36P90) {
        this.healthWbScore0to36P90 = healthWbScore0to36P90;
    }

    public void setHealthWbScore0to36P25(double healthWbScore0to36P25) {
        this.healthWbScore0to36P25 = healthWbScore0to36P25;
    }

    public void setHealthWbScore0to36P75(double healthWbScore0to36P75) {
        this.healthWbScore0to36P75 = healthWbScore0to36P75;
    }

    public void setHealthMentalMcsAvg(double healthMentalMcsAvg) {
        this.healthMentalMcsAvg = healthMentalMcsAvg;
    }

    public void setHealthMentalMcsP50(double healthMentalMcsP50) {
        this.healthMentalMcsP50 = healthMentalMcsP50;
    }

    public void setHealthMentalMcsP10(double healthMentalMcsP10) {
        this.healthMentalMcsP10 = healthMentalMcsP10;
    }

    public void setHealthMentalMcsP90(double healthMentalMcsP90) {
        this.healthMentalMcsP90 = healthMentalMcsP90;
    }

    public void setHealthMentalMcsP25(double healthMentalMcsP25) {
        this.healthMentalMcsP25 = healthMentalMcsP25;
    }

    public void setHealthMentalMcsP75(double healthMentalMcsP75) {
        this.healthMentalMcsP75 = healthMentalMcsP75;
    }

    public void setHealthPhysicalPcsAvg(double healthPhysicalPcsAvg) {
        this.healthPhysicalPcsAvg = healthPhysicalPcsAvg;
    }

    public void setHealthPhysicalPcsP50(double healthPhysicalPcsP50) {
        this.healthPhysicalPcsP50 = healthPhysicalPcsP50;
    }

    public void setHealthPhysicalPcsP10(double healthPhysicalPcsP10) {
        this.healthPhysicalPcsP10 = healthPhysicalPcsP10;
    }

    public void setHealthPhysicalPcsP90(double healthPhysicalPcsP90) {
        this.healthPhysicalPcsP90 = healthPhysicalPcsP90;
    }

    public void setHealthPhysicalPcsP25(double healthPhysicalPcsP25) {
        this.healthPhysicalPcsP25 = healthPhysicalPcsP25;
    }

    public void setHealthPhysicalPcsP75(double healthPhysicalPcsP75) {
        this.healthPhysicalPcsP75 = healthPhysicalPcsP75;
    }

    public void setDemLifeSatScore0to10Avg(double demLifeSatScore0to10Avg) {
        this.demLifeSatScore0to10Avg = demLifeSatScore0to10Avg;
    }

    public void setDemLifeSatScore0to10P50(double demLifeSatScore0to10P50) {
        this.demLifeSatScore0to10P50 = demLifeSatScore0to10P50;
    }

    public void setDemLifeSatScore0to10P10(double demLifeSatScore0to10P10) {
        this.demLifeSatScore0to10P10 = demLifeSatScore0to10P10;
    }

    public void setDemLifeSatScore0to10P90(double demLifeSatScore0to10P90) {
        this.demLifeSatScore0to10P90 = demLifeSatScore0to10P90;
    }

    public void setDemLifeSatScore0to10P25(double demLifeSatScore0to10P25) {
        this.demLifeSatScore0to10P25 = demLifeSatScore0to10P25;
    }

    public void setDemLifeSatScore0to10P75(double demLifeSatScore0to10P75) {
        this.demLifeSatScore0to10P75 = demLifeSatScore0to10P75;
    }

    public void setN(int n) {
        healthNObsSubGroup = n;
    }

    public void setQalys(double healthLifeYearQualAdj) {
        this.healthLifeYearQualAdj = healthLifeYearQualAdj;
    }

    public void setWellbys(double healthLifeYearWbAdj) {
        this.healthLifeYearWbAdj = healthLifeYearWbAdj;
    }

    public void update(SimPathsModel model, String gender_s) {
        // set gender, and key this record so the three gender groups stay distinct
        setGender(gender_s);
        key = new PanelEntityKey(genderKeyId(gender_s));

        var filter = Filters.ageRange(25, 64);
        if (!gender_s.equals("Total")) {
            filter = filter.and(Filters.gender(Gender.valueOf(gender_s)));
        }

        var filteredPop = new FilteredCollection<>(model::getPersons, filter).once();


        // dhm score (mental health)
        var dhm_cs = new CrossSection<>(filteredPop, Person::getHealthWbScore0to36);
        var dhm_stats = new Stats(dhm_cs.get()).descrStats();
        setHealthWbScore0to36Avg(dhm_stats.getMean());
        setHealthWbScore0to36P10(dhm_stats.getPercentile(10.0));
        setHealthWbScore0to36P25(dhm_stats.getPercentile(25.0));
        setHealthWbScore0to36P50(dhm_stats.getPercentile(50.0));
        setHealthWbScore0to36P75(dhm_stats.getPercentile(75.0));
        setHealthWbScore0to36P90(dhm_stats.getPercentile(90.0));

        // mcs score (mental health)
        var mcs_cs = new CrossSection<>(filteredPop, Person::getHealthMentalMcs);
        var mcs_stats = new Stats(mcs_cs.get()).descrStats();
        setHealthMentalMcsAvg(mcs_stats.getMean());
        setHealthMentalMcsP10(mcs_stats.getPercentile(10.0));
        setHealthMentalMcsP25(mcs_stats.getPercentile(25.0));
        setHealthMentalMcsP50(mcs_stats.getPercentile(50.0));
        setHealthMentalMcsP75(mcs_stats.getPercentile(75.0));
        setHealthMentalMcsP90(mcs_stats.getPercentile(90.0));

        // pcs score (physical well-being)
        var pcs_cs = new CrossSection<>(filteredPop, Person::getHealthPhysicalPcs);
        var pcs_stats = new Stats(pcs_cs.get()).descrStats();
        setHealthPhysicalPcsAvg(pcs_stats.getMean());
        setHealthPhysicalPcsP10(pcs_stats.getPercentile(10.0));
        setHealthPhysicalPcsP25(pcs_stats.getPercentile(25.0));
        setHealthPhysicalPcsP50(pcs_stats.getPercentile(50.0));
        setHealthPhysicalPcsP75(pcs_stats.getPercentile(75.0));
        setHealthPhysicalPcsP90(pcs_stats.getPercentile(90.0));

        // Life Satisfaction score
        var dls_cs = new CrossSection<>(filteredPop, Person::getDemLifeSatScore0to10);
        var dls_stats = new Stats(dls_cs.get()).descrStats();
        setDemLifeSatScore0to10Avg(dls_stats.getMean());
        setDemLifeSatScore0to10P10(dls_stats.getPercentile(10.0));
        setDemLifeSatScore0to10P25(dls_stats.getPercentile(25.0));
        setDemLifeSatScore0to10P50(dls_stats.getPercentile(50.0));
        setDemLifeSatScore0to10P75(dls_stats.getPercentile(75.0));
        setDemLifeSatScore0to10P90(dls_stats.getPercentile(90.0));

        // QALYS as sum of EQ5D
        var eq5d_cs = new CrossSection<>(filteredPop, Person::getDemLifeSatEQ5D);
        var eq5d_stats = new Stats(eq5d_cs.get());
        setQalys(eq5d_stats.sum());

        // WELLBYs as sum of 'points' in 0-10-scale life satisfaction (adjusted)
        setWellbys(dls_stats.getSum());

        // count
        setN(filteredPop.get().size());
    }
}
