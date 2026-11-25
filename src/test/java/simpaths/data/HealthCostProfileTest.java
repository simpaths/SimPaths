package simpaths.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import simpaths.model.enums.Gender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static simpaths.data.HealthCostProfileTestData.writeAgeOnlyWorkbook;

class HealthCostProfileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsBothSheetsOfTheDefaultModelWorkbook() throws IOException {
        HealthCostProfile profile = new HealthCostProfile();

        assertEquals("age_by_sex_health_cost_profile.xlsx", Parameters.getAgeHealthCostProfileFile().getName());
        assertTrue(profile.isSexSpecific());
        assertEquals(1481.542465087039, profile.cost(2028, 0, Gender.Male), 1e-8);
        assertEquals(1176.8493949232945, profile.cost(2028, 0, Gender.Female), 1e-8);
        assertEquals(18457.198085567856, profile.cost(2060, 102, Gender.Female), 1e-8);
        assertEquals(profile.cost(2060, 101, Gender.Male), profile.cost(2060, 110, Gender.Male));
    }

    @Test
    void readsCompleteSyntheticMatrix() throws IOException {
        HealthCostProfile profile = new HealthCostProfile(writeAgeOnlyWorkbook(temporaryDirectory, 2060, sheet -> {}));

        assertEquals(1000.0, profile.cost(2028, 0));
        assertEquals(1421.0, profile.cost(2060, 101));
    }

    @Test
    void rejectsUnsupportedLookupYearsAndNegativeAges() throws IOException {
        HealthCostProfile profile = new HealthCostProfile(writeAgeOnlyWorkbook(temporaryDirectory, 2060, sheet -> {}));

        assertThrows(IllegalArgumentException.class, () -> profile.cost(2027, 0));
        assertThrows(IllegalArgumentException.class, () -> profile.cost(2061, 0));
        assertThrows(IllegalArgumentException.class, () -> profile.cost(2028, -1));
    }

    @Test
    void rejectsMissingOrDuplicateAgeHeader() throws IOException {
        File file = writeAgeOnlyWorkbook(temporaryDirectory, 2060,
                sheet -> sheet.getRow(0).getCell(2).setCellValue("age_0"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("age_1"));
        assertTrue(error.getMessage().contains("C1"));
    }

    @Test
    void rejectsMissingYear() throws IOException {
        File file = writeAgeOnlyWorkbook(temporaryDirectory, 2059, sheet -> {});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("2060"));
    }

    @Test
    void rejectsDuplicateYear() throws IOException {
        File file = writeAgeOnlyWorkbook(temporaryDirectory, 2060,
                sheet -> sheet.getRow(2).getCell(0).setCellValue(2028));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("Duplicate health cost year 2028"));
    }

    @Test
    void rejectsExtraYear() throws IOException {
        File file = writeAgeOnlyWorkbook(temporaryDirectory, 2061, sheet -> {});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("2061"));
    }

    @Test
    void rejectsZeroAndMissingCostCells() throws IOException {
        File zeroFile = writeAgeOnlyWorkbook(temporaryDirectory, 2060,
                sheet -> sheet.getRow(1).getCell(2).setCellValue(0.0));
        IllegalArgumentException zeroError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(zeroFile));
        assertTrue(zeroError.getMessage().contains("year 2028, age_1"));

        File missingFile = writeAgeOnlyWorkbook(temporaryDirectory, 2060,
                sheet -> sheet.getRow(1).removeCell(sheet.getRow(1).getCell(2)));
        IllegalArgumentException missingError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(missingFile));
        assertTrue(missingError.getMessage().contains("year 2028, age_1"));
    }

    @Test
    void rejectsFormulaAndUnexpectedColumn() throws IOException {
        File formulaFile = writeAgeOnlyWorkbook(temporaryDirectory, 2060,
                sheet -> sheet.getRow(1).getCell(2).setCellFormula("1+2"));
        IllegalArgumentException formulaError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(formulaFile));
        assertTrue(formulaError.getMessage().contains("year 2028, age_1"));

        File extraColumnFile = writeAgeOnlyWorkbook(temporaryDirectory, 2060,
                sheet -> sheet.getRow(0).createCell(103).setCellValue("age_102"));
        IllegalArgumentException columnError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(extraColumnFile));
        assertTrue(columnError.getMessage().contains("after age_101"));
    }

    @Test
    void requiresSexWhenLookingUpCostsInTheDefaultWorkbook() throws IOException {
        var profile = new HealthCostProfile();
        assertThrows(IllegalStateException.class, () -> profile.cost(2028, 0));
    }

    @Test
    void rejectsAgeBySexWorkbookWithOneSheetMissing() throws IOException {
        File file = writeAgeOnlyWorkbook(temporaryDirectory, 2060, sheet -> sheet.getWorkbook().setSheetName(0, "male"));
        var error = assertThrows(IllegalArgumentException.class, () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("both 'male' and 'female'"));
    }

}
