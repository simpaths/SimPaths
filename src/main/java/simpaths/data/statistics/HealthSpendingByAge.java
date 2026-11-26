package simpaths.data.statistics;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import microsim.data.db.PanelEntityKey;

/** One actual single-year age's population, cost and public health spending. */
@Entity
public class HealthSpendingByAge {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    private int ageYears;
    private double populationExpandedPeople;
    private double costAnnualGbp2015PerPerson;
    private double expenditureAnnualGbp2015;

    public void update(int age, double population, double cost, double expenditure) {
        if (age < 0 || !Double.isFinite(population) || population < 0.0
                || !Double.isFinite(cost) || cost <= 0.0
                || !Double.isFinite(expenditure) || expenditure < 0.0) {
            throw new IllegalArgumentException("Invalid health-spending age detail for age " + age);
        }
        key = new PanelEntityKey((long) age + 1L);
        ageYears = age;
        populationExpandedPeople = population;
        costAnnualGbp2015PerPerson = cost;
        expenditureAnnualGbp2015 = expenditure;
    }

    public int getAgeYears() {
        return ageYears;
    }

    public double getPopulationExpandedPeople() {
        return populationExpandedPeople;
    }

    public double getCostAnnualGbp2015PerPerson() {
        return costAnnualGbp2015PerPerson;
    }

    public double getExpenditureAnnualGbp2015() {
        return expenditureAnnualGbp2015;
    }
}
