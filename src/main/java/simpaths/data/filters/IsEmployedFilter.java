package simpaths.data.filters;

import microsim.statistics.ICollectionFilter;
import simpaths.data.Parameters;
import simpaths.model.Person;
import simpaths.model.enums.Indicator;
import simpaths.model.enums.Les_c4;

public class IsEmployedFilter implements ICollectionFilter {

    public IsEmployedFilter() {
        super();
    }

    @Override
    public boolean isFiltered(Object o) {
        Person person = (Person) o;

        return (person.getLabC4() == Les_c4.EmployedOrSelfEmployed);
    }
}
