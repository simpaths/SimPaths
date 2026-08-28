package simpaths.data.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import microsim.data.db.PanelEntityKey;
import simpaths.data.Parameters;
import simpaths.data.filters.Filters;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.*;

/**
 *
 * CLASS TO REPORT ALIGNMENT ADJUSTMENT FACTORS AND SIMULATED VS TARGET SHARES
 *
 * Covers: partnership, fertility, in-school, utility adjustment factors, and
 * employment shares by occupancy type. Disability and retirement are excluded.
 *
 */
@Entity
public class AlignmentStatistics {

    @Id
    private PanelEntityKey key = new PanelEntityKey(1L);

    // ------------------------------------------------------------------
    // Partnership
    // ------------------------------------------------------------------
    @Column(name = "align_partner_adj")
    private double alignPartnerAdj;

    @Column(name = "align_partner_sim_share")
    private double alignPartnerSimShare;

    @Column(name = "align_partner_tgt_share")
    private double alignPartnerTgtShare;

    // ------------------------------------------------------------------
    // Fertility
    // ------------------------------------------------------------------
    @Column(name = "align_fert_adj")
    private double alignFertAdj;

    @Column(name = "align_fert_rate_sim")
    private double alignFertRateSim;

    @Column(name = "align_fert_rate_tgt")
    private double alignFertRateTgt;

    // ------------------------------------------------------------------
    // In-school
    // ------------------------------------------------------------------
    @Column(name = "align_in_school_adj")
    private double alignInSchoolAdj;

    @Column(name = "align_in_school_sim_share")
    private double alignInSchoolSimShare;

    @Column(name = "align_in_school_tgt_share")
    private double alignInSchoolTgtShare;

    // ------------------------------------------------------------------
    // Utility adjustment factors (one per occupancy type)
    // ------------------------------------------------------------------
    @Column(name = "align_util_adj_single_m")
    private double alignUtilAdjSingleM;

    @Column(name = "align_util_adj_ac_m")
    private double alignUtilAdjACM;

    @Column(name = "align_util_adj_single_f")
    private double alignUtilAdjSingleF;

    @Column(name = "align_util_adj_ac_f")
    private double alignUtilAdjACF;

    @Column(name = "align_util_adj_couple")
    private double alignUtilAdjCouple;

    @Column(name = "align_util_adj_m_with_dep")
    private double alignUtilAdjMWithDep;

    @Column(name = "align_util_adj_f_with_dep")
    private double alignUtilAdjFWithDep;

    // ------------------------------------------------------------------
    // Employment shares — simulated
    // ------------------------------------------------------------------
    @Column(name = "align_emp_sim_single_m_share")
    private double alignEmpSimSingleMShare;

    @Column(name = "align_emp_sim_single_f_share")
    private double alignEmpSimSingleFShare;

    @Column(name = "align_emp_sim_ac_m_share")
    private double alignEmpSimACMShare;

    @Column(name = "align_emp_sim_ac_f_share")
    private double alignEmpSimACFShare;

    @Column(name = "align_emp_sim_couples_share")
    private double alignEmpSimCouplesShare;

    @Column(name = "align_emp_sim_m_with_dep_share")
    private double alignEmpSimMWithDepShare;

    @Column(name = "align_emp_sim_f_with_dep_share")
    private double alignEmpSimFWithDepShare;

    // ------------------------------------------------------------------
    // Employment shares — target
    // ------------------------------------------------------------------
    @Column(name = "align_emp_tgt_single_m_share")
    private double alignEmpTgtSingleMShare;

    @Column(name = "align_emp_tgt_single_f_share")
    private double alignEmpTgtSingleFShare;

    @Column(name = "align_emp_tgt_ac_m_share")
    private double alignEmpTgtACMShare;

    @Column(name = "align_emp_tgt_ac_f_share")
    private double alignEmpTgtACFShare;

    @Column(name = "align_emp_tgt_couples_share")
    private double alignEmpTgtCouplesShare;

    @Column(name = "align_emp_tgt_m_with_dep_share")
    private double alignEmpTgtMWithDepShare;

    @Column(name = "align_emp_tgt_f_with_dep_share")
    private double alignEmpTgtFWithDepShare;


    // ------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------

