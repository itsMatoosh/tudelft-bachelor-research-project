plugins {
    id("java")
}

group = "me.matoosh.repominer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("net.sourceforge.argparse4j:argparse4j:0.9.0")
    implementation("org.kohsuke:github-api:1.318")
    // https://mvnrepository.com/artifact/net.lingala.zip4j/zip4j
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // https://mvnrepository.com/artifact/com.googlecode.json-simple/json-simple
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    // https://mvnrepository.com/artifact/org.yaml/snakeyaml
    implementation("org.yaml:snakeyaml:2.2")

}

tasks.test {
    useJUnitPlatform()
}