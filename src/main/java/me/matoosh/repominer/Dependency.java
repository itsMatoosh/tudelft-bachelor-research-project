package me.matoosh.repominer;

import java.util.Objects;

public class Dependency {
    public String provider;
    public String id;
    public String version;
    public DependencyType type;

    public Dependency() {
    }

    public Dependency(String provider, String id, String versionId, DependencyType type) {
        this.provider = provider;
        this.id = id;
        this.version = versionId;
        this.type = type;
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "provider='" + provider + '\'' +
                ", id='" + id + '\'' +
                ", versionId='" + version + '\'' +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dependency that = (Dependency) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

