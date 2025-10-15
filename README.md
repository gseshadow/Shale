# Shale

Modernised Maven configuration for the Shale intake generator. The project now
uses the conventional `src/main/java` and `src/main/resources` layout so that it
can be packaged and updated using standard Java tooling.

## Prerequisites

* Java 17 JDK (or newer)
* Apache Maven 3.6+

## Building

```bash
mvn clean package
```

The command above produces both the regular application JAR and a
`jar-with-dependencies` assembly under `target/`.

## Running the application

When executing directly from Maven use the JavaFX plugin:

```bash
mvn javafx:run
```

Alternatively, launch the assembled JAR:

```bash
java -jar target/IntakeGeneratorMaven-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

All bundled assets are delivered from the classpath and copied to the user's
application data directory the first time the program runs.
