package simpaths.model;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WeightedPopulationStartupTest {
    @Test
    void weightedClonesRetainSurveyWeights() {
        Household household = mock(Household.class);

        SimPathsModel.resetCloneWeights(household, true);

        verify(household, never()).resetWeights(1.0d);
    }

    @Test
    void unweightedClonesResetSurveyWeights() {
        Household household = mock(Household.class);

        SimPathsModel.resetCloneWeights(household, false);

        verify(household).resetWeights(1.0d);
    }
}
