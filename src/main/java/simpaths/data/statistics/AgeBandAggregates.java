package simpaths.data.statistics;

import java.util.Collection;
import java.util.function.Supplier;

import simpaths.data.Parameters;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Indicator;

/**
 *
 * POPULATION AGGREGATES BY AGE BAND, SHARED BY THE ANNUAL STATISTICS OUTPUTS
 *
 * Bands are indexed 0 = 18-29, 1 = 30-54, 2 = 55-74.
 *
 * WealthIncomeStatistics, DemographicStatistics, LabourStatistics and HealthStatistics each report a
 * subset of these aggregates. Computing them together keeps a single traversal of the
 * population per simulated year, and a single definition of each statistic, whichever
 * of those four outputs happen to be enabled.
 *
 */
public class AgeBandAggregates {

    public final double[] prMarr = {0.,0.,0.};          // share cohabiting
    public final double[] avkids = {0.,0.,0.};          // average dependent children
    public final double[] health = {0.,0.,0.};          // average self-rated health
    public final double[] prDisa = {0.,0.,0.};          // share with long-term disability
    public final double[] workFT = {0.,0.,0.};          // share in full-time work
    public final double[] workPT = {0.,0.,0.};          // share in part-time work
    public final double[] labInc = {0.,0.,0.};          // weekly earnings per worker
    public final double[] invInc = {0.,0.,0.};          // monthly equivalised investment income
    public final double[] invLosses = {0.,0.,0.};       // monthly equivalised investment losses
    public final double[] penInc = {0.,0.,0.};          // monthly equivalised pension income
    public final double[] grossDisInc = {0.,0.,0.};     // monthly equivalised disposable income gross of investment losses
    public final double[] wealth = {0.,0.,0.};          // equivalised total wealth
    public final double[] popula = {0.,0.,0.};          // population count

    private AgeBandAggregates() {}

    /// Compute the age-band aggregates with an arbitrary [Supplier].
    /// This is intended for testing.
    public static AgeBandAggregates computeWithSupplier(Supplier<Collection<Person>> supplier) {
        var persons = supplier.get();
        AgeBandAggregates agg = new AgeBandAggregates();

        double[] prMarr = agg.prMarr;
        double[] avkids = agg.avkids;
        double[] health = agg.health;
        double[] prDisa = agg.prDisa;
        double[] workFT = agg.workFT;
        double[] workPT = agg.workPT;
        double[] labInc = agg.labInc;
        double[] invInc = agg.invInc;
        double[] invLosses = agg.invLosses;
        double[] penInc = agg.penInc;
        double[] grossDisInc = agg.grossDisInc;
        double[] wealth = agg.wealth;
        double[] popula = agg.popula;
        for (var person : persons) {
            // loop over entire population

            int ii = -1;
            if (person.getDemAge()>=18 && person.getDemAge()<=29) {
                ii = 0;
            } else if (person.getDemAge()>=30 && person.getDemAge()<=54) {
                ii = 1;
            } else if (person.getDemAge()>=55 && person.getDemAge()<=74) {
                ii = 2;
            }
            if (ii>=0) {

                double es = person.getBenefitUnit().getEquivalisedWeight();

                prMarr[ii] += person.getCohabiting();
                avkids[ii] += person.getBenefitUnit().getNumberChildrenAll();
                health[ii] += person.getHealthSelfRatedValue();
                prDisa[ii] += (Indicator.True.equals(person.getHealthDsblLongtermFlag()))? 1.0: 0.0;
                labInc[ii] += person.getEarningsWeekly();
                if ((double)person.getLabourSupplyHoursWeekly() > Parameters.MIN_HOURS_FULL_TIME_EMPLOYED)
                    workFT[ii] += 1.0;
                else if ((double)person.getLabourSupplyHoursWeekly() > 1.0)
                    workPT[ii] += 1.0;

                invInc[ii] += person.getBenefitUnit().getInvestmentIncomeAnnual() / 12.0 / es;
                penInc[ii] += person.getBenefitUnit().getPensionIncomeAnnual() / 12.0 / es;
                if (person.getBenefitUnit().getInvestmentIncomeAnnual()<0.0) {
                    invLosses[ii] += person.getBenefitUnit().getInvestmentIncomeAnnual() / 12.0 / es;
                    grossDisInc[ii] += (person.getBenefitUnit().getDisposableIncomeMonthlyNoNull() -
                            person.getBenefitUnit().getInvestmentIncomeAnnual() / 12.0) / es;
                } else {
                    grossDisInc[ii] += person.getBenefitUnit().getDisposableIncomeMonthlyNoNull() / es;
                }
                wealth[ii] += person.getBenefitUnit().getWealthTotValue(false) / es;
                popula[ii] += 1.0;
            }
        }
        for (int ii=0; ii<=2; ii++) {

            // guard both denominators: an empty age band, or a band with no workers, would
            // otherwise divide by zero and put NaN into the output
            if (workFT[ii] + workPT[ii] > 0.0)
                labInc[ii] /= (workFT[ii] + workPT[ii]);

            if (popula[ii]>0) {

                prMarr[ii] /= popula[ii];
                avkids[ii] /= popula[ii];
                health[ii] /= popula[ii];
                prDisa[ii] /= popula[ii];
                workFT[ii] /= popula[ii];
                workPT[ii] /= popula[ii];
                invInc[ii] /= popula[ii];
                penInc[ii] /= popula[ii];
                invLosses[ii] /= popula[ii];
                grossDisInc[ii] /= popula[ii];
                wealth[ii] /= popula[ii];
            }
        }

        return agg;
    }

    /// Traverse the population and evaluate all age-band aggregates.
    ///
    /// @param model the simulation manager holding the population
    /// @return the evaluated aggregates
    public static AgeBandAggregates compute(SimPathsModel model) {
        return AgeBandAggregates.computeWithSupplier(model::getPersons);
    }
}
