plugins {
    id("java-library")
}

java {
    withSourcesJar()
    withJavadocJar()
}

group = "io.github.qupath"
version = rootProject.version

base {
    group = "io.github.qupath"
}