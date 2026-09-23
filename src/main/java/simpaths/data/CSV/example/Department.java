package simpaths.data.CSV.example;

/**
 * Example "complex" attribute type used to demonstrate {@link
 * simpaths.data.CSV.ObjectToCsvWriter}'s id-substitution behavior: when a
 * written field's value is a non-simple type like this one, the writer
 * looks for an {@code int}/{@code Integer} field named {@code id} on it and
 * writes that instead of calling {@code toString()} on the whole object.
 */
public class Department {

    private int id;
    private String name;

    private Department() {
    }

    public static Department create(int id, String name) {
        Department department = new Department();
        department.id = id;
        department.name = name;
        return department;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Department{id=" + id + ", name='" + name + "'}";
    }
}
