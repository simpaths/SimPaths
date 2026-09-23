package simpaths.data.CSV.example;

import simpaths.data.CSV.CsvToObjectLoader;
import simpaths.data.CSV.ObjectToCsvWriter;


import java.io.IOException;
import java.util.ArrayList;

/**
 * Demonstrates the ordered-list round trip: {@link CsvToObjectLoader#loadList}
 * reads every row (including duplicates) into an {@link ArrayList}, and
 * {@link ObjectToCsvWriter#write(String, java.util.List, Class)} writes that
 * same list straight back out to CSV, preserving row order and duplicates.
 *
 * Usage: java com.example.csv.example.OrderedListDemo path/to/employees.csv path/to/output.csv
 */
public class DemoOrderedList {

    public static void main(String[] args) throws IOException {
        String inputPath = args.length > 0 ? args[0] : "src/test/resources/employees.csv";
        String outputPath = args.length > 1 ? args[1] : "employees_ordered_out.csv";

        ArrayList<Employee> employees = CsvToObjectLoader.loadList(inputPath, Employee.class);
        System.out.println("loadList() -> " + employees.size() + " row(s) from " + inputPath + " (duplicates kept):");
        employees.forEach(e -> System.out.println("  " + e));

        ObjectToCsvWriter.write(outputPath, employees, Employee.class);
        System.out.println("Wrote " + employees.size() + " row(s) to " + outputPath + " in the same order");

        ArrayList<Employee> reloaded = CsvToObjectLoader.loadList(outputPath, Employee.class);
        boolean matches = employees.equals(reloaded); // ArrayList.equals compares elements in order
        System.out.println("Reloaded list equals original list (same elements, same order): " + matches);

        if (!matches) {
            throw new AssertionError("Ordered round-trip mismatch!");
        }
    }
}
