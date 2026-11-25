package simpaths.data;

import microsim.data.MultiKeyCoefficientMap;
import microsim.data.excel.ExcelAssistant;
import org.apache.commons.collections4.keyvalue.MultiKey;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Occupancy;

import java.util.HashMap;
import java.util.Map;

/**
 * UKHLS distribution of observed weekly hours worked (lhw), by sample type and gender. Labour categories with
 * DistributionOption.OBSERVED_LHW use it to draw realised hours within their band, so that each hour is drawn
 * with its observed share within the band.
 *
 * Loaded from input/ukhls_lhw_distribution.xlsx (see Parameters.USE_UKHLS_LHW_DISTRIBUTION), which has one sheet
 * per sample type and gender, named e.g. "Couple_female", listing the frequency of each observed weekly hours value.
 */
public class UkhlsHoursDistribution {

    public static final String FILE_NAME = "ukhls_lhw_distribution.xlsx";

    /** Sample types, mirroring the samples of the labour-supply estimates in BenefitUnit; each names a sheet prefix. */
    public enum Sample {
        Single_indep,   // single, not an adult child
        Couple,         // couple in which both partners are flexible in labour supply
        Single_dep,     // couple in which the partner is not flexible in labour supply
        Adult_child     // single adult child
    }

    // cumulative frequency F(h) = f(0) + ... + f(h), indexed by weekly hours h = 0..MAX_LABOUR_HOURS_IN_WEEK, keyed by sheet name
    private final Map<String, double[]> cumulativeBySheet;

    private UkhlsHoursDistribution(Map<String, double[]> cumulativeBySheet) {
        this.cumulativeBySheet = cumulativeBySheet;
    }

    /** Loads the distribution of every sample type and gender from the given Excel file. */
    public static UkhlsHoursDistribution load(String file) {
        Map<String, double[]> cumulativeBySheet = new HashMap<>();
        for (Sample sample : Sample.values()) {
            for (Gender gender : Gender.values()) {
                String sheet = sheetName(sample, gender);
                cumulativeBySheet.put(sheet, loadCumulative(file, sheet));
            }
        }
        return new UkhlsHoursDistribution(cumulativeBySheet);
    }

    /** Reads the frequencies f(h) of one sheet, then turns them in place into the cumulative distribution F(h). */
    private static double[] loadCumulative(String file, String sheet) {
        MultiKeyCoefficientMap map = ExcelAssistant.loadCoefficientMap(file, sheet, 1);
        double[] cumFreq = new double[Parameters.MAX_LABOUR_HOURS_IN_WEEK + 1];
        for (Object entryObject : map.entrySet()) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
            int hours = (int) Math.round(Double.parseDouble(((MultiKey<?>) entry.getKey()).getKey(0).toString()));
            if (hours >= 0 && hours < cumFreq.length)
                cumFreq[hours] += ((Number) entry.getValue()).doubleValue();
        }
        for (int h = 1; h < cumFreq.length; h++)
            cumFreq[h] += cumFreq[h - 1];
        return cumFreq;
    }

    public static String sheetName(Sample sample, Gender gender) {
        return sample.name() + "_" + gender.name().toLowerCase();
    }

    /** Sheet for the person's current sample type and gender. */
    public static String sheetFor(Person person) {
        return sheetName(sampleOf(person), person.getDemMaleFlag());
    }

    private static Sample sampleOf(Person person) {
        BenefitUnit benefitUnit = person.getBenefitUnit();
        if (benefitUnit != null && Occupancy.Couple.equals(benefitUnit.getOccupancy())) {
            Person partner = Gender.Female.equals(person.getDemMaleFlag()) ? benefitUnit.getMale() : benefitUnit.getFemale();
            return (partner != null && partner.atRiskOfWork()) ? Sample.Couple : Sample.Single_dep;
        }
        return (person.getAdultChildFlag() == 1) ? Sample.Adult_child : Sample.Single_indep;
    }

    /**
     * Draws realised weekly hours in the band [min, max] from the distribution of the given sheet, so that each
     * hour is drawn with probability equal to its observed share within the band (hours never observed are never
     * drawn). u is the person's uniform draw. Returns -1 if the band has no observed frequency.
     */
    public int draw(String sheet, int min, int max, double u) {
        double[] cumFreq = cumulativeBySheet.get(sheet);
        if (cumFreq == null)
            throw new IllegalArgumentException("No UKHLS hours distribution for sheet " + sheet);

        int bandLow = Math.max(min, 1);
        int bandHigh = Math.min(max, cumFreq.length - 1);
        if (bandHigh < bandLow) return -1;

        double cumBelowBand = cumFreq[bandLow - 1];
        double bandShare = cumFreq[bandHigh] - cumBelowBand;
        if (!(bandShare > 0.0)) return -1;

        // Map the uniform draw u onto the cumulative probability range of the band and select the first hour whose
        // cumulative probability exceeds this target, preserving the relative UKHLS frequencies within the band.
        double cumTarget = cumBelowBand + u * bandShare;
        for (int h = bandLow; h <= bandHigh; h++)
            if (cumFreq[h] > cumTarget) return h;

        // Only reached when u is (close to) 1: return the highest observed hour in the band.
        int h = bandHigh;
        while (h > bandLow && cumFreq[h - 1] >= cumFreq[h]) h--;
        return h;
    }
}
