package simpaths.model.lifetime_incomes;

import jakarta.persistence.*;
import microsim.statistics.IDoubleSource;
import simpaths.data.Parameters;
import simpaths.model.Person;
import simpaths.model.enums.Gender;
import simpaths.model.enums.TimeSeriesVariable;

import java.util.*;

import java.util.random.RandomGenerator;

public class TemplateIndividual {


    /**
     * ATTRIBUTES
     */
    private int id;                                                         // id defining template individual
    private double fixedEffect;                                             // fixed effect assigned to individual
    private List<NormalisedIncome> normIncomes = new ArrayList<>();         // list of normalised incomes
    RandomGenerator generator;                                              // random number generator


    /**
     * CONSTRUCTOR
     */
    public TemplateIndividual() {
    }

    public TemplateIndividual(int id, InitialisationObservation obs, long seed) {
        this.id = id;
        fixedEffect = obs.getFixedEffect() * AdjustmentFactors.fixedEffect;
        NormalisedIncome normIncome = new NormalisedIncome(this, 0, obs.getZ());
        normIncomes.add(normIncome);
        generator = new Random(seed);
    }

    public int getNextInt(int val) {
        return generator.nextInt(val);
    }

    public double getFixedEffect() {return fixedEffect;}

    public List<NormalisedIncome> getNormIncomes() {return normIncomes;}

    public NormalisedIncome getNormIncome(int age) {
        return normIncomes.get(age);
    }

    public void projectNormIncome(int age, double noise) {

        NormalisedIncome normIncome = new NormalisedIncome(this, age, noise);
        normIncomes.add(normIncome);
    }
}
