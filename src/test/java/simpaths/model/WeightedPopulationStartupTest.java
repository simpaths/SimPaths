package simpaths.model;

import org.junit.jupiter.api.Test;
import simpaths.model.enums.Country;

import static org.junit.jupiter.api.Assertions.*;

class WeightedPopulationStartupTest {
    @Test
    void weightedRunsAreRejectedBeforeStartup() {
        SimPathsModel model = new SimPathsModel(Country.UK);
        model.setUseWeights(true);

        IllegalStateException error = assertThrows(IllegalStateException.class, model::buildObjects);
        assertTrue(error.getMessage().contains("useWeights"));
        assertTrue(error.getMessage().contains("unsupported"));
    }
}