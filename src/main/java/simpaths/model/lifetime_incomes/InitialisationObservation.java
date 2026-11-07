package simpaths.model.lifetime_incomes;

import com.opencsv.bean.CsvBindByName;

/**
 * Class used to store data for initialising fixed effects and
 * z values for the lifetime income model.
 */
public class InitialisationObservation {

    @CsvBindByName private double pidp;             // person id
    @CsvBindByName private double z;                // z value (normalised income)
    @CsvBindByName private double fixedEffect;      // estimated fixed effects

    // Required by CsvToObjectLoader (reflection-based instantiation).
    public InitialisationObservation() {
    }

    // Getters and setters.
    public double getPidp() {return pidp; }
    public void setPidp(double pidp) { this.pidp = pidp; }
    public double getFixedEffect() {
        return fixedEffect;
    }
    public void setFixedEffect(double fixedEffect) {
        this.fixedEffect = fixedEffect;
    }
    public double getZ() {
        return z;
    }
    public void setZ(double z) {
        this.z = z;
    }
}
