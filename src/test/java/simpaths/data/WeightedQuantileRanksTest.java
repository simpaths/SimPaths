package simpaths.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightedQuantileRanksTest {

    @Test
    void assignsTenEqualSizedGroupsForEqualWeights() {
        List<Observation> observations = new ArrayList<>();
        for (int value = 1; value <= 100; value++) {
            observations.add(new Observation(value, value, 1.0));
        }

        Map<Observation, Integer> ranks = assign(observations);

        for (Observation observation : observations) {
            int expected = (observation.id() - 1) / 10 + 1;
            assertEquals(expected, ranks.get(observation));
        }
    }

    @Test
    void respectsWeightsAndKeepsTiesTogether() {
        Observation low = new Observation(1, -2.0, 1.0);
        Observation zeroA = new Observation(2, 0.0, 4.0);
        Observation zeroB = new Observation(3, 0.0, 4.0);
        Observation high = new Observation(4, 10.0, 1.0);

        Map<Observation, Integer> ranks = assign(List.of(low, zeroA, zeroB, high));

        assertEquals(1, ranks.get(low));
        assertEquals(ranks.get(zeroA), ranks.get(zeroB));
        assertEquals(2, ranks.get(zeroA));
        assertEquals(10, ranks.get(high));
    }

    @Test
    void isPermutationInvariantIncludingAtTies() {
        List<Observation> observations = List.of(
                new Observation(1, -1.0, 2.0),
                new Observation(2, 0.0, 3.0),
                new Observation(3, 0.0, 4.0),
                new Observation(4, 2.0, 1.0),
                new Observation(5, 8.0, 5.0));
        List<Observation> reversed = new ArrayList<>(observations);
        Collections.reverse(reversed);

        Map<Observation, Integer> forwardRanks = assign(observations);
        Map<Observation, Integer> reverseRanks = assign(reversed);

        for (Observation observation : observations) {
            assertEquals(forwardRanks.get(observation), reverseRanks.get(observation));
        }
    }

    @Test
    void rejectsEmptyGroupsAndInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> assign(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> assign(List.of(new Observation(1, Double.NaN, 1.0))));
        assertThrows(IllegalArgumentException.class,
                () -> assign(List.of(new Observation(1, 0.0, 0.0))));
    }

    private Map<Observation, Integer> assign(List<Observation> observations) {
        return WeightedQuantileRanks.assign(
                observations, Observation::value, Observation::weight, 10);
    }

    private record Observation(int id, double value, double weight) {
    }
}
