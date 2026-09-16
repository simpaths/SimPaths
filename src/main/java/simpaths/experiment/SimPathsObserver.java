// define package
package simpaths.experiment;

// import Java packages
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;

// import plug-in packages
import simpaths.model.BenefitUnit;
import simpaths.model.SimPathsModel;
import simpaths.model.Validator;
import simpaths.model.enums.Country;
import simpaths.model.enums.Education;
import simpaths.model.enums.EducationLevel;
import simpaths.model.enums.Gender;
import simpaths.model.enums.HistogramTypeEnum;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.Region;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import net.miginfocom.swing.MigLayout;
import microsim.FilteredCollection;
// import JAS-mine packages
import microsim.annotation.GUIparameter;
import microsim.caching.OnceUntil;
import microsim.dev.statistics.CrossSection;
import microsim.dev.statistics.Stats;
import microsim.dev.statistics.WeightedCrossSection;
import microsim.dev.statistics.WeightedStats;
import microsim.engine.AbstractSimulationObserverManager;
import microsim.engine.SimulationCollectorManager;
import microsim.engine.SimulationManager;
import microsim.event.CommonEventType;
import microsim.event.EventGroup;
import microsim.event.EventListener;
import microsim.event.SingleTargetEvent;
import microsim.gui.GuiUtils;
import microsim.gui.plot.IndividualBarSimulationPlotter;
import microsim.gui.plot.ScatterplotSimulationPlotterRefreshable;
import microsim.gui.plot.Weighted_PyramidPlotter;
import microsim.gui.plot.TimeSeriesSimulationPlotter;
import microsim.gui.plot.Weighted_HistogramSimulationPlotter;

// import LABOURsim packages
import simpaths.model.Person;
import simpaths.data.Parameters;
import simpaths.data.filters.BenefitUnitFilters;
import simpaths.data.filters.Filters;

import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

record AgeRange(int from, int to) implements Predicate<Person> {
    public double employmentValidation(int year, Gender gender) {
        return Parameters.validationEmployment(year, gender, this.from, this.to);
    }

    public double mentalHealthValidation(int year, Gender gender) {
        return Parameters.validationMentalHealth(year, gender, this.from, this.to);
    }

    public double psychDistressValidation(int year, Gender gender) {
        return Parameters.validationPsychDistress(year, gender, this.from, this.to);
    }

    public double lifeSatValidation(int year, Gender gender) {
        return Parameters.validationLifeSatisfaction(year, gender, this.from, this.to);
    }

    public double mcsValidation(int year, Gender gender) {
        return Parameters.validationHealthMcs(year, gender, this.from, this.to);
    }

    public double pcsValidation(int year, Gender gender) {
        return Parameters.validationHealthPcs(year, gender, this.from, this.to);
    }

    public double eduValidation(int year, EducationLevel level) {
        return Parameters.validationEduc(year, level, this.from, this.to);
    }

    public double healthValidation(int year, Gender gender) {
        return Parameters.validationHealth(year, gender, this.from, this.to);
    }

    @Override
    public boolean test(Person arg0) {
        return Filters.ageRange(this.from, this.to).test(arg0);
    }
}

@FunctionalInterface
interface AgeGenderValidation {
    Double apply(AgeRange ar, int year, Gender gender);
}


/**
 *
 * CLASS TO MANAGE OBSERVER OF SIMULATED OUTPUT
 *
 */
public class SimPathsObserver extends AbstractSimulationObserverManager implements EventListener {

	@GUIparameter(description="Toggle to turn all charts on/off")
	private Boolean showCharts = true;

	@GUIparameter(description = "Enable additional charts")
	private Boolean showAdditionalCharts = true;

	@GUIparameter(description = "Enable validation statistics")
	private Boolean showValidationStatistics = true;
	
	@GUIparameter(description = "Set the time-period between chart updates")
	private Double displayFrequency = 1.;
	
//	@GUIparameter(description = "Set the type of histogram to display")		//Histogram types other than Frequency do not work properly with weighted histograms / cross sections
	private HistogramTypeEnum histogramType = HistogramTypeEnum.Frequency;

	@GUIparameter(description = "Set the number of bins to use in the Histograms")
    private Integer numberOfHistogramBins = 100;

//	@GUIparameter(description = "Specify the maximum number of most recent data points to show on the scatterplot of the Bowker norm of labour market demand * supply elasticities")
	private Integer convergenceElasticitiesPlotMaxSamples = 50;

//	@GUIparameter(description = "Specify the maximum number of most recent data points to show on the scatterplot of potential earnings during the convergence process")
	private Integer potentialEarningsPlotMaxSamples = 20;
	
//	@GUIparameter(description = "Specify the maximum number of most recent data points to show on the scatterplot of the aggregate labour demand and supply during the convergence process")
	private Integer labourMarketPlotMaxSamples = 20;
	
	//GUI Parameters to toggle specific charts on/off

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean educationByAge = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean educationByRegion = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean educationOfAdults = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean employmentByAge = true;

//	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean employmentByRegion = false;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean employmentOfAdults = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean femaleEmploymentByMaternity = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean grossEarningsByRegionAndEducation = true;

//	@GUIparameter(description="Toggle to turn chart on/off")
//	private boolean health = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean healthByAge = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean householdComposition = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean incomeHistograms = true;

//	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean populationPyramid = true;
	
//	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean workingHoursPyramid = false;

	@GUIparameter(description = "Toggle to turn chart on/off")
	private boolean securityIndex = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean labourSupply = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean population = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean poverty = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean studentsByAge = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean studentsByRegion = true;

	private boolean activityStatus = true;

	@GUIparameter(description="Toggle to turn chart on/off")
	private boolean homeownershipStatus = true;


//	@GUIparameter(description = "Allow convergence plots to float freely in GUI, otherwise contain plots in a frame")
	private boolean floatingConvergencePlots = false;		//Allow convergence plots to float freely in GUI, otherwise contain plots in a frame 

    private ArrayList<AgeRange> decades;
    private ArrayList<AgeRange> healthAgeRanges;

	private ScatterplotSimulationPlotterRefreshable convergenceElasticitiesPlotter;

	Set<JInternalFrame> updateChartSet;

	Set<JComponent> tabSet;

	Set<JInternalFrame> convergencePlots = new LinkedHashSet<JInternalFrame>();
	
	Map<Education, ScatterplotSimulationPlotterRefreshable> labourMarketPlots;
	
	Map<Education, ScatterplotSimulationPlotterRefreshable> potentialEarningsPlots;
	
	MultiKeyMap<Object, Supplier<Double>> meanPotentialEarningsMultiMap;
	
	private long countIterations = 0;

	private SimPathsModel model;

	private Validator validator;

	private int ordering = Parameters.OBSERVER_ORDERING;	//Schedule at the same time as the model and collector events, but with a higher order, so will be fired after the model and collector have updated.


	/**
	 *
	 * CONSTRUCTOR FOR SIMULATION OBSERVER
	 *
	 */
	public SimPathsObserver(SimulationManager manager, SimulationCollectorManager simulationCollectionManager) {
		super(manager, simulationCollectionManager);		
	}

    private void ageGenderPlots(String label,
            List<AgeRange> ageRanges,
            Function<? super Person, ? extends Number> getObservable,
            AgeGenderValidation validation) {
        var engine = this.getEngine();
        var plots = new LinkedHashSet<JInternalFrame>();

        // FIXME: reduce duplication with colorArrayList
        var colors = new ArrayList<Color>();
        colors.add(new Color(162, 56, 255));
        colors.add(new Color(254, 131, 0));

        for (var ar : ageRanges) {
            var inRange = new FilteredCollection<>(this.model::getPersons, ar).oncePerSimTime(engine);
            var males = new FilteredCollection<>(inRange, Filters.male());
            var females = new FilteredCollection<>(inRange, Filters.female());

            var maleCs = new WeightedCrossSection<>(males, getObservable, Person::getWeight);
            var femaleCs = new WeightedCrossSection<>(females, getObservable, Person::getWeight);

            // FIXME: should this be cached? What about validation values?
            var meanMale = OnceUntil.timeChanges(() -> new WeightedStats(maleCs.get()).mean(), engine);
            var meanFemale = OnceUntil.timeChanges(() -> new WeightedStats(femaleCs.get()).mean(), engine);

            Supplier<Double> validMale = () -> validation.apply(ar, this.model.getYear(), Gender.Male);
            Supplier<Double> validFemale = () -> validation.apply(ar, this.model.getYear(), Gender.Female);

            var plotter = new TimeSeriesSimulationPlotter(label + " by age: " + ar.from() + " - " + ar.to(), "");
            plotter.addSource("males", meanMale, colors.get(0), false);
            plotter.addSource("females", meanFemale, colors.get(1), false);
            plotter.addSource("Validation males", validMale, colors.get(0), true);
            plotter.addSource("Validation females", validFemale, colors.get(1), true);

            this.updateChartSet.add(plotter); // set to be updated in buildSchedule method
            plots.add(plotter);
        }
        this.tabSet.add(createScrollPaneFromPlots(plots, label + ": age/gender", 2));
    }


