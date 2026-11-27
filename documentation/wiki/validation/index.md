---
title: Model Validation
hide:
  - toc
---

<div class="validation-masthead" markdown="1">
<div class="validation-masthead__inner" markdown="1">

# Model Validation

<div class="validation-masthead__intro" markdown="1">

This section explains the current procedures used to validate SimPaths inputs and outputs, from regression checks to aggregate comparisons against external survey data. Validation is a core part of maintaining trust in the model after changes to parameters, code, or input data, and it provides a structured way to spot specification or implementation issues early.

The procedures below describe how to perform the checks; they do not present a set of validation results for a particular model release.

</div>
<nav class="validation-sections" aria-label="Validation sections">
  <a href="#1-introduction">Scope of the checks</a>
  <a href="#2-obtaining-the-validation-scripts">Get the scripts</a>
  <a href="#3-running-the-validation-scripts">Run the checks</a>
</nav>
</div>
</div>

<div class="validation-sheet" markdown="1">

<section class="validation-scope" markdown="1">

## Scope of the checks { #1-introduction }

The procedures documented here cover two kinds of checks:

1. **Validating regression estimates.** Check the regressions governing processes such as leaving the parental home or returning to education. Use the estimated coefficients to generate predictions for the estimation sample, then compare aggregate statistics from those predictions with the observed sample.

2. **Validating simulated output.** Run SimPaths over 2011–2023 and compare aggregate outputs with benchmarks from the UK Household Longitudinal Study (UKHLS). These checks assess trends over time and distributions within each year, rather than the accuracy of individual trajectories.

</section>

<section class="validation-downloads" markdown="1">

## Obtaining the validation scripts { #2-obtaining-the-validation-scripts }

Validation procedures are currently executed in Stata. The corresponding do-files are located in the validation subfolder on the `develop` branch of the SimPaths GitHub repository.

You can access these files in one of three ways:

1. **Clone the repository.** This is suitable for developers who want the full version history or plan to contribute changes. See the [Working in GitHub guide](../developer-guide/working-in-github.md).

2. **Download the repository as a ZIP file.** This provides a snapshot of all files on the selected branch.

    - In the GitHub interface, select the `develop` branch, click Code, and choose Download ZIP.
    - Extract the ZIP file locally and navigate to the validation folder.

3. **Download individual files directly from GitHub.** This is suitable if you only need a few specific scripts.

    - Select the `develop` branch and navigate to the desired file in the validation folder.
    - Click the Download raw file icon (the downward arrow) at the top right of the file viewer to save it locally.

These methods differ in how much of the repository you download. Downloading an individual script does not include the other files it may need.

</section>

<section class="validation-run" markdown="1">

## Running the validation scripts { #3-running-the-validation-scripts }

Once you have obtained the relevant validation files, the next step is to run them in Stata.
This section explains how to set up your working environment, what data are required, and how to execute the validation do-files for each stage of validation.

<div class="validation-run__grid" markdown="1">
<section markdown="1">

### Validating regression estimates { #31-validating-regression-estimates }

These do-files are contained in the subfolder 01_estimate_validation.
Before running these scripts, four preparatory steps are required:

1. **Run the regression estimation do-files.**
    The validation do-files require datasets produced during the regression estimation stage. Ensure that the estimation do-files have been run and their output data are available before proceeding.

2. **Set up the file structure.**
    Place the downloaded do-files in a do_files subfolder within your estimate-validation folder. Create data and graphs subfolders alongside it. Within graphs, create:

    - `education`
    - `fertility`
    - `health`
    - `home_ownership`
    - `income`
    - `leave_parental_home`
    - `partnership`
    - `retirement`
    - `wages`

3. **Check the location of the input data files.**
    Place the necessary data files in the data subfolder. These contain “sample” in their names (e.g. E1_sample) and are produced by the regression estimation do-files.

4. **Update directory paths in 00_master.do.**
    Open 00_master.do and update the global file paths. With the structure described above, set dir_work to the main estimate-validation folder.

Run the validation do-files to produce the plots once these steps are complete.

</section>
<section markdown="1">

### Validating the simulated output { #32-validating-the-simulated-output }

The do-files for validating the simulated output are contained in the subfolder 02_simulated_output_validation.
These should be run after executing SimPaths, as they rely on a number of .csv files produced by the model.

Before running these scripts, complete the following preparatory steps:

1. **Obtain simulated output from SimPaths.**
    See [Running Your First Simulation](../getting-started/first-simulation.md) or [Multiple Runs](../user-guide/multiple-runs.md) for instructions on running the model and obtaining its outputs.

2. **Set up the file structure.**
    Place the downloaded do-files in a do_files subfolder within your simulation-validation folder. Create data and graphs subfolders alongside it. Within graphs, create:

    - `care`
    - `children`
    - `disability`
    - `economic_activity`
    - `education`
    - `health`
    - `hours_worked`
    - `income/capital_income`
    - `income/disposable_income`
    - `income/equivalized_disposable_income`
    - `income/gross_income`
    - `income/gross_labour_income`
    - `income/pension_income`
    - `inequality`
    - `partnership`
    - `poverty`
    - `wages`

    These subfolders will contain the plots produced by the corresponding do-files.

3. **Place the input data in the data subfolder.**
    Include the simulated output CSV files named `Person`, `BenefitUnit` and `Household`. You also need Understanding Society survey data for comparison. The initial population files currently used are:

    - ukhls_pooled_all_obs_01
    - ukhls_pooled_all_obs_09

4. **Update directory paths in 00_master.do.**
    Open 00_master.do in the do_files subfolder. In “Define directories”, set the global dir_path to the main simulation-validation folder. Run the file up to, but not including, “Run do files” to set directories and parameters, adjusting these as necessary.

Run the validation do-files to produce the plots once these steps are complete.

</section>
</div>
</section>

</div>
