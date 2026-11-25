package simpaths.model;

import microsim.engine.SimulationEngine;
import microsim.statistics.regression.BinomialRegression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import simpaths.data.Parameters;
import simpaths.model.decisions.DecisionParams;
import simpaths.model.enums.Labour;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.TargetShares;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The retirement target (alignment_targets_retirement.xlsx) is the share of retired persons among those aged
 * {@link Parameters#MIN_AGE_TO_RETIRE} or older. These tests check that {@link RetirementAlignment} measures the
 * simulated share on the same population, and that its expected share follows Person.updateRetirementStatus.
 */
class RetirementAlignmentTest {

    private static final int YEAR = 2020;
    private static final double TARGET_SHARE = 0.5;
    private static final double PROBABILITY_TO_RETIRE = 0.2;

    private MockedStatic<Parameters> parametersMock;
    private MockedStatic<SimulationEngine> engineMock;
    private boolean savedIntertemporalOptimisations;
    private boolean savedFlagRetirement;
    private Object savedRegR1a;
    private Object savedRegR1b;

    @BeforeEach
    void setUp() throws Exception {
        savedIntertemporalOptimisations = Parameters.enableIntertemporalOptimisations;
        savedFlagRetirement = DecisionParams.flagRetirement;
        Parameters.enableIntertemporalOptimisations = false;
        DecisionParams.flagRetirement = false;

        SimPathsModel model = mock(SimPathsModel.class);
        when(model.getYear()).thenReturn(YEAR);
        SimulationEngine engine = mock(SimulationEngine.class);
        when(engine.getManager(SimPathsModel.class.getCanonicalName())).thenReturn(model);
        engineMock = Mockito.mockStatic(SimulationEngine.class);
        engineMock.when(SimulationEngine::getInstance).thenReturn(engine);

        // evaluate() scores persons on a parallel stream, and a static mock applies only to the thread that created
        // it, so the regressions are set in the fields their (real) getters read
        // logistic probability, with the score set so that the probability to retire is PROBABILITY_TO_RETIRE at
        // a zero adjustment
        BinomialRegression regression = mock(BinomialRegression.class);
        when(regression.getScore(any(), eq(Person.Variables.class)))
                .thenReturn(Math.log(PROBABILITY_TO_RETIRE / (1.0 - PROBABILITY_TO_RETIRE)));
        when(regression.getProbability(anyDouble()))
                .thenAnswer(invocation -> 1.0 / (1.0 + Math.exp(-(double) invocation.getArgument(0))));
        savedRegR1a = setParametersField("regRetirementR1a", regression);
        savedRegR1b = setParametersField("regRetirementR1b", regression);

        parametersMock = Mockito.mockStatic(Parameters.class, Mockito.CALLS_REAL_METHODS);
        parametersMock.when(() -> Parameters.getTargetShare(YEAR, TargetShares.Retirement)).thenReturn(TARGET_SHARE);
    }

    @AfterEach
    void tearDown() throws Exception {
        parametersMock.close();
        engineMock.close();
        setParametersField("regRetirementR1a", savedRegR1a);
        setParametersField("regRetirementR1b", savedRegR1b);
        Parameters.enableIntertemporalOptimisations = savedIntertemporalOptimisations;
        DecisionParams.flagRetirement = savedFlagRetirement;
    }

    private static Object setParametersField(String name, Object value) throws Exception {
        Field field = Parameters.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }

    private static Person person(int age, Les_c4 status, Les_c4 statusLag1) {
        Person person = mock(Person.class);
        when(person.getDemAge()).thenReturn(age);
        when(person.getLabC4()).thenReturn(status);
        when(person.getLabC4L1()).thenReturn(statusLag1);
        return person;
    }

    /**
     * A person as the alignment sees them, before the Retirement process: labC4 is @NullInitialised, so this year's
     * status is not set yet and only last year's status is known.
     */
    private static Person atStartOfYear(int age, Les_c4 statusLag1) {
        return person(age, null, statusLag1);
    }

    @Test
    void targetPopulationIsPersonsAgedMinAgeToRetireOrOlderWithALabourStatus() {
        int minAge = Parameters.MIN_AGE_TO_RETIRE;
        assertFalse(RetirementAlignment.inTargetPopulation(person(minAge - 1, Les_c4.EmployedOrSelfEmployed, Les_c4.EmployedOrSelfEmployed)));
        assertTrue(RetirementAlignment.inTargetPopulation(person(minAge, Les_c4.EmployedOrSelfEmployed, Les_c4.EmployedOrSelfEmployed)));
        assertTrue(RetirementAlignment.inTargetPopulation(person(90, Les_c4.Retired, Les_c4.Retired)));
        assertFalse(RetirementAlignment.inTargetPopulation(person(minAge + 10, null, null)));
    }

    @Test
    void retiredShareLeavesOutPersonsBelowMinAgeToRetire() {
        Set<Person> persons = new LinkedHashSet<>(List.of(
                atStartOfYear(30, Les_c4.EmployedOrSelfEmployed),  // below 50: not in the share
                atStartOfYear(45, Les_c4.NotEmployed),             // below 50: not in the share
                atStartOfYear(55, Les_c4.Retired),
                atStartOfYear(72, Les_c4.Retired),
                atStartOfYear(50, Les_c4.NotEmployed),             // retires with PROBABILITY_TO_RETIRE
                atStartOfYear(60, Les_c4.EmployedOrSelfEmployed))); // retires with PROBABILITY_TO_RETIRE

        RetirementAlignment alignment = new RetirementAlignment(persons);

        // expected retired share among the four persons aged 50+: (2 + 2 * 0.2) / 4 = 0.6, not 2.4 / 6 over everyone
        double expectedRetiredShare = (2 + 2 * PROBABILITY_TO_RETIRE) / 4;
        assertEquals(TARGET_SHARE - expectedRetiredShare, alignment.evaluate(new double[]{0.0}), 1e-12);
    }

    @Test
    void aHigherAdjustmentRaisesTheExpectedRetiredShare() {
        // the root search relies on this: with the current status cleared at the start of the year, a population
        // read from labC4 instead of labC4L1 would be empty and the share would not respond at all
        Set<Person> persons = new LinkedHashSet<>(List.of(
                atStartOfYear(55, Les_c4.Retired),
                atStartOfYear(60, Les_c4.EmployedOrSelfEmployed),
                atStartOfYear(65, Les_c4.NotEmployed)));

        RetirementAlignment alignment = new RetirementAlignment(persons);

        double atMinusOne = alignment.evaluate(new double[]{-1.0});
        double atZero = alignment.evaluate(new double[]{0.0});
        double atPlusOne = alignment.evaluate(new double[]{1.0});
        assertTrue(atMinusOne > atZero && atZero > atPlusOne,
                "target minus expected share should fall as the adjustment rises: " + atMinusOne + ", " + atZero + ", " + atPlusOne);
    }

    @Test
    void personsAboveMaxAgeFlexibleLabourSupplyRetireForCertain() {
        int maxAge = Parameters.MAX_AGE_FLEXIBLE_LABOUR_SUPPLY;
        Set<Person> persons = new LinkedHashSet<>(List.of(
                atStartOfYear(maxAge, Les_c4.NotEmployed),        // last age of the R1a/R1b probit
                atStartOfYear(maxAge + 1, Les_c4.NotEmployed)));  // retires in updateRetirementStatus

        RetirementAlignment alignment = new RetirementAlignment(persons);

        double expectedRetiredShare = (PROBABILITY_TO_RETIRE + 1.0) / 2;
        assertEquals(TARGET_SHARE - expectedRetiredShare, alignment.evaluate(new double[]{0.0}), 1e-12);
    }

    @Test
    void withARetirementStateOnlyPersonsWhoDidNotWorkRetireAndTheAdjustmentHasNoEffect() {
        Parameters.enableIntertemporalOptimisations = true;
        DecisionParams.flagRetirement = true;
        Person notWorking = atStartOfYear(60, Les_c4.NotEmployed);
        when(notWorking.getLabHrsWorkEnumWeekL1()).thenReturn(Labour.ZERO);
        Person working = atStartOfYear(60, Les_c4.EmployedOrSelfEmployed);
        when(working.getLabHrsWorkEnumWeekL1()).thenReturn(Labour.THIRTY_EIGHT);
        Set<Person> persons = new LinkedHashSet<>(List.of(notWorking, working));

        RetirementAlignment alignment = new RetirementAlignment(persons);

        assertEquals(TARGET_SHARE - 0.5, alignment.evaluate(new double[]{0.0}), 1e-12);
        assertEquals(TARGET_SHARE - 0.5, alignment.evaluate(new double[]{2.0}), 1e-12);
    }
}
