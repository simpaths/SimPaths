# How to Run and Update Integration Tests

`RunSimPathsIntegrationTest` (`src/test/java/simpaths/integrationtest/RunSimPathsIntegrationTest.java`) runs the full simulation end-to-end and compares the resulting CSV output against reference files.

## What it does

1. Builds the input database with `test_create_database.yml` and checks that `input/input.mv.db` and related files are created.
2. Runs a full simulation with `test_run.yml`.
3. Compares the CSV files in the run's `output/<run-timestamp>/csv/` directory against the reference files in `src/test/java/simpaths/integrationtest/expected/`:
      - `WealthIncomeStatistics.csv`
      - `DemographicStatistics.csv`
      - `WellbeingByGender.csv`
      - `HealthStatistics.csv`
      - `LabourStatistics.csv`

`AlignmentStatistics.csv` is only checked for existence, not compared value-by-value.

Numeric values are compared with a tolerance rather than exact string matching (see below), so small floating-point differences between runs or platforms don't fail the test on their own.

## Running it locally

The test drives the packaged `multirun.jar` directly, so it needs a current build before it runs:

```bash
mvn package
mvn failsafe:integration-test
```

`mvn test` does **not** run this test — it is excluded by name there and only picked up by the failsafe plugin (`**/*IntegrationTest.java`).

## Adjusting the tolerance locally

Numeric comparison uses two constants at the top of `RunSimPathsIntegrationTest`:

```java
private static final double ABS_EPSILON = 1e-9;
private static final double REL_EPSILON = 1e-6;
```

For each pair of values, the allowed tolerance is `max(ABS_EPSILON, REL_EPSILON * max(|expected|, |actual|))`. If you are iterating locally and expect larger run-to-run noise (for example while experimenting with the number of simulated agents, or RNG seeding), you can temporarily raise one or both constants to stop unrelated noise from failing the test while you focus on a specific change. Revert them before committing — the checked-in values are the tolerances the project expects in CI.

## When the test fails

A failure prints a line-by-line diff between the actual and expected CSV files. If the difference is expected — for example you changed a substantive model process or the output structure — then:

1. Verify the new output is correct.
2. Replace the expected file with the new output, as the failure message shows:

    ```bash
    cp output/<run-timestamp>/csv/<File>.csv src/test/java/simpaths/integrationtest/expected/<File>.csv
    ```

3. Commit the updated expected file(s) so the change is visible in your pull request and the test passes for reviewers.
