package simpaths.data;

import simpaths.model.enums.Gender;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCostProfileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsPreparedModelInput() throws IOException {
        HealthCostProfile profile = new HealthCostProfile();

        assertEquals(1860.0 * 0.716601857911347, profile.cost(2028, 0), 1e-8);
        assertTrue(profile.cost(2060, 101) > 0.0);
        assertEquals(profile.cost(2060, 101), profile.cost(2060, 102));
        assertEquals(profile.cost(2060, 101), profile.cost(2060, 110));
    }

    @Test
    void readsCompleteSyntheticMatrix() throws IOException {
        HealthCostProfile profile = new HealthCostProfile(writeWorkbook(2060, sheet -> {}));

        assertEquals(1000.0, profile.cost(2028, 0));
        assertEquals(1421.0, profile.cost(2060, 101));
    }

    @Test
    void rejectsUnsupportedLookupYearsAndNegativeAges() throws IOException {
        HealthCostProfile profile = new HealthCostProfile(writeWorkbook(2060, sheet -> {}));

        assertThrows(IllegalArgumentException.class, () -> profile.cost(2027, 0));
        assertThrows(IllegalArgumentException.class, () -> profile.cost(2061, 0));
        assertThrows(IllegalArgumentException.class, () -> profile.cost(2028, -1));
    }

    @Test
    void rejectsMissingOrDuplicateAgeHeader() throws IOException {
        File file = writeWorkbook(2060,
                sheet -> sheet.getRow(0).getCell(2).setCellValue("age_0"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("age_1"));
        assertTrue(error.getMessage().contains("C1"));
    }

    @Test
    void rejectsMissingYear() throws IOException {
        File file = writeWorkbook(2059, sheet -> {});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("2060"));
    }

    @Test
    void rejectsDuplicateYear() throws IOException {
        File file = writeWorkbook(2060,
                sheet -> sheet.getRow(2).getCell(0).setCellValue(2028));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("Duplicate health cost year 2028"));
    }

    @Test
    void rejectsExtraYear() throws IOException {
        File file = writeWorkbook(2061, sheet -> {});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("2061"));
    }

    @Test
    void rejectsZeroAndMissingCostCells() throws IOException {
        File zeroFile = writeWorkbook(2060,
                sheet -> sheet.getRow(1).getCell(2).setCellValue(0.0));
        IllegalArgumentException zeroError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(zeroFile));
        assertTrue(zeroError.getMessage().contains("year 2028, age_1"));

        File missingFile = writeWorkbook(2060,
                sheet -> sheet.getRow(1).removeCell(sheet.getRow(1).getCell(2)));
        IllegalArgumentException missingError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(missingFile));
        assertTrue(missingError.getMessage().contains("year 2028, age_1"));
    }

    @Test
    void rejectsFormulaAndUnexpectedColumn() throws IOException {
        File formulaFile = writeWorkbook(2060,
                sheet -> sheet.getRow(1).getCell(2).setCellFormula("1+2"));
        IllegalArgumentException formulaError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(formulaFile));
        assertTrue(formulaError.getMessage().contains("year 2028, age_1"));

        File extraColumnFile = writeWorkbook(2060,
                sheet -> sheet.getRow(0).createCell(103).setCellValue("age_102"));
        IllegalArgumentException columnError = assertThrows(IllegalArgumentException.class,
                () -> new HealthCostProfile(extraColumnFile));
        assertTrue(columnError.getMessage().contains("after age_101"));
    }

    @Test
    void readsBothSheetsOfPreparedAgeBySexProfile() throws IOException {
        var profile = new HealthCostProfile(Parameters.getAgeHealthCostProfileFile("age_by_sex_health_cost_profile.xlsx"));
        assertTrue(profile.isSexSpecific());
        assertEquals(1481.542465087039, profile.cost(2028, 0, Gender.Male), 1e-8);
        assertEquals(1176.8493949232945, profile.cost(2028, 0, Gender.Female), 1e-8);
        assertEquals(18457.198085567856, profile.cost(2060, 102, Gender.Female), 1e-8);
        assertThrows(IllegalStateException.class, () -> profile.cost(2028, 0));
    }

    @Test
    void rejectsAgeBySexWorkbookWithOneSheetMissing() throws IOException {
        File file = writeWorkbook(2060, sheet -> sheet.getWorkbook().setSheetName(0, "male"));
        var error = assertThrows(IllegalArgumentException.class, () -> new HealthCostProfile(file));
        assertTrue(error.getMessage().contains("both 'male' and 'female'"));
    }

    private File writeWorkbook(int lastYear, Consumer<Sheet> change) throws IOException {
        Path file = temporaryDirectory.resolve("health-cost-" + System.nanoTime() + ".xlsx");
        try (Workbook workbook = WorkbookFactory.create(true)) {
            Sheet sheet = workbook.createSheet("Age health cost");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("year");
            for (int age = 0; age <= 101; age++) {
                header.createCell(age + 1).setCellValue("age_" + age);
            }
            for (int year = 2028; year <= lastYear; year++) {
                Row row = sheet.createRow(year - 2028 + 1);
                row.createCell(0).setCellValue(year);
                for (int age = 0; age <= 101; age++) {
                    row.createCell(age + 1).setCellValue(1000.0 + 10.0 * (year - 2028) + age);
                }
            }
            change.accept(sheet);
            try (OutputStream output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
        return file.toFile();
    }
}
