package simpaths.data;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

/**
 * Recovers the principal annual transition matrix from a biennial transition matrix.
 */
public final class TransitionMatrixAnnualiser {

    private static final double EIGENVALUE_TOLERANCE = 1.0e-10;
    private static final double PROBABILITY_TOLERANCE = 1.0e-8;
    private static final double RECONSTRUCTION_TOLERANCE = 1.0e-7;

    private TransitionMatrixAnnualiser() {
    }

    public static double[][] annualiseBiennial(double[][] biennial) {
        validateTransitionMatrix(biennial);

        RealMatrix biennialMatrix = new Array2DRowRealMatrix(biennial, true);
        EigenDecomposition decomposition = new EigenDecomposition(biennialMatrix);
        double[] realEigenvalues = decomposition.getRealEigenvalues();
        double[] imaginaryEigenvalues = decomposition.getImagEigenvalues();
        int size = biennial.length;
        RealMatrix rootEigenvalues = new Array2DRowRealMatrix(size, size);

        for (int index = 0; index < size; index++) {
            double real = realEigenvalues[index];
            double imaginary = imaginaryEigenvalues[index];
            if (Math.abs(imaginary) <= EIGENVALUE_TOLERANCE) {
                if (real < -EIGENVALUE_TOLERANCE) {
                    throw new ArithmeticException("biennial transition matrix has a negative real eigenvalue");
                }
                rootEigenvalues.setEntry(index, index, Math.sqrt(Math.max(0.0, real)));
            } else {
                if (index + 1 >= size ||
                        Math.abs(realEigenvalues[index + 1] - real) > EIGENVALUE_TOLERANCE ||
                        Math.abs(imaginaryEigenvalues[index + 1] + imaginary) > EIGENVALUE_TOLERANCE) {
                    throw new ArithmeticException("failed to identify a conjugate eigenvalue pair");
                }
                double modulus = Math.hypot(real, imaginary);
                double rootReal = Math.sqrt(Math.max(0.0, (modulus + real) / 2.0));
                double rootImaginary = rootReal > EIGENVALUE_TOLERANCE
                        ? imaginary / (2.0 * rootReal)
                        : Math.copySign(Math.sqrt(Math.max(0.0, (modulus - real) / 2.0)), imaginary);
                rootEigenvalues.setEntry(index, index, rootReal);
                rootEigenvalues.setEntry(index, index + 1, rootImaginary);
                rootEigenvalues.setEntry(index + 1, index, -rootImaginary);
                rootEigenvalues.setEntry(index + 1, index + 1, rootReal);
                index++;
            }
        }

        RealMatrix eigenvectors = decomposition.getV();
        RealMatrix inverseEigenvectors;
        try {
            inverseEigenvectors = new LUDecomposition(eigenvectors).getSolver().getInverse();
        } catch (SingularMatrixException exception) {
            throw new ArithmeticException("biennial transition matrix has a singular eigenvector matrix");
        }
        RealMatrix annualMatrix = eigenvectors.multiply(rootEigenvalues).multiply(inverseEigenvectors);
        double[][] annual = annualMatrix.getData();

        for (int row = 0; row < size; row++) {
            double rowSum = 0.0;
            for (int column = 0; column < size; column++) {
                double probability = annual[row][column];
                if (!Double.isFinite(probability) || probability < -PROBABILITY_TOLERANCE ||
                        probability > 1.0 + PROBABILITY_TOLERANCE) {
                    throw new ArithmeticException("principal matrix square root is not row-stochastic");
                }
                annual[row][column] = Math.min(1.0, Math.max(0.0, probability));
                rowSum += annual[row][column];
            }
            if (Math.abs(rowSum - 1.0) > PROBABILITY_TOLERANCE) {
                throw new ArithmeticException("principal matrix square root does not sum to one by row");
            }
            for (int column = 0; column < size; column++) {
                annual[row][column] /= rowSum;
            }
        }

        RealMatrix reconstructed = new Array2DRowRealMatrix(annual, false)
                .multiply(new Array2DRowRealMatrix(annual, false));
        double maxError = maximumAbsoluteDifference(reconstructed, biennialMatrix);
        if (maxError > RECONSTRUCTION_TOLERANCE) {
            throw new ArithmeticException("annual transition matrix does not reproduce the biennial matrix; max error " + maxError);
        }
        return annual;
    }

    private static void validateTransitionMatrix(double[][] matrix) {
        if (matrix == null || matrix.length < 2) {
            throw new IllegalArgumentException("transition matrix must contain at least two states");
        }
        int size = matrix.length;
        for (double[] row : matrix) {
            if (row == null || row.length != size) {
                throw new IllegalArgumentException("transition matrix must be square");
            }
            double rowSum = 0.0;
            for (double probability : row) {
                if (!Double.isFinite(probability) || probability < -PROBABILITY_TOLERANCE ||
                        probability > 1.0 + PROBABILITY_TOLERANCE) {
                    throw new IllegalArgumentException("transition matrix contains an invalid probability");
                }
                rowSum += probability;
            }
            if (Math.abs(rowSum - 1.0) > PROBABILITY_TOLERANCE) {
                throw new IllegalArgumentException("transition matrix probabilities must sum to one by row");
            }
        }
    }

    private static double maximumAbsoluteDifference(RealMatrix first, RealMatrix second) {
        double maximum = 0.0;
        for (int row = 0; row < first.getRowDimension(); row++) {
            for (int column = 0; column < first.getColumnDimension(); column++) {
                maximum = Math.max(maximum, Math.abs(first.getEntry(row, column) - second.getEntry(row, column)));
            }
        }
        return maximum;
    }
}
