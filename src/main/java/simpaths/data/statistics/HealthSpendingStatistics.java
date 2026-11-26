package simpaths.data.statistics;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import microsim.data.db.PanelEntityKey;

/** Annual UK public health spending, in real-2015 pounds. */
@Entity
public class HealthSpendingStatistics {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    // Names carry units because CSV export uses Java field names as headers.
    private double populationExpandedPeople;
    private double benchmarkAnnualGbp2015;
    private Double fixedAgeStructureAnnualGbp2015;
    private Double ageingEffectAnnualGbp2015;

    public void update(HealthSpendingCalculator.SpendingResult result) {
        populationExpandedPeople = result.getTotalPopulation();
        benchmarkAnnualGbp2015 = result.getBenchmarkExpenditure();
        fixedAgeStructureAnnualGbp2015 = result.getFixedAgeExpenditure().isPresent()
                ? result.getFixedAgeExpenditure().getAsDouble() : null;
        ageingEffectAnnualGbp2015 = result.getAgeingEffect().isPresent()
                ? result.getAgeingEffect().getAsDouble() : null;
    }

    public double getPopulationExpandedPeople() {
        return populationExpandedPeople;
    }

    public double getBenchmarkAnnualGbp2015() {
        return benchmarkAnnualGbp2015;
    }

    public Double getFixedAgeStructureAnnualGbp2015() {
        return fixedAgeStructureAnnualGbp2015;
    }

    public Double getAgeingEffectAnnualGbp2015() {
        return ageingEffectAnnualGbp2015;
    }
}
