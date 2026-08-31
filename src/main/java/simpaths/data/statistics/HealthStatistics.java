package simpaths.data.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import microsim.data.db.PanelEntityKey;

/**
 *
 * POPULATION HEALTH STATISTICS BY AGE BAND
 *
 * One row per simulated year, covering the whole population in each of the three age
 * bands 18-29, 30-54 and 55-74.
 *
 * Subjective wellbeing and health-related quality of life, which are reported by gender
 * over ages 25 to 64, live in WellbeingByGender.
 *
 */
@Entity
public class HealthStatistics {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    //average health
    @Column(name = "health_18_29")
    private double healthScore18to29Avg;

    @Column(name = "health_30_54")
    private double healthScore30to54Avg;

    @Column(name = "health_55_74")
    private double healthScore55to74Avg;

    //population shares disabled
    @Column(name = "pr_disabled_18_29")
    private double healthDsbl18to29Share;

    @Column(name = "pr_disabled_30_54")
    private double healthDsbl30to54Share;

    @Column(name = "pr_disabled_55_74")
    private double healthDsbl55to74Share;

    public double getHealth18to29() {
        return healthScore18to29Avg;
    }

    public void setHealth18to29(double healthScore18to29Avg) {
        this.healthScore18to29Avg = healthScore18to29Avg;
    }

    public double getHealth30to54() {
        return healthScore30to54Avg;
    }

    public void setHealth30to54(double healthScore30to54Avg) {
        this.healthScore30to54Avg = healthScore30to54Avg;
    }

    public double getHealth55to74() {
        return healthScore55to74Avg;
    }

    public void setHealth55to74(double healthScore55to74Avg) {
        this.healthScore55to74Avg = healthScore55to74Avg;
    }

    public double getPrDisabled18to29() {
        return healthDsbl18to29Share;
    }

    public void setPrDisabled18to29(double healthDsbl18to29Share) {
        this.healthDsbl18to29Share = healthDsbl18to29Share;
    }

    public double getPrDisabled30to54() {
        return healthDsbl30to54Share;
    }

    public void setPrDisabled30to54(double healthDsbl30to54Share) {
        this.healthDsbl30to54Share = healthDsbl30to54Share;
    }

    public double getPrDisabled55to74() {
        return healthDsbl55to74Share;
    }

    public void setPrDisabled55to74(double healthDsbl55to74Share) {
        this.healthDsbl55to74Share = healthDsbl55to74Share;
    }

    public void update(AgeBandAggregates agg) {

        setHealth18to29(agg.health[0]);
        setHealth30to54(agg.health[1]);
        setHealth55to74(agg.health[2]);

        setPrDisabled18to29(agg.prDisa[0]);
        setPrDisabled30to54(agg.prDisa[1]);
        setPrDisabled55to74(agg.prDisa[2]);
    }
}
