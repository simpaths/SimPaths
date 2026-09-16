package simpaths.model;

import microsim.engine.SimulationEngine;
import simpaths.data.IEvaluation;
import simpaths.data.Parameters;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.TargetShares;

import java.util.Set;


/**
 * InSchoolAlignment adjusts the probability of being a student.
 * The object is designed to assist modification of the intercept of the "inSchool" models.
 *
 * A search routine is used to find the value by which the intercept should be adjusted.
 * If the projected share of students in the population differs from the desired target by more than a specified threshold,
 * then the intercept is adjusted and the share re-evaluated.
 *
 * Importantly, the adjustment needs to be only found once. Modified intercepts can then be used in subsequent simulations.
 */
public class InSchoolAlignment implements IEvaluation {

    private static final int MIN_STUDENT_AGE = Parameters.MIN_AGE_TO_LEAVE_EDUCATION;
    private static final int MAX_STUDENT_AGE = Parameters.MAX_AGE_TO_STAY_IN_CONTINUOUS_EDUCATION;

    private double targetStudentShare;
    private Set<Person> persons;
    private SimPathsModel model;
    private long numEligible;
    private long numDeterministicStudents;
    private double baseReEntryExpected;


    // CONSTRUCTOR
    public InSchoolAlignment(Set<Person> persons) {
        this.model = (SimPathsModel) SimulationEngine.getInstance().getManager(SimPathsModel.class.getCanonicalName());
        this.persons = persons;
        targetStudentShare = Parameters.getTargetShare(model.getYear(), TargetShares.Students);

        numEligible = persons.stream()
                .filter(person -> person.getLabC4() != null
                        && person.getDemAge() >= MIN_STUDENT_AGE
                        && person.getDemAge() <= MAX_STUDENT_AGE)
                .count();

        // Lagged students below minimum quitting age: deterministically remain students
        numDeterministicStudents = persons.stream()
                .filter(person -> person.getLabC4() != null
                        && person.getDemAge() >= MIN_STUDENT_AGE
                        && person.getDemAge() <= MAX_STUDENT_AGE
                        && Les_c4.Student.equals(person.getLabC4L1())
                        && person.getDemAge() < Parameters.MIN_AGE_TO_LEAVE_EDUCATION)
                .count();

        // Non-students who are not retired: E1b probability of re-entering (no adjustment applied)
        baseReEntryExpected = persons.parallelStream()
                .filter(person -> person.getLabC4() != null
                        && person.getDemAge() >= MIN_STUDENT_AGE
                        && person.getDemAge() <= MAX_STUDENT_AGE
                        && !Les_c4.Student.equals(person.getLabC4L1())
                        && !Les_c4.Retired.equals(person.getLabC4L1()))
                .mapToDouble(person -> {
                    double score = Parameters.getRegEducationE1b().getScore(person, Person.DoublesVariables.class);
                    return Parameters.getRegEducationE1b().getProbability(score);
                })
                .sum();
    }


    /**
     * Evaluates the discrepancy between the expected (smooth) student share at a candidate adjustment
     * and the target share. Uses sum of probit probabilities rather than stochastic realisations,
     * yielding a smooth, deterministic objective for the root search.
     *
     * The adjustment only affects the E1a model (continuing students deciding whether to stay).
     * The E1b model (re-entry) does not use the adjustment.
     *
     * @param args An array of parameters, where args[0] represents the probit intercept adjustment.
     * @return target student share minus expected student share at the given adjustment.
     */
    @Override
    public double evaluate(double[] args) {

        if (numEligible == 0) return targetStudentShare;

        // Lagged students within quitting age range: E1a probability of remaining (adjustment applies)
        // Students above MAX_AGE_TO_STAY_IN_CONTINUOUS_EDUCATION deterministically leave → contribute 0
        double expectedContinuing = persons.parallelStream()
                .filter(person -> person.getLabC4() != null
                        && person.getDemAge() >= MIN_STUDENT_AGE
                        && person.getDemAge() <= MAX_STUDENT_AGE
                        && Les_c4.Student.equals(person.getLabC4L1())
                        && person.getDemAge() >= Parameters.MIN_AGE_TO_LEAVE_EDUCATION
                        && person.getDemAge() <= Parameters.MAX_AGE_TO_STAY_IN_CONTINUOUS_EDUCATION)
                .mapToDouble(person -> {
                    double score = Parameters.getRegEducationE1a().getScore(person, Person.DoublesVariables.class);
                    return Parameters.getRegEducationE1a().getProbability(score + args[0]);
                })
                .sum();

        double expectedStudents = numDeterministicStudents + expectedContinuing + baseReEntryExpected;
        double expectedStudentShare = expectedStudents / numEligible;
        return targetStudentShare - expectedStudentShare;
    }

    public double getTargetStudentShare() {
        return targetStudentShare;
    }
}
