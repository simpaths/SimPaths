package simpaths.data.filters;

import microsim.statistics.ICollectionFilter;
import simpaths.model.Person;
import simpaths.model.enums.Education;

public class EducationWorkingAgeCSfilter implements ICollectionFilter {


    private Education education;
    private int ageFrom = 24;
    private int ageTo = 64;

    public EducationWorkingAgeCSfilter(Education edu) {
        super();
        this.education = edu;

    }

    public boolean isFiltered(Object object) {
        Person person = (Person) object;
        return (person.getDag() >= ageFrom) && (person.getDag() <= ageTo) && person.getDeh_c3().equals(education);

    }


}
