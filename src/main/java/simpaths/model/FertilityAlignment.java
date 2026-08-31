package simpaths.model;

import microsim.engine.SimulationEngine;
import simpaths.data.IEvaluation;
import simpaths.data.Parameters;
import simpaths.data.filters.Filters;

import java.util.Set;


/**
 * FertilityAlignment adjusts the probability of fertile women having a child to match total fertility rates implicit in population projections.
 * The object is designed to assist modification of the intercept of the "fertility" probit models.
 *
 * A search routine is used to find the value by which the intercept should be adjusted.
 * If the projected share of the population having a child differs from the desired target by more than a specified threshold,
 * then the intercept is adjusted and the share re-evaluated.
 *
 * Importantly, the adjustment needs to be only found once. Modified intercepts can then be used in subsequent simulations.
 */
public class FertilityAlignment implements IEvaluation {

    private double targetFertilityRate;
    private Set<Person> persons;
    private SimPathsModel model;
    private long numFertile;


    // CONSTRUCTOR
    public FertilityAlignment(Set<Person> persons) {
        this.model = (SimPathsModel) SimulationEngine.getInstance().getManager(SimPathsModel.class.getCanonicalName());
        this.persons = persons;
        targetFertilityRate = Parameters.getFertilityRateByYear(model.getYear());
        // FIXME: should cache fertile persons to only filter once
        numFertile = persons.stream()
                .filter(Filters.fertile())
                .count();
    }


    /**
     * Evaluates the discrepancy between the expected (smooth) fertility rate at a candidate adjustment
     * and the target rate. Uses sum of probit probabilities rather than stochastic realisations,
     * yielding a smooth, deterministic objective for the root search.
     *
     * @param args An array of parameters, where args[0] represents the probit intercept adjustment.
     * @return target fertility rate minus expected fertility rate at the given adjustment.
     */
    @Override
    public double evaluate(double[] args) {

        if (numFertile == 0) return targetFertilityRate;

        double expectedBirths = persons.parallelStream()
                .filter(Filters.fertile())
                .mapToDouble(person -> {
                    double score = Parameters.getRegFertilityF1()
                            .getScore(person, Person.DoublesVariables.class);
                    return Parameters.getRegFertilityF1()
                            .getProbability(score + args[0]);
                })
                .sum();
        double expectedRate = expectedBirths / numFertile;
        return targetFertilityRate - expectedRate;
    }
}
