package me.matoosh.repominer;

import java.util.List;
import java.util.Set;

/**
 * Repo with mined dependencies.
 */
public class MinedRepo extends Repo {
    public MinedRepo() {
        super();
    }

    public MinedRepo(String id, String category, Set<Dependency> dependencies) {
        super(id, category);
        this.dependencies = dependencies;
    }

    public Set<Dependency> dependencies;
}