    public double getAlignPartnerAdj() { return alignPartnerAdj; }
    public void setAlignPartnerAdj(double v) { alignPartnerAdj = v; }

    public double getAlignPartnerSimShare() { return alignPartnerSimShare; }
    public void setAlignPartnerSimShare(double v) { alignPartnerSimShare = v; }

    public double getAlignPartnerTgtShare() { return alignPartnerTgtShare; }
    public void setAlignPartnerTgtShare(double v) { alignPartnerTgtShare = v; }

    public double getAlignFertAdj() { return alignFertAdj; }
    public void setAlignFertAdj(double v) { alignFertAdj = v; }

    public double getAlignFertRateSim() { return alignFertRateSim; }
    public void setAlignFertRateSim(double v) { alignFertRateSim = v; }

    public double getAlignFertRateTgt() { return alignFertRateTgt; }
    public void setAlignFertRateTgt(double v) { alignFertRateTgt = v; }

    public double getAlignInSchoolAdj() { return alignInSchoolAdj; }
    public void setAlignInSchoolAdj(double v) { alignInSchoolAdj = v; }

    public double getAlignInSchoolSimShare() { return alignInSchoolSimShare; }
    public void setAlignInSchoolSimShare(double v) { alignInSchoolSimShare = v; }

    public double getAlignInSchoolTgtShare() { return alignInSchoolTgtShare; }
    public void setAlignInSchoolTgtShare(double v) { alignInSchoolTgtShare = v; }

    public double getAlignUtilAdjSingleM() { return alignUtilAdjSingleM; }
    public void setAlignUtilAdjSingleM(double v) { alignUtilAdjSingleM = v; }

    public double getAlignUtilAdjACM() { return alignUtilAdjACM; }
    public void setAlignUtilAdjACM(double v) { alignUtilAdjACM = v; }

    public double getAlignUtilAdjSingleF() { return alignUtilAdjSingleF; }
    public void setAlignUtilAdjSingleF(double v) { alignUtilAdjSingleF = v; }

    public double getAlignUtilAdjACF() { return alignUtilAdjACF; }
    public void setAlignUtilAdjACF(double v) { alignUtilAdjACF = v; }

    public double getAlignUtilAdjCouple() { return alignUtilAdjCouple; }
    public void setAlignUtilAdjCouple(double v) { alignUtilAdjCouple = v; }

    public double getAlignUtilAdjMWithDep() { return alignUtilAdjMWithDep; }
    public void setAlignUtilAdjMWithDep(double v) { alignUtilAdjMWithDep = v; }

    public double getAlignUtilAdjFWithDep() { return alignUtilAdjFWithDep; }
    public void setAlignUtilAdjFWithDep(double v) { alignUtilAdjFWithDep = v; }

    public double getAlignEmpSimSingleMShare() { return alignEmpSimSingleMShare; }
    public void setAlignEmpSimSingleMShare(double v) { alignEmpSimSingleMShare = v; }

    public double getAlignEmpSimSingleFShare() { return alignEmpSimSingleFShare; }
    public void setAlignEmpSimSingleFShare(double v) { alignEmpSimSingleFShare = v; }

    public double getAlignEmpSimACMShare() { return alignEmpSimACMShare; }
    public void setAlignEmpSimACMShare(double v) { alignEmpSimACMShare = v; }

    public double getAlignEmpSimACFShare() { return alignEmpSimACFShare; }
    public void setAlignEmpSimACFShare(double v) { alignEmpSimACFShare = v; }

    public double getAlignEmpSimCouplesShare() { return alignEmpSimCouplesShare; }
    public void setAlignEmpSimCouplesShare(double v) { alignEmpSimCouplesShare = v; }

    public double getAlignEmpSimMWithDepShare() { return alignEmpSimMWithDepShare; }
    public void setAlignEmpSimMWithDepShare(double v) { alignEmpSimMWithDepShare = v; }

    public double getAlignEmpSimFWithDepShare() { return alignEmpSimFWithDepShare; }
    public void setAlignEmpSimFWithDepShare(double v) { alignEmpSimFWithDepShare = v; }

    public double getAlignEmpTgtSingleMShare() { return alignEmpTgtSingleMShare; }
    public void setAlignEmpTgtSingleMShare(double v) { alignEmpTgtSingleMShare = v; }

