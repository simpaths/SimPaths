package simpaths.model;

import simpaths.data.Parameters;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Region;
import microsim.engine.SimulationEngine;


public class Validator {

    private final SimPathsModel model;

    Number value;

    public Validator() {
        super();
        model = (SimPathsModel) SimulationEngine.getInstance().getManager(SimPathsModel.class.getCanonicalName());
    }

    public int getPopulationProjectionByAge(int startAge, int endAge) {
        double numberOfPeople = 0.;
        for (Gender gender : Gender.values()) {
            for (Region region : Parameters.getCountryRegions()) {
                for (int age = startAge; age <= endAge; age++) {
                    numberOfPeople += Parameters.getPopulationProjections(gender, region, age, model.getYear());
                }
            }
        }
        int numberOfPeopleScaled = (int) Math.round(numberOfPeople / model.getScalingFactor());
        return numberOfPeopleScaled;
    }
}
