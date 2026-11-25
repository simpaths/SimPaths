package simpaths.model;

import simpaths.model.enums.Gender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.random.RandomGenerator;

final class NewbornHealthDonors {
    record Scores(double mcs, double pcs) {}

    private final EnumMap<Gender, List<Scores>> byGender = new EnumMap<>(Gender.class);

    NewbornHealthDonors(Collection<Person> population) {
        for (Gender gender : Gender.values()) byGender.put(gender, new ArrayList<>());
        for (Person person : population) {
            Scores scores = person.newbornHealthDonorScores();
            if (scores != null) byGender.get(person.getDemMaleFlag()).add(scores);
        }
    }

    Scores sample(Gender gender, RandomGenerator random) {
        List<Scores> donors = byGender.get(gender);
        if (donors == null || donors.isEmpty()) {
            throw new IllegalStateException("No valid starting-population under-16 MCS/PCS donors for sex " + gender);
        }
        return donors.get(random.nextInt(donors.size()));
    }
}