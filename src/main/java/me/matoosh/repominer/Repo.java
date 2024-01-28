package me.matoosh.repominer;

/**
 * Represents a repo to mine.
 */
public class Repo {
    public String id;
    public String category;

    public Repo() {
    }

    public Repo(String id, String category) {
        this.id = id;
        this.category = category;
    }

    @Override
    public String toString() {
        return "Repo{" +
                "id='" + id + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}
