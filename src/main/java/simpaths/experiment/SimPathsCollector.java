// define package
package simpaths.experiment;

// import Java packages
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import microsim.data.db.PanelEntityKey;
import simpaths.data.filters.Filters;
import simpaths.data.statistics.AgeBandAggregates;
import simpaths.data.statistics.LabourStatistics;
import simpaths.data.statistics.HealthStatistics;
import simpaths.data.statistics.WellbeingByGender;
import simpaths.model.BenefitUnit;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Education;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Quintiles;
// import plug-in packages
import org.apache.commons.math3.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import microsim.FilteredCollection;
// import JAS-mine packages
import microsim.annotation.GUIparameter;
import microsim.data.DataExport;
import microsim.dev.statistics.CrossSection;
import microsim.dev.statistics.Stats;
import microsim.engine.AbstractSimulationCollectorManager;
import microsim.engine.SimulationEngine;
import microsim.engine.SimulationManager;
import microsim.event.EventListener;
import microsim.event.SingleTargetEvent;
// import LABOURsim packages
import simpaths.data.Parameters;
import simpaths.data.statistics.AlignmentStatistics;
import simpaths.data.statistics.WealthIncomeStatistics;
import simpaths.data.statistics.DemographicStatistics;
import simpaths.model.Person;
import simpaths.model.enums.Region;


/**
 *
 * CLASS TO MANAGE COLLECTION OF SIMULATED OUTPUT
 *
 */
public class SimPathsCollector extends AbstractSimulationCollectorManager implements EventListener {

    // default simulation parameters
    private static Logger log = LogManager.getLogger(SimPathsCollector.class);

    @GUIparameter(description="Calculate the Gini coefficients of income (also displayed in charts)")
    private boolean calculateGiniCoefficients = false;

    @GUIparameter(description="Calculate extended set of population characteristics (useful for validation)")
    private boolean persistDemographicStatistics = true;

    @GUIparameter(description="Report alignment adjustment factors (AlignmentStatistics.csv)")
    private boolean persistAlignmentStatistics = true;

    private boolean persistLabourStatistics = true;

    @GUIparameter(description="Report population health statistics by age band (HealthStatistics.csv)")
    private boolean persistHealthStatistics = true;

    @GUIparameter(description="Report wellbeing statistics by gender (WellbeingByGender.csv)")
    private boolean persistWellbeingByGender = true;

    @GUIparameter(description="Toggle to turn database persistence on/off")
    private boolean exportToDatabase = false;

    @GUIparameter(description="Toggle to turn export to .csv files on/off")
    private boolean exportToCSV = true;

    @GUIparameter(description="Toggle to turn persistence of statistics on/off")
    private boolean persistWealthIncomeStatistics = true;

    @GUIparameter(description="Toggle to turn persistence of persons on/off")
    private boolean persistPersons = true;

    @GUIparameter(description="Toggle to turn persistence of benefit units on/off")
    private boolean persistBenefitUnits = true;

    @GUIparameter(description = "Toggle to turn persistence of households on/off")
    private boolean persistHouseholds = true;

    @GUIparameter(description="First time-step to dump data to database")
    private Long dataDumpStartTime = 0L;

    @GUIparameter(description="Number of time-steps in between database dumps")
    private Double dataDumpTimePeriod = 1.;

    // Schedule at the same time as the model and observer events, but with an order
    // higher than model but less than observer, so will be fired after the model
    // and before the observe have updated.
    private int ordering = Parameters.COLLECTOR_ORDERING;

    private SimPathsModel model;

    private WealthIncomeStatistics wealthIncomeStats;

    private DemographicStatistics demographicStats;

    private AlignmentStatistics alignmentStats;

    private HealthStatistics statsHealthGender;

    private HealthStatistics statsHealthAgeGrps;

    private HealthStatistics statsHealthHousehold;

    private HealthStatistics statsHealthEducation;

    private LabourStatistics labourStats;

    private WellbeingByGender wellbeingByGender;

    private AgeBandAggregates ageBandAggregates;

    private double ageBandAggregatesTime = Double.NaN;

    protected GiniPersonalGrossEarnings giniPersonalGrossEarnings;

    protected GiniEquivalisedHouseholdDisposableIncome giniEquivalisedHouseholdDisposableIncome;

    private Ydses_c5 yHhQuintilesMonthC5;

    private GrossLabourIncome grossLabourIncome;

    private DataExport exportPersons;

    private DataExport exportBenefitUnits;

    private DataExport exportHouseholds;

    private DataExport exportWealthIncomeStatistics;

    private DataExport exportDemographicStatistics;

    private DataExport exportAlignmentStatistics;

    private DataExport exportLabourStatistics;

    private DataExport exportStatisticsEmploymentAgeGrps;

    private DataExport exportHealthStatisticsGender;

