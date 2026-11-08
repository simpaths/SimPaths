package simpaths.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransitionMatrixAnnualiserTest {

    @Test
    void recoversPrincipalAnnualMatrix() {
        double[][] annual = {
                {0.80, 0.10, 0.05, 0.05},
                {0.20, 0.60, 0.10, 0.10},
                {0.10, 0.10, 0.70, 0.10},
                {0.05, 0.15, 0.20, 0.60}
        };
        double[][] biennial = multiply(annual, annual);

        double[][] recovered = TransitionMatrixAnnualiser.annualiseBiennial(biennial);

        for (int row = 0; row < annual.length; row++) {
            double rowSum = 0.0;
            for (int column = 0; column < annual.length; column++) {
                assertEquals(annual[row][column], recovered[row][column], 1.0e-9);
                rowSum += recovered[row][column];
            }
            assertEquals(1.0, rowSum, 1.0e-12);
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
