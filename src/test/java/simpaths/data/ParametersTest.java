package simpaths.data;

import microsim.data.MultiKeyCoefficientMap;
import microsim.engine.SimulationEngine;
import microsim.statistics.regression.LinearRegression;
import microsim.statistics.regression.OrderedRegression;
import microsim.statistics.regression.RegressionUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;

class ParametersTest {

    private MockedStatic<SimulationEngine> mockEngine;
    private MockedStatic<RegressionUtils> mockRegressionUtils;

    @Nested
    @DisplayName("Loading regression parameters")
    class testLoadRegressionParameters {

        @BeforeAll
        public static void setup() {

            SimulationEngine.getInstance();
            SimulationEngine.getRnd().setSeed(1234);

        }



        /**
         * Loads and validates DHE MCS parameters
         */
        @Test
        @DisplayName("Loads MCS parameters")
        void loadDHE_MCSParameters() {

            try {
                Parameters.loadDHE_MCSParameters("UK", Boolean.TRUE);
            } catch (NullPointerException e) {
                System.out.println("Not all DHE_MCS worksheets loaded");
            };


            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthMCS1(), "`regHealthMCS1` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthMCS2Males(), "`regHealthMCS2Males` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthMCS2Females(), "`regHealthMCS2Females` not loaded");
        }

        /**
         * Loads and validates physical component summary parameters
         */
        @Test
        @DisplayName("Loads PCS parameters")
        void loadDHE_PCSParameters() {

            try {
                Parameters.loadDHE_PCSParameters("UK", Boolean.TRUE);
            } catch (NullPointerException e) {
                System.out.println("Not all DHE_PCS worksheets loaded");
            };


            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthPCS1(), "`regHealthPCS1` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthPCS2Males(), "`regHealthPCS2Males` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthPCS2Females(), "`regHealthPCS2Females` not loaded");
        }

        /**
         * Loads and validates life satisfaction regression parameters
         */
        @Test
        @DisplayName("Loads Life Satisfaction parameters")
        void loadDLSParameters() {

            try {
                Parameters.loadDLSParameters("UK", Boolean.TRUE);
            } catch (NullPointerException e) {
                System.out.println("Not all DLS worksheets loaded");
            };

            assertInstanceOf(LinearRegression.class, Parameters.getRegLifeSatisfaction1(), "`regLifeSatisfaction1` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLifeSatisfaction2Males(), "`regLifeSatisfaction2Males` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLifeSatisfaction2Females(), "`regLifeSatisfaction2Females` not loaded");
        }

        /**
         * Loads and validates health mental parameters
         */
        @Test
        @DisplayName("Loads Health Mental (level and caseness) parameters")
        void loadDHMParameters() {

            try {
                Parameters.loadDHMParameters("UK", Boolean.TRUE);
            } catch (NullPointerException e) {
                System.out.println("Not all DHM worksheets loaded");
            };


            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM1Level(), "`regHealthHM1Level` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2LevelMales(), "`regHealthHM2LevelMales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2LevelFemales(), "`regHealthHM2LevelFemales` not loaded");
            assertInstanceOf(OrderedRegression.class, Parameters.getRegHealthHM1Case(), "`regHealthHM1Case` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2CaseMales(), "`regHealthHM2CaseMales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2CaseFemales(), "`regHealthHM2CaseFemales` not loaded");

        }

        /**
         * Loads and validates labour supply utility parameters
         */
        @Test
        @DisplayName("Loads labour supply utility parameters")
        void loadLabourSupplyUtilityParameters() {

            try {
                Parameters.loadLabourSupplyUtilityParameters("UK", Boolean.TRUE);
            } catch (NullPointerException e) {
                throw new RuntimeException("Not all LabourSupplyUtility worksheets loaded. Error: " + e.getMessage());
            };


            assertDoesNotThrow(Parameters::getRegLabourSupplyUtilityMales, "`RegLabourSupplyUtilityMales` not loaded");
            assertDoesNotThrow(Parameters::getRegLabourSupplyUtilityFemales, "`RegLabourSupplyUtilityFemales` not loaded");
            assertDoesNotThrow(Parameters::getRegLabourSupplyUtilityMalesWithDependent, "`RegLabourSupplyUtilityMalesWithDependent` not loaded");
            assertDoesNotThrow(Parameters::getRegLabourSupplyUtilityFemalesWithDependent, "`RegLabourSupplyUtilityFemalesWithDependent` not loaded");
            assertDoesNotThrow(Parameters::getRegLabourSupplyUtilityACMales, "`RegLabourSupplyUtilityACMales` not loaded");
            assertDoesNotThrow(Parameters::getRegLabourSupplyUtilityACFemales, "`RegLabourSupplyUtilityACFemales` not loaded");
            assertDoesNotThrow(Parameters::getRegLabourSupplyUtilityCouples, "`RegLabourSupplyUtilityCouples` not loaded");
        }
    }


    /**
     * Tests regressor validation logic using valid/invalid maps
     */
    @Test
    void validatePersonRegressors() {

        String[] badValueVector = new String[] {"Dag", "Not_a_valid_value"};
        String[] goodValueVector = new String[] {"Dag", "D_Home_owner", "PovertyToNonPoverty"};
        String[] keyVector = new String[] {"REGRESSOR"};

        MultiKeyCoefficientMap badMap = new MultiKeyCoefficientMap(keyVector, badValueVector);
        for (String badValue : badValueVector) {badMap.putValue(badValue, 0);}

        MultiKeyCoefficientMap goodMap = new MultiKeyCoefficientMap(keyVector, goodValueVector);
        for (String goodValue : goodValueVector) {goodMap.putValue(goodValue, 0);}

        assertThrows(RuntimeException.class, () -> Parameters.validateRegressors(badMap, "A map designed to contain invalid values", "Sheet1"));
        assertDoesNotThrow(() -> Parameters.validateRegressors(goodMap, "A map designed to contain valid values", "Sheet1"));

    }
}
