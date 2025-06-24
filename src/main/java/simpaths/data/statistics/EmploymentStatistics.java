package simpaths.data.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;

import jakarta.persistence.Transient;
import microsim.data.db.PanelEntityKey;
import microsim.statistics.CrossSection;
import microsim.statistics.IDoubleSource;
import microsim.statistics.functions.MeanArrayFunction;
import simpaths.data.Parameters;
import simpaths.data.filters.*;
import simpaths.experiment.SimPathsCollector;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Les_c4;
import simpaths.model.Person;

@Entity
public class EmploymentStatistics {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    @Column(name = "scenario")
    private String scenario = Parameters.scenario;

    @Column(name = "gender")
    private String gender;

    @Column(name = "agegroup")
    private String agegroup;

    @Column(name= "EmpToNotEmp")
    private double EmpToNotEmp;         // Proportion of employed people becoming unemployed

    @Column(name= "NotEmpToEmp")
    private double NotEmpToEmp;         // Proportion of unemployed people becoming employed

    @Column(name = "PropEmployed")
    private double PropEmployed;

    @Column(name = "PropUnemployed")
    private double PropUnemployed;

    @Column(name = "meanLabourHours")
    private double meanLabourHours;


    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setAgegroup(SimPathsCollector.AgeRange agegroup) {
        String agegroup_s = agegroup.toString();
        this.agegroup = agegroup_s;
    }


    public double getEmpToNotEmp() {
        return EmpToNotEmp;
    }

    public void setEmpToNotEmp(double empToNotEmp) {
        EmpToNotEmp = empToNotEmp;
    }

    public double getNotEmpToEmp() {
        return NotEmpToEmp;
    }

    public void setNotEmpToEmp(double notEmpToEmp) {
        NotEmpToEmp = notEmpToEmp;
    }

    public double getPropEmployed() {
        return PropEmployed;
    }

    public void setPropEmployed(double propEmployed) {
        PropEmployed = propEmployed;
    }

    public double getPropUnemployed() {
        return PropUnemployed;
    }

    public void setPropUnemployed(double propUnemployed) {
        PropUnemployed = propUnemployed;
    }

    public void setMeanLabourHours(double meanLabourHours) {
        this.meanLabourHours = meanLabourHours;
    }

    public void update(SimPathsModel model, String gender_s, SimPathsCollector.AgeRange ageRange) {

        AgeGenderCSfilter ageGenderCSfilter;
        EmploymentAgeGenderCSfilter employmentCSfilter;
        EmploymentHistoryFilter employmentHistoryEmployed;
        EmploymentHistoryFilter employmentHistoryUnemployed;

        if (gender_s.equals("Total")) {
            ageGenderCSfilter = new AgeGenderCSfilter(ageRange.lowerBound(), ageRange.upperBound());
            employmentCSfilter = new EmploymentAgeGenderCSfilter(Les_c4.EmployedOrSelfEmployed, ageRange.lowerBound(), ageRange.upperBound());

            employmentHistoryEmployed = new EmploymentHistoryFilter(Les_c4.EmployedOrSelfEmployed, ageRange.lowerBound(), ageRange.upperBound());
            employmentHistoryUnemployed = new EmploymentHistoryFilter(Les_c4.NotEmployed, ageRange.lowerBound(), ageRange.upperBound());
        } else {
            ageGenderCSfilter = new AgeGenderCSfilter(ageRange.lowerBound(), ageRange.upperBound(), Gender.valueOf(gender_s));
            employmentCSfilter = new EmploymentAgeGenderCSfilter(Les_c4.EmployedOrSelfEmployed, ageRange.lowerBound(), ageRange.upperBound(), Gender.valueOf(gender_s));

            employmentHistoryEmployed = new EmploymentHistoryFilter(Les_c4.EmployedOrSelfEmployed, ageRange.lowerBound(), ageRange.upperBound(), Gender.valueOf(gender_s));
            employmentHistoryUnemployed = new EmploymentHistoryFilter(Les_c4.NotEmployed, ageRange.lowerBound(), ageRange.upperBound(), Gender.valueOf(gender_s));
        }

        // set gender
        setGender(gender_s);

        // set agegroup
        setAgegroup(ageRange);



        // Entering employment transition rate
        CrossSection.Integer personsNotEmpToEmp = new CrossSection.Integer(model.getPersons(), Person.class, "getEmployed", true);
        personsNotEmpToEmp.setFilter(employmentHistoryUnemployed);
        // Entering not employed transition rate
        CrossSection.Integer personsEmpToNotEmp = new CrossSection.Integer(model.getPersons(), Person.class, "getNonwork", true);
        personsEmpToNotEmp.setFilter(employmentHistoryEmployed);


        MeanArrayFunction isNotEmpToEmp = new MeanArrayFunction(personsNotEmpToEmp);
        isNotEmpToEmp.applyFunction();
        setNotEmpToEmp(isNotEmpToEmp.getDoubleValue(IDoubleSource.Variables.Default));

        MeanArrayFunction isEmpToNotEmp = new MeanArrayFunction(personsEmpToNotEmp);
        isEmpToNotEmp.applyFunction();
        setEmpToNotEmp(isEmpToNotEmp.getDoubleValue(IDoubleSource.Variables.Default));

        // Employed and unemployed in age-groups
        CrossSection.Integer personsEmployed = new CrossSection.Integer(model.getPersons(), Person.class, "getEmployed", true);
        CrossSection.Integer personsUnemployed = new CrossSection.Integer(model.getPersons(), Person.class, "getNonwork", true);

        personsEmployed.setFilter(ageGenderCSfilter);
        personsUnemployed.setFilter(ageGenderCSfilter);

        MeanArrayFunction isEmployed = new MeanArrayFunction(personsEmployed);
        isEmployed.applyFunction();
        setPropEmployed(isEmployed.getDoubleValue(IDoubleSource.Variables.Default));

        MeanArrayFunction isUnemployed = new MeanArrayFunction(personsUnemployed);
        isUnemployed.applyFunction();
        setPropUnemployed(isUnemployed.getDoubleValue(IDoubleSource.Variables.Default));

        // Mean hours worked amongst employed
        CrossSection.Double hoursWorked = new CrossSection.Double(model.getPersons(), Person.class, "getHoursWorkedWeekly", true);
        hoursWorked.setFilter(employmentCSfilter);

        MeanArrayFunction meanHoursWorked = new MeanArrayFunction(hoursWorked);
        meanHoursWorked.applyFunction();
        setMeanLabourHours(meanHoursWorked.getDoubleValue(IDoubleSource.Variables.Default));


    }
}
