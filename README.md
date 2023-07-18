# EGI Service Level Management API

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/EGI-Federation/egi-service-level-management?color=darkcyan&label=Release)
![GitHub issues](https://img.shields.io/github/issues/EGI-Federation/egi-service-level-management?label=Issues)
![GitHub issue custom search in repo](https://img.shields.io/github/issues-search/EGI-Federation/egi-service-level-management?label=Bugs&color=red&query=is%3Aopen%20label%3Abug)

The Service Level Management API is a component of the [EGI IMS Tools](../../README.md)
service, which provides tools to automate and simplify the IMS processes. This component supports the
[Service Level Management](https://confluence.egi.eu/display/IMS/Service+Level+Management+SLM)
(SLM) process.

This project uses [Quarkus](https://quarkus.io), the Supersonic Subatomic Java Framework.
It requires [Java 17](https://openjdk.org/projects/jdk/17/).

## Authentication and authorization

TBD

## Configuration

TBD

## Building and packaging

For details on how to build the API, [look here](BUILDING.md).

For details on how to package the API and deploy it as a component of the IMS Tools service,
[look here](../../deploy/README.md).

## Related guides

- [REST server implementation](https://quarkus.io/guides/resteasy-reactive) Writing reactive REST services
- [REST client implementation](https://quarkus.io/guides/rest-client-reactive): REST client to easily call APIs
- [Configuration reference](https://quarkus.io/guides/config-reference): Configuration reference guide
- [YAML Configuration](https://quarkus.io/guides/config#yaml): Use YAML to configure your application
- [Introduction to CDI](https://quarkus.io/guides/cdi): Contexts and dependency injection guide
- [OpenTelemetry support](https://quarkus.io/guides/opentelemetry): Adding observability to your application
- [Metrics with Micrometer](https://quarkus.io/guides/micrometer): Sending API metrics to Prometheus
- [Swagger UI](https://quarkus.io/guides/openapi-swaggerui): User-friendly UI to document and test your API
- [Mutiny Guides](https://smallrye.io/smallrye-mutiny/2.1.0/guides): Reactive programming with Mutiny
- [Optionals](https://dzone.com/articles/optional-in-java): How to use Optional in Java