	@Override
	public void buildObjects() {
        var engine = this.getEngine();

		if(showCharts) {
			
			model = (SimPathsModel) getManager();
			final SimPathsCollector collector = (SimPathsCollector) getCollectorManager();
			validator = new Validator();

			//Renderers - these allow different graphs to use different look for the series displayed
			XYLineAndShapeRenderer studentAgeRenderer = new XYLineAndShapeRenderer(); //Set up a new renderer to define series colors for this chart

            // FIXME: cache the filtered population?
            this.decades = new ArrayList<>();
            this.decades.add(new AgeRange(20, 29));
            this.decades.add(new AgeRange(30, 39));
            this.decades.add(new AgeRange(40, 49));
            this.decades.add(new AgeRange(50, 59));

            // FIXME: cache the filtered population?
            this.healthAgeRanges = new ArrayList<>();
            this.healthAgeRanges.add(new AgeRange(0, 49));
            this.healthAgeRanges.add(new AgeRange(50, 74));
            this.healthAgeRanges.add(new AgeRange(75, 100));

			updateChartSet = new LinkedHashSet<JInternalFrame>();	//Set of all charts needed to be scheduled for updating (NOT the convergence plot!)
			tabSet = new LinkedHashSet<JComponent>();		//Set of all JInternalFrames each having a tab.  Each tab frame will potentially contain more than one chart each.
			labourMarketPlots = new LinkedHashMap<Education, ScatterplotSimulationPlotterRefreshable>();
			potentialEarningsPlots = new LinkedHashMap<Education, ScatterplotSimulationPlotterRefreshable>();						
			meanPotentialEarningsMultiMap = MultiKeyMap.multiKeyMap(new LinkedMap<>());
			for(Region region: Parameters.getCountryRegions()) {
				for(Education edu: Education.values()) {
                    // do not cache this one to be able to update during convergence.
                    var filtered = new FilteredCollection<>(model::getPersons,
                            Filters.education(edu).and(Filters.region(region)));
                    var wagesCs = new WeightedCrossSection<>(filtered, Person::getHourlyWageRate1, Person::getWeight);
                    var wstats = WeightedStats.supplier(wagesCs);
                    meanPotentialEarningsMultiMap.put(region, edu, () -> wstats.get().mean());
				}
			}
						

			
			//----------------------------------------------------------------------------------------------------------------------------------------
			//
			//	INTER-TIMESTEP CHARTS FOR CONVERGENCE PROCESS - those that update potentially several times in between 'time-steps' (scheduled events)
			//
			//----------------------------------------------------------------------------------------------------------------------------------------
		    
			//POTENTIAL EARNINGS & LABOUR MARKET CONVERGENCE PLOTS
			int width = 400;
			int height = 300;
			Map<Education, Integer> chartXpos = new LinkedHashMap<>();
			Map<Education, Integer> chartYpos = new LinkedHashMap<>();
			for(Education edu: Education.values()) {
				int x = 450, y = 150;
				if(edu.equals(Education.Medium)) {
					x += width;
				}
				else if(edu.equals(Education.High)) {
					x += width*2;
				}
				chartXpos.put(edu, x);
				chartYpos.put(edu, y);
			}
			for(Education edu: Education.values()) {
				ScatterplotSimulationPlotterRefreshable labourPlot = new ScatterplotSimulationPlotterRefreshable(edu + " skill aggregate labour statistics", "iteration", "Hours per Week");
				labourPlot.setMaxSamples(labourMarketPlotMaxSamples);
		    	labourMarketPlots.put(edu, labourPlot);
		    	if(floatingConvergencePlots) {
		    		GuiUtils.addWindow(labourPlot, chartXpos.get(edu), chartYpos.get(edu), width, height);
		    	}
		    	else {
		    		convergencePlots.add(labourPlot);
		    	}
			}		    
			for(Education edu: Education.values()) {
				ScatterplotSimulationPlotterRefreshable potentialEarningsPlot = new ScatterplotSimulationPlotterRefreshable(edu + " skill mean potential earnings", "iteration", "currency (per hour)");
		    	potentialEarningsPlot.setMaxSamples(potentialEarningsPlotMaxSamples);
		    	potentialEarningsPlots.put(edu, potentialEarningsPlot);
		    	if(floatingConvergencePlots) {
		    		GuiUtils.addWindow(potentialEarningsPlot, chartXpos.get(edu), chartYpos.get(edu) + height, width, height);
		    	}
		    	else {
		    		convergencePlots.add(potentialEarningsPlot);
		    	}
			}
			
			
			//This is the color palette used by graphs in the simulation
			ArrayList<Color> colorArrayList = new ArrayList<>();
			colorArrayList.add(new Color(162,56,255));
			colorArrayList.add(new Color(254, 131, 0));
			colorArrayList.add(new Color(151,144,0));
			colorArrayList.add(new Color(0,144,15));
			colorArrayList.add(new Color(0,53,144));
			colorArrayList.add(new Color(254,0,0));
			colorArrayList.add(new Color(198,0,190));
			colorArrayList.add(new Color(175,0,0));
			colorArrayList.add(new Color(0,0,0));
			colorArrayList.add(new Color(255, 172, 172));
			colorArrayList.add(new Color(255, 186, 132));
			colorArrayList.add(new Color(179, 129, 15));
			colorArrayList.add(new Color(175, 255, 148));
			colorArrayList.add(new Color(86, 173, 153));
			colorArrayList.add(new Color(0, 233, 255));
			
			//POPULATION CHART
			if(population) {

				// POPULATION PYRAMID GRAPH
				if (populationPyramid) {
					Set<JInternalFrame> populationPyramidPlots = new LinkedHashSet<JInternalFrame>();
					Weighted_PyramidPlotter populationAgeGenderPlotter = new Weighted_PyramidPlotter();
                    var males = new FilteredCollection<>(model::getPersons, Filters.male());
                    var females = new FilteredCollection<>(model::getPersons, Filters.female());

                    var malesCs = new WeightedCrossSection<>(males, Person::getDemAge, Person::getWeight)
                            .oncePerSimTime(engine);
                    var femalesCs = new WeightedCrossSection<>(females, Person::getDemAge, Person::getWeight)
                            .oncePerSimTime(engine);

					populationAgeGenderPlotter.setScalingFactor(model.getScalingFactor());
                    populationAgeGenderPlotter.setLeft(malesCs);
                    populationAgeGenderPlotter.setRight(femalesCs);

					updateChartSet.add(populationAgeGenderPlotter);			//Add to set to be updated in buildSchedule method
					populationPyramidPlots.add(populationAgeGenderPlotter);

					tabSet.add(createScrollPaneFromPlots(populationPyramidPlots, "Population Pyramid", 1));
				}

				TimeSeriesSimulationPlotter populationPlotter = new TimeSeriesSimulationPlotter("Population Statistics", "");
				if (showAdditionalCharts) {
                    populationPlotter.addSource("(Scaled) Number of Households, occupants below 80 yo",
                            () -> model.getWeightedNumberOfHouseholds80minus());
                }
                populationPlotter.addSource("(Scaled) Population Size",
                        () -> model.getWeightedNumberOfPersons());

				populationPlotter.setName("Population statistics");
			    updateChartSet.add(populationPlotter);			//Add to set to be updated in buildSchedule method
			    tabSet.add(populationPlotter);
			}

            // Population share by age.
            if(showAdditionalCharts) {
                var agesPopShare = new ArrayList<AgeRange>(8);
                agesPopShare.add(new AgeRange(0, 18));
                agesPopShare.add(new AgeRange(0, 0));
                agesPopShare.add(new AgeRange(2, 10));
                agesPopShare.add(new AgeRange(11, 15));
                agesPopShare.add(new AgeRange(19, 25));
                agesPopShare.add(new AgeRange(40, 59));
                agesPopShare.add(new AgeRange(60, 79));
                agesPopShare.add(new AgeRange(80, 100));

                var popShares = agesPopShare.stream()
                        .map(ar -> new FilteredCollection<>(model::getPersons, ar))
                        .map(fc -> new CrossSection<>(fc, Person::getWeight))
                        .map(cs -> Stats.supplier(cs))
                        .map(s -> OnceUntil.timeChanges(() -> s.get().sum(), engine))
                        .toList();

                var plotter = new TimeSeriesSimulationPlotter("Individuals by age", "");
                for (var i = 0; i < popShares.size(); i++) {
                    var ar = agesPopShare.get(i);
                    var ageStr = ar.from() + "-" + ar.to() + " yo";
                    plotter.addSource(ageStr, popShares.get(i), colorArrayList.get(i), false);
                    if (showValidationStatistics) {
                        plotter.addSource(ageStr + " projection",
                                () -> validator.getPopulationProjectionByAge(ar.from(), ar.to()),
                                colorArrayList.get(i), true);
                    }
                }
                plotter.setName("Individuals by age");
                updateChartSet.add(plotter);
                tabSet.add(plotter);
            }

            if (activityStatus) {
                var statuses = new ArrayList<Function<Person, Number>>(5);
                statuses.add(Person::getEmployed);
                statuses.add(Person::getNonwork);
                statuses.add(Person::getStudent);
                statuses.add(Person::getRetired);
                statuses.add(p -> {
                    var status = p.getLabC4();
                    return (status == Les_c4.NotEmployed || status == Les_c4.Retired) ? 1 : 0;
                });
                var statusShares = statuses.stream()
                        .map(f -> new WeightedCrossSection<>(model::getPersons, f, Person::getWeight))
                        .map(wcs -> WeightedStats.supplier(wcs))
                        .map(ws -> OnceUntil.timeChanges(() -> ws.get().mean(), engine))
                        .toList();

                var plot = new TimeSeriesSimulationPlotter("Share of individuals by activity status", "");
                plot.addSource("Employed", statusShares.get(0), colorArrayList.get(0), false);
                plot.addSource("Not Employed / Retired", statusShares.get(4), colorArrayList.get(1), false);
                plot.addSource("Not Employed", statusShares.get(1), colorArrayList.get(4), false);
                plot.addSource("Student", statusShares.get(2), colorArrayList.get(2), false);
                plot.addSource("Retired", statusShares.get(3), colorArrayList.get(3), false);

                plot.addSource("Employed validation",
                        () -> Parameters.validationActivityStatus(model.getYear(), "employed"),
                        colorArrayList.get(0), true);
                plot.addSource("Not Employed / Retired validation",
                        () -> Parameters.validationActivityStatus(model.getYear(), "notemployedretired"),
                        colorArrayList.get(1), true);
                plot.addSource("Student validation",
                        () -> Parameters.validationActivityStatus(model.getYear(), "student"),
                        colorArrayList.get(2), true);

                plot.setName("Activity status");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // home ownership status
            if (homeownershipStatus) {
                var homeOwnerCs = new WeightedCrossSection<>(model::getBenefitUnits,
                        bu -> bu.isHousingOwned() ? 1 : 0, BenefitUnit::getWeight);
                var homeOwnerStats = WeightedStats.supplier(homeOwnerCs);
                var plot = new TimeSeriesSimulationPlotter("Share of benefit units owning homes", "");
                plot.addSource("Homeowners", () -> homeOwnerStats.get().mean(), colorArrayList.get(0), false);
                plot.addSource("Homeowners validation",
                        () -> Parameters.validationHomeOwnership(model.getYear()), colorArrayList.get(0), true);
                plot.setName("Homeownership status");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // Student enrollment charts
            if (studentsByAge) {
                var ageRanges = new ArrayList<AgeRange>(8);
                ageRanges.add(new AgeRange(15, 19));
                ageRanges.add(new AgeRange(20, 24));
                ageRanges.add(new AgeRange(25, 29));
                if (showAdditionalCharts) {
                    ageRanges.add(new AgeRange(30, 34));
                    ageRanges.add(new AgeRange(35, 39));
                    ageRanges.add(new AgeRange(40, 59));
                    ageRanges.add(new AgeRange(60, 79));
                    ageRanges.add(new AgeRange(80, 100));
                }

                var studentShares = ageRanges.stream()
                        .map(ar -> new FilteredCollection<>(model::getPersons, ar))
                        .map(fc -> new WeightedCrossSection<>(fc, Person::getStudent, Person::getWeight))
                        .map(cs -> WeightedStats.supplier(cs))
                        .map(ws -> OnceUntil.timeChanges(() -> ws.get().mean(), engine))
                        .toList();

                var plotter = new TimeSeriesSimulationPlotter("Proportion of students by age", "");
                plotter.setRenderer(studentAgeRenderer);
                for (var i = 0; i < studentShares.size(); i++) {
                    var ar = ageRanges.get(i);
                    var ageStr = ar.from() + "-" + ar.to() + " yo";
                    plotter.addSource(ageStr, studentShares.get(i), colorArrayList.get(i), false);
                    if (showValidationStatistics) {
                        plotter.addSource("Validation " + ageStr,
                                () -> Parameters.validationStudents(model.getYear(), ar.from(), ar.to()),
                                colorArrayList.get(i), true);
                    }
                }

                if (showAdditionalCharts) {
                    // Unfiltered student cross-section (nationally, for all ages)
                    var studentCs = new WeightedCrossSection<>(model::getPersons, Person::getStudent, Person::getWeight);
                    var studentWs = WeightedStats.supplier(studentCs);
                    var studentShareAll = OnceUntil.timeChanges(() -> studentWs.get().mean(), engine);
                    plotter.addSource("all ages", studentShareAll, new Color(0, 0, 0), false);
                    if (showValidationStatistics) {
                        plotter.addSource("Validation all ages",
                                () -> Parameters.validationStudents(model.getYear()),
                                new Color(0, 0, 0), true);
                    }
                }

                plotter.setName("Students by age");
                updateChartSet.add(plotter);
                tabSet.add(plotter);
            }

            if (studentsByRegion && showAdditionalCharts) {
                // Student chart by Region
                var plot = new TimeSeriesSimulationPlotter("Proportion of students by region", "");
                int colorCounter = 0;
                for(var region : Parameters.getCountryRegions()) {
                    var inRegion = new FilteredCollection<>(model::getPersons, Filters.region(region));
                    var regionCs = new WeightedCrossSection<>(inRegion, Person::getStudent, Person::getWeight);
                    var stats = WeightedStats.supplier(regionCs);
                    plot.addSource(region.getName(), () -> stats.get().mean(), colorArrayList.get(colorCounter), false);
                    if (showValidationStatistics) {
                        plot.addSource("Validation " + region.getName(),
                                () -> Parameters.validationStudents(model.getYear(), region),
                                colorArrayList.get(colorCounter), true);
                    }
                    colorCounter++;
                }
                plot.setName("Students by region");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // EDUCATION LEVEL CHARTS

            // Education levels for all adults (18 years old and over)
            if (educationOfAdults) {
                // FIXME: parameterise with EducationLevel
                var filter = Filters.ageRange(18, 100).and(Filters.employment(Les_c4.Student).negate());
                var filtered = new FilteredCollection<>(model::getPersons, filter).oncePerSimTime(engine);

                var lowEduCs = new WeightedCrossSection<>(filtered, Person::getLowEducation, Person::getWeight);
                var midEduCs = new WeightedCrossSection<>(filtered, Person::getMidEducation, Person::getWeight);
                var highEduCs = new WeightedCrossSection<>(filtered, Person::getHighEducation, Person::getWeight);

                var meanLow = OnceUntil.timeChanges(() -> new WeightedStats(lowEduCs.get()).mean(), engine);
                var meanMid = OnceUntil.timeChanges(() -> new WeightedStats(midEduCs.get()).mean(), engine);
                var meanHigh = OnceUntil.timeChanges(() -> new WeightedStats(highEduCs.get()).mean(), engine);

                var plot = new TimeSeriesSimulationPlotter("Education level of over-17 yo's \n(excluding students)", "");
                plot.addSource("Low", meanLow, colorArrayList.get(0), false);
                plot.addSource("Medium", meanMid, colorArrayList.get(1), false);
                plot.addSource("High", meanHigh, colorArrayList.get(2), false);
                plot.setName("Education");
                updateChartSet.add(plot);
                tabSet.add(plot);

                if (showValidationStatistics) {
                    plot.addSource("Validation Low",
                            () -> Parameters.validationEduc(model.getYear(), EducationLevel.Low),
                            colorArrayList.get(0), true);
                    plot.addSource("Validation Medium",
                            () -> Parameters.validationEduc(model.getYear(), EducationLevel.Medium),
                            colorArrayList.get(1), true);
                    plot.addSource("Validation High",
                            () -> Parameters.validationEduc(model.getYear(), EducationLevel.High),
                            colorArrayList.get(2), true);
                }
            }

            // Education levels by age groups
            if (educationByAge && showAdditionalCharts) {
                var plots = new LinkedHashSet<JInternalFrame>();
                for (var ar : this.decades) {
                    // FIXME: parameterise with EducationLevel
                    var filter = ar.and(Filters.employment(Les_c4.Student).negate());
                    var filtered = new FilteredCollection<>(model::getPersons, filter).oncePerSimTime(engine);

                    var lowEduCs = new WeightedCrossSection<>(filtered, Person::getLowEducation, Person::getWeight);
                    var midEduCs = new WeightedCrossSection<>(filtered, Person::getMidEducation, Person::getWeight);
                    var highEduCs = new WeightedCrossSection<>(filtered, Person::getHighEducation, Person::getWeight);

                    var meanLow = OnceUntil.timeChanges(() -> new WeightedStats(lowEduCs.get()).mean(), engine);
                    var meanMid = OnceUntil.timeChanges(() -> new WeightedStats(midEduCs.get()).mean(), engine);
                    var meanHigh = OnceUntil.timeChanges(() -> new WeightedStats(highEduCs.get()).mean(), engine);

                    var plotter = new TimeSeriesSimulationPlotter(
                            "Education level by age: " + ar.from() + " - " + ar.to() + "\n(excluding students)", "");
                    plotter.addSource("low", meanLow, colorArrayList.get(0), false);
                    plotter.addSource("mid", meanMid, colorArrayList.get(1), false);
                    plotter.addSource("high", meanHigh, colorArrayList.get(2), false);

                    if (showValidationStatistics) {
                        Supplier<Double> validLow = () -> ar.eduValidation(this.model.getYear(), EducationLevel.Low);
                        Supplier<Double> validMid = () -> ar.eduValidation(this.model.getYear(), EducationLevel.Medium);
                        Supplier<Double> validHigh = () -> ar.eduValidation(this.model.getYear(), EducationLevel.High);
                        plotter.addSource("Validation Low", validLow, colorArrayList.get(0), true);
                        plotter.addSource("Validation Medium", validMid, colorArrayList.get(1), true);
                        plotter.addSource("Validation High", validHigh, colorArrayList.get(2), true);
                    }

                    updateChartSet.add(plotter);
                    plots.add(plotter);
                }
                tabSet.add(createScrollPaneFromPlots(plots, "Education by age", 2));
            }

            // Low & High Education By Region
            if(educationByRegion && showAdditionalCharts) {
                var plots = new LinkedHashSet<JInternalFrame>();

                // FIXME: parameterise by EducationLevel
                var lowPlot = new TimeSeriesSimulationPlotter("Low education level by region", "");
                var highPlot = new TimeSeriesSimulationPlotter("High education level by region", "");

                int colorCounter = 0;
                var validEduFilter = Filters.olderOr(18)
                        .and(Filters.employment(Les_c4.Student).negate())
                        .and(Filters.education(null).negate());
                var withValidEdu = new FilteredCollection<>(model::getPersons, validEduFilter)
                        .oncePerSimTime(engine);

                for(var region : Parameters.getCountryRegions()) {
                    var inRegion = new FilteredCollection<>(withValidEdu, Filters.region(region))
                            .oncePerSimTime(engine);
                    var lowCs = new WeightedCrossSection<>(inRegion, Person::getLowEducation, Person::getWeight);
                    var highCs = new WeightedCrossSection<>(inRegion, Person::getHighEducation, Person::getWeight);

                    var lowStats = WeightedStats.supplier(lowCs);
                    var highStats = WeightedStats.supplier(highCs);

                    lowPlot.addSource(region.getName(), () -> lowStats.get().mean(),
                            colorArrayList.get(colorCounter), false);
                    lowPlot.addSource("Validation " + region.getName(),
                            () -> Parameters.validationEduc(model.getYear(), EducationLevel.Low, region),
                            colorArrayList.get(colorCounter), true);

                    highPlot.addSource(region.getName(), () -> highStats.get().mean(),
                            colorArrayList.get(colorCounter), false);
                    highPlot.addSource("Validation " + region.getName(),
                            () -> Parameters.validationEduc(model.getYear(), EducationLevel.High, region),
                            colorArrayList.get(colorCounter), true);

                    colorCounter++;
                }
                updateChartSet.add(lowPlot);
                plots.add(lowPlot);
                updateChartSet.add(highPlot);
                plots.add(highPlot);

                tabSet.add(createScrollPaneFromPlots(plots, "Education by region (excluding students)", 2));
            }

            // household composition chart
            if (householdComposition) {
                // Proportion of households with couple occupancy (i.e. there is both a
                // responsible male and female in the household) by region
                var plot = new TimeSeriesSimulationPlotter("Share of couples", "");
                int icolor = 0;
                for(var region: Parameters.getCountryRegions()) {
                    var inRegion = new FilteredCollection<>(model::getBenefitUnits, BenefitUnitFilters.region(region));
                    var cs = new WeightedCrossSection<>(inRegion, BenefitUnit::getCoupleDummy, BenefitUnit::getWeight);
                    var stats = WeightedStats.supplier(cs);
                    var mean = OnceUntil.timeChanges(() -> stats.get().mean(), engine);
                    plot.addSource(region.getName(), mean, colorArrayList.get(icolor), false);
                    plot.addSource("Validation " + region.getName(),
                            () -> Parameters.validationPartnered(model.getYear(), region),
                            colorArrayList.get(icolor), true);
                    icolor++;
                }
                var cs = new WeightedCrossSection<>(model::getBenefitUnits,
                        BenefitUnit::getCoupleDummy, BenefitUnit::getWeight);
                var stats = WeightedStats.supplier(cs);
                var mean = OnceUntil.timeChanges(() -> stats.get().mean(), engine);
                plot.addSource("national", mean, colorArrayList.get(icolor), false);
                plot.addSource("Validation national",
                        () -> Parameters.validationPartnered(model.getYear()),
                        colorArrayList.get(icolor), true);

                plot.setName("Cohabitation status");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // Number of males and females who want to cohabit
            if (householdComposition) {
                var plot = new TimeSeriesSimulationPlotter("Individuals looking for partner, by gender", "");
                var males = new FilteredCollection<>(model::getPersons, Filters.male());
                var females = new FilteredCollection<>(model::getPersons, Filters.female());
                var malesCs = new WeightedCrossSection<>(males, p -> p.isToBePartnered() ? 1 : 0, Person::getWeight);
                var femalesCs = new WeightedCrossSection<>(females, p -> p.isToBePartnered() ? 1 : 0, Person::getWeight);
                var malesStats = WeightedStats.supplier(malesCs);
                var femalesStats = WeightedStats.supplier(femalesCs);

                plot.addSource("Males", () -> malesStats.get().sum(), colorArrayList.get(0), false);
                plot.addSource("Females", () -> femalesStats.get().sum(), colorArrayList.get(1), false);
                plot.setName("Individuals looking for partner");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // health charts

            // Male/Female health by age groups
            if (healthByAge) {
                var disabledAgePlots = new LinkedHashSet<JInternalFrame>();
                var inAgeRange = new FilteredCollection<>(model::getPersons, Filters.ageRange(16, 100))
                        .oncePerSimTime(engine);
                var malesDisabled = new FilteredCollection<>(inAgeRange, Filters.male());
                var femalesDisabled = new FilteredCollection<>(inAgeRange, Filters.female());
                var malesCs = new WeightedCrossSection<>(malesDisabled, Person::getBadHealth, Person::getWeight);
                var femalesCs = new WeightedCrossSection<>(femalesDisabled, Person::getBadHealth, Person::getWeight);
                var malesStats = WeightedStats.supplier(malesCs);
                var femalesStats = WeightedStats.supplier(femalesCs);

                var plot = new TimeSeriesSimulationPlotter("Disability rate", "");
                plot.addSource("males", () -> malesStats.get().mean(), colorArrayList.get(0), false);
                plot.addSource("females", () -> femalesStats.get().mean(), colorArrayList.get(1), false);
                plot.addSource("Validation males",
                        () -> Parameters.validationDisabled(model.getYear(), Gender.Male),
                        colorArrayList.get(0), true);
                plot.addSource("Validation females",
                        () -> Parameters.validationDisabled(model.getYear(), Gender.Female),
                        colorArrayList.get(1), true);

                updateChartSet.add(plot);
                disabledAgePlots.add(plot);
                tabSet.add(createScrollPaneFromPlots(disabledAgePlots, "Disability: gender", 2));

                ageGenderPlots("Health score", this.healthAgeRanges, Person::getHealthSelfRatedValue, AgeRange::healthValidation);

                // mental health plots
                ageGenderPlots("Psychological distress score", this.decades, Person::getHealthWbScore0to36, AgeRange::mentalHealthValidation);
                ageGenderPlots("Share in psychological distress (case-based)", this.decades, Person::isPsychologicallyDistressed, AgeRange::psychDistressValidation);

                // Psychological distress (case-based) by education
                var psychologicalDistressCasesAgeEducationPlots = new LinkedHashSet<JInternalFrame>();
                for (Education education : Education.values()) {
                    for (var ar : this.decades) {
                        var withEduInAgeRange = new FilteredCollection<>(model::getPersons, ar.and(Filters.education(education))).oncePerSimTime(engine);
                        var males = new FilteredCollection<>(withEduInAgeRange, Filters.male());
                        var females = new FilteredCollection<>(withEduInAgeRange, Filters.female());

                        var maleCs = new WeightedCrossSection<>(males, Person::isPsychologicallyDistressed, Person::getWeight);
                        var femaleCs = new WeightedCrossSection<>(females, Person::isPsychologicallyDistressed, Person::getWeight);

                        // FIXME: should this be cached? What about validation values?
                        var meanMale = OnceUntil.timeChanges(() -> new WeightedStats(maleCs.get()).mean(), engine);
                        var meanFemale = OnceUntil.timeChanges(() -> new WeightedStats(femaleCs.get()).mean(), engine);

                        Supplier<Double> validMale = () -> ar.psychDistressValidation(model.getYear(), Gender.Male);
                        Supplier<Double> validFemale = () -> ar.psychDistressValidation(model.getYear(), Gender.Female);

                        var plotter = new TimeSeriesSimulationPlotter("Share in psychological distress by age: " + ar.from() + " - " + ar.to(), "");
                        plotter.addSource("males " + education + " educ", meanMale, colorArrayList.get(0), false);
                        plotter.addSource("females " + education + " educ", meanFemale, colorArrayList.get(1), false);
                        plotter.addSource("Validation males", validMale, colorArrayList.get(0), true);
                        plotter.addSource("Validation females", validFemale, colorArrayList.get(1), true);
                        updateChartSet.add(plotter);
                        psychologicalDistressCasesAgeEducationPlots.add(plotter);
                    }
                }
                tabSet.add(createScrollPaneFromPlots(psychologicalDistressCasesAgeEducationPlots, "Share in psychological distress (case-based): age/gender/education", 2));

                // Psychological distress (case-based) by education
                var psychologicalDistressCasesEducationPlots = new LinkedHashSet<JInternalFrame>();
                for (Education education : Education.values()) {
                    var filter = Filters.ageRange(25, 64).and(Filters.education(education));
                    var withEduInAgeRange = new FilteredCollection<>(model::getPersons, filter).oncePerSimTime(engine);

                    var males = new FilteredCollection<>(withEduInAgeRange, Filters.male());
                    var females = new FilteredCollection<>(withEduInAgeRange, Filters.female());

                    var maleCs = new WeightedCrossSection<>(males, Person::isPsychologicallyDistressed, Person::getWeight);
                    var femaleCs = new WeightedCrossSection<>(females, Person::isPsychologicallyDistressed, Person::getWeight);

                    // FIXME: should this be cached? What about validation values?
                    var meanMale = OnceUntil.timeChanges(() -> new WeightedStats(maleCs.get()).mean(), engine);
                    var meanFemale = OnceUntil.timeChanges(() -> new WeightedStats(femaleCs.get()).mean(), engine);

                    var plotter = new TimeSeriesSimulationPlotter("Share in psychological distress by education:", "");
                    plotter.addSource("males " + education + " educ", meanMale, colorArrayList.get(0), false);
                    plotter.addSource("females " + education + " educ", meanFemale, colorArrayList.get(1), false);

                    updateChartSet.add(plotter);
                    psychologicalDistressCasesEducationPlots.add(plotter);
                }
                tabSet.add(createScrollPaneFromPlots(psychologicalDistressCasesEducationPlots, "Share in psychological distress (case-based): gender/education", 2));

                ageGenderPlots("Life satisfaction score", this.decades, Person::getDemLifeSatScore0to10, AgeRange::lifeSatValidation);
                ageGenderPlots("Mental health MCS score", this.decades, Person::getHealthMentalMcs, AgeRange::mcsValidation);
                ageGenderPlots("Physical health PCS score", this.decades, Person::getHealthPhysicalPcs, AgeRange::pcsValidation);
            }

            // employment charts
            if (employmentOfAdults) {
                var inAgeRange = new FilteredCollection<>(model::getPersons, Filters.ageRange(18, 64))
                        .oncePerSimTime(engine);
                var males = new FilteredCollection<>(inAgeRange, Filters.male());
                var females = new FilteredCollection<>(inAgeRange, Filters.female());
                var malesCs = new WeightedCrossSection<>(males, Person::getEmployed, Person::getWeight);
                var femalesCs = new WeightedCrossSection<>(females, Person::getEmployed, Person::getWeight);
                var malesStats = WeightedStats.supplier(malesCs);
                var femalesStats = WeightedStats.supplier(femalesCs);

                var plot = new TimeSeriesSimulationPlotter("Employment rate (18 - 64)", "");
                plot.addSource("males", () -> malesStats.get().mean(), colorArrayList.get(0), false);
                plot.addSource("females", () -> femalesStats.get().mean(), colorArrayList.get(1), false);
                plot.addSource("Validation males",
                        () -> Parameters.validationEmployment(model.getYear(), Gender.Male),
                        colorArrayList.get(0), true);
                plot.addSource("Validation females",
                        () -> Parameters.validationEmployment(model.getYear(), Gender.Female),
                        colorArrayList.get(1), true);

                plot.setName("Employment");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // Male/Female employment rates by age groups
            if(employmentByAge) {
                ageGenderPlots("Employment rate", this.decades, Person::getEmployed, AgeRange::employmentValidation);
            }

            // One graph for employment age by maternity status, conditional on age of children
            if (femaleEmploymentByMaternity) {
                var emplAgeMaternityPlots = new LinkedHashSet<JInternalFrame>();

                var femalesInAgeRange = new FilteredCollection<>(model::getPersons,
                        Filters.female().and(Filters.ageRange(20, 65))).oncePerSimTime(engine);
                // FIXME: include 18 or not?
                var with0_5 = new FilteredCollection<>(femalesInAgeRange, Filters.hasChildInAgeRange(0, 5));
                var with6_18 = new FilteredCollection<>(femalesInAgeRange, Filters.hasChildInAgeRange(6, 18));
                var without = new FilteredCollection<>(femalesInAgeRange, Filters.hasChildInAgeRange(0, 17).negate());
                var empRates = List.of(with0_5, with6_18, without).stream()
                        .map(fc -> new WeightedCrossSection<>(fc, Person::getEmployed, Person::getWeight))
                        .map(wcs -> WeightedStats.supplier(wcs))
                        .map(ws -> OnceUntil.timeChanges(() -> ws.get().mean(), engine))
                        .toList();

                var plotter = new TimeSeriesSimulationPlotter("Female employment rate, by age of children \n Women aged 20 - 65", "");
                plotter.addSource("with children aged 0 - 5 yo", empRates.get(0), colorArrayList.get(0), false);
                plotter.addSource("with children aged 6 - 18 yo", empRates.get(1), colorArrayList.get(1), false);
                plotter.addSource("without children under 18 yo", empRates.get(2), colorArrayList.get(2), false);
                plotter.addSource("Validation with children aged 0 - 5 yo",
                        () -> Parameters.validationEmployment(model.getYear(), true, true),
                        colorArrayList.get(0), true);
                plotter.addSource("Validation with children aged 6 - 18 yo",
                        () -> Parameters.validationEmployment(model.getYear(), true, false),
                        colorArrayList.get(1), true);
                plotter.addSource("Validation without children under 18 yo",
                        () -> Parameters.validationEmployment(model.getYear(), false, false),
                        colorArrayList.get(2), true);

                updateChartSet.add(plotter);
                emplAgeMaternityPlots.add(plotter);
                tabSet.add(createScrollPaneFromPlots(emplAgeMaternityPlots, "Employment (female): age/maternity", 2));
            }

            // Employment by region
            if (employmentByRegion) {
                var inAgeRange = new FilteredCollection<>(model::getPersons, Filters.ageRange(18, 64))
                        .oncePerSimTime(engine);
                var emplGenderRegionPlots = new LinkedHashSet<JInternalFrame>();
                var malePlot = new TimeSeriesSimulationPlotter("Male employment rate by region\n Age 18 - 64", "");
                var femalePlot = new TimeSeriesSimulationPlotter("Female employment rate by region\n Age 18 - 64", "");
                int colorCounter = 0;
                for (var region : Parameters.getCountryRegions()) {
                    var inRegion = new FilteredCollection<>(inAgeRange, Filters.region(region))
                            .oncePerSimTime(engine);
                    var malesInRegion = new FilteredCollection<>(inRegion, Filters.male());
                    var femalesInRegion = new FilteredCollection<>(inRegion, Filters.female());
                    var malesCs = new WeightedCrossSection<>(malesInRegion, Person::getEmployed, Person::getWeight);
                    var femalesCs = new WeightedCrossSection<>(femalesInRegion, Person::getEmployed, Person::getWeight);
                    var malesStats = WeightedStats.supplier(malesCs);
                    var femalesStats = WeightedStats.supplier(femalesCs);

                    malePlot.addSource(region.getName(), () -> malesStats.get().mean(),
                            colorArrayList.get(colorCounter), false);
                    malePlot.addSource("Validation " + region.getName(),
                            () -> Parameters.validationEmployment(model.getYear(), Gender.Male, region),
                            colorArrayList.get(colorCounter), true);

                    femalePlot.addSource(region.getName(), () -> femalesStats.get().mean(),
                            colorArrayList.get(colorCounter), false);
                    femalePlot.addSource("Validation " + region.getName(),
                            () -> Parameters.validationEmployment(model.getYear(), Gender.Female, region),
                            colorArrayList.get(colorCounter), true);
                }
                updateChartSet.add(malePlot);
                updateChartSet.add(femalePlot);
                emplGenderRegionPlots.add(malePlot);
                emplGenderRegionPlots.add(femalePlot);
                tabSet.add(createScrollPaneFromPlots(emplGenderRegionPlots, "Employment: gender/region", 2));
            }

            // labour supply chart
            if (labourSupply) {
                var plot = new TimeSeriesSimulationPlotter("Labour supply by education", "Yearly hours worked");
                int colorCounter = 0;
                var flexibleLabour = new FilteredCollection<>(model::getPersons, Filters.flexibleLabourSupply())
                        .oncePerSimTime(engine);
                for(var edu : Education.values()) {
                    if (Education.InEducation.equals(edu)) {
                        continue;
                    }
                    var withEdu = new FilteredCollection<>(flexibleLabour, Filters.education(edu));
                    var supplyCs = new WeightedCrossSection<>(withEdu, Person::getLabourSupplyHoursYearly, Person::getWeight);
                    var supplyStats = WeightedStats.supplier(supplyCs);
                    plot.addSource(edu.toString(), () -> supplyStats.get().mean(), colorArrayList.get(colorCounter), false);
                    plot.addSource("Validation " + edu.toString(),
                            () -> Parameters.validationLabourSupply(model.getYear(), edu),
                            colorArrayList.get(colorCounter), true);
                    colorCounter++;
                }
                plot.setName("Labour supply");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            var employedEarningFilter = Filters.employment(Les_c4.EmployedOrSelfEmployed)
                    .and(Filters.grossEarningsYearlyAtLeast(0.0));
            var employed = new FilteredCollection<>(model::getPersons, employedEarningFilter)
                    .oncePerSimTime(engine);

            // income charts - gross wages by region and education level
            if (grossEarningsByRegionAndEducation) {
                var currency = model.getCountry().equals(Country.UK) ? "£" : "€";
                var plot = new IndividualBarSimulationPlotter("Yearly Gross Earnings by Education and Region (excludes non-workers)", currency);

                for (var region: Parameters.getCountryRegions()) {
                    for (var edu: Education.values()) {
                        if (Education.InEducation.equals(edu)) {
                            continue;
                        }
                        var inRegionWithEdu = new FilteredCollection<>(employed, Filters.region(region).and(Filters.education(edu)));
                        var cs = new WeightedCrossSection<>(inRegionWithEdu, Person::getGrossEarningsYearly, Person::getWeight);
                        var stats = WeightedStats.supplier(cs);
                        plot.addSource("(" + region.getName() + ", " + edu.toString() + ")",
                                () -> stats.get().mean(), colorOfEducation(edu));
                    }
                }
                plot.setName("Gross Earnings");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // income charts b: gross earnings by education
            if (grossEarningsByRegionAndEducation) {
                var currency = model.getCountry().equals(Country.UK) ? "£" : "€";
                var plotEarnings = new TimeSeriesSimulationPlotter("Yearly Gross Earnings by Gender And Education", currency);
                var plotWages = new TimeSeriesSimulationPlotter("Hourly Wages by Gender And Education", currency);
                int colorCounter = 0;

                // FIXME: why slightly different from previous?
                var employedEarningFilter2 = Filters.employment(Les_c4.EmployedOrSelfEmployed)
                        .and(Filters.grossEarningsYearlyAtLeast(1.0))
                        .and(p -> p.getLabourSupplyHoursWeekly() > 0);
                var employed2 = new FilteredCollection<>(model::getPersons, employedEarningFilter2)
                        .oncePerSimTime(engine);

                for(Education edu: Education.values()) {
                    if (Education.InEducation.equals(edu)) {
                        continue;
                    }
                    for (Gender gender : Gender.values()) {
                        var ofGenderWithEdu = new FilteredCollection<>(employed2, Filters.gender(gender).and(Filters.education(edu)))
                                .oncePerSimTime(engine);
                        // Note: these are nominal values for each simulated year
                        var earningsCs = new WeightedCrossSection<>(ofGenderWithEdu, Person::getGrossEarningsYearly, Person::getWeight);
                        var wagesCs = new WeightedCrossSection<>(ofGenderWithEdu, Person::getHourlyWageRate1, Person::getWeight);
                        var earningsStats = WeightedStats.supplier(earningsCs);
                        var wagesStats = WeightedStats.supplier(wagesCs);

                        var pStr = "(" + gender.toString() + ", " + edu.toString() + ")";
                        plotEarnings.addSource(pStr, () -> earningsStats.get().mean(), colorArrayList.get(colorCounter), false);
                        plotWages.addSource(pStr, () -> wagesStats.get().mean(), colorArrayList.get(colorCounter), false);
                        plotEarnings.addSource("Validation " + pStr,
                                () -> Parameters.validationGrossEarnings(model.getYear(), gender, edu),
                                colorArrayList.get(colorCounter), true);
                        plotWages.addSource("Validation " + pStr,
                                () -> Parameters.validationHourlyWage(model.getYear(), gender, edu),
                                colorArrayList.get(colorCounter), true);
                        colorCounter++;
                    }
                }
                plotEarnings.setName("Gross Earnings by Gender / Education");
                plotWages.setName("Hourly Wages by Gender / Education");
                updateChartSet.add(plotEarnings);
                tabSet.add(plotEarnings);
                updateChartSet.add(plotWages);
                tabSet.add(plotWages);
            }

            if (grossEarningsByRegionAndEducation) {
                var plot = new TimeSeriesSimulationPlotter("Hours of Work Weekly by Gender", "Hours");
                int icolor = 0;
                for (var gender : Gender.values()) {
                    var ofGender = new FilteredCollection<>(employed, Filters.gender(gender));
                    // Note: these are nominal values for each simulated year
                    var hoursCs = new WeightedCrossSection<>(ofGender, Person::getDoubleLabourSupplyHoursWeekly, Person::getWeight);
                    var hoursStats = WeightedStats.supplier(hoursCs);
                    plot.addSource(gender.toString(), () -> hoursStats.get().mean(), colorArrayList.get(icolor), false);
                    plot.addSource("Validation " + gender,
                            () -> Parameters.validationLhw(model.getYear(), gender),
                            colorArrayList.get(icolor), true);
                    icolor++;
                    }
                plot.setName("Hours of Work by Gender");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

			//Statistics dependent charts
		    if(collector.isCalculateGiniCoefficients()) {	//As these charts need statistics to be calculated within the simulation, turn off these charts if the statistics are not calculated
                // FIXME: no need to use AccumulatorStats just to get a `lastValue`...

				//INCOME CHARTS - GINI
			    Set<JInternalFrame> giniIncomeRegionPlots = new LinkedHashSet<JInternalFrame>();			    
			    //Gini coefficient of market (gross) individual income
			    TimeSeriesSimulationPlotter personalGrossEarningsGiniPlotter = new TimeSeriesSimulationPlotter("Gini: Gross individual earnings", "Gini coefficient");
			    //Add Series at national and regional level
			    for(Region region: Parameters.getCountryRegions()) {
                    personalGrossEarningsGiniPlotter.addSource(region.getName(),
                            () -> collector.giniPersonalGrossEarnings.inRegion(region));
			    }
                personalGrossEarningsGiniPlotter.addSource("national", collector.giniPersonalGrossEarnings::national);
			    updateChartSet.add(personalGrossEarningsGiniPlotter);			//Add to set to be updated in buildSchedule method
			    giniIncomeRegionPlots.add(personalGrossEarningsGiniPlotter);
			    
			    //Gini coefficient of equivalised household disposable income
			    TimeSeriesSimulationPlotter equivalisedHouseholdDisposableIncomeGiniPlotter = new TimeSeriesSimulationPlotter("Gini: Equivalised household disposable income", "Gini coefficient");
			    //Add Series at national and regional level
			    for(Region region: Parameters.getCountryRegions()) {
                    equivalisedHouseholdDisposableIncomeGiniPlotter.addSource(region.getName(),
                            () -> collector.giniEquivalisedHouseholdDisposableIncome.inRegion(region));
			    }
                equivalisedHouseholdDisposableIncomeGiniPlotter.addSource("national",
                        collector.giniEquivalisedHouseholdDisposableIncome::national);
			    updateChartSet.add(equivalisedHouseholdDisposableIncomeGiniPlotter);			//Add to set to be updated in buildSchedule method		    
			    giniIncomeRegionPlots.add(equivalisedHouseholdDisposableIncomeGiniPlotter);
			    
			    tabSet.add(createScrollPaneFromPlots(giniIncomeRegionPlots, "Gini income", 2));
		    }			
			
		    
            var buWithIncome = new FilteredCollection<>(model::getBenefitUnits, BenefitUnitFilters.validIncome())
                    .oncePerSimTime(engine);
            // poverty charts
            if (poverty) {
                var povertyPlots = new LinkedHashSet<JInternalFrame>();
                var housePlot = new TimeSeriesSimulationPlotter("Share of Households at risk of poverty", "");
                var childPlot = new TimeSeriesSimulationPlotter("Share of Children at risk of poverty", "");
                var childWithIncome = new FilteredCollection<>(model::getPersons, Filters.child().and(Filters.validIncome()))
                        .oncePerSimTime(engine);
                for (var region : Parameters.getCountryRegions()) {
                    // Households
                    var buInRegion = new FilteredCollection<>(buWithIncome, BenefitUnitFilters.region(region));
                    var buAtRiskCs = new WeightedCrossSection<>(buInRegion, BenefitUnit::getYPvrtyFlag, BenefitUnit::getWeight);
                    var buStats = WeightedStats.supplier(buAtRiskCs);
                    housePlot.addSource(region.getName(), () -> buStats.get().mean());

                    // Children
                    var childInRegion = new FilteredCollection<>(childWithIncome, Filters.region(region));
                    var childAtRiskCs = new WeightedCrossSection<>(childInRegion, Person::getAtRiskOfPoverty, Person::getWeight);
                    var childStats = WeightedStats.supplier(childAtRiskCs);
                    childPlot.addSource(region.getName(), () -> childStats.get().mean());
                }
                // Households
                var buCs = new WeightedCrossSection<>(buWithIncome, BenefitUnit::getYPvrtyFlag, BenefitUnit::getWeight);
                var buStats = WeightedStats.supplier(buCs);
                housePlot.addSource("national", () -> buStats.get().mean());
                updateChartSet.add(housePlot);
                povertyPlots.add(housePlot);

                // Children
                var childCs = new WeightedCrossSection<>(childWithIncome, Person::getAtRiskOfPoverty, Person::getWeight);
                var childStats = WeightedStats.supplier(childCs);
                childPlot.addSource("national", () -> childStats.get().mean());
                updateChartSet.add(childPlot);
                povertyPlots.add(childPlot);

                tabSet.add(createScrollPaneFromPlots(povertyPlots, "Poverty", 2));
            }

            // histograms of income
            if (incomeHistograms) {
                var histogramIncomePlots = new LinkedHashSet<JInternalFrame>();

                var plot = new Weighted_HistogramSimulationPlotter("Individual Gross Earnings (yearly)", "Euro", histogramType.getHistogramType(), numberOfHistogramBins);
                var withValidEarnings = new FilteredCollection<>(model::getPersons, Filters.grossEarningsYearlyAtLeast(0.0));
                var cs = new WeightedCrossSection<>(withValidEarnings, Person::getGrossEarningsYearly, Person::getWeight);
                plot.addSource("Gross Earnings", cs);
                updateChartSet.add(plot);
                histogramIncomePlots.add(plot);

                var houseCs = new WeightedCrossSection<>(buWithIncome, BenefitUnit::getEquivalisedDisposableIncomeYearly, BenefitUnit::getWeight);
                var plotHouse = new Weighted_HistogramSimulationPlotter("Equivalised Disposable Income of Benefit Unit (yearly)", "Euro", histogramType.getHistogramType(), numberOfHistogramBins);
                plotHouse.addSource("Equivalised BenefitUnit Disposable Income", houseCs);
                updateChartSet.add(plotHouse);
                histogramIncomePlots.add(plotHouse);

                tabSet.add(createScrollPaneFromPlots(histogramIncomePlots, "Income", 2));
            }


            // FIXME: reuse where relevant
            var currency = model.getCountry().equals(Country.UK) ? "£" : "€";
            if (incomeHistograms) {
                var plot = new TimeSeriesSimulationPlotter("EDI by Gender And Education", currency);
                int icolor = 0;

                // FIXME: why slightly different from `employed` in parent scope?
                var employedEarningFilter2 = Filters.employment(Les_c4.EmployedOrSelfEmployed)
                        .and(Filters.grossEarningsYearlyAtLeast(1.0))
                        .and(p -> p.getLabourSupplyHoursWeekly() > 0);
                var employed2 = new FilteredCollection<>(model::getPersons, employedEarningFilter2)
                        .oncePerSimTime(engine);

                for (var edu: Education.values()) {
                    if (Education.InEducation.equals(edu)) {
                        continue;
                    }
                    for (var gender : Gender.values()) {
                        var genderEduFilter = Filters.gender(gender).and(Filters.education(edu));
                        var employedOfGenderWithEdu = new FilteredCollection<>(employed2, genderEduFilter);
                        // FIXME: such a strange combination of filters...
                        var ofGenderWithEdu = new FilteredCollection<>(model::getPersons, genderEduFilter.and(Filters.grossEarningsYearlyAtLeast(0.0)));

                        var employedCs = new WeightedCrossSection<>(employedOfGenderWithEdu, Person::getEquivalisedDisposableIncomeYearly, Person::getWeight);
                        var allCs = new WeightedCrossSection<>(ofGenderWithEdu, Person::getEquivalisedDisposableIncomeYearly, Person::getWeight);
                        var employedStats = WeightedStats.supplier(employedCs);
                        var allStats = WeightedStats.supplier(allCs);

                        plot.addSource("Workers (" + gender.toString() + ", " + edu.toString() + ")",
                                () -> employedStats.get().mean(), colorArrayList.get(icolor), false);
                        plot.addSource("All (" + gender.toString() + ", " + edu.toString() + ")",
                                () -> allStats.get().mean(), colorArrayList.get(icolor), false);
                        icolor++;
                    }
                }
                plot.setName("EDI by Gender / Education");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            if (incomeHistograms) {
                var plot = new TimeSeriesSimulationPlotter("Disp income by Gender And Education", currency);
                int icolor = 0;

                // FIXME: why slightly different from `employed` in parent scope?
                var employedEarningFilter2 = Filters.employment(Les_c4.EmployedOrSelfEmployed)
                        .and(Filters.grossEarningsYearlyAtLeast(1.0))
                        .and(p -> p.getLabourSupplyHoursWeekly() > 0);
                var employed2 = new FilteredCollection<>(model::getPersons, employedEarningFilter2)
                        .oncePerSimTime(engine);

                for (var edu: Education.values()) {
                    if (Education.InEducation.equals(edu)) {
                        continue;
                    }
                    for (var gender : Gender.values()) {
                        var genderEduFilter = Filters.gender(gender).and(Filters.education(edu));
                        var employedOfGenderWithEdu = new FilteredCollection<>(employed2, genderEduFilter);
                        // FIXME: such a strange combination of filters...
                        var ofGenderWithEdu = new FilteredCollection<>(model::getPersons, genderEduFilter.and(Filters.grossEarningsYearlyAtLeast(0.0)));

                        var employedCs = new WeightedCrossSection<>(employedOfGenderWithEdu, Person::getDisposableIncomeMonthlyNoNull, Person::getWeight);
                        var allCs = new WeightedCrossSection<>(ofGenderWithEdu, Person::getDisposableIncomeMonthlyNoNull, Person::getWeight);
                        var employedStats = WeightedStats.supplier(employedCs);
                        var allStats = WeightedStats.supplier(allCs);

                        plot.addSource("Workers (" + gender.toString() + ", " + edu.toString() + ")",
                                () -> employedStats.get().mean(), colorArrayList.get(icolor), false);
                        plot.addSource("All (" + gender.toString() + ", " + edu.toString() + ")",
                                () -> allStats.get().mean(), colorArrayList.get(icolor), false);
                        icolor++;
                    }
                }
                plot.setName("Disp income by Gender / Education");
                updateChartSet.add(plot);
                tabSet.add(plot);
            }

            // WORKING HOURS PYRAMID GRAPH
            if (workingHoursPyramid) {
                var workingHoursPyramidPlots = new LinkedHashSet<JInternalFrame>();
                var plot = new Weighted_PyramidPlotter("Working hours over time", "Total hours worked",
                        Weighted_PyramidPlotter.DEFAULT_YAXIS, Weighted_PyramidPlotter.DEFAULT_LEFT_CAT, Weighted_PyramidPlotter.DEFAULT_RIGHT_CAT);
                var males = new FilteredCollection<>(model::getPersons, Filters.male());
                var females = new FilteredCollection<>(model::getPersons, Filters.female());
                var malesCs = new WeightedCrossSection<>(males, Person::getLiwwh, Person::getWeight);
                var femalesCs = new WeightedCrossSection<>(females, Person::getLiwwh, Person::getWeight);

                plot.setScalingFactor(model.getScalingFactor());
                plot.setLeft(malesCs);
                plot.setRight(femalesCs);

                updateChartSet.add(plot);
                workingHoursPyramidPlots.add(plot);

                tabSet.add(createScrollPaneFromPlots(workingHoursPyramidPlots, "Working Hours Pyramid", 1));
            }

		    //-------------------------------------------------------------------------------------------------------
		    //
	    	//	BUILD A TABBED PANE HOLDING ALL THE CHARTS THAT ONLY UPDATE AT EACH TIME-STEP (not convergence plots)
		    //
	    	//-------------------------------------------------------------------------------------------------------
		    
	    	JInternalFrame chartsFrame = new JInternalFrame("Charts");
			JTabbedPane tabbedPane = new JTabbedPane();
			chartsFrame.add(tabbedPane);
			
			for(JComponent plot: tabSet) {
				tabbedPane.addTab(plot.getName(), plot);
			}
			tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
	    	chartsFrame.setResizable(true);
	    	chartsFrame.setMaximizable(true);
			GuiUtils.addWindow(chartsFrame, 300, 0, 1560, 660);
		    
			
			
		}
							
	}	



	@Override
	public void buildSchedule() {
		
		if(showCharts) {
			
			EventGroup chartingEvents = new EventGroup();
			for(JInternalFrame plot: updateChartSet) {
				chartingEvents.addEvent(plot, CommonEventType.Update);
			}
			getEngine().getEventQueue().scheduleRepeat(chartingEvents, model.getStartYear(), ordering, displayFrequency);
			getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.ResetConvergenceChart), model.getStartYear(), ordering, displayFrequency);

		}
							
	}
	
	//--------------------------------------------------------------------------
	//	Other Methods 
	//--------------------------------------------------------------------------


	//For use with bar charts to specify what colour to use based on education level
	private Color colorOfEducation(Education edu) {
		if(edu.equals(Education.Low)) {
			return Color.RED;
		}
		else if(edu.equals(Education.Medium)) {
			return Color.BLUE;
		}
		else if(edu.equals(Education.High)) {
			return Color.WHITE;
		}
		else if(edu.equals(Education.InEducation)) {
			return Color.GRAY;
		}
		else throw new IllegalArgumentException("ERROR - no color is specified for " + edu + " in SimPathsObserver class!");
	}

	
	/**
	 * Method to re-arrange JInternalFrames such as JFreeChart plots into 
	 * a single JInternalFrame (e.g. to use in a TabbedPane of plots).
	 * 
	 * @param internalFrames - a set of JInternalFrames such as JFreeChart plots 
	 * @param name - the name of the JScrollPane returned
	 * @param columns - the number of columns with which the JInternalFrames will be laid out 
	 * @return A JScrollPane laying of a set of JInternalFrames 
	 */
	private JScrollPane createScrollPaneFromPlots(Set<JInternalFrame> internalFrames, String name, int columns) {		
		
		String layoutConstraints = "wrap " + columns;
		MigLayout layout = new MigLayout(layoutConstraints, "fill, grow", "fill, grow");
		JPanel panel = new JPanel(layout);

		for(JInternalFrame internalFrame: internalFrames) {
			internalFrame.setVisible(true);
			internalFrame.setResizable(false);	//The components (charts) are not able to expand beyond their assigned row/column, so the only way to resize is to resize the whole pane. 
			panel.add(internalFrame);
		}		
		JScrollPane frame = new JScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		frame.setName(name);
		return frame;
	}
	

	//For bowker norm of labour supply/demand elasticities updated during convergence process
	public void updateConvergencePlotter() {		
		convergenceElasticitiesPlotter.update();
	}

	//For potential earnings and aggregate labour supply/demand plots updated during convergence process
	public void updateLabourMarketPlots(Region region) {

		//Potential Earnings
		for(ScatterplotSimulationPlotterRefreshable plot: potentialEarningsPlots.values()) {
			plot.update();
		}
		
		//Labour Market
		for(ScatterplotSimulationPlotterRefreshable plot: labourMarketPlots.values()) {
			plot.update();
		}
		
		//Increment iterations count
		countIterations++;
	}
		
	public void resetLabourMarketPlots(Region region) {
		
		for(ScatterplotSimulationPlotterRefreshable plot: potentialEarningsPlots.values()) {
			plot.reset();
		}

		for(ScatterplotSimulationPlotterRefreshable plot: labourMarketPlots.values()) {
			plot.reset();
		}
		countIterations = 0;

        for(var edu: Education.values()) {
            // Potential Earnings
            potentialEarningsPlots.get(edu).addSource(region.getName(),
                    () -> this.countIterations,
                    meanPotentialEarningsMultiMap.get(region, edu));
        }
    }

	
	//--------------------------------------------------------------------------
	//	Event Listener implementation 
	//--------------------------------------------------------------------------
	
	
	public enum Processes {
		ResetConvergenceChart,
	}
	
	@Override
	public void onEvent(Enum<?> type) {
		switch ((Processes) type) {
		
		case ResetConvergenceChart:
			break;
			
		}
		
	}

	//--------------------------------------------------------------------------
	// Access methods
	//--------------------------------------------------------------------------
	
	public Double getDisplayFrequency() {
		return displayFrequency;
	}

	public void setDisplayFrequency(Double displayFrequency) {
		this.displayFrequency = displayFrequency;
	}
	
	public Boolean getShowCharts() {
		return showCharts;
	}

	public void setShowCharts(Boolean showCharts) {
		this.showCharts = showCharts;
	}

	public Boolean getShowAdditionalCharts() {
		return showAdditionalCharts;
	}

	public void setShowAdditionalCharts(Boolean showAdditionalCharts) {
		this.showAdditionalCharts = showAdditionalCharts;
	}

	public Boolean getShowValidationStatistics() {
		return showValidationStatistics;
	}

	public void setShowValidationStatistics(Boolean showValidationStatistics) {
		this.showValidationStatistics = showValidationStatistics;
	}

	public Integer getNumberOfHistogramBins() {
		return numberOfHistogramBins;
	}

	public void setNumberOfHistogramBins(Integer numberOfHistogramBins) {
		this.numberOfHistogramBins = numberOfHistogramBins;
	}


	public HistogramTypeEnum getHistogramType() {
		return histogramType;
	}


	public void setHistogramType(HistogramTypeEnum histogramType) {
		this.histogramType = histogramType;
	}


	public boolean isEducationByAge() {
		return educationByAge;
	}


	public void setEducationByAge(boolean educationByAge) {
		this.educationByAge = educationByAge;
	}


	public boolean isEducationByRegion() {
		return educationByRegion;
	}


	public void setEducationByRegion(boolean educationByRegion) {
		this.educationByRegion = educationByRegion;
	}


	public boolean isEducationOfAdults() {
		return educationOfAdults;
	}


	public void setEducationOfAdults(boolean educationOfAdults) {
		this.educationOfAdults = educationOfAdults;
	}


	public boolean isEmploymentByAge() {
		return employmentByAge;
	}


	public void setEmploymentByAge(boolean employmentByAge) {
		this.employmentByAge = employmentByAge;
	}


	public boolean isEmploymentByRegion() {
		return employmentByRegion;
	}


	public void setEmploymentByRegion(boolean employmentByRegion) {
		this.employmentByRegion = employmentByRegion;
	}


	public boolean isEmploymentOfAdults() {
		return employmentOfAdults;
	}


	public void setEmploymentOfAdults(boolean employmentOfAdults) {
		this.employmentOfAdults = employmentOfAdults;
	}


	public boolean isFemaleEmploymentByMaternity() {
		return femaleEmploymentByMaternity;
	}


	public void setFemaleEmploymentByMaternity(boolean femaleEmploymentByMaternity) {
		this.femaleEmploymentByMaternity = femaleEmploymentByMaternity;
	}


	public boolean isHouseholdComposition() {
		return householdComposition;
	}


	public void setHouseholdComposition(boolean householdComposition) {
		this.householdComposition = householdComposition;
	}


	public boolean isIncomeHistograms() {
		return incomeHistograms;
	}


	public void setIncomeHistograms(boolean incomeHistograms) {
		this.incomeHistograms = incomeHistograms;
	}


	public boolean isPopulationPyramid() {
		return populationPyramid;
	}


	public void setPopulationPyramid(boolean populationPyramid) {
		this.populationPyramid = populationPyramid;
	}


	public boolean isWorkingHoursPyramid() {
		return workingHoursPyramid;
	}


	public void setWorkingHoursPyramid(boolean workingHoursPyramid) {
		this.workingHoursPyramid = workingHoursPyramid;
	}


	public boolean isLabourSupply() {
		return labourSupply;
	}


	public void setLabourSupply(boolean labourSupply) {
		this.labourSupply = labourSupply;
	}


	public boolean isPopulation() {
		return population;
	}


	public void setPopulation(boolean population) {
		this.population = population;
	}


	public boolean isPoverty() {
		return poverty;
	}


	public void setPoverty(boolean poverty) {
		this.poverty = poverty;
	}


	public boolean isStudentsByAge() {
		return studentsByAge;
	}


	public void setStudentsByAge(boolean studentsByAge) {
		this.studentsByAge = studentsByAge;
	}


	public boolean isStudentsByRegion() {
		return studentsByRegion;
	}


	public void setStudentsByRegion(boolean studentsByRegion) {
		this.studentsByRegion = studentsByRegion;
	}

	public boolean isGrossEarningsByRegionAndEducation() {
		return grossEarningsByRegionAndEducation;
	}

	public void setGrossEarningsByRegionAndEducation(boolean grossEarningsByRegionAndEducation) {
		this.grossEarningsByRegionAndEducation = grossEarningsByRegionAndEducation;
	}


	public Integer getPotentialEarningsPlotMaxSamples() {
		return potentialEarningsPlotMaxSamples;
	}


	public void setPotentialEarningsPlotMaxSamples(Integer potentialEarningsPlotMaxSamples) {
		this.potentialEarningsPlotMaxSamples = potentialEarningsPlotMaxSamples;
	}


	public Integer getLabourMarketPlotMaxSamples() {
		return labourMarketPlotMaxSamples;
	}


	public void setLabourMarketPlotMaxSamples(Integer labourMarketPlotMaxSamples) {
		this.labourMarketPlotMaxSamples = labourMarketPlotMaxSamples;
	}

	public boolean isFloatingConvergencePlots() {
		return floatingConvergencePlots;
	}

	public void setFloatingConvergencePlots(boolean floatingConvergencePlots) {
		this.floatingConvergencePlots = floatingConvergencePlots;
	}


	public Integer getConvergenceElasticitiesPlotMaxSamples() {
		return convergenceElasticitiesPlotMaxSamples;
	}


	public void setConvergenceElasticitiesPlotMaxSamples(Integer convergenceElasticitiesPlotMaxSamples) {
		this.convergenceElasticitiesPlotMaxSamples = convergenceElasticitiesPlotMaxSamples;
	}

	public boolean isHealthByAge() {
		return healthByAge;
	}

	public void setHealthByAge(boolean healthByAge) {
		this.healthByAge = healthByAge;
	}

	public boolean isSecurityIndex() {
		return securityIndex;
	}

	public void setSecurityIndex(boolean securityIndex) {
		this.securityIndex = securityIndex;
	}

	public boolean isActivityStatus() {
		return activityStatus;
	}

	public void setActivityStatus(boolean activityStatus) {
		this.activityStatus = activityStatus;
	}

	public boolean isHomeownershipStatus() {
		return homeownershipStatus;
	}

	public void setHomeownershipStatus(boolean homeownershipStatus) {
		this.homeownershipStatus = homeownershipStatus;
	}

}
