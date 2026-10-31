package simpaths.model.lifetime_incomes;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import simpaths.data.Parameters;
import java.io.*;
import java.util.*;
import java.util.random.RandomGenerator;
import org.apache.logging.log4j.Logger;
import simpaths.model.Person;
import simpaths.model.enums.Gender;

public class ManagerLifetimeIncomes {


    public static void generateNormalisedProfiles(Logger log, Integer capAge, Integer endAge, Integer simCohortSize, List<TemplateIndividual> templateIndividuals, boolean writeToCSV, long seed, String outputDir) {

        log.info("Initialising lifetime income projections");
        System.out.println("Initialising lifetime income projections");


        /*
        SampleIndividual ind = new SampleIndividual();
        ind.setGender(Gender.Male);
        double income = ind.getGMEquivalisedIncome(80, 2043);
        income = ind.getGMEquivalisedIncome(85, 2050);
        */


        // start projecting lifetime incomes
        RandomGenerator generator = new Random(seed);

        // load initialisation values
        List<InitialisationObservation> initialisationObservations;
        try {
            String path = Parameters.getInputDirectory() + "lifetime_incomes" + File.separator + "initialisation_observations.csv";
            FileReader fileReader = new FileReader(path);
            initialisationObservations = new CsvToBeanBuilder(fileReader).withType(InitialisationObservation.class).build().parse();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem reading initialisation observations");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
        int initSampleSize = initialisationObservations.size();

        // load white noise estimates
        List<WhiteNoiseEstimate> whiteNoiseEstimatesAll;
        try {
            String path = Parameters.getInputDirectory() + "lifetime_incomes" + File.separator + "whitenoise_estimates.csv";
            FileReader fileReader = new FileReader(path);
            whiteNoiseEstimatesAll = new CsvToBeanBuilder(fileReader).withType(WhiteNoiseEstimate.class).build().parse();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem reading initialisation observations");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }

        // initialise template donors for all age/gender cohorts
        for (int ii=0; ii < simCohortSize; ii++) {

            InitialisationObservation obs = initialisationObservations.get(generator.nextInt(initSampleSize));
            TemplateIndividual individual = new TemplateIndividual(ii, obs, generator.nextLong());
            templateIndividuals.add(individual);
        }

        // project lifetimes for template donors (normalised incomes)
        for (int age = 1; age <= endAge; age++) {

            final int currentCapAge = Math.min(age, capAge);
            final int currentUncapAge = age;
            List<WhiteNoiseEstimate> whiteNoiseEstimates = whiteNoiseEstimatesAll.stream().filter(w -> w.getAge() == currentCapAge).toList();
            int noiseSampleSize = whiteNoiseEstimates.size();
            double adjFactor = AdjustmentFactors.getWhiteNoise(currentCapAge);

            templateIndividuals.parallelStream().forEach(individual -> {
            //for (TemplateIndividual individual : templateIndividuals) {

                WhiteNoiseEstimate noise = whiteNoiseEstimates.get(individual.getNextInt(noiseSampleSize));
                double etaAdj = noise.getEtaHat() * adjFactor;
                individual.projectNormIncome(currentUncapAge, etaAdj);
            //}
            });
        }

        // write template profiles to csv
        boolean writeToCSV2 = false;
        if (writeToCSV2) {

            List<NormalisedIncome> incomes = new ArrayList<>();
            for (TemplateIndividual individual : templateIndividuals) {

                incomes.addAll(individual.getNormIncomes());
            }
            try {
                String path = outputDir + File.separator + "normalised_incomes.csv";
                Writer writer = new FileWriter(path);
                StatefulBeanToCsv beanToCsv = new StatefulBeanToCsvBuilder(writer).build();
                beanToCsv.write(incomes);
                writer.close();
            } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
                // a bean had an empty required field or a value that couldn't be converted
                System.err.println("Could not write CSV: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Problem reading initialisation observations");
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }

        // finalise
        log.info("Completed lifetime income projections");
    }

    public static void imputeProfiles(Logger log, Set<Person> personSet, List<TemplateIndividual> templateIndividuals, boolean writeToCSV, String outputDir) {

        log.info("Imputing lifetime income profiles for sample");
        System.out.println("Imputing lifetime income profiles for sample");

        int endAge = templateIndividuals.getFirst().getNormIncomes().size() - 1;

        // loop through each age in order
        for (int age = 0; age <= endAge; age++) {

            // sort template individuals by norm income at current age
            int ageHere = age;
            templateIndividuals.sort(Comparator.comparingDouble(individual -> individual.getNormIncome(ageHere).getZValue()));
            final double[] sortedZ = templateIndividuals.stream().mapToDouble(individual -> individual.getNormIncome(ageHere).getZValue()).toArray();
            boolean agePlus = (age==endAge);

            // loop through person set
            personSet.parallelStream().forEach(person -> {
            //for (Person person : personSet) {

                person.matchLifetimeIncome(ageHere, agePlus, sortedZ, templateIndividuals);
            //}
            });
        }

        // write template profiles to csv
        boolean writeToCSV2 = true;
        if (writeToCSV) {

            List<ImputedIncome> incomes = new ArrayList<>();
            for (Person person : personSet) {

                for (int age = 0; age <= endAge; age++) {

                    ImputedIncome income = new ImputedIncome(person, age);
                    incomes.add(income);
                }
            }
            try {
                String path = outputDir + File.separator + "imputed_incomes.csv";
                Writer writer = new FileWriter(path);
                StatefulBeanToCsv beanToCsv = new StatefulBeanToCsvBuilder(writer).build();
                beanToCsv.write(incomes);
                writer.close();
            } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
                // a bean had an empty required field or a value that couldn't be converted
                System.err.println("Could not write CSV: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Problem writing imputed incomes");
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }

        // finalise
        log.info("Completed imputing lifetime incomes");
    }

    public static void imputeProfiles2(Logger log, Integer capAge, long seed, List<TemplateIndividual> templateIndividuals, boolean writeToCSV, String outputDir) {

        log.info("Imputing lifetime income profiles for sample");
        System.out.println("Imputing lifetime income profiles for sample");

        // define year
        int year = 2023;

        // load survey sample
        List<SampleIndividual> sampleIndividuals;
        try {
            String path = Parameters.getInputDirectory() + "lifetime_incomes" + File.separator + "/sample/" + year + ".csv";
            FileReader fileReader = new FileReader(path);
            sampleIndividuals = new CsvToBeanBuilder(fileReader).withType(SampleIndividual.class).build().parse();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem reading initialisation start sample for analysis");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
        long seedHere = seed;
        for (SampleIndividual individual : sampleIndividuals) {

            seedHere += 1;
            individual.setGender(individual.getMale() == 1 ? Gender.Male : Gender.Female);
            individual.setGenerator(seedHere);
        }

        // match templates to sample popualtion
        // loop through each age in order
        for (int age = 0; age <= capAge; age++) {

            // sort template individuals by norm income at current age
            int ageHere = age;
            templateIndividuals.sort(Comparator.comparingDouble(individual -> individual.getNormIncome(ageHere).getZValue()));
            final double[] sortedZ = templateIndividuals.stream().mapToDouble(individual -> individual.getNormIncome(ageHere).getZValue()).toArray();
            boolean agePlus = (age>=capAge);

            // loop through sample
            sampleIndividuals.parallelStream().forEach(individual -> {
                //for (Person person : personSet) {

                individual.matchLifetimeIncome(ageHere, agePlus, sortedZ, templateIndividuals);
                //}
            });
        }

        // identify end age of template population
        int endAge = templateIndividuals.getFirst().getNormIncomes().size() - 1;

        // write matched income profiles to csv
        if (writeToCSV) {

            List<ImputedIncome> incomes = new ArrayList<>();
            for (SampleIndividual individual : sampleIndividuals) {

                individual.projectAgeAtDeath();
                for (int age = 0; age <= endAge; age++) {

                    ImputedIncome income = new ImputedIncome(individual, age);
                    incomes.add(income);
                }
            }
            try {
                String path = outputDir + File.separator + "imputed_incomes2.csv";
                Writer writer = new FileWriter(path);
                StatefulBeanToCsv beanToCsv = new StatefulBeanToCsvBuilder(writer).build();
                beanToCsv.write(incomes);
                writer.close();
            } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
                // a bean had an empty required field or a value that couldn't be converted
                System.err.println("Could not write CSV: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Problem writing imputed incomes");
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }

        // finalise
        log.info("Completed imputing lifetime incomes");
    }
}
