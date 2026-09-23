package simpaths.data.CSV.example;

import simpaths.data.CSV.CsvToObjectLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Demonstrates the difference between {@link CsvToObjectLoader#load} (dedups
 * into a LinkedHashSet) and {@link CsvToObjectLoader#loadList} (keeps every
 * row, including duplicates, in an ArrayList), using the sample data that
 * contains a duplicate employee row.
 */
public class DemoLoadList {

    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "src/test/resources/employees.csv";

        LinkedHashSet<Employee> asSet = CsvToObjectLoader.load(path, Employee.class);
        ArrayList<Employee> asList = CsvToObjectLoader.loadList(path, Employee.class);

        System.out.println("load()     -> LinkedHashSet, " + asSet.size() + " element(s):");
        asSet.forEach(e -> System.out.println("  " + e));

        System.out.println("loadList() -> ArrayList, " + asList.size() + " element(s):");
        asList.forEach(e -> System.out.println("  " + e));
    }
}
