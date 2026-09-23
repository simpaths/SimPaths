package simpaths.data.CSV.example;

import simpaths.data.CSV.CsvToObjectLoader;
import simpaths.data.CSV.ObjectToCsvWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;

/**
 * Demonstrates a full round trip: read a CSV into objects with {@link
 * CsvToObjectLoader}, write those objects back out to a new CSV with {@link
 * ObjectToCsvWriter} (including a value that needs quoting), then read the
 * new file back in and verify the data matches.
 *
 * Usage: java simpaths.data.CSV.example.DemoRoundTrip path/to/employees.csv path/to/output.csv
 */
public class DemoRoundTrip {

    public static void main(String[] args) throws IOException {
        String inputPath = args.length > 0 ? args[0] : "src/main/resources/csv/employees.csv";
        String outputPath = args.length > 1 ? args[1] : "src/main/resources/csv/employees_out.csv";

        LinkedHashSet<Employee> original = CsvToObjectLoader.load(inputPath, Employee.class);
        System.out.println("Loaded " + original.size() + " employee(s) from " + inputPath);

        // Add an entry whose name needs CSV quoting (embedded comma and quote).
        original.add(Employee.create(9, "Grace, \"Amazing\"", "Hopper", new BigDecimal("99999.99"),
                LocalDate.of(2023, 6, 1), true));

        ObjectToCsvWriter.write(outputPath, original, Employee.class);
        System.out.println("Wrote " + original.size() + " employee(s) to " + outputPath);

        LinkedHashSet<Employee> reloaded = CsvToObjectLoader.load(outputPath, Employee.class);
        System.out.println("Re-loaded " + reloaded.size() + " employee(s) from " + outputPath);

        boolean matches = original.equals(reloaded);
        System.out.println("Round trip matches (by id via equals/hashCode): " + matches);

        for (Employee e : reloaded) {
            System.out.println("  " + e);
        }

        if (!matches) {
            throw new AssertionError("Round-trip mismatch!");
        }
    }
}
