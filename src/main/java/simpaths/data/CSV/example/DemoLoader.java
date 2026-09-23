package simpaths.data.CSV.example;


import simpaths.data.CSV.CsvToObjectLoader;

import java.io.IOException;
import java.util.LinkedHashSet;

/**
 * Small runnable demo of {@link CsvToObjectLoader}.
 *
 * Usage: java com.example.csv.example.Demo path/to/employees.csv
 */
public class DemoLoader {

    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "src/main/resources/csv/employees.csv";

        LinkedHashSet<Employee> employees = CsvToObjectLoader.load(path, Employee.class);

        System.out.println("Loaded " + employees.size() + " employee(s) (duplicates collapsed):");
        for (Employee e : employees) {
            System.out.println("  " + e);
        }
    }
}
