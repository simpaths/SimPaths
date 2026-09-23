package simpaths.data.CSV.example;

import simpaths.data.CSV.ObjectToCsvWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates {@link ObjectToCsvWriter}'s handling of "complex" (non-simple
 * typed) attributes:
 * <ul>
 *   <li>{@code department} is a {@link Department}, which has an {@code int
 *       id} field &mdash; the writer outputs that id instead of dumping the
 *       whole object.
 *   <li>{@code tag} is a {@link Tag}, which has no {@code id} field at all
 *       &mdash; the writer falls back to {@code Tag.toString()}.
 *   <li>one project has a {@code null} department &mdash; the writer emits
 *       an empty cell, as usual for null values.
 * </ul>
 *
 * Usage: java simpaths.data.CSV.example.ComplexAttributeDemo path/to/output.csv
 */
public class DemoComplexAttribute {

    public static void main(String[] args) throws IOException {
        String outputPath = args.length > 0 ? args[0] : "projects_out.csv";

        Department engineering = Department.create(7, "Engineering");
        Department marketing = Department.create(12, "Marketing");

        List<Project> projects = new ArrayList<>();
        projects.add(Project.create(1, "CSV Toolkit", engineering, Tag.create("infra")));
        projects.add(Project.create(2, "Launch Campaign", marketing, Tag.create("q3")));
        projects.add(Project.create(3, "Unassigned Effort", null, null));

        ObjectToCsvWriter.write(outputPath, projects, Project.class);

        System.out.println("Wrote " + projects.size() + " project(s) to " + outputPath + ":");
        System.out.println(Files.readString(Path.of(outputPath)));
    }
}
