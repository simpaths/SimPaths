# SimPaths API

The API documentation is generated from Javadoc comments in the Java source. Use it to inspect classes and methods; use the [Repository Guide](../repository-guide.md) and [model schedule](../jasmine/model-and-schedule.md) to understand how those components work together.

## Generate the API locally {#1-introduction}

From the repository root, with the project's JDK and Maven installed:

```bash
mvn javadoc:javadoc
```

Inspect the generated HTML under `target/reports/apidocs/` or `target/site/apidocs/`, depending on the Maven Javadoc plugin version and configuration. The command output reports the destination.

When changing a public method, document its purpose, inputs, return value, side effects and relevant assumptions. Verify the generated page before submitting the change through the normal code-review process.

## Publishing workflow {#2-workflow-details}

The [Javadoc workflow](https://github.com/simpaths/SimPaths/blob/b223738b9cdf1d814cc3c6f09b04bc4930d3c667/.github/workflows/publish-javadoc.yml) builds on pushes to `develop` and pull requests to `main` or `develop`. It compiles SimPaths, generates Javadoc and includes a publishing step targeting the `javadoc` branch.

Check the actual workflow run before assuming that a source change has been published. Generation and publication are separate steps, and the publishing step has its own event condition.

The [generated Javadoc branch](https://github.com/simpaths/SimPaths/tree/javadoc) is separate from this MkDocs site. The documentation website uses its own `documentation` branch and [Pages workflow](https://github.com/simpaths/SimPaths/blob/documentation/.github/workflows/deploy-docs.yml); updating API output does not by itself rebuild these explanatory pages.
