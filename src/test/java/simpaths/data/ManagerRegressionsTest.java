package simpaths.data;

import microsim.data.MultiKeyCoefficientMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagerRegressionsTest {

    @Test
    void recoversAnnualBinaryProbabilities() {
        double biennialEntry = 0.20;
        double biennialPersistence = 0.80;

        double[] annual = ManagerRegressions.recoverAnnualEntryPersistence(biennialEntry, biennialPersistence);
        double[][] annualMatrix = {
                {1.0 - annual[0], annual[0]},
                {1.0 - annual[1], annual[1]}
        };
        double[][] reconstructed = multiply(annualMatrix, annualMatrix);

        assertEquals(biennialEntry, reconstructed[0][1], 1.0e-12);
        assertEquals(biennialPersistence, reconstructed[1][1], 1.0e-12);
    }

    @Test
    void usesFallbackWhenEntryIsNotBelowPersistence() {
        double[] annual = ManagerRegressions.recoverAnnualEntryPersistence(0.80, 0.20);

        assertEquals(0.40, annual[0], 1.0e-12);
        assertEquals(Math.sqrt(0.20), annual[1], 1.0e-12);
    }

    @Test
    void readsIntegerValuedRegressionCoefficientAsDouble() {
        MultiKeyCoefficientMap rmse = new MultiKeyCoefficientMap(
                new String[]{"REGRESSION"}, new String[]{"COEFFICIENT"});
        rmse.putValue("HW2c", 0);

        try (MockedStatic<Parameters> parameters = Mockito.mockStatic(Parameters.class)) {
            parameters.when(Parameters::getCoefficientMapRMSE).thenReturn(rmse);
            assertEquals(0.0,
                    ManagerRegressions.getRegressionCoeff(RegressionName.RMSE, "HW2c"));
        }
    }

    private double[][] multiply(double[][] first, double[][] second) {
        int size = first.length;
        double[][] result = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                for (int inner = 0; inner < size; inner++) {
                    result[row][column] += first[row][inner] * second[inner][column];
                }
            }
        }
        return result;
    }
}
