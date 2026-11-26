package simpaths.data;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import simpaths.model.enums.Gender;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Annual public health cost per person, in real 2015 pounds. The input already
 * includes the agreed price conversion, morbidity shift and non-demographic growth.
 */
public final class HealthCostProfile {

    public static final int FIRST_YEAR = 2028;
    public static final int LAST_YEAR = 2060;
    public static final int MAX_INPUT_AGE = 101;

    private static final String SHEET_NAME = "Age health cost";
    private static final int AGE_COLUMNS = MAX_INPUT_AGE + 1;
    private static final int TOTAL_COLUMNS = AGE_COLUMNS + 1;

    private final double[][] costs;
    private final double[][] femaleCosts;

    public HealthCostProfile() throws IOException {
        this(Parameters.getAgeHealthCostProfileFile());
    }

    public HealthCostProfile(File file) throws IOException {
        Objects.requireNonNull(file, "Health cost profile file must not be null");
        try (InputStream input = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet male = workbook.getSheet("male");
            Sheet female = workbook.getSheet("female");
            if (male != null || female != null) {
                if (male == null || female == null) {
                    throw new IllegalArgumentException("An age-by-sex profile requires both 'male' and 'female' worksheets in " + file);
                }
                costs = readSheet(male);
                femaleCosts = readSheet(female);
            } else {
                Sheet sheet = workbook.getSheet(SHEET_NAME);
                if (sheet == null) {
                    throw new IllegalArgumentException("Missing worksheet '" + SHEET_NAME + "' in " + file);
                }
                costs = readSheet(sheet);
                femaleCosts = null;
            }
        }
    }

    public boolean isSexSpecific() {
        return femaleCosts != null;
    }

    /** Returns the annual cost from an age-only profile. */
    public double cost(int year, int age) {
        if (isSexSpecific()) {
            throw new IllegalStateException("An age-by-sex health cost lookup requires sex");
        }
        return cost(year, age, costs);
    }

    /** Returns the annual cost for the person's sex and actual single-year age. */
    public double cost(int year, int age, Gender gender) {
        if (!isSexSpecific()) {
            return cost(year, age);
        }
        Objects.requireNonNull(gender, "An age-by-sex health cost lookup requires sex");
        return cost(year, age, gender == Gender.Female ? femaleCosts : costs);
    }

    private static double cost(int year, int age, double[][] matrix) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw new IllegalArgumentException("Health cost year " + year + " is outside "
                    + FIRST_YEAR + "-" + LAST_YEAR);
        }
        if (age < 0) {
            throw new IllegalArgumentException("Health cost age must be non-negative: " + age);
        }
        return matrix[year - FIRST_YEAR][Math.min(age, MAX_INPUT_AGE)];
    }

    private static double[][] readSheet(Sheet sheet) {
        double[][] costs = new double[LAST_YEAR - FIRST_YEAR + 1][AGE_COLUMNS];
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new IllegalArgumentException("Missing header row in worksheet '" + sheet.getSheetName() + "'");
        }
        for (int column = 0; column < TOTAL_COLUMNS; column++) {
            String expected = column == 0 ? "year" : "age_" + (column - 1);
            Cell cell = header.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String actual = cell != null && cell.getCellType() == CellType.STRING
                    ? cell.getStringCellValue() : null;
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("Expected header '" + expected + "' in "
                        + new CellReference(0, column).formatAsString() + ", found '" + actual + "'");
            }
        }
        if (header.getLastCellNum() > TOTAL_COLUMNS) {
            throw new IllegalArgumentException("Unexpected header column after age_" + MAX_INPUT_AGE);
        }

        boolean[] yearsSeen = new boolean[costs.length];
        for (int rowNumber = 1; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
            Row row = sheet.getRow(rowNumber);
            if (row == null) {
                throw new IllegalArgumentException("Missing data row at Excel row " + (rowNumber + 1));
            }
            double yearValue = numericValue(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL),
                    "year in Excel row " + (rowNumber + 1));
            if (yearValue != Math.rint(yearValue) || yearValue < FIRST_YEAR || yearValue > LAST_YEAR) {
                throw new IllegalArgumentException("Unsupported health cost year " + yearValue
                        + " in Excel row " + (rowNumber + 1));
            }
            int year = (int) yearValue;
            int yearIndex = year - FIRST_YEAR;
            if (yearsSeen[yearIndex]) {
                throw new IllegalArgumentException("Duplicate health cost year " + year
                        + " in Excel row " + (rowNumber + 1));
            }
            yearsSeen[yearIndex] = true;

            for (int age = 0; age <= MAX_INPUT_AGE; age++) {
                double value = numericValue(row.getCell(age + 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL),
                        "year " + year + ", age_" + age);
                if (value <= 0.0) {
                    throw new IllegalArgumentException("Health cost must be positive for year "
                            + year + ", age_" + age + ": " + value);
                }
                costs[yearIndex][age] = value;
            }
            if (row.getLastCellNum() > TOTAL_COLUMNS) {
                throw new IllegalArgumentException("Unexpected column after age_" + MAX_INPUT_AGE
                        + " for year " + year);
            }
        }
        for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
            if (!yearsSeen[year - FIRST_YEAR]) {
                throw new IllegalArgumentException("Missing health cost year " + year);
            }
        }
        return costs;
    }

    private static double numericValue(Cell cell, String context) {
        if (cell == null || cell.getCellType() != CellType.NUMERIC) {
            throw new IllegalArgumentException("Expected a numeric value for " + context);
        }
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite numeric value for " + context);
        }
        return value;
    }
}
