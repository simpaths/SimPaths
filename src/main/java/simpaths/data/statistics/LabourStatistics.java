package simpaths.data.statistics;

import java.util.Collection;
import java.util.function.Supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import microsim.FilteredCollection;
import microsim.data.db.PanelEntityKey;
import microsim.dev.statistics.CrossSection;
import microsim.dev.statistics.Stats;
import simpaths.data.filters.Filters;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Les_c4;
import simpaths.model.Person;

@Entity
public class LabourStatistics {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    @Column(name= "EmpToNotEmp")
    private double labEmpToNotEmpShare;         // Proportion of employed people becoming unemployed

    @Column(name= "NotEmpToEmp")
    private double labNotEmpToEmpShare;         // Proportion of unemployed people becoming employed

    @Column(name = "PropEmployed")
    private double labEmpShare;

    @Column(name = "PropUnemployed")
    private double labUnempShare;

    //average labour status by age band
    @Column(name = "work_fulltime_18_29")
    private double labWorkFullTime18to29Share;

    @Column(name = "work_fulltime_30_54")
    private double labWorkFullTime30to54Share;

    @Column(name = "work_fulltime_55_74")
    private double labWorkFullTime55to74Share;

    @Column(name = "work_parttime_18_29")
    private double labWorkPartTime18to29Share;

    @Column(name = "work_parttime_30_54")
    private double labWorkPartTime30to54Share;

    @Column(name = "work_parttime_55_74")
    private double labWorkPartTime55to74Share;


    public double getEmpToNotEmp() {
        return labEmpToNotEmpShare;
    }

    public void setEmpToNotEmp(double empToNotEmp) {
        labEmpToNotEmpShare = empToNotEmp;
    }

    public double getNotEmpToEmp() {
        return labNotEmpToEmpShare;
    }

    public void setNotEmpToEmp(double notEmpToEmp) {
        labNotEmpToEmpShare = notEmpToEmp;
    }

    public double getPropEmployed() {
        return labEmpShare;
    }

    public void setPropEmployed(double propEmployed) {
        labEmpShare = propEmployed;
    }

    public double getPropUnemployed() {
        return labUnempShare;
    }

    public void setPropUnemployed(double propUnemployed) {
        labUnempShare = propUnemployed;
    }

    public double getWorkFulltime18to29() {
        return labWorkFullTime18to29Share;
    }

    public void setWorkFulltime18to29(double labWorkFullTime18to29Share) {
        this.labWorkFullTime18to29Share = labWorkFullTime18to29Share;
    }

    public double getWorkFulltime30to54() {
        return labWorkFullTime30to54Share;
    }

    public void setWorkFulltime30to54(double labWorkFullTime30to54Share) {
        this.labWorkFullTime30to54Share = labWorkFullTime30to54Share;
    }

    public double getWorkFulltime55to74() {
        return labWorkFullTime55to74Share;
    }

    public void setWorkFulltime55to74(double labWorkFullTime55to74Share) {
        this.labWorkFullTime55to74Share = labWorkFullTime55to74Share;
    }

    public double getWorkParttime18to29() {
        return labWorkPartTime18to29Share;
    }

    public void setWorkParttime18to29(double labWorkPartTime18to29Share) {
        this.labWorkPartTime18to29Share = labWorkPartTime18to29Share;
    }

    public double getWorkParttime30to54() {
        return labWorkPartTime30to54Share;
    }

    public void setWorkParttime30to54(double labWorkPartTime30to54Share) {
        this.labWorkPartTime30to54Share = labWorkPartTime30to54Share;
    }

    public double getWorkParttime55to74() {
        return labWorkPartTime55to74Share;
    }

    public void setWorkParttime55to74(double labWorkPartTime55to74Share) {
        this.labWorkPartTime55to74Share = labWorkPartTime55to74Share;
    }

    /// Update the statistics with an arbitrary [Supplier].
    /// This is intended for testing.
    void updateWithSupplier(Supplier<Collection<Person>> supplier, AgeBandAggregates agg) {
        var histEmployed = new FilteredCollection<>(supplier,
                Filters.employmentHistory(Les_c4.EmployedOrSelfEmployed));
        var histNotEmployed = new FilteredCollection<>(supplier,
                Filters.employmentHistory(Les_c4.NotEmployed));

        // Entering employment transition rate
        var personsNotEmpToEmp = new CrossSection<>(histNotEmployed, Person::getEmployed);
        // Entering not employed transition rate
        var personsEmpToNotEmp = new CrossSection<>(histEmployed, Person::getNonwork);

        var isNotEmpToEmp = new Stats(personsNotEmpToEmp.get());
        this.setNotEmpToEmp(isNotEmpToEmp.mean());

        var isEmpToNotEmp = new Stats(personsEmpToNotEmp.get());
        this.setEmpToNotEmp(isEmpToNotEmp.mean());

        // Employment and non-employment, working age adults 16-64
        var from16to64 = new FilteredCollection<>(supplier, Filters.ageRange(16, 64)).once();
        var personsEmployed = new CrossSection<>(from16to64, Person::getEmployed);
        var personsNotEmployed = new CrossSection<>(from16to64, Person::getNonwork);

        var isEmployed = new Stats(personsEmployed.get());
        var isNotEmployed = new Stats(personsNotEmployed.get());
        setPropEmployed(isEmployed.mean());
        setPropUnemployed(isNotEmployed.mean());

        // labour status by age band
        setWorkFulltime18to29(agg.workFT[0]);
        setWorkFulltime30to54(agg.workFT[1]);
        setWorkFulltime55to74(agg.workFT[2]);

        setWorkParttime18to29(agg.workPT[0]);
        setWorkParttime30to54(agg.workPT[1]);
        setWorkParttime55to74(agg.workPT[2]);
    }

    public void update(SimPathsModel model, AgeBandAggregates agg) {
        this.updateWithSupplier(model::getPersons, agg);
    }
}
