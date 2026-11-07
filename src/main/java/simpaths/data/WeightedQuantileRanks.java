package simpaths.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Assigns observations to categories using cut points from a weighted empirical
 * distribution. Equal values are always assigned to the same category.
 */
public final class WeightedQuantileRanks {

    private WeightedQuantileRanks() {
    }

    public static <T> Map<T, Integer> assign(
            Collection<T> observations,
            ToDoubleFunction<T> valueFunction,
            ToDoubleFunction<T> weightFunction,
            int numberOfGroups) {

        if (numberOfGroups < 2)
            throw new IllegalArgumentException("numberOfGroups must be at least 2");
        if (observations.isEmpty())
            throw new IllegalArgumentException("Cannot rank an empty collection");

        List<WeightedValue<T>> sorted = new ArrayList<>(observations.size());
        double totalWeight = 0.0;
        for (T observation : observations) {
            double value = valueFunction.applyAsDouble(observation);
            double weight = weightFunction.applyAsDouble(observation);
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Ranking value must be finite");
            if (!Double.isFinite(weight) || weight <= 0.0)
                throw new IllegalArgumentException("Ranking weight must be finite and positive");
            sorted.add(new WeightedValue<>(observation, value, weight));
            totalWeight += weight;
        }
        if (!Double.isFinite(totalWeight) || totalWeight <= 0.0)
            throw new IllegalArgumentException("Total ranking weight must be finite and positive");

        sorted.sort(Comparator.comparingDouble(WeightedValue::value));
        double[] cutPoints = new double[numberOfGroups - 1];
        int cutPointIndex = 0;
        double cumulativeWeight = 0.0;
        for (WeightedValue<T> observation : sorted) {
            cumulativeWeight += observation.weight();
            while (cutPointIndex < cutPoints.length
                    && cumulativeWeight >= totalWeight * (cutPointIndex + 1.0) / numberOfGroups) {
                cutPoints[cutPointIndex] = observation.value();
                cutPointIndex++;
            }
        }
        while (cutPointIndex < cutPoints.length) {
            cutPoints[cutPointIndex] = sorted.get(sorted.size() - 1).value();
            cutPointIndex++;
        }

        Map<T, Integer> ranks = new LinkedHashMap<>();
        for (WeightedValue<T> observation : sorted) {
            int rank = 1;
            while (rank < numberOfGroups && observation.value() > cutPoints[rank - 1]) {
                rank++;
            }
            ranks.put(observation.item(), rank);
        }
        return ranks;
    }

    private record WeightedValue<T>(T item, double value, double weight) {
    }
}