    public double getAlignEmpTgtSingleFShare() { return alignEmpTgtSingleFShare; }
    public void setAlignEmpTgtSingleFShare(double v) { alignEmpTgtSingleFShare = v; }

    public double getAlignEmpTgtACMShare() { return alignEmpTgtACMShare; }
    public void setAlignEmpTgtACMShare(double v) { alignEmpTgtACMShare = v; }

    public double getAlignEmpTgtACFShare() { return alignEmpTgtACFShare; }
    public void setAlignEmpTgtACFShare(double v) { alignEmpTgtACFShare = v; }

    public double getAlignEmpTgtCouplesShare() { return alignEmpTgtCouplesShare; }
    public void setAlignEmpTgtCouplesShare(double v) { alignEmpTgtCouplesShare = v; }

    public double getAlignEmpTgtMWithDepShare() { return alignEmpTgtMWithDepShare; }
    public void setAlignEmpTgtMWithDepShare(double v) { alignEmpTgtMWithDepShare = v; }

    public double getAlignEmpTgtFWithDepShare() { return alignEmpTgtFWithDepShare; }
    public void setAlignEmpTgtFWithDepShare(double v) { alignEmpTgtFWithDepShare = v; }


    // ------------------------------------------------------------------
    // update()
    // ------------------------------------------------------------------

    public void update(SimPathsModel model) {

        int year = model.getYear() - 1;  // year just simulated (consistent with annual collector exports)

        // --- Partnership ---
        setAlignPartnerAdj(
                Parameters.getTimeSeriesValue(year, TimeSeriesVariable.PartnershipAdjustment)
                + model.getPartnershipAdjustment(year));
        long numPersonsCohabEligible = model.getPersons().stream()
                .filter(p -> p.getDemAge() >= Parameters.MIN_AGE_COHABITATION)
                .count();
        long numPersonsPartnered = model.getPersons().stream()
                .filter(p -> Dcpst.Partnered.equals(p.getDemPartnerStatus()))
                .count();
        setAlignPartnerSimShare(numPersonsCohabEligible > 0
                ? (double) numPersonsPartnered / numPersonsCohabEligible : 0.0);
        setAlignPartnerTgtShare(Parameters.getTargetShare(year, TargetShares.Partnership));

        // --- Fertility ---
        setAlignFertAdj(
                Parameters.getTimeSeriesValue(year, TimeSeriesVariable.FertilityAdjustment)
                + model.getFertilityAdjustment(year));
        long numFertile = model.getPersons().stream()
                .filter(Filters.fertile())
                .count();
        long numBirths = model.getPersons().stream()
                .filter(p -> p.getDemAge() < 1)
                .count();
        setAlignFertRateSim(numFertile > 0 ? (double) numBirths / numFertile : 0.0);
        setAlignFertRateTgt(Parameters.getFertilityRateByYear(year));

        // --- In-school ---
        setAlignInSchoolAdj(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.InSchoolAdjustment));
        long numStudents = model.getPersons().stream()
                .filter(p -> p.getDemAge() >= Parameters.MIN_AGE_TO_LEAVE_EDUCATION
                        && p.getDemAge() <= Parameters.MAX_AGE_TO_STAY_IN_CONTINUOUS_EDUCATION
                        && !p.isToLeaveSchool()
                        && Les_c4.Student.equals(p.getLabC4()))
                .count();
        long numInSchoolAge = model.getPersons().stream()
                .filter(p -> p.getDemAge() >= Parameters.MIN_AGE_TO_LEAVE_EDUCATION
                        && p.getDemAge() <= Parameters.MAX_AGE_TO_STAY_IN_CONTINUOUS_EDUCATION
                        && p.getLabC4() != null)
                .count();
        setAlignInSchoolSimShare(numInSchoolAge > 0 ? (double) numStudents / numInSchoolAge : 0.0);
        setAlignInSchoolTgtShare(Parameters.getTargetShare(year, TargetShares.Students));

