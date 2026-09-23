package simpaths.model.lifetime_incomes;

/**
 * Class used to store data for initialising fixed effects and
 * z values for the lifetime income model.
 */
public class InitialisationObservation {

    public double pidp;          // person id
    public double z;            // z value (normalised income)
    public double fixedEffect;  // estimated fixed effects

    // Required by CsvToObjectLoader (reflection-based instantiation).
    private InitialisationObservation() {
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
