package simpaths.data.CSV.example;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Example POJO used to demonstrate {@link simpaths.data.CSV.CsvToObjectLoader}.
 *
 * Field names here are matched (case-insensitively, ignoring spaces,
 * underscores, and hyphens) against the CSV header row, so a header of
 * "First Name" or "first_name" both map onto {@code firstName}.
 *
 * A no-argument constructor is required by the loader; it may be private,
 * as here.
 */
public class Employee {

    private int id;
    private String firstName;
    private String lastName;
    private BigDecimal salary;
    private LocalDate hireDate;
    private boolean active;

    // Required by CsvToObjectLoader (reflection-based instantiation).
    private Employee() {
    }

    // Convenience factory for building instances directly in code (e.g. demos/tests).
    public static Employee create(int id, String firstName, String lastName, BigDecimal salary,
                                  LocalDate hireDate, boolean active) {
        Employee employee = new Employee();
        employee.id = id;
        employee.firstName = firstName;
        employee.lastName = lastName;
        employee.salary = salary;
        employee.hireDate = hireDate;
        employee.active = active;
        return employee;
    }

    public int getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Employee)) return false;
        Employee employee = (Employee) o;
        return id == employee.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Employee{id=" + id
                + ", firstName='" + firstName + '\''
                + ", lastName='" + lastName + '\''
                + ", salary=" + salary
                + ", hireDate=" + hireDate
                + ", active=" + active
                + '}';
    }
}
