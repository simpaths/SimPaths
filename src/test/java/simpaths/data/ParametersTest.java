package simpaths.data;

import microsim.statistics.regression.LinearRegression;
import org.apache.xmlbeans.impl.tool.Extension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParametersTest {

    @Nested
    @DisplayName("Loading regression parameters")
    class testLoadRegressionParameters {


        @Test
        @DisplayName("Loads MCS parameters")
        void loadDHE_MCSParameters() {

            try {
                Parameters.loadDHE_MCSParameters("UK");
            } catch (NullPointerException e) {
                System.out.println("Not all DHE_MCS worksheets loaded");
            };


            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthMCS1(), "`regHealthMCS1` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthMCS2Males(), "`regHealthMCS2Males` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthMCS2Females(), "`regHealthMCS2Females` not loaded");
        }

        @Test
        @DisplayName("Loads PCS parameters")
        void loadDHE_PCSParameters() {

            try {
                Parameters.loadDHE_PCSParameters("UK");
            } catch (NullPointerException e) {
                System.out.println("Not all DHE_PCS worksheets loaded");
            };


            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthPCS1(), "`regHealthPCS1` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthPCS2Males(), "`regHealthPCS2Males` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthPCS2Females(), "`regHealthPCS2Females` not loaded");
        }

        @Test
        @DisplayName("Loads Life Satisfaction parameters")
        void loadDLSParameters() {

            try {
                Parameters.loadDLSParameters("UK");
            } catch (NullPointerException e) {
                System.out.println("Not all DLS worksheets loaded");
            };

            assertInstanceOf(LinearRegression.class, Parameters.getRegLifeSatisfaction1(), "`regLifeSatisfaction1` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLifeSatisfaction2Males(), "`regLifeSatisfaction2Males` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLifeSatisfaction2Females(), "`regLifeSatisfaction2Females` not loaded");
        }

        @Test
        @DisplayName("Loads Health Mental (level and caseness) parameters")
        void loadDHMParameters() {

            try {
                Parameters.loadDHMParameters("UK");
            } catch (NullPointerException e) {
                System.out.println("Not all DHM worksheets loaded");
            };


            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM1Level(), "`regHealthHM1Level` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2LevelMales(), "`regHealthHM2LevelMales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2LevelFemales(), "`regHealthHM2LevelFemales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM1Case(), "`regHealthHM1Case` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2CaseMales(), "`regHealthHM2CaseMales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegHealthHM2CaseFemales(), "`regHealthHM2CaseFemales` not loaded");

        }

        @Test
        @DisplayName("Loads labour supply utility parameters")
        void loadLabourSupplyUtilityParameters() {

            try {
                Parameters.loadLabourSupplyUtilityParameters("UK");
            } catch (NullPointerException e) {
                System.out.println("Not all LabourSupplyUtility worksheets loaded");
            };


            assertInstanceOf(LinearRegression.class, Parameters.getRegLabourSupplyUtilityMales(), "`RegLabourSupplyUtilityMales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLabourSupplyUtilityFemales(), "`RegLabourSupplyUtilityFemales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLabourSupplyUtilityMalesWithDependent(), "`RegLabourSupplyUtilityMalesWithDependent` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLabourSupplyUtilityFemalesWithDependent(), "`RegLabourSupplyUtilityFemalesWithDependent` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLabourSupplyUtilityACMales(), "`RegLabourSupplyUtilityACMales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLabourSupplyUtilityACFemales(), "`RegLabourSupplyUtilityACFemales` not loaded");
            assertInstanceOf(LinearRegression.class, Parameters.getRegLabourSupplyUtilityCouples(), "`RegLabourSupplyUtilityCouples` not loaded");
        }
    }
}
