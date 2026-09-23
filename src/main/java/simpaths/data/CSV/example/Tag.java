package simpaths.data.CSV.example;

/**
 * Another example "complex" attribute type, but deliberately <em>without</em>
 * an {@code id} field, used to demonstrate {@link
 * simpaths.data.CSV.ObjectToCsvWriter}'s fallback: with no {@code int}/{@code
 * Integer} {@code id} field to substitute, the writer falls back to this
 * class's own {@code toString()}.
 */
public class Tag {

    private String label;

    private Tag() {
    }

    public static Tag create(String label) {
        Tag tag = new Tag();
        tag.label = label;
        return tag;
    }

    @Override
    public String toString() {
        return "#" + label;
    }
}
