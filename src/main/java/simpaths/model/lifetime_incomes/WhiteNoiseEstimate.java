package simpaths.model.lifetime_incomes;

import com.opencsv.bean.CsvBindByName;

/**
 * Class used to store data for initialising fixed effects and
 * z values for the lifetime income model.
 */
public class WhiteNoiseEstimate {

    @CsvBindByName private double pidp;         // person id
    @CsvBindByName private double etaHat;       // estimated white noise
    @CsvBindByName private int age;          // observation age
    @CsvBindByName private double weight;       // survey weight

    // Required by CsvToObjectLoader (reflection-based instantiation).
    public WhiteNoiseEstimate() {
    }

    // Getters and setters.
    public double getPidp() {return pidp; }
    public void setPidp(double pidp) { this.pidp = pidp; }
    public double getEtaHat() { return etaHat; }
    public void setEtaHat(double etaHat) { this.etaHat = etaHat; }
    public int getAge() { return age; }
}
