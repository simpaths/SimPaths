package simpaths.model;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import simpaths.data.Parameters;
import simpaths.model.enums.Country;
import simpaths.model.enums.UpratingCase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialPopulationUpratingTest {
    @Test
    void post2024PopulationUsesCurrentSchemaAndPreservesRelationships() throws Exception {
        String path = "./target/uprating-" + UUID.randomUUID();
        String url = "jdbc:h2:file:" + path + ";AUTO_SERVER=TRUE";
        Field databasePath = SimPathsModel.class.getDeclaredField("RunDatabasePath");
        databasePath.setAccessible(true);
        Object previousPath = databasePath.get(null);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement sql = connection.createStatement();
             MockedStatic<Parameters> parameters = mockStatic(Parameters.class, CALLS_REAL_METHODS)) {
            parameters.when(Parameters::getMaxStartYear).thenReturn(2024);
            parameters.when(() -> Parameters.getTimeSeriesIndex(2024, UpratingCase.ModelInitialise)).thenReturn(1.0);
            parameters.when(() -> Parameters.getTimeSeriesIndex(2025, UpratingCase.ModelInitialise)).thenReturn(1.5);
            sql.execute("CREATE TABLE HOUSEHOLD_UK_2024 (id INT, simulation_time INT)");
            sql.execute("INSERT INTO HOUSEHOLD_UK_2024 VALUES (1, 2024)");
            sql.execute("CREATE TABLE BENEFITUNIT_UK_2024 (id INT, simulation_time INT, hhid INT, hhtime INT, " +
                    "yDispMonth VARCHAR, wealthTotValue VARCHAR, wealthPensValue VARCHAR, wealthPrptyValue VARCHAR, " +
                    "wealthMortgageDebtValue VARCHAR, wealthUnsecuredDebtLowValue VARCHAR, wealthUnsecuredDebtHighValue VARCHAR)");
            sql.execute("INSERT INTO BENEFITUNIT_UK_2024 VALUES (2, 2024, 1, 2024, '100', '1000', '200', '600', '100', '20', '30')");
            sql.execute("CREATE TABLE PERSON_UK_2024 (id INT, simulation_time INT, system_year INT, buid INT, butime INT, " +
                    "HOURS_WORKED_WEEKLY INT, yNonBenPersGrossMonth VARCHAR, yMiscPersGrossMonth VARCHAR, " +
                    "yCapitalPersMonth VARCHAR, yPensPersGrossMonth VARCHAR, yEmpPersGrossMonth VARCHAR, " +
                    "yPersAndPartnerGrossDiffMonth VARCHAR, labWageHrly VARCHAR, labWageHrlyL1 VARCHAR, careFormalX VARCHAR)");
            double transformed = Math.log(100 + Math.sqrt(10001));
            sql.execute("INSERT INTO PERSON_UK_2024 VALUES (3, 2024, 2024, 2, 2024, 35, '" + transformed + "', '" + transformed + "', '" + transformed + "', '" + transformed + "', '" + transformed + "', '2', '10', '-9', '20')");
            sql.execute("INSERT INTO PERSON_UK_2024 VALUES (4, 2024, 2024, 2, 2024, 0, '" + -transformed + "', '0', NULL, '-9', '" + transformed + "', NULL, '-9', NULL, '-9')");
            databasePath.set(null, "file:" + path);
            Method method = SimPathsModel.class.getDeclaredMethod("inputDatabaseInteraction");
            method.setAccessible(true);
            method.invoke(new SimPathsModel(Country.UK, 2025));

            try (ResultSet rs = sql.executeQuery("SELECT * FROM PERSON")) {
                assertTrue(rs.next());
                for (String column : new String[]{"yNonBenPersGrossMonth", "yMiscPersGrossMonth", "yCapitalPersMonth", "yPensPersGrossMonth", "yEmpPersGrossMonth"})
                    assertEquals(150.0, Math.sinh(rs.getDouble(column)), 1e-5, column);
                assertEquals(15.0, rs.getDouble("labWageHrly"), 1e-8);
                assertEquals(-9.0, rs.getDouble("labWageHrlyL1"), 1e-8);
                assertEquals(30.0, rs.getDouble("careFormalX"), 1e-8);
                assertEquals(2025, rs.getInt("simulation_time"));
                assertEquals(2025, rs.getInt("butime"));
                assertTrue(rs.next());
                assertEquals(-150.0, Math.sinh(rs.getDouble("yNonBenPersGrossMonth")), 1e-5);
                assertEquals(0.0, rs.getDouble("yMiscPersGrossMonth"), 1e-8);
                assertNull(rs.getObject("yCapitalPersMonth"));
                assertEquals(-9.0, rs.getDouble("yPensPersGrossMonth"), 1e-8);
            }
            try (ResultSet rs = sql.executeQuery("SELECT * FROM BENEFITUNIT")) {
                assertTrue(rs.next());
                assertEquals(150.0, rs.getDouble("yDispMonth"), 1e-8);
                assertEquals(1500.0, rs.getDouble("wealthTotValue"), 1e-8);
                assertEquals(2025, rs.getInt("simulation_time"));
                assertEquals(2025, rs.getInt("hhtime"));
            }
            try (ResultSet rs = sql.executeQuery("SELECT simulation_time FROM HOUSEHOLD")) {
                assertTrue(rs.next());
                assertEquals(2025, rs.getInt(1));
            }
            SimPathsModel subsequentRun = new SimPathsModel(Country.UK, 2025);
            subsequentRun.setFirstRun(false);
            method.invoke(subsequentRun);
            try (ResultSet rs = sql.executeQuery("SELECT yNonBenPersGrossMonth FROM PERSON WHERE id = 3")) {
                assertTrue(rs.next());
                assertEquals(150.0, Math.sinh(rs.getDouble(1)), 1e-5);
            }
            method.invoke(new SimPathsModel(Country.UK, 2024));
            try (ResultSet rs = sql.executeQuery("SELECT yNonBenPersGrossMonth, simulation_time FROM PERSON WHERE id = 3")) {
                assertTrue(rs.next());
                assertEquals(100.0, Math.sinh(rs.getDouble(1)), 1e-5);
                assertEquals(2024, rs.getInt(2));
            }
        } finally {
            databasePath.set(null, previousPath);
            Files.deleteIfExists(Path.of(path + ".mv.db"));
            Files.deleteIfExists(Path.of(path + ".trace.db"));
        }
    }
}