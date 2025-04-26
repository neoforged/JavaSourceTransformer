package net.neoforged.jst.api;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ProblemGroup {
    private final String id;

    private final String displayName;

    @Nullable
    private final ProblemGroup parent;

    private ProblemGroup(String id, String displayName) {
        this(id, displayName, null);
    }

    private ProblemGroup(String id, String displayName, @Nullable ProblemGroup parent) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.parent = parent;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public @Nullable ProblemGroup parent() {
        return parent;
    }

    public static ProblemGroup create(String id, String displayName) {
        return create(id, displayName, null);
    }

    public static ProblemGroup create(String id, String displayName, @Nullable ProblemGroup parent) {
        return new ProblemGroup(id, displayName, parent);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProblemGroup that = (ProblemGroup) o;
        return Objects.equals(id, that.id) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, parent);
    }

    @Override
    public String toString() {
        if (parent != null) {
            return parent + ":" + id;
        } else {
            return id;
        }
    }
}
