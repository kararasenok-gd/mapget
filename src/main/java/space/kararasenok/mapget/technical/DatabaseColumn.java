package space.kararasenok.mapget.technical;

import java.util.List;
import java.util.Objects;

public record DatabaseColumn(
        String name,
        String type,
        List<String> constraints
) {
    public DatabaseColumn(String name, String type, String... constraints) {
        this(name, type, List.of(constraints));
    }

    public DatabaseColumn {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        constraints = List.copyOf(constraints);
    }

    public String definition() {
        return definition(true);
    }

    public String definition(boolean includeConstraints) {
        return name + " " + type + (includeConstraints && !constraints.isEmpty()
                ? " " + String.join(" ", constraints)
                : "");
    }
}
