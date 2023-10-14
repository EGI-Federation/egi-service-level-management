## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

or if you have IntelliJ IDEA or JBang installed, you can change directory to
the project folder and run the following command:

```shell script
quarkus dev
```

> Quarkus now ships with a Dev UI, which is available at http://localhost:8083/q/dev/ (in dev mode only).
> The port on which the API runs can be changed from the [configuration](README.md#configuration).

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/service-level-management-1.0-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling
