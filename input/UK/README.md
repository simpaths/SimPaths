# WELLSIM macro bundles for SimPaths UK

| Directory | Simulation start | Use |
|---|---|---|
| `MacroModel` | 2023 | Default bundle lookup and UK reference export |
| `MacroModel-YYYYq1` | 2015–2022 | One bundle per year for retrospective runs. The 2019 bundle also works with the UK training data. |

For a macro-enabled run, set `startYear` to the year in the bundle's
`growth_terminal_state.json`. Set `mm_macroBundleDir` under `model_args` to
select a bundle explicitly. The default location is `UK/MacroModel` under the
configured input directory. Java reads the exported files at runtime.

For example, a 2015 trend run uses:

```yaml
startYear: 2015

model_args:
    mm_macroModel: ramsey
    mm_macroBundleDir: input/UK/MacroModel-2015q1
```

Training data support the 2019 start. The other starts require the
corresponding UK research initial population and UKMOD donor inputs.

Select `ramsey` for the trend or `ramsey_dsge` for the trend and cycle. The
`dsge` mode is available for cycle diagnostics. The normal UK model starts in
`off` mode. A run with a Ramsey trend uses the start-year real-wage index in
the wage equation and the macro trend supplies subsequent growth. The UK
first-year schedule uses the initial population's wages and hours. The
macro wage reaches labour supply from the second simulated year.

Each earlier bundle re-solves the Ramsey handoff using the existing UK
calibration and the same population alignment workbook as UK SimPaths.
All bundles use the original fitted UK DSGE policy and shock files. The
exports trace back to the SimPathsEU WELLSIM checkout at
`304a8aca4040e17483d5d324553d0f7c8b6401e2`.
The earlier bundles' provenance records identify both the date-specific
Ramsey detrending audit and the original data used to estimate the DSGE.

See the [UK rebuild workflow](../../input_processing/macromodel/README.md) to
prepare a bundle for another start year or to refresh the underlying data
and re-estimate the DSGE.

## Training smoke test

From the repository root, build the JAR and training database, then run the
2019–2022 macro test:

```text
mvn package
java -jar multirun.jar -DBSetup -config test_create_database.yml
java -jar multirun.jar -config test_macro_uk.yml
```

The smoke test applies a one-standard-deviation UK rate shock in 2020Q1.
Open `csv/MacroPaths.csv` in the experiment output for the trend, cycle and
realised feedback paths. The training data support testing the integration.
