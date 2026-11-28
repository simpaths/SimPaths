# Rebuild the WELLSIM macro bundle for SimPaths UK

SimPaths reads a prepared bundle in Java. The build sources here produce the
Ramsey trend and the quarterly Dynare policy matrices for that bundle. See the
[UK run guide](../../input/UK/README.md) for the shipped 2015Q1–2023Q1
bundles and the Java smoke test.

The pipeline was ported from SimPathsEU at
`304a8aca4040e17483d5d324553d0f7c8b6401e2`. The checked-in UK aggregate data and
original DSGE estimation sample span 2000Q1–2025Q1.
[data/PROVENANCE_UK.txt](data/PROVENANCE_UK.txt) records the data vintage and
[stata/make_datasets.do](stata/make_datasets.do) records the ONS, OECD and
Eurostat series identifiers and transformations. The population workbook is
the same `input/alignment_files/align_popProjections.xlsx` used in UK SimPaths.

## Rebuild for a different start year

From `input_processing/macromodel` in MATLAB, run:

```matlab
bundle = rebuild_uk_bundle(2019);
```

Use the returned absolute directory as `model_args.mm_macroBundleDir` and set
`startYear` to the same year in the UK run configuration. For a chosen workspace
location, pass a second argument such as `'../../output/uk-2019-rebuild'`.
Each call creates a fresh workspace, by default under `output/macro-rebuilds/`.
The returned bundle is in that workspace's `input/UK/MacroModel` directory.
The checkout's tracked inputs and ready-to-run bundles are preserved.

This command uses MATLAB with the Statistics and Machine Learning Toolbox
and a JVM. It was verified with MATLAB R2026a. It re-solves the Ramsey model
and exports capital, productivity, employment, population and hours for the
requested January handoff. It reuses the calibrated UK Ramsey parameters
and the fitted UK DSGE policy matrices. Keep the workspace to retain the
manifest references to both the handoff's Ramsey detrending data and the
original sample used to fit the DSGE. The lead-in data and microsimulation
inputs need to cover the chosen year.

## Refresh the quarterly data

From `input_processing/macromodel` in MATLAB, create a work directory:

```matlab
modelFolder = prepare_uk_workspace();
disp(modelFolder)
```

Set Stata's working directory to the returned folder's `stata` subdirectory.
Use Stata 19. Install the DBnomics client and its dependencies if needed.

```stata
ssc install dbnomics
ssc install libjson
ssc install moss
```

The SSC client version 1.2.0 can stop at `variable series_num not found` on
single-series imports. If you hit this error, use `which dbnomics` to locate
the client and make the following change in a local copy on Stata's ado path.
In `dbnomics_import`, add these two lines immediately before the existing
``qui replace series_num = series_num + `offset'`` line.

```stata
capture confirm variable series_num
if _rc quietly generate long series_num = 0
```

If Mata reports `urlencode() is built-in`, rename the client's own
`urlencode` definition and its calls to `dbnomics_urlencode` in the same
copy. The live UK refresh was verified on StataNow 19.5 with these changes
and the cached UK CPI seasonal adjustment.

Then run the data script.

```stata
do make_datasets.do UK
```

The script writes the level-data CSV, an HP-filtered comparison CSV and the
provenance stamp under the workspace's `data/` directory. It reuses the
included `hicp_q_sa_uk.dta` seasonal adjustment by default. To refresh that
cache as well, install X-13ARIMA-SEATS in the workspace as described in
[the X-13 setup](tools/x13as/README.md), then enable
`do_HICP_seasonal_adjustment` in the staged Stata data script. Review the UK
sample dates and the CPI coverage together when extending the sample. The
cached price level covers through 2025Q4.

## Re-estimate and export the DSGE

Use the workspace made above, with either the shipped data or the refreshed
levels. In MATLAB, add the installed Dynare `matlab` folder to the path, then
change the working directory to `modelFolder` and run:

```matlab
check_dependencies('strict', true);
run_full_workflow('UK', 'projection_start', '2023q1');
```

The full workflow calibrates the Ramsey base-year levels, solves the trend
and demographic paths, estimates the DSGE, freezes the posterior modes and
exports both models to the workspace's `input/UK/MacroModel`. The original
fit used MATLAB R2025b and Dynare 6.5. Dynare's `use_dll` estimation step
also needs a C compiler. The country files under `country_params/` define
the UK assumptions, priors and measurement errors. The shared model
equations are in `Macromodel_template.mod`.

## Verify a rebuild

Check the export reports a verified bundle and the expected `handoff_time` in
`growth_terminal_state.json`. The reuse workflow should carry the original
`policy_A.csv`, `policy_Bs.csv`, `policy_C.csv`, `policy_D.csv`,
`shock_params.csv` and `model_info.json` unchanged. To run the 2019 UK shock
test against a rebuild, copy the example shock CSV from
`input/UK/MacroModel-2019q1` into the new bundle and point a copy of
`config/test_macro_uk.yml` at that bundle. See the [UK run guide](../../input/UK/README.md)
for the database setup and launch commands. Java should reproduce the fitted
Dynare hourly wage response (`w - h`) from the bundle's impulse responses.