    private DataExport exportHealthStatisticsAgeGrps;

    private DataExport exportHealthStatisticsHousehold;

    private DataExport exportHealthStatisticsEducation;

    public record AgeRange(int lowerBound, int upperBound) {
        @Override
        public String toString() {
            return lowerBound + "-" + upperBound;
        }
    }

    public record HouseholdStructure(boolean coupled, boolean children, Gender gender) {
        @Override
        public String toString() {
            return gender.toString() + "-" + (coupled ? "Partnered" : "Single") + "-" + (children ? "Children" : "No children");
        }
    }

    private DataExport exportWellbeingByGender;

    /**
     *
     * CONSTRUCTOR FOR SIMULATION COLLECTOR
     *
     */
    public SimPathsCollector(SimulationManager manager) {
        super(manager);
    }

    // ---------------------------------------------------------------------
    // Event Listener
    // ---------------------------------------------------------------------

    public enum Processes {

        CalculateHouseholdsGrossIncome,
        CalculateEquivalisedHouseholdDisposableIncome,
        CalculateGiniCoefficients,
        DumpPersons,
        DumpBenefitUnits,
        DumpHouseholds,
        DumpWealthIncomeStatistics,
        DumpDemographicStatistics,
        DumpAlignmentStatistics,
        DumpLabourStatistics,
        DumpHealthStatistics,
        DumpWellbeingByGender
    }

    String[] genders = {"Total", "Male", "Female"};

    List<AgeRange> ageGroups = List.of(
            new AgeRange(16, 17),
            new AgeRange(18, 24),
            new AgeRange(25, 34),
            new AgeRange(35, 49),
            new AgeRange(50, 64),
            new AgeRange(65, 130)
    );

    List<HouseholdStructure> householdStructures = List.of(
            new HouseholdStructure(true, true, Gender.Male),
            new HouseholdStructure(true, false, Gender.Male),
            new HouseholdStructure(false, true, Gender.Male),
            new HouseholdStructure(false, false, Gender.Male),
            new HouseholdStructure(true, true, Gender.Female),
            new HouseholdStructure(true, false, Gender.Female),
            new HouseholdStructure(false, true, Gender.Female),
            new HouseholdStructure(false, false, Gender.Female)
    );


