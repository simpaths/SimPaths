package simpaths.model;

import jakarta.persistence.Column;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;
import simpaths.model.annotations.UpdateManager;
import simpaths.model.enums.Country;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.Labour;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmploymentHistoryTest {
    @Test
    void populationColumnIsSelectedAndMapped() throws Exception {
        assertTrue(Arrays.asList(Parameters.PERSON_VARIABLES_INITIAL).contains("labEmpNyear"));
        assertEquals("labEmpNyear", Person.class.getDeclaredField("labEmpNyear").getAnnotation(Column.class).name());
    }

    @Test
    void populationValueIsLoadedFromDatabase() {
        var factory = Persistence.createEntityManagerFactory("starting-population", Map.of(
                "hibernate.connection.url", "jdbc:h2:mem:employment_history_" + UUID.randomUUID(),
                "hibernate.hbm2ddl.auto", "create-drop"));
        var em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO Person (id, simulation_time, simulation_run, working_id, labEmpNyear) "
                    + "VALUES (101, 2019, 0, 0, 9)").executeUpdate();
            em.getTransaction().commit();
            Person person = em.createQuery("SELECT p FROM Person p WHERE p.key.id = 101", Person.class).getSingleResult();
            assertEquals(9, person.getLiwwh());
        } finally {
            em.close();
            factory.close();
        }
    }

    @Test
    void agingCountsOnlyLastYearsEmployment() throws Exception {
        Person person = new Person(1001L);
        person.setDemAge(40);
        person.setLiwwh(5);
        BenefitUnit unit = new BenefitUnit(1L, 1L);
        unit.setHousehold(new Household(1L));
        unit.getMembers().add(person);
        setField(person, "benefitUnit", unit);
        setField(person, "model", new SimPathsModel(Country.UK));
        setField(person, "labHrsWorkEnumWeek", Labour.ZERO);
        person.setLabC4(Les_c4.EmployedOrSelfEmployed);
        UpdateManager.applyAnnotations(person);
        assertNull(person.getLabC4());
        person.onEvent(Person.Processes.Aging);
        assertEquals(6, person.getLiwwh());
        person.setLabC4(Les_c4.NotEmployed);
        setField(person, "labHrsWorkEnumWeek", Labour.ZERO);
        UpdateManager.applyAnnotations(person);
        person.onEvent(Person.Processes.Aging);
        assertEquals(6, person.getLiwwh());
    }

    private static void setField(Person person, String name, Object value) throws Exception {
        Field field = Person.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(person, value);
    }
}