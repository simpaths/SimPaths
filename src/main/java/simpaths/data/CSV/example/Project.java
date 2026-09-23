package simpaths.data.CSV.example;

/**
 * Example POJO with a "complex" (non-simple-typed) attribute, {@code
 * department}, plus another, {@code tag}, whose type has no {@code id}
 * field. Used to demonstrate {@link simpaths.data.CSV.ObjectToCsvWriter}'s
 * id-substitution behavior and its toString() fallback.
 */
public class Project {

    private int id;
    private String name;
    private Department department;
    private Tag tag;

    private Project() {
    }

    public static Project create(int id, String name, Department department, Tag tag) {
        Project project = new Project();
        project.id = id;
        project.name = name;
        project.department = department;
        project.tag = tag;
        return project;
    }

    @Override
    public String toString() {
        return "Project{id=" + id + ", name='" + name + "', department=" + department + ", tag=" + tag + "}";
    }
}
