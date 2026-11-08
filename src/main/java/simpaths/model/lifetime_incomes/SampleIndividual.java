package simpaths.model.lifetime_incomes;

import com.opencsv.bean.CsvBindByName;
import simpaths.data.Parameters;
import simpaths.model.enums.Gender;
import simpaths.model.enums.TimeSeriesVariable;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public class SampleIndividual {

    @CsvBindByName private int id;
    @CsvBindByName private int age;
    @CsvBindByName private int male;
    @CsvBindByName private double income;
    @CsvBindByName private int year;
    private Gender gender;
    private int ageAtDeath;
    TemplateIndividual ltIncomeDonor;
    RandomGenerator generator;                                              // random number generator

    public SampleIndividual() {
    }

    public void setGenerator(long seed) {
        generator = new Random(seed);
    }
    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getIncome() {
        return income;
    }

    public void setIncome(double income) {
        this.income = income;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMale() {
        gender = (male ==1) ? Gender.Male : Gender.Female;
        return male;
    }

    public void setMale(int male) {
        this.male = male;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public TemplateIndividual getLtIncomeDonor() {
        return ltIncomeDonor;
    }

    public void setLtIncomeDonor(TemplateIndividual ltIncomeDonor) {
        this.ltIncomeDonor = ltIncomeDonor;
    }

    public int getAgeAtDeath() {
        return ageAtDeath;
    }

    public void setAgeAtDeath(int ageAtDeath) {
        this.ageAtDeath = ageAtDeath;
    }


    public void matchLifetimeIncome(int ageH, boolean agePlus, final double[] sortedZ, List<TemplateIndividual> templateIndividuals) {

        boolean matchHere;
        if (agePlus)
            matchHere = (age >= ageH);
        else
            matchHere = (age == ageH);
        if (matchHere) {

            // identify normalised income target
            if (income > 0.0) {

                double gmEqDispIncomePerYear = getGMEquivalisedIncome(age, year);
                double zTarget = Math.log(income / gmEqDispIncomePerYear);
                int ii = Arrays.binarySearch(sortedZ, zTarget);
                if (ii >= 0) {
                    // exact match found
                    setLtIncomeDonor(templateIndividuals.get(ii));
                } else {
                    // adopt nearest neighbour
                    int ins = -ii - 1;
                    if (ins == 0) {
                        // lower bound
                        setLtIncomeDonor(templateIndividuals.get(ins));
                    } else if (ins == sortedZ.length) {
                        // upper bound
                        setLtIncomeDonor(templateIndividuals.get(ins - 1));
                    } else if (zTarget - sortedZ[ins -1] < sortedZ[ins] - zTarget) {
                        // choose lower bound
                        setLtIncomeDonor(templateIndividuals.get(ins - 1));
                    } else {
                        // choose upper bound
                        setLtIncomeDonor(templateIndividuals.get(ins));
                    }
                }
            }
            else {

                setLtIncomeDonor(templateIndividuals.get(0));
            }
       }
    }

    public double getGMEquivalisedIncome(int ageH, int yearH) {

        Double val = Parameters.getEquivalisedIncome(gender, ageH, yearH);
        if (!Parameters.isFinite(val)) {
            // outside observed range, use regression model

            RegressionProxy proxy = new RegressionProxy(ageH, yearH);
            if (gender.toBoolean()) {
                val = Math.exp(Parameters.getRegLifetimeIncome1a().getScore(proxy, RegressionProxy.Variables.class));
            }
            else {
                val = Math.exp(Parameters.getRegLifetimeIncome1b().getScore(proxy, RegressionProxy.Variables.class));
            }
        }
        return val;
    }

    public double getImputedIncome(int ageH) {

        int yearH = year - age + ageH;
        double gmIncome = getGMEquivalisedIncome(ageH, yearH);
        if (ltIncomeDonor != null)
            return Math.exp(ltIncomeDonor.getNormIncome(ageH).getZValue()) * gmIncome;
        else
            return gmIncome;
    }

    public void projectAgeAtDeath() {

        boolean found = false;
        int maxAge = ltIncomeDonor.getNormIncomes().size() - 1;
        for (int ageHere = age+1; ageHere < maxAge; ageHere++) {
            int yearHere = year - age + ageHere;
            double mortality = Parameters.getMortalityProbability(gender, ageHere, yearHere);
            if (generator.nextDouble() < mortality) {
                found = true;
                ageAtDeath = ageHere;
                break;
            }
        }
        if (!found)
            ageAtDeath = maxAge;
    }
}
