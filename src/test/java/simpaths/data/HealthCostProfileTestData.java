package simpaths.data;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Cost workbooks for health-spending tests. */
public final class HealthCostProfileTestData {

    private HealthCostProfileTestData() {
    }

    public static HealthCostProfile bundledAgeBySexProfile() throws IOException {
        // Use the checked-in workbook independently of the model's shared input directory.
        File workbook = Path.of("input", Parameters.AGE_HEALTH_COST_PROFILE_FILENAME).toFile();
        return new HealthCostProfile(workbook);
    }

    public static HealthCostProfile ageOnlyProfile(Path directory) throws IOException {
        return new HealthCostProfile(writeAgeOnlyWorkbook(directory, HealthCostProfile.LAST_YEAR, sheet -> {}));
    }

    static File writeAgeOnlyWorkbook(Path directory, int lastYear, Consumer<Sheet> change) throws IOException {
        Path file = Files.createTempFile(directory, "health-cost-", ".xlsx");
        try (Workbook workbook = WorkbookFactory.create(true)) {
            Sheet sheet = workbook.createSheet("Age health cost");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("year");
            for (int age = 0; age <= HealthCostProfile.MAX_INPUT_AGE; age++) {
                header.createCell(age + 1).setCellValue("age_" + age);
            }
            for (int year = HealthCostProfile.FIRST_YEAR; year <= lastYear; year++) {
                Row row = sheet.createRow(year - HealthCostProfile.FIRST_YEAR + 1);
                row.createCell(0).setCellValue(year);
                for (int age = 0; age <= HealthCostProfile.MAX_INPUT_AGE; age++) {
                    row.createCell(age + 1).setCellValue(1000.0 + 10.0 * (year - HealthCostProfile.FIRST_YEAR) + age);
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
