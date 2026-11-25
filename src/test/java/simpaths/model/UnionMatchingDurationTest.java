package simpaths.model;

import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.Test;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Region;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;

class UnionMatchingDurationTest {
    @Test
    void newUnionStartsInFirstYearForBothPartners() throws Exception {
        Person male = mock(Person.class);
        Person female = mock(Person.class);
        when(male.getDemMaleFlag()).thenReturn(Gender.Male);
        when(female.getDemMaleFlag()).thenReturn(Gender.Female);
        when(male.getRegion()).thenReturn(Region.UKC);
        when(female.getRegion()).thenReturn(Region.UKC);
        UnionMatching matching = new UnionMatching(new Pair<>(new HashSet<>(Set.of(male)), new HashSet<>(Set.of(female))));
        Method match = UnionMatching.class.getDeclaredMethod("localMatch", Person.class, Person.class);
        match.setAccessible(true);
        match.invoke(matching, male, female);
        verify(male).setDemPartnerNYear(1);
        verify(female).setDemPartnerNYear(1);
    }
}