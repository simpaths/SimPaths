package simpaths.model;

import microsim.engine.SimulationEngine;
import simpaths.data.IEvaluation;
import simpaths.data.Parameters;
import simpaths.model.decisions.DecisionParams;
import simpaths.model.enums.Labour;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.TargetShares;

import java.util.Set;


/**
 * RetirementAlignment adjusts the probability of retiring.
 * The object is designed to assist modification of the intercept of the retirement models (R1a for singles, R1b for
 * persons with a partner).
 *
 * The target (input/alignment_files/alignment_targets_retirement.xlsx) is the share of retired persons among those
 * aged {@link Parameters#MIN_AGE_TO_RETIRE} or older with a labour status, the population of
 * {@link #inTargetPopulation(Person)}. Retirement is considered only from that age, so younger persons do not enter
 * the share.
 *
 * The alignment runs just before the Retirement process, when the current activity status labC4 has been cleared for
 * the new year (it is @NullInitialised; labC4L1 holds last year's status). Like
 * {@link Person#updateRetirementStatus(double)}, it therefore reads the lagged status: everyone aged
 * MIN_AGE_TO_RETIRE or older gets a status this year, those retired last year stay retired, those not retired last
 * year and aged up to {@link Parameters#MAX_AGE_FLEXIBLE_LABOUR_SUPPLY} retire with the (adjusted) R1a/R1b
 * probability, and older ones retire for certain. With intertemporal optimisation and a retirement state the decision
 * is deterministic, so the adjustment has no effect on it.
 *
 * A search routine is used to find the value by which the intercept should be adjusted.
 * If the projected share of retired individuals in the target population differs from the desired target by more
 * than a specified threshold, then the intercept is adjusted and the share re-evaluated.
 *
 * Importantly, the adjustment needs to be only found once. Modified intercepts can then be used in subsequent simulations.
 */
public class RetirementAlignment implements IEvaluation {

    private double targetRetiredShare;
    private Set<Person> persons;
    private SimPathsModel model;
    private long numInTargetPopulation;
    private long numAlreadyRetired;
    private long numDeterministicRetire;
    private boolean intertemporalPath;


    // CONSTRUCTOR
    public RetirementAlignment(Set<Person> persons) {
        this.model = (SimPathsModel) SimulationEngine.getInstance().getManager(SimPathsModel.class.getCanonicalName());
        this.persons = persons;
        targetRetiredShare = Parameters.getTargetShare(model.getYear(), TargetShares.Retirement);
        intertemporalPath = Parameters.enableIntertemporalOptimisations && DecisionParams.flagRetirement;

        numInTargetPopulation = persons.stream()
                .filter(RetirementAlignment::atTargetAge)
                .count();
        numAlreadyRetired = persons.stream()
                .filter(person -> atTargetAge(person) && Les_c4.Retired.equals(person.getLabC4L1()))
                .count();
        if (intertemporalPath) {
            numDeterministicRetire = persons.stream()
                    .filter(person -> atRiskOfRetirement(person)
                            && Labour.ZERO.equals(person.getLabHrsWorkEnumWeekL1()))
                    .count();
        } else {
            // above MAX_AGE_FLEXIBLE_LABOUR_SUPPLY everyone not yet retired retires, whatever the adjustment
            numDeterministicRetire = persons.stream()
                    .filter(person -> atRiskOfRetirement(person)
                            && person.getDemAge() > Parameters.MAX_AGE_FLEXIBLE_LABOUR_SUPPLY)
                    .count();
        }
    }


    /**
     * Whether a person belongs to the population of the retirement target once statuses are set for the year: aged
     * {@link Parameters#MIN_AGE_TO_RETIRE} or older, with a labour status. The target is computed on the same
     * population from the initial populations (input/alignment_files/DoFilesTarget/05_retirement_targets_initpopdata.do),
     * and AlignmentStatistics reports the simulated share on it at the end of each year.
     */
    public static boolean inTargetPopulation(Person person) {
        return person.getLabC4() != null && atTargetAge(person);
    }


    /**
     * The same population when the alignment runs, before this year's statuses are set: everyone aged
     * {@link Parameters#MIN_AGE_TO_RETIRE} or older is given a status this year.
     */
    private static boolean atTargetAge(Person person) {
        return person.getDemAge() >= Parameters.MIN_AGE_TO_RETIRE;
    }


    /**
     * Whether a person of the target population can enter retirement this year: not retired in the previous year
     * (the condition of {@link Person#updateRetirementStatus(double)}).
     */
    private static boolean atRiskOfRetirement(Person person) {
        return atTargetAge(person) && !Les_c4.Retired.equals(person.getLabC4L1());
    }


    /**
     * Evaluates the discrepancy between the expected (smooth) retired share at a candidate adjustment
     * and the target share. Uses sum of probit probabilities rather than stochastic realisations,
     * yielding a smooth, deterministic objective for the root search.
     *
     * @param args An array of parameters, where args[0] represents the probit intercept adjustment.
     * @return target retired share minus expected retired share of the target population at the given adjustment.
     */
    @Override
    public double evaluate(double[] args) {

        if (numInTargetPopulation == 0) return targetRetiredShare;

        double expectedRetiring = numDeterministicRetire;
        if (!intertemporalPath) {
            expectedRetiring += persons.parallelStream()
                    .filter(person -> atRiskOfRetirement(person)
                            && person.getDemAge() <= Parameters.MAX_AGE_FLEXIBLE_LABOUR_SUPPLY)
                    .mapToDouble(person -> {
                        double score;
                        if (person.getPartner() != null) {
                            score = Parameters.getRegRetirementR1b().getScore(person, Person.Variables.class);
                            return Parameters.getRegRetirementR1b().getProbability(score + args[0]);
                        } else {
                            score = Parameters.getRegRetirementR1a().getScore(person, Person.Variables.class);
                            return Parameters.getRegRetirementR1a().getProbability(score + args[0]);
                        }
                    })
                    .sum();
        }

        double expectedRetiredShare = (numAlreadyRetired + expectedRetiring) / numInTargetPopulation;
        return targetRetiredShare - expectedRetiredShare;
    }

    public double getTargetRetiredShare() {
        return targetRetiredShare;
    }
}
