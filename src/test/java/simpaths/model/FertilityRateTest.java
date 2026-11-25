package simpaths.model;

import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class FertilityRateTest {
    @Test
    void fertilityPredictorMatchesEstimationForPeopleAndProxies() {
        int[] years = {2009, 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017,
                2018, 2019, 2020, 2021, 2022, 2023, 2024, 2050};
        double[] rates = {62.5, 64.0, 64.0, 64.6, 61.7, 61.4, 61.5, 61.2, 59.7,
                57.6, 55.8, 53.4, 54.1, 51.8, 49.8, 48.9, 48.9};
        for (boolean proxy : new boolean[]{false, true}) {
            Person person = spy(proxy ? new Person(true) : new Person(1L));
            for (int i = 0; i < years.length; i++) {
                doReturn(years[i]).when(person).getYear();
                assertEquals(rates[i], person.getDoubleValue(Person.Variables.fertilityRate));
                assertEquals(rates[i], person.getDoubleValue(Person.Variables.FertilityRate));
            }
            doReturn(2008).when(person).getYear();
            assertThrows(IllegalArgumentException.class, () -> person.getDoubleValue(Person.Variables.fertilityRate));
        }
    }

    @Test
    void fertilityRateUsesInputSeriesAndHoldsLastValueAfterItsEnd() {
        assertEquals(62.5, Parameters.getFertilityF1Rate(2009));
        assertEquals(48.9, Parameters.getFertilityF1Rate(2024));
        assertEquals(48.9, Parameters.getFertilityF1Rate(2050));
    }
}
