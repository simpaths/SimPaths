package simpaths.model.lifetime_incomes;

import simpaths.model.Person;

public class ImputedIncome {

    private long id;
    private int age;
    private int year;
    private int female;
    private int ageAtDeath;
    private double income;
    private Double observedIncome;

    public ImputedIncome() {}
    public ImputedIncome(Person person, int age) {
        this.id = person.getKey().getId();
        this.age = age;
        this.year = person.getYear() - person.getDemAge() + age;
        this.female = person.getDemMaleFlag().toBoolean() ? 0 : 1;
        this.income = person.getImputedIncome(age);
        if (person.getDemAge() == age) {

            this.observedIncome = person.getBenefitUnit().getHousehold().getEquivalisedDisposableIncomeYearly();
        }
        else {
            this.observedIncome = null;
        }
    }
    public ImputedIncome(SampleIndividual individual, int age) {
        this.id = individual.getId();
        this.age = age;
        this.year = individual.getYear() - individual.getAge() + age;
        this.female = individual.getGender().toBoolean() ? 0 : 1;
        this.ageAtDeath = individual.getAgeAtDeath();
        this.income = individual.getImputedIncome(age);
        if (individual.getAge() == age) {

            this.observedIncome = individual.getIncome();
        }
        else {
            this.observedIncome = null;
        }
    }

    public Double getObservedIncome() {
        return observedIncome;
    }

    public void setObservedIncome(Double observedIncome) {
        this.observedIncome = observedIncome;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getIncome() {
        return income;
    }

    public void setIncome(double income) {
        this.income = income;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getFemale() {
        return female;
    }

    public void setFemmale(int female) {
        this.female = female;
    }

    public int getAgeAtDeath() {return ageAtDeath;}

    public void setAgeAtDeath(int ageAtDeath) {this.ageAtDeath = ageAtDeath;}
}
