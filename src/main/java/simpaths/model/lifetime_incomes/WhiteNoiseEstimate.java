package simpaths.model.lifetime_incomes;

/**
 * Class used to store data for initialising fixed effects and
 * z values for the lifetime income model.
 */
public class WhiteNoiseEstimate {

    public double pidp;         // person id
    public double etaHat;       // estimated white noise
    public double age;          // observation age
    public double weight;       // survey weight

    // Required by CsvToObjectLoader (reflection-based instantiation).
    private WhiteNoiseEstimate() {
    }

    // Getters and setters.
    public double getPidp() {return pidp; }
    public void setPidp(double pidp) { this.pidp = pidp; }
    public double getEtaHat() { return etaHat; }
    public void setEtaHat(double etaHat) { this.etaHat = etaHat; }
    public double getAge() { return age; }
}
