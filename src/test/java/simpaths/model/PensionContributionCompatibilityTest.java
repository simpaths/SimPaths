package simpaths.model;

import jakarta.persistence.Persistence;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PensionContributionCompatibilityTest {

    @Test
    void renamedFieldsReadAndUpdateLegacyPopulationColumns() {
        var factory = Persistence.createEntityManagerFactory("starting-population", Map.of(
                "hibernate.connection.url", "jdbc:h2:mem:pension_names_" + UUID.randomUUID(),
                "hibernate.hbm2ddl.auto", "create-drop"));
        var em = factory.createEntityManager();
        try {
            // Reproduce a row from an existing input/processed population, using only legacy names.
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO Person (id, simulation_time, simulation_run, working_id, demAge, "
                            + "contRateOPEe, contRateOPEr, contRatePP) "
                            + "VALUES (101, 2019, 0, 0, 40, 0.04, 0.06, 0.10)")
                    .executeUpdate();
            em.getTransaction().commit();

            Person person = em.createQuery("SELECT p FROM Person p WHERE p.key.id = 101", Person.class)
                    .getSingleResult();
            assertEquals(0.04, person.getWealthPensOpEeContRate(), 1e-12);
            assertEquals(0.06, person.getWealthPensOpErContRate(), 1e-12);
            assertEquals(0.10, person.getWealthPensPpContRate(), 1e-12);

            em.getTransaction().begin();
            person.setWealthPensOpEeContRate(0.03);
            person.setWealthPensOpErContRate(0.05);
            person.setWealthPensPpContRate(0.08);
            em.getTransaction().commit();

            Object[] stored = (Object[]) em.createNativeQuery(
                    "SELECT contRateOPEe, contRateOPEr, contRatePP FROM Person WHERE id = 101")
                    .getSingleResult();
            assertEquals(0.03, ((Number) stored[0]).doubleValue(), 1e-12);
            assertEquals(0.05, ((Number) stored[1]).doubleValue(), 1e-12);
            assertEquals(0.08, ((Number) stored[2]).doubleValue(), 1e-12);
        } finally {
            em.close();
            factory.close();
        }
    }
}