        // --- Utility adjustment factors ---
        setAlignUtilAdjSingleM(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.UtilityAdjustmentSingleMales));
        setAlignUtilAdjACM(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.UtilityAdjustmentACMales));
        setAlignUtilAdjSingleF(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.UtilityAdjustmentSingleFemales));
        setAlignUtilAdjACF(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.UtilityAdjustmentACFemales));
        setAlignUtilAdjCouple(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.UtilityAdjustmentCouples));
        setAlignUtilAdjMWithDep(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.UtilityAdjustmentSingleDepMen));
        setAlignUtilAdjFWithDep(Parameters.getTimeSeriesValue(year, TimeSeriesVariable.UtilityAdjustmentSingleDepWomen));

        // --- Employment shares ---
        double[] totSM  = new double[2];  // [count, fracEmployed sum]
        double[] totSF  = new double[2];
        double[] totACM = new double[2];
        double[] totACF = new double[2];
        double[] totCou = new double[2];
        double[] totSDM = new double[2];
        double[] totSDF = new double[2];

        for (BenefitUnit bu : model.getBenefitUnits()) {
            Occupancy occ = bu.getOccupancy();
            Person male   = bu.getMale();
            Person female = bu.getFemale();
            boolean maleAtRisk   = (male   != null) && male.atRiskOfWork();
            boolean femaleAtRisk = (female != null) && female.atRiskOfWork();
            int acFlag = 0;
            if (occ == Occupancy.Single_Male   && male   != null) acFlag = male.getAdultChildFlag();
            if (occ == Occupancy.Single_Female && female != null) acFlag = female.getAdultChildFlag();

            double frac = bu.fracEmployed();

            if (occ == Occupancy.Single_Male && acFlag != 1) {
                totSM[0]++; totSM[1] += frac;
            } else if (occ == Occupancy.Single_Male && acFlag == 1) {
                totACM[0]++; totACM[1] += frac;
            } else if (occ == Occupancy.Single_Female && acFlag != 1) {
                totSF[0]++; totSF[1] += frac;
            } else if (occ == Occupancy.Single_Female && acFlag == 1) {
                totACF[0]++; totACF[1] += frac;
            } else if (occ == Occupancy.Couple && maleAtRisk && femaleAtRisk) {
                totCou[0]++; totCou[1] += frac;
            } else if (occ == Occupancy.Couple && maleAtRisk && !femaleAtRisk) {
                totSDM[0]++; totSDM[1] += frac;
            } else if (occ == Occupancy.Couple && !maleAtRisk && femaleAtRisk) {
                totSDF[0]++; totSDF[1] += frac;
            }
        }

        setAlignEmpSimSingleMShare(  totSM[0]  > 0 ? totSM[1]  / totSM[0]  : 0.0);
        setAlignEmpSimSingleFShare(totSF[0]  > 0 ? totSF[1]  / totSF[0]  : 0.0);
        setAlignEmpSimACMShare(      totACM[0] > 0 ? totACM[1] / totACM[0] : 0.0);
        setAlignEmpSimACFShare(    totACF[0] > 0 ? totACF[1] / totACF[0] : 0.0);
        setAlignEmpSimCouplesShare(      totCou[0] > 0 ? totCou[1] / totCou[0] : 0.0);
        setAlignEmpSimMWithDepShare(  totSDM[0] > 0 ? totSDM[1] / totSDM[0] : 0.0);
        setAlignEmpSimFWithDepShare(totSDF[0] > 0 ? totSDF[1] / totSDF[0] : 0.0);

        setAlignEmpTgtSingleMShare(  Parameters.getTargetShare(year, TargetShares.EmploymentSingleMales));
        setAlignEmpTgtSingleFShare(Parameters.getTargetShare(year, TargetShares.EmploymentSingleFemales));
        setAlignEmpTgtACMShare(      Parameters.getTargetShare(year, TargetShares.EmploymentACMales));
        setAlignEmpTgtACFShare(    Parameters.getTargetShare(year, TargetShares.EmploymentACFemales));
        setAlignEmpTgtCouplesShare(      Parameters.getTargetShare(year, TargetShares.EmploymentCouples));
        setAlignEmpTgtMWithDepShare(  Parameters.getTargetShare(year, TargetShares.EmploymentSingleDepMales));
        setAlignEmpTgtFWithDepShare(Parameters.getTargetShare(year, TargetShares.EmploymentSingleDepFemales));
    }
}
