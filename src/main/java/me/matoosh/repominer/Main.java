package me.matoosh.repominer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // extract run params
        Namespace params = mapParams(args);

        // read repo file
        String path = (String) params.getList("file").get(0);
        List<Repo> repoList = readRepoList(path);

        // connect to github
        GitHub git = GitHub.connect();

        // get previously mined repos
        System.out.println("Getting previously mined repos...");
        MinedRepo[] previouslyMined = readMinedReposFromFile("mined.json");
        HashMap<String, MinedRepo> previouslyMinedMap = new HashMap<>();
        if (previouslyMined != null) {
            for (MinedRepo mined : previouslyMined) {
                previouslyMinedMap.put(mined.id, mined);
            }
        }
        System.out.println("Read " + previouslyMinedMap.size() + " previously mined repos!");

        // mine each repo
        System.out.println("Mining repos...");
        List<MinedRepo> minedRepos = new ArrayList<>();
        for (Repo r :
                repoList) {
            // check if already mined
            if (previouslyMinedMap.containsKey(r.id)) {
                MinedRepo minedRepo = previouslyMinedMap.get(r.id);
                minedRepo.category = r.category;
                minedRepos.add(minedRepo);
                System.out.println(r.id + ": Already mined!");
                writeMinedReposToFile(minedRepos, "mined.json");
                continue;
            }

            // mine deps
            final Set<Dependency> dependencies = mineRepo(git, r);
            if (dependencies == null) {
                System.out.println(r.id + ": Invalid!");
                continue;
            }
            System.out.println(r.id + ": Extracted " + dependencies.size() + " dependencies!");

            // save to memory
            final MinedRepo minedRepo = new MinedRepo(r.id, r.category, dependencies);
            minedRepos.add(minedRepo);

            // save to disk
            writeMinedReposToFile(minedRepos, "mined.json");
        }
        System.out.println("Mined " + minedRepos.size() + " new repos!");
    }

    /**
     * Mines the given repo.
     *
     * @param git  Git library.
     * @param repo Repo to mine.
     */
    private static Set<Dependency> mineRepo(GitHub git, Repo repo) throws IOException {
        System.out.println(repo.id + ": mining...");

        // get repository
        final GHRepository repository = git.getRepository(repo.id);

        // get pom.xml
        GHContent pomFile = null;
        try {
            pomFile = repository.getFileContent("pom.xml");
        } catch (Exception e) {
            // ignore
        }
        if (pomFile != null && pomFile.isFile()) {
            return minePomDependencies(repository);
        }

        // get package.json
        GHContent packageJsonFile = null;
        try {
            packageJsonFile = repository.getFileContent("package.json");
        } catch (Exception e) {
            // ignore
        }
        if (packageJsonFile != null && packageJsonFile.isFile()) {
            return minePackageJsonDependencies(packageJsonFile);
        }

        // get pubspec.yaml
        GHContent pubspecYamlFile = null;
        try {
            pubspecYamlFile = repository.getFileContent("pubspec.yaml");
        } catch (Exception e) {
            // ignore
        }
        if (pubspecYamlFile != null && pubspecYamlFile.isFile()) {
            return minePubspecYamlDependencies(pubspecYamlFile);
        }

        // get build.gradle or build.gradle.kts
        GHContent buildGradleFile = null;
        try {
            buildGradleFile = repository.getFileContent("build.gradle");
        } catch (Exception e) {
            // ignore
        }
        if (buildGradleFile != null && buildGradleFile.isFile()) {
            return mineGradleDependencies(repository);
        }
        GHContent buildGradleKtsFile = null;
        try {
            buildGradleKtsFile = repository.getFileContent("build.gradle.kts");
        } catch (Exception e) {
            // ignore
        }
        if (buildGradleKtsFile != null && buildGradleKtsFile.isFile()) {
            return mineGradleDependencies(repository);
        }

        return null;
    }

    /**
     * Mines gradle dependencies.
     *
     * @param repository
     * @return
     */
    private static Set<Dependency> mineGradleDependencies(GHRepository repository) {
        // clone repo
        final File repo = cloneRepo(repository);
        if (repo == null) return null;

        // get sub-projects
        CommandUtil.executeCommand("chmod +ux ./gradlew", repo);
        String projectsOutput = CommandUtil.executeCommand("./gradlew projects", repo);
        if (projectsOutput == null) return null;

        // extract sub-projects
        Set<String> gradleProjects = new HashSet<>();
        gradleProjects.add("root");
        for (String line : projectsOutput.lines().toList()) {
            if (line.startsWith("+---")) {
                String[] lineSplit = line.split(" ");
                String projStr = lineSplit[2].replaceAll("'", "").replace(":", "");
                gradleProjects.add(projStr);
            }
        }

        // extract dependencies
        Set<Dependency> dependencies = new HashSet<>();
        for (String gradleProject : gradleProjects) {
            System.out.println("Mining project " + gradleProject + " using Gradle");
            String output;
            if (gradleProject.equals("root")) {
                // mine root project
                output = CommandUtil.executeCommand("./gradlew dependencies", repo);
            } else {
                // mine sub-project
                output = CommandUtil.executeCommand("./gradlew " + gradleProject + ":dependencies", repo);
            }
            if (output == null) continue;

            // extract dependencies
            for (String line : output.lines().toList()) {
                // check if dependency
                int plusIndex = line.indexOf("+---");
                int slashIndex = line.indexOf("\\---");
                if (plusIndex == -1 && slashIndex == -1) continue;

                // get line
                String lineTrimmed;
                if (plusIndex > -1) {
                    lineTrimmed = line.substring(plusIndex);
                } else {
                    lineTrimmed = line.substring(slashIndex);
                }

                // extract dependencies
                String[] lineSplit = lineTrimmed.split(" ");
                String depStr = lineSplit[1];
                String[] depStrSplit = depStr.trim().split(":");
                if (depStrSplit.length != 3) continue;

                DependencyType dependencyType;
                if (plusIndex == 0 || slashIndex == 0) {
                    dependencyType = DependencyType.DIRECT;
                } else {
                    dependencyType = DependencyType.TRANSITIVE;
                }
                String groupId = depStrSplit[0];
                String artifactId = depStrSplit[1];
                String version = depStrSplit[2];

                dependencies.add(new Dependency("maven", groupId + ":" + artifactId, version, dependencyType));
            }
        }


        if (dependencies.isEmpty()) return null;
        return dependencies;
    }

    /**
     * Finds child directories which contain the given file.
     *
     * @param fileName      Name of the file to search for.
     * @param rootDirectory Root directory to start the search from.
     * @return Found directories containing the searched file.
     */
    private static Set<File> findFilesRecursive(String fileName, File rootDirectory) {
        // iterate all files
        Set<File> foundFiles = new HashSet<>();
        File[] files = rootDirectory.listFiles();
        for (File file : files) {
            if (file.isFile() && file.getName().equals(fileName)) {
                foundFiles.add(file);
            } else if (file.isDirectory()) {
                // recur
                foundFiles.addAll(findFilesRecursive(fileName, file));
            }
        }

        return foundFiles;
    }

    /**
     * Mines dependencies from a POM repository.
     *
     * @param repository The repository to mine from.
     * @return The mined dependencies.
     */
    private static Set<Dependency> minePomDependencies(GHRepository repository) {
        System.out.println("Mining dependencies using pom.xml");

        // clone repo
        final File repo = cloneRepo(repository);
        if (repo == null) return null;

        // get pom files
        Set<File> pomFiles = findFilesRecursive("pom.xml", repo);
        if (pomFiles.isEmpty()) return null;

        // run mvn
        Set<Dependency> dependencies = new HashSet<>();
        for (File pomFile : pomFiles) {
            Set<Dependency> deps = minePomModuleDependencies(pomFile);
            if (deps != null) {
                dependencies.addAll(deps);
            }
        }

        if (dependencies.isEmpty()) return null;
        return dependencies;
    }

    /**
     * Mines dependencies from a POM file.
     *
     * @param pomFile POM file to read.
     * @return List of mined dependencies.
     */
    private static Set<Dependency> minePomModuleDependencies(File pomFile) {
        System.out.println("Mining pom.xml at " + pomFile);

        // run mvn
        String output = CommandUtil.executeCommand("mvn dependency:tree", pomFile.getParentFile());
        if (output == null) return null;

        // extract dependencies
        Set<Dependency> dependencies = new HashSet<>();
        for (String line : output.lines().toList()) {
            // check if dependency
            int plusIndex = line.indexOf("+-");
            int slashIndex = line.indexOf("\\-");
            if (plusIndex == -1 && slashIndex == -1) continue;

            // get line
            String lineTrimmed;
            if (plusIndex > -1) {
                lineTrimmed = line.substring(plusIndex);
            } else {
                lineTrimmed = line.substring(slashIndex);
            }

            // extract dependencies
            String[] lineSplit = lineTrimmed.split(" ");
            String depStr = lineSplit[1];
            String[] depStrSplit = depStr.trim().split(":");
            if (depStrSplit.length != 5) continue;

            DependencyType dependencyType;
            if (plusIndex == 7 || slashIndex == 7) {
                dependencyType = DependencyType.DIRECT;
            } else {
                dependencyType = DependencyType.TRANSITIVE;
            }
            String groupId = depStrSplit[0];
            String artifactId = depStrSplit[1];
            String version = depStrSplit[3];

            dependencies.add(new Dependency("maven", groupId + ":" + artifactId, version, dependencyType));
        }


        if (dependencies.isEmpty()) return null;
        return dependencies;

//        // parse pom xml
//        Document document;
//        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//        try (InputStream input = Files.newInputStream(pomFile.toPath())) {
//            DocumentBuilder builder = factory.newDocumentBuilder();
//            document = builder.parse(input);
//        } catch (IOException | ParserConfigurationException | SAXException e) {
//            e.printStackTrace();
//            return null;
//        }
//
//        // get dependencies
//        final Set<Dependency> dependencies = new HashSet<>();
//        final Node dependenciesNode = document.getElementsByTagName("dependencies").item(0);
//        if (dependenciesNode == null) return null;
//        final NodeList dependenciesNodes = dependenciesNode.getChildNodes();
//        for (int i = 0; i < dependenciesNodes.getLength(); i++) {
//            final Node dependency = dependenciesNodes.item(i);
//            final NodeList dependencyElements = dependency.getChildNodes();
//
//            // get metadata
//            String groupId = null;
//            String artifactId = null;
//            String version = null;
//
//            // get dependency elements
//            for (int j = 0; j < dependencyElements.getLength(); j++) {
//                // get data from tags
//                final Node dependencyMeta = dependencyElements.item(j);
//                final String tagName = dependencyMeta.getNodeName();
//                final String tagValue = dependencyMeta.getTextContent();
//
//                // extract metadata
//                switch (tagName) {
//                    case "groupId":
//                        groupId = tagValue;
//                        break;
//                    case "artifactId":
//                        artifactId = tagValue;
//                        break;
//                    case "version":
//                        version = tagValue;
//                        break;
//                }
//
//            }
//
//            // skip invalid data
//            if (groupId == null || artifactId == null || version == null) continue;
//
//            dependencies.add(new Dependency("maven", groupId + ":" + artifactId, version, DependencyType.DIRECT));
//        }
//
//
//        return dependencies;
    }

    /**
     * Mines dependencies from a package.json file.
     *
     * @param packageJsonFile File to read.
     * @return List of mined dependencies.
     */
    private static Set<Dependency> minePackageJsonDependencies(GHContent packageJsonFile) {
        System.out.println("Mining dependencies using package.json");

        // get package json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode document;
        try (InputStream input = packageJsonFile.read()) {
            document = mapper.readTree(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // get dependencies
        Set<Dependency> dependencies = new HashSet<>();
        JsonNode dependenciesNode = document.get("dependencies");
        if (dependenciesNode == null) return null;
        for (Iterator<String> it = dependenciesNode.fieldNames(); it.hasNext(); ) {
            String dependencyName = it.next();
            String dependencyVersion = dependenciesNode.get(dependencyName).asText();
            dependencies.add(new Dependency("npm", dependencyName, dependencyVersion, DependencyType.DIRECT));
        }
        return dependencies;
    }

    /**
     * Mines dependencies from a pubspec.yaml file.
     *
     * @param pubspecYamlFile File to read.
     * @return List of mined dependencies.
     */
    private static Set<Dependency> minePubspecYamlDependencies(GHContent pubspecYamlFile) {
        System.out.println("Mining dependencies using pubspec.yaml");

        // get pubspec yaml
        Yaml yaml = new Yaml();
        HashMap document;
        try (InputStream input = pubspecYamlFile.read()) {
            document = yaml.load(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // get dependencies
        Set<Dependency> dependencies = new HashSet<>();
        HashMap dependencyMap = (HashMap) document.get("dependencies");
        if (dependencyMap == null) return null;
        for (Object key :
                dependencyMap.keySet()) {
            String dependencyName = (String) key;
            Object dependencyVersion = dependencyMap.get(dependencyName);
            if (dependencyVersion instanceof String) {
                dependencies.add(new Dependency("pub.dev", dependencyName, (String) dependencyVersion, DependencyType.DIRECT));
            }
        }

        return dependencies;
    }

    /**
     * Reads dependencies from the saved mined deps file.
     *
     * @param fileName
     * @return
     */
    private static MinedRepo[] readMinedReposFromFile(String fileName) {
        System.out.println("Reading mined dependencies from " + fileName);

        // get package json
        File file = new File(fileName);
        ObjectMapper mapper = new ObjectMapper();
        MinedRepo[] repos;
        try (InputStream input = Files.newInputStream(file.toPath())) {
            repos = mapper.readValue(input, MinedRepo[].class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return repos;
    }

    /**
     * Writes dependencies of the mined repos into a file.
     *
     * @param repos    Mined repos.
     * @param fileName File to save to.
     * @throws IOException
     */
    private static void writeMinedReposToFile(List<MinedRepo> repos, String fileName) throws IOException {
        System.out.println("Saving mined repositories.");

        // get file
        File file = new File(fileName);

        // write list
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(file, repos);
    }

    /**
     * Clones a repo and returns an unzipped folder.
     *
     * @param repository The repository to clone.
     */
    private static File cloneRepo(GHRepository repository) {
        System.out.println("Cloning repo: " + repository);

        // get main branch
//        final String defaultBranch = repository.getDefaultBranch();

        // get download url
//        final String downloadUrl = repository.getHtmlUrl() + "/archive/refs/heads/" + defaultBranch + ".zip";

        // clone
//        final File repoZip = downloadFile(downloadUrl, "repo.zip");

        // get repos dir
        final File reposDir = new File("../miner-repos");
        try {
            FileUtils.deleteDirectory(reposDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!reposDir.exists()) {
            reposDir.mkdir();
        }

        // clone repo
        System.out.println("repo dir: " + reposDir.getAbsolutePath());
        CommandUtil.executeCommand("git clone " + repository.getHttpTransportUrl(), reposDir);

//        // unzip repo
//        try {
//            ZipFile zipFile = new ZipFile(repoZip);
//            zipFile.extractAll(reposDir.getAbsolutePath());
//        } catch (ZipException e) {
//            e.printStackTrace();
//        }
//
        // get extracted folder
        for (File file :
                reposDir.listFiles()) {
            if (file.isDirectory() && file.getName().equals(repository.getName())) {
                return file;
            }
        }
        return null;
    }

    /**
     * Downloads a given file.
     *
     * @param file             File to download.
     * @param downloadFileName Name of the downloaded file.
     * @return Downloaded file.
     * @throws IOException
     */
    private static File downloadFile(GHContent file, String downloadFileName) throws IOException {
        final String downloadUrl = file.getDownloadUrl();
        return downloadFile(downloadUrl, downloadFileName);
    }

    /**
     * Downloads a given file.
     *
     * @param downloadUrl      URL of the file to download.
     * @param downloadFileName Name of the downloaded file.
     * @return
     */
    private static File downloadFile(String downloadUrl, String downloadFileName) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(downloadUrl).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(downloadFileName)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            // handle exception
        }
        return new File(downloadFileName);
    }

    /**
     * Reads the list of repos to mine.
     *
     * @param path Path of the repo list.
     * @return Parsed list of repos.
     */
    private static List<Repo> readRepoList(String path) throws Exception {
        // get repo file
        System.out.println("Reading repo file: " + path);
        File repoFile = new File(path);
        if (!repoFile.exists()) throw new Exception("Repo file missing!");

        // parse file
        final List<String> reposRaw = Files.readAllLines(repoFile.toPath());
        final List<Repo> repos = new ArrayList<>();
        for (String repoRaw :
                reposRaw) {
            // skip comments
            if (repoRaw.startsWith("#")) continue;
            if (repoRaw.trim().isEmpty()) continue;

            // parse each repo
            try {
                final String[] repoSplit = repoRaw.split("~");
                final String repoUri = repoSplit[0];
                String repoCategory = null;
                if (repoSplit.length > 1) {
                    repoCategory = repoSplit[1];
                }
                final URI uri = URI.create(repoUri);
                final String uriPath = uri.getPath();
                final String uriHost = uri.getHost();
                switch (uriHost) {
                    case "github.com":
                        final String[] pathSplit = uriPath.split("/");
                        final String user = pathSplit[1];
                        final String repoName = pathSplit[2];
                        final Repo repo = new Repo(user + "/" + repoName, repoCategory);
                        repos.add(repo);
                        break;
                    default:
                        System.out.println("Unknown provider: " + uriHost);
                }
            } catch (Exception e) {
                System.out.println("Error parsing uri: " + repoRaw);
            }
        }
        System.out.println("Read " + repos.size() + " repos from the repo file.");

        return repos;
    }

    /**
     * Maps runtime args into a map.
     *
     * @param args Arguments list.
     * @return Arguments list.
     */
    private static Namespace mapParams(String[] args) {
        // parse arguments
        ArgumentParser parser = ArgumentParsers.newFor("RepoMiner").build()
                .defaultHelp(true)
                .description("Mine dependencies for a list of GitHub dependencies.");
        parser.addArgument("--file", "-f").nargs(1)
                .help("Text file with a list of repositories.");
        try {
            return parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            e.printStackTrace();
            parser.handleError(e);
            System.exit(1);
            return null;
        }
    }
}