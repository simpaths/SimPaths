package simpaths.model.utilities;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import simpaths.data.Parameters;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Occupancy;
import simpaths.model.enums.TimeSeriesVariable;


/*****************************************************
 * CLASS TO MANAGE ANNUITY RATES
 /*****************************************************/
public class AnnuityRates {

    MultiKeyMap<Object, Double> annuityRates;      // first key: gender; second key: year; third key: age

    public AnnuityRates() {

        annuityRates = MultiKeyMap.multiKeyMap(new LinkedMap<>());
    }

    public void evalAnnuityRates(int minYear, int maxYear, int minAge, int maxAge) {

        int minBirthYear = minYear - maxAge;
        int maxBirthYear = maxYear - minAge;
        for (int birthYear=minBirthYear; birthYear<=maxBirthYear; birthYear++) {

            evalAnnuityRates(Gender.Female, birthYear);
            evalAnnuityRates(Gender.Male, birthYear);
        }
    }

    private void evalAnnuityRates(Gender gender, int birthYear) {

        double fairAnnuityRate = 1.0, survivalRate, inflationIndexP1, inflationIndex = 1.0;
        double realReturn = 1.0 + Parameters.annuityRealRateOfReturn;
        for (int age=Parameters.maxAge; age>= Parameters.MIN_AGE_TO_RETIRE; age--) {

            int year = birthYear + age;
            inflationIndexP1 = inflationIndex;
            inflationIndex = Parameters.getTimeSeriesValue(year, TimeSeriesVariable.Inflation);
            if (age!=Parameters.maxAge) {

                if (Gender.Female.equals(gender)) {

                    survivalRate = (1.0 - Parameters.getMortalityProbability(Gender.Female, age, birthYear+age));
                } else {

                    survivalRate = (1.0 - Parameters.getMortalityProbability(Gender.Male, age, birthYear+age));
                }
                fairAnnuityRate = 1.0 + fairAnnuityRate / realReturn * inflationIndex / inflationIndexP1 * survivalRate;
            }
            annuityRates.put(gender, year, age, fairAnnuityRate / Parameters.annuityMoneysWorth);
        }
    }

    public double getAnnuityRateByGenderAgeYear(Gender gender, int age, int year) {

        Double annuityRate = annuityRates.get(gender, year, age);
        if (annuityRate==null)
            throw new RuntimeException("Annuity rate not found for " + gender + " " + age + " " + year);
        return annuityRate;
    }

    public double getAnnuityRateByOccupancyBirthYearAge(Occupancy occupancy, int birthYear, int age) {

        if (Occupancy.Single_Male.equals(occupancy)) {
            return annuityRates.get(Gender.Male, birthYear+age, age);
        } else if (Occupancy.Single_Female.equals(occupancy)) {
            return annuityRates.get(Gender.Female, birthYear+age, age);
        } else {
            return 0.5 * (annuityRates.get(Gender.Male, birthYear+age, age) + annuityRates.get(Gender.Female, birthYear+age, age));
        }
    }
}