    @Override
    public void onEvent(Enum<?> type) {
        switch ((Processes) type) {

        case CalculateHouseholdsGrossIncome:
            calculateGrossIncome();
        case CalculateEquivalisedHouseholdDisposableIncome:
            calculateEquivalisedHouseholdDisposableIncome();
            break;
        case CalculateGiniCoefficients:
            calculateGiniCoefficients();
            break;
        //To output data:
        case DumpPersons:
            try {
                exportPersons.export();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            break;
        case DumpBenefitUnits:
            try {
                exportBenefitUnits.export();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            break;
        case DumpHouseholds:
            try {
                exportHouseholds.export();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            break;
        case DumpWealthIncomeStatistics:
            wealthIncomeStats.update(ageBands());
            try {
                exportWealthIncomeStatistics.export();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            break;
        case DumpDemographicStatistics:
            demographicStats.update(ageBands());
            try {
                exportDemographicStatistics.export();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            break;
        case DumpAlignmentStatistics:
            alignmentStats.update(model);
            try {
                exportAlignmentStatistics.export();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            break;
        case DumpLabourStatistics:
            labourStats.update(model, ageBands());
            try {
                exportLabourStatistics.export();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            break;
        case DumpWellbeingByGender:
            for (String gender_s: genders) {
                wellbeingByGender.update(model, gender_s);
                try {
                    exportWellbeingByGender.export();
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }

            break;
        case DumpHealthStatistics:
            for (String gender_s: genders) {
                // FIXME: adapt to new stats
                // statsHealthGender.update(model, gender_s, new AgeRange(18, 64));
                try {
                    exportHealthStatisticsGender.export();
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }

            for (AgeRange ageGroup: ageGroups) {
                for (String gender_s: genders) {
                    // FIXME: adapt to new stats
                    // statsHealthAgeGrps.update(model, gender_s, ageGroup);
                    try {
                        exportHealthStatisticsAgeGrps.export();
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                }
            }

            for (HouseholdStructure householdStructure: householdStructures) {
                // FIXME: adapt to new stats
                // statsHealthHousehold.update(model, householdStructure);
                try {
                    exportHealthStatisticsHousehold.export();
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }

            for (Education education: Education.values()) {
                // FIXME: adapt to new stats
                // statsHealthEducation.update(model, education);
                try {
                    exportHealthStatisticsEducation.export();
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }

            break;
        }
    }


    // ---------------------------------------------------------------------
    // Manager
    // ---------------------------------------------------------------------

    @Override
    public void buildObjects() {

        model = (SimPathsModel) getManager();

        statsHealthGender = new HealthStatistics();
        statsHealthAgeGrps = new HealthStatistics();
        statsHealthHousehold = new HealthStatistics();
        statsHealthEducation = new HealthStatistics();

        wealthIncomeStats = new WealthIncomeStatistics();
        demographicStats = new DemographicStatistics();
        alignmentStats = new AlignmentStatistics();
        labourStats = new LabourStatistics();
        wellbeingByGender = new WellbeingByGender();

        //For export to database or .csv files.
        if(persistPersons)
            exportPersons = new DataExport(model.getPersons(), exportToDatabase, exportToCSV);
        if(persistBenefitUnits)
            exportBenefitUnits = new DataExport(model.getBenefitUnits(), exportToDatabase, exportToCSV);
        if (persistHouseholds)
            exportHouseholds = new DataExport(model.getHouseholds(), exportToDatabase, exportToCSV);

        if (persistHealthStatistics) {
            exportHealthStatisticsGender = new DataExport(statsHealthGender, exportToDatabase, exportToCSV);
            exportHealthStatisticsAgeGrps = new DataExport(statsHealthAgeGrps, exportToDatabase, exportToCSV);
            exportHealthStatisticsHousehold = new DataExport(statsHealthHousehold, exportToDatabase, exportToCSV);
            exportHealthStatisticsEducation = new DataExport(statsHealthEducation, exportToDatabase, exportToCSV);
        }

        if (persistWealthIncomeStatistics)
            exportWealthIncomeStatistics = new DataExport(List.of(wealthIncomeStats), exportToDatabase, exportToCSV);
        if (persistDemographicStatistics)
            exportDemographicStatistics = new DataExport(List.of(demographicStats), exportToDatabase, exportToCSV);
        if (persistAlignmentStatistics)
            exportAlignmentStatistics = new DataExport(List.of(alignmentStats), exportToDatabase, exportToCSV);
        if (persistLabourStatistics)
            exportLabourStatistics = new DataExport(List.of(labourStats), exportToDatabase, exportToCSV);
        if (persistWellbeingByGender)
            exportWellbeingByGender = new DataExport(List.of(wellbeingByGender), exportToDatabase, exportToCSV);


        if (exportToCSV)
            OutputReadme.write(this, model);

        if (calculateGiniCoefficients) {
            giniPersonalGrossEarnings = new GiniPersonalGrossEarnings();
            giniEquivalisedHouseholdDisposableIncome = new GiniEquivalisedHouseholdDisposableIncome();
        }

        yHhQuintilesMonthC5 = new Ydses_c5();
        grossLabourIncome = new GrossLabourIncome();


    }

    /**
     *
     * EVALUATE THE AGE-BAND POPULATION AGGREGATES SHARED BY THE ANNUAL STATISTICS OUTPUTS
     *
     * WealthIncomeStatistics, DemographicStatistics, LabourStatistics and HealthStatistics all report
     * subsets of the same aggregates, and each is toggled independently. Caching on the
     * simulated time keeps the population traversal to once per year whichever
     * combination of those outputs is enabled, without imposing an order on their events.
     *
     */
    private AgeBandAggregates ageBands() {

        double time = SimulationEngine.getInstance().getTime();
        if (ageBandAggregates == null || ageBandAggregatesTime != time) {
            ageBandAggregates = AgeBandAggregates.compute(model);
            ageBandAggregatesTime = time;
        }
        return ageBandAggregates;
    }


    @Override
    public void buildSchedule() {

        SimPathsModel model = (SimPathsModel) SimulationEngine.getInstance().getManager(SimPathsModel.class.getCanonicalName());

        getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.CalculateHouseholdsGrossIncome), model.getStartYear(), ordering, dataDumpTimePeriod);

//		getEngine().getEventQueue().scheduleRepeat(new CollectionTargetEvent(model.getHouseholds(), BenefitUnit.Processes.CalculateEquivalisedDisposableIncome, true), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
//		getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.CalculateEquivalisedHouseholdDisposableIncome), model.getStartYear(), Order.BEFORE_ALL.getOrdering());
//		getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.CalculateEquivalisedHouseholdDisposableIncome), model.getStartYear(), -2); //Run once in the start year, before the model?
//		getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.CalculateEquivalisedHouseholdDisposableIncome), model.getStartYear(), ordering, dataDumpTimePeriod);
//		getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.CalculateEquivalisedHouseholdDisposableIncome), model.getEndYear(), -2);
        if (calculateGiniCoefficients) {
            getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.CalculateGiniCoefficients), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistWealthIncomeStatistics) {
            getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpWealthIncomeStatistics), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
//			getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.DumpWealthIncomeStatistics), model.getEndYear(), -2);		//Ensures the database is persisted on the last time-step
        }

        if (persistDemographicStatistics) {
            getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpDemographicStatistics), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistAlignmentStatistics) {
            getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpAlignmentStatistics), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistLabourStatistics) {
			getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpLabourStatistics), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistHealthStatistics){
			getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpHealthStatistics), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistWellbeingByGender){
			getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpWellbeingByGender), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistPersons) {
            getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpPersons), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistBenefitUnits) {
            getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpBenefitUnits), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }

        if (persistHouseholds) {
            getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpHouseholds), model.getStartYear() + dataDumpStartTime, ordering, dataDumpTimePeriod);
        }
    }

    // ---------------------------------------------------------------------
    //	Inner classes for data collection
    // ---------------------------------------------------------------------

    private class GrossLabourIncome {

        final SimPathsModel model = (SimPathsModel) getManager();

        public void update() {
            var filtered = new FilteredCollection<>(model::getPersons, Filters.flexibleLabourSupply());
            var income_cs = new CrossSection<>(filtered, Person::getCovidYLabGross);
            var income_stats = new Stats(income_cs.get()).descrStats();

            wealthIncomeStats.setYLabP20(income_stats.getPercentile(20.0));
            wealthIncomeStats.setYLabP40(income_stats.getPercentile(40.0));
            wealthIncomeStats.setYLabP60(income_stats.getPercentile(60.0));
            wealthIncomeStats.setYLabP80(income_stats.getPercentile(80.0));

            for (Person person : model.getPersons()) {
                double covidModuleGrossLabourIncomeBaseline = person.getCovidYLabGross();
                if (covidModuleGrossLabourIncomeBaseline <= wealthIncomeStats.getYLabP20()) {
                    person.setCovidYLabGrossXt5(Quintiles.Q1);
                } else if (covidModuleGrossLabourIncomeBaseline <= wealthIncomeStats.getYLabP40()) {
                    person.setCovidYLabGrossXt5(Quintiles.Q2);
                } else if (covidModuleGrossLabourIncomeBaseline <= wealthIncomeStats.getYLabP60()) {
                    person.setCovidYLabGrossXt5(Quintiles.Q3);
                } else if (covidModuleGrossLabourIncomeBaseline <= wealthIncomeStats.getYLabP80()) {
                    person.setCovidYLabGrossXt5(Quintiles.Q4);
                } else {
                    person.setCovidYLabGrossXt5(Quintiles.Q5);
                }
            }

        }
    }


    /*
     *This method calculates quintiles of household gross income
     *
     */
    private class Ydses_c5 {

        final SimPathsModel model = (SimPathsModel) getManager();

        private boolean initialDistributionCalculated;

        public void update() {
            //Ydses_c5
            var hh_income_cs = new CrossSection<>(model::getBenefitUnits, BenefitUnit::getI_yNonBenHhGrossAsinhNoNull);
            var hh_income_stats = new Stats(hh_income_cs.get()).descrStats();

            wealthIncomeStats.setYHhQuintilesC5P20(hh_income_stats.getPercentile(20.0));
            wealthIncomeStats.setYHhQuintilesC5P40(hh_income_stats.getPercentile(40.0));
            wealthIncomeStats.setYHhQuintilesC5P60(hh_income_stats.getPercentile(60.0));
            wealthIncomeStats.setYHhQuintilesC5P80(hh_income_stats.getPercentile(80.0));

            if (initialDistributionCalculated) {
                for (BenefitUnit benefitUnit : model.getBenefitUnits()) {
                    benefitUnit.updateIncomeQuintile();
                }
            }
            initialDistributionCalculated = true;
        }
    }

    public class GiniPersonalGrossEarnings {
        //I calculate that the Gini coefficient for household-weights w_i and variables x_i:
        //	G = [ sum_i sum_j w_i * w_j * abs( x_i - x_j) ] / [ 2 * (sum_i w_i) * (sum_j w_j * x_j) ]
        //Note in this particular case, the x_i are the personal (individual) gross income (potential earnings * labour supply)

        final SimPathsModel model = (SimPathsModel) getManager();

        private Map<Region, Double> giniWeightedPersonalGrossEarningsRegionalMap = new LinkedHashMap<Region, Double>();

        private double giniWeightedPersonalGrossEarningsNational;

        //Update gini coefficient of personal gross earnings
        private void update() {

            //Note that weighted means individual person weighted measures
            double weightedAbsDiffPersonalGrossEarningsNational = 0.;	//Sum of absolute difference between two person's weighted gross earnings (personal weight * potential earnings * labour supply)

            Map<Region, Double> weightedAbsDiffPersonalGrossEarningsRegional = new LinkedHashMap<Region, Double>();	//Sum of absolute difference between two person's weighted gross earnings (personal weight * potential earnings * labour supply)
            Map<Region, Double> totalWeightedPersonalGrossEarningsRegional = new LinkedHashMap<Region, Double>();	//Sum of (personal weight * potential earnings * labour supply)
            Map<Region, Double> totalPersonWeightRegional = new LinkedHashMap<Region, Double>();	//Sum of personal weights

            for(Region region: Parameters.getCountryRegions()) {
                weightedAbsDiffPersonalGrossEarningsRegional.put(region,  0.);
                totalWeightedPersonalGrossEarningsRegional.put(region,  0.);
                totalPersonWeightRegional.put(region,  0.);
            }

            //Filter out people with non-finite or negative gross earnings
            Map<Person, Double> validPersonalGrossEarningsMap = new LinkedHashMap<Person, Double>();
            for(Person person: model.getPersons()) {
                Double grossEarnings = person.getGrossEarningsWeekly();
                if(grossEarnings != null && Double.isFinite(grossEarnings) && grossEarnings >= 0.) {
                    validPersonalGrossEarningsMap.put(person, grossEarnings);
                }
            }

            for(Map.Entry<Person, Double> e1 : validPersonalGrossEarningsMap.entrySet()) {

                Person person1 = e1.getKey();
                Region region1 = person1.getRegion();
                double personWeight1 = person1.getWeight();
                double grossEarnings1 = e1.getValue();
                double weightedPersonalGrossEarnings1 = personWeight1 * grossEarnings1;

                Double thw = totalPersonWeightRegional.get(region1);
                totalPersonWeightRegional.put(region1,  thw + personWeight1);

                Double tir = totalWeightedPersonalGrossEarningsRegional.get(region1);
                totalWeightedPersonalGrossEarningsRegional.put(region1, tir + weightedPersonalGrossEarnings1);

                for(Map.Entry<Person, Double> e2 : validPersonalGrossEarningsMap.entrySet()) {

                    Person person2 = e2.getKey();
                    Region region2 = person2.getRegion();
                    double personWeight2 = person2.getWeight();
                    double grossEarnings2 = e2.getValue();

                    double weightedAbsDiffGrossEarnings = personWeight1 * personWeight2 * Math.abs(grossEarnings1 - grossEarnings2);
                    weightedAbsDiffPersonalGrossEarningsNational += weightedAbsDiffGrossEarnings;

                    if(region1.equals(region2)) {
                        double adGrossEarningsR = weightedAbsDiffPersonalGrossEarningsRegional.get(region1);
                        weightedAbsDiffPersonalGrossEarningsRegional.put(region1, adGrossEarningsR + weightedAbsDiffGrossEarnings);
                    }
                }
            }

            double totalWeightedPersonalGrossEarningsNational = 0.;	//Sum of (personal weight * potential earnings * labour supply)
            double totalPersonWeightNational = 0.;	//Sum of (person Weight)
            for(Region region : Parameters.getCountryRegions()) {
                double totalPersonWeightForRegion = totalPersonWeightRegional.get(region);
                totalPersonWeightNational += totalPersonWeightForRegion;

                double totalWeightedPersonalGrossEarningsForRegion = totalWeightedPersonalGrossEarningsRegional.get(region);
                totalWeightedPersonalGrossEarningsNational += totalWeightedPersonalGrossEarningsForRegion;

                double giniRegional = weightedAbsDiffPersonalGrossEarningsRegional.get(region) / (2. * totalPersonWeightForRegion * totalWeightedPersonalGrossEarningsForRegion);
                giniWeightedPersonalGrossEarningsRegionalMap.put(region, giniRegional);
                log.info("giniWeightedPersonalGrossEarningsRegionalMap for " + region + " = " + giniWeightedPersonalGrossEarningsRegionalMap.get(region) + ", weightedAbsDiffEquivalisedIncomeRegional.get(region) = " + weightedAbsDiffPersonalGrossEarningsRegional.get(region) + ", totalPersonWeightForRegion = " + totalPersonWeightForRegion + ", totalWeightedPersonalGrossEarningsForRegion = " + totalWeightedPersonalGrossEarningsForRegion);
            }
            giniWeightedPersonalGrossEarningsNational = weightedAbsDiffPersonalGrossEarningsNational / (2. * totalPersonWeightNational * totalWeightedPersonalGrossEarningsNational);
            wealthIncomeStats.setGiniPersonalGrossEarningsNational(giniWeightedPersonalGrossEarningsNational);
            log.info("giniWeightedPersonalGrossEarningsNational = " + giniWeightedPersonalGrossEarningsNational + ", weightedAbsDiffPersonalGrossEarningsNational = " + weightedAbsDiffPersonalGrossEarningsNational + ", totalPersonWeightNational = " + totalPersonWeightNational + ", totalWeightedPersonalGrossEarningsNational = " + totalWeightedPersonalGrossEarningsNational);

        }

        /// Gini coefficient at the national level.
        public double national() {
            return giniWeightedPersonalGrossEarningsNational;
        }

        /// Gini coefficient in the specified [Region].
        public double inRegion(Region region) {
            return giniWeightedPersonalGrossEarningsRegionalMap.get(region);
        }

    }


    public class GiniEquivalisedHouseholdDisposableIncome {

        //I calculate that the Gini coefficient for household-weights w_i and variables x_i:
        //	G = [ sum_i sum_j w_i * w_j * abs( x_i - x_j) ] / [ 2 * (sum_i w_i) * (sum_j w_j * x_j) ]
        //Note in this particular case, the x_i are the equivalised household income, so the variable itself also contains an 'equivalised weight', which is treated as part of the income (and different from the household-weight)

        final SimPathsModel model = (SimPathsModel) getManager();

        private Map<Region, Double> giniWeightedEquivalisedHouseholdDisposableIncomeRegionalMap = new LinkedHashMap<Region, Double>();

        private double giniWeightedEquivalisedHouseholdDisposableIncomeNational;

        //Update gini coefficient of household disposable income
        private void update() {

            //Note that weighted means household weighted measures
            double weightedAbsDiffEquivalisedIncomeNational = 0.;	//Sum of absolute difference between two household's weighted equivalised income (household weight * equivalised weight * household disposable income)

            Map<Region, Double> weightedAbsDiffEquivalisedIncomeRegional = new LinkedHashMap<Region, Double>();	//Sum of absolute difference between two household's weighted equivalised income (household weight * equivalised weight * household disposable income)
            Map<Region, Double> totalWeightedEquivalisedHouseholdIncomeRegional = new LinkedHashMap<Region, Double>();	//Sum of (household weight * equivalised weight * household disposable income)
            Map<Region, Double> totalHouseholdWeightRegional = new LinkedHashMap<Region, Double>();	//Sum of (household weight)

            for(Region region: Parameters.getCountryRegions()) {
                weightedAbsDiffEquivalisedIncomeRegional.put(region,  0.);
                totalWeightedEquivalisedHouseholdIncomeRegional.put(region,  0.);
                totalHouseholdWeightRegional.put(region,  0.);
            }

            //Filter out households with non-finite or negative disposable income
            Map<BenefitUnit, Double> validHousesEquivalisedIncomeMap = new LinkedHashMap<BenefitUnit, Double>();
            for(BenefitUnit house: model.getBenefitUnits()) {
                Double income = house.getEquivalisedDisposableIncomeYearly();
                if(income != null && Double.isFinite(income) && income >= 0.) {
                    validHousesEquivalisedIncomeMap.put(house, income);
                }
            }

            for(Map.Entry<BenefitUnit, Double> e1 : validHousesEquivalisedIncomeMap.entrySet()) {

                BenefitUnit house1 = e1.getKey();
                Region region1 = house1.getRegion();
                double houseWeight1 = house1.getWeight();
                double equivalisedIncome1 = e1.getValue();
                double weightedEquivalisedHouseholdIncome1 = houseWeight1 * equivalisedIncome1;	//Equivalised income * BenefitUnit-Weight

                Double thw = totalHouseholdWeightRegional.get(region1);
                totalHouseholdWeightRegional.put(region1,  thw + houseWeight1);

                Double tir = totalWeightedEquivalisedHouseholdIncomeRegional.get(region1);
                totalWeightedEquivalisedHouseholdIncomeRegional.put(region1, tir + weightedEquivalisedHouseholdIncome1);

                for(Map.Entry<BenefitUnit, Double> e2 : validHousesEquivalisedIncomeMap.entrySet()) {

                    BenefitUnit house2 = e2.getKey();
                    Region region2 = house2.getRegion();
                    double houseWeight2 = house2.getWeight();
                    double equivalisedIncome2 = e2.getValue();

                    double weightedAbsDiffEquivalisedIncome = houseWeight1 * houseWeight2 * Math.abs(equivalisedIncome1 - equivalisedIncome2);
                    weightedAbsDiffEquivalisedIncomeNational += weightedAbsDiffEquivalisedIncome;

                    if(region1.equals(region2)) {
                        double adIncomeR = weightedAbsDiffEquivalisedIncomeRegional.get(region1);
                        weightedAbsDiffEquivalisedIncomeRegional.put(region1, adIncomeR + weightedAbsDiffEquivalisedIncome);
                    }
                }
            }

            double totalWeightedEquivalisedHouseholdIncomeNational = 0.;	//Sum of (household weight * equivalised weight * household disposable income)
            double totalHouseholdWeightNational = 0.;	//Sum of (household weight)
            for(Region region : Parameters.getCountryRegions()) {
                double totalHouseholdWeightForRegion = totalHouseholdWeightRegional.get(region);
                totalHouseholdWeightNational += totalHouseholdWeightForRegion;

                double totalWeightedEquivalisedHouseholdIncomeForRegion = totalWeightedEquivalisedHouseholdIncomeRegional.get(region);
                totalWeightedEquivalisedHouseholdIncomeNational += totalWeightedEquivalisedHouseholdIncomeForRegion;

                double giniRegional = weightedAbsDiffEquivalisedIncomeRegional.get(region) / (2. * totalHouseholdWeightForRegion * totalWeightedEquivalisedHouseholdIncomeForRegion);
                giniWeightedEquivalisedHouseholdDisposableIncomeRegionalMap.put(region, giniRegional);
                log.info("giniHouseholdDisposableIncomeRegional for " + region + " = " + giniWeightedEquivalisedHouseholdDisposableIncomeRegionalMap.get(region) + ", weightedAbsDiffEquivalisedIncomeRegional.get(region) = " + weightedAbsDiffEquivalisedIncomeRegional.get(region) + ", totalHouseholdWeightForRegion = " + totalHouseholdWeightForRegion + ", totalWeightedEquivalisedHouseholdIncomeForRegion = " + totalWeightedEquivalisedHouseholdIncomeForRegion);
            }
            giniWeightedEquivalisedHouseholdDisposableIncomeNational = weightedAbsDiffEquivalisedIncomeNational / (2. * totalHouseholdWeightNational * totalWeightedEquivalisedHouseholdIncomeNational);
            wealthIncomeStats.setGiniEquivalisedHouseholdDisposableIncomeNational(giniWeightedEquivalisedHouseholdDisposableIncomeNational);
            log.info("giniWeightedEquivalisedHouseholdDisposableIncomeNational = " + giniWeightedEquivalisedHouseholdDisposableIncomeNational + ", weightedAbsDiffEquivalisedIncomeNational = " + weightedAbsDiffEquivalisedIncomeNational + ", totalHouseholdWeightNational = " + totalHouseholdWeightNational + ", totalWeightedEquivalisedHouseholdIncomeNational = " + totalWeightedEquivalisedHouseholdIncomeNational);

        }

        /// Gini coefficient at the national level.
        public double national() {
            return giniWeightedEquivalisedHouseholdDisposableIncomeNational;
        }

        /// Gini coefficient in the specified [Region].
        public double inRegion(Region region) {
            return giniWeightedEquivalisedHouseholdDisposableIncomeRegionalMap.get(region);
        }

    }


    // ---------------------------------------------------------------------
    // methods
    // ---------------------------------------------------------------------

    private void calculateEquivalisedHouseholdDisposableIncome() {

        ArrayList<Pair<BenefitUnit, Double>> arrHouse_eqHouseholdDispIncome = new ArrayList<Pair<BenefitUnit, Double>>();
        double totalWeight = 0.;
        for(BenefitUnit house: model.getBenefitUnits()) {
            double hedi = house.calculateEquivalisedDisposableIncomeYearly();
            if(hedi >= 0.) {
                arrHouse_eqHouseholdDispIncome.add(new Pair<BenefitUnit, Double>(house, hedi));
                totalWeight += house.getWeight();
            }
            else {		//Cannot include house in statistics as unable to calculate eq disp income
                house.setYPvrtyFlag(1);		//If benefit unit has equivalised disposable income < 0, it should be classified as at risk of poverty
            }
        }

        arrHouse_eqHouseholdDispIncome.sort(new Comparator<Pair<BenefitUnit, Double>>(){
                @Override
                public int compare(Pair<BenefitUnit, Double> pair1, Pair<BenefitUnit, Double> pair2) {
                    return (int) Math.signum(pair1.getSecond() - pair2.getSecond());
                }
            }
        );

        double WeightCounter = 0.;
        Double median = null;
//		log.info("arrHouse_eqHouseholdDispIncome " + arrHouse_eqHouseholdDispIncome + ", size " + arrHouse_eqHouseholdDispIncome.size());
        for(Pair<BenefitUnit, Double> pairHouse_Income: arrHouse_eqHouseholdDispIncome) {

            WeightCounter += pairHouse_Income.getFirst().getWeight();
//			log.info("eq hh disp income " + pairHouse_Income.getSecond() + ", WeightCounter " + WeightCounter + ", total Weight " + totalWeight + ", proportion so far " + WeightCounter/totalWeight);
            if(WeightCounter >= totalWeight/2.) {
                median = pairHouse_Income.getSecond();
//				log.info("WeightCounter " + WeightCounter + ", median " + median);
                break;
            }
        }

        if (median == null) {
            throw new IllegalStateException(
                    "Median could not be calculated — totalWeight=" + totalWeight +
                            ", households considered=" + arrHouse_eqHouseholdDispIncome.size()
            );
        }

        double atRiskOfPovertyThreshold = median * 0.6;
//		log.info("atRiskOfPovertyThreshold = " + atRiskOfPovertyThreshold);
        wealthIncomeStats.setMedianEquivalisedHouseholdDisposableIncome(median);		//Save median household equivalised disposable income in statistics object
//		wealthIncomeStats.setRiskOfPovertyThreshold(atRiskOfPovertyThreshold);			//Risk-of-poverty threshold is set at 60% of the national median equivalised household disposable income.
//		System.out.println("Median EDI " + median + " Poverty threshold " + atRiskOfPovertyThreshold);

        //For use in charts
        for(Pair<BenefitUnit, Double> pairHouse_Income: arrHouse_eqHouseholdDispIncome) {
            BenefitUnit house = pairHouse_Income.getFirst();
            if(house.getEquivalisedDisposableIncomeYearly() < atRiskOfPovertyThreshold) {
                house.setYPvrtyFlag(1);
            }
            else {
                house.setYPvrtyFlag(0);
            }
        }

    }

    private void calculateGiniCoefficients() {			//Called just before database dump of statistics entity

        giniPersonalGrossEarnings.update();
        giniEquivalisedHouseholdDisposableIncome.update();

    }

    private void calculateGrossIncome() {
        yHhQuintilesMonthC5.update();
        grossLabourIncome.update();
    }

    // ---------------------------------------------------------------------
    // getters and setters
    // ---------------------------------------------------------------------

    public boolean isPersistPersons() {
        return persistPersons;
    }

    public void setPersistPersons(boolean persistPersons) {
        this.persistPersons = persistPersons;
    }

    public boolean isPersistBenefitUnits() {
        return persistBenefitUnits;
    }

    public void setPersistBenefitUnits(boolean persistBenefitUnits) {
        this.persistBenefitUnits = persistBenefitUnits;
    }

    public boolean isPersistHouseholds() { return persistHouseholds; }

    public void setPersistHouseholds(boolean persistHouseholds) { this.persistHouseholds = persistHouseholds; }

    public Long getDataDumpStartTime() {
        return dataDumpStartTime;
    }

    public void setDataDumpStartTime(Long dataDumpStartTime) {
        this.dataDumpStartTime = dataDumpStartTime;
    }

    public Double getDataDumpTimePeriod() {
        return dataDumpTimePeriod;
    }

    public void setDataDumpTimePeriod(Double dataDumpTimePeriod) {
        this.dataDumpTimePeriod = dataDumpTimePeriod;
    }

    public WealthIncomeStatistics getWealthIncomeStats() {
        return wealthIncomeStats;
    }

    public void setWealthIncomeStats(WealthIncomeStatistics wealthIncomeStats) {
        this.wealthIncomeStats = wealthIncomeStats;
    }

    public DemographicStatistics getDemographicStats() { return demographicStats; }

    public void setDemographicStats(DemographicStatistics demographicStats) { this.demographicStats = demographicStats; }

    public boolean isExportToDatabase() {
        return exportToDatabase;
    }

    public void setExportToDatabase(boolean exportToDatabase) {
        this.exportToDatabase = exportToDatabase;
    }

    public boolean isExportToCSV() {
        return exportToCSV;
    }

    public void setExportToCSV(boolean exportToCSV) {
        this.exportToCSV = exportToCSV;
    }

    public boolean isPersistWealthIncomeStatistics() {
        return persistWealthIncomeStatistics;
    }

    public void setPersistWealthIncomeStatistics(boolean persistWealthIncomeStatistics) {
        this.persistWealthIncomeStatistics = persistWealthIncomeStatistics;
    }

    public boolean isCalculateGiniCoefficients() {
        return calculateGiniCoefficients;
    }

    public void setCalculateGiniCoefficients(boolean calculateGiniCoefficients) {
        this.calculateGiniCoefficients = calculateGiniCoefficients;
    }

    public boolean isPersistDemographicStatistics() {
        return persistDemographicStatistics;
    }

    public void setPersistDemographicStatistics(boolean val) {
        persistDemographicStatistics = val;
    }

    public boolean isPersistAlignmentStatistics() {
        return persistAlignmentStatistics;
    }

    public void setPersistAlignmentStatistics(boolean val) {
        persistAlignmentStatistics = val;
    }

    public void calculateAtRiskOfPoverty() {
        calculateEquivalisedHouseholdDisposableIncome();
    }

    public boolean isPersistLabourStatistics() {
        return persistLabourStatistics;
    }

    public void setPersistLabourStatistics(boolean persistLabourStatistics) {
        this.persistLabourStatistics = persistLabourStatistics;
    }

    public boolean isPersistHealthStatistics() {
        return persistHealthStatistics;
    }

    public void setPersistHealthStatistics(boolean persistHealthStatistics) {
        this.persistHealthStatistics = persistHealthStatistics;
    }

    public boolean isPersistWellbeingByGender() {
        return persistWellbeingByGender;
    }

    public void setPersistWellbeingByGender(boolean persistWellbeingByGender) {
        this.persistWellbeingByGender = persistWellbeingByGender;
    }
}
