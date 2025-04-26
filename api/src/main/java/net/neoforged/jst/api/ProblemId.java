package net.neoforged.jst.api;

import java.util.Objects;

public final class ProblemId {
    private final String id;

    private final String displayName;

    private final ProblemGroup group;

    private ProblemId(String id, String displayName, ProblemGroup group) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.group = Objects.requireNonNull(group, "group");
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ProblemGroup group() {
        return group;
    }

    public static ProblemId create(String id, String displayName, ProblemGroup group) {
        return new ProblemId(id, displayName, group);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProblemId problemId = (ProblemId) o;
        return Objects.equals(id, problemId.id) && Objects.equals(group, problemId.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, group);
    }

    @Override
    public String toString() {
        return group + ":" + id;
    }
}
