/*******************************************************************************
* PROJECT: SimPaths UK - wealth-module validation
* AUTHOR: Liang Shi
* LAST UPDATE: 28 October 2026
*
* SETUP AND EXECUTION
* Edit the >>> EDIT <<< settings in section 1, then run from the repository root:
*   do "validation/02_simulated_output_validation/do_files/00_master_wealth.do"
* No separate configuration file or run-list CSV needs to be created.
* To run after the existing 18 validation modules, configure this file first and
* set run_wealth_validation=1 in 00_master.do. Its default remains 0.
*
* INPUTS
* - Completed SimPaths outputs only; no simulation or estimation is launched.
*   Give each independent run's CSV directory below. Use distinct directories,
*   include only completed runs, and set wealth_n_runs to their number. Run IDs
*   are assigned 1..N consistently across BenefitUnit.csv and Person.csv.
* - BenefitUnit.csv supplies consumption, housing and debt; Person.csv supplies
*   pensions. Only enabled modules require their respective survey/CSV inputs.
*   Actual first exported years must equal wealth_initial_year; dropping early
*   output years cannot reinitialise model stocks.
* - LCFS: lcfs_sorted.dta must use the personal_net_ct disposable-income and
*   broad_net_ct expenditure definitions, both net of allocated Council Tax.
*   Consumption estimation remains upstream. Target preparation checks the
*   compiled schema and selects the existing weighted BU comparison sample.
* - WAS: one consolidated was_wealthdata_5_8_longitudinal.dta, with finalised
*   debt/wealth definitions AND earnings, pp_contrate, ocdc_contrate_emee and
*   ocdc_contrate_emer. Keep full R5-R8 (2014-2022 interview years) for linkage.
*   It must have unique was_pid/was_round and one bu_rp reference row per BU.
*   Optional raw-data construction is in 02_compile_WAS_wealth_data.do; it is
*   separate from this validation entry point and behavioural estimation.
* - UKHLS: UKHLS_pooled_ipop.dta must contain idbenefitunit pno stm dag les_c4
*   contrateopee contrateoper contratepp total_pensions. Pension shares/wealth
*   follow pension_wealth_surv.do (Justin van de Ven, 12 August 2026): unweighted
*   contributor shares, full-pooled-file adult counts by idbenefitunit (dag>17),
*   float equal-adult wealth proxy, positive-holder means/own-wealth quintiles.
*   The reference's literal missing-value comparisons are retained and audited.
*   Contribution rates use WAS. No pension Excel benchmarks are imported.
*
* PREPARATION AND OUTPUTS
* The three 03 files prepare targets by survey: UKHLS (pensions mode), WAS and
* LCFS. The original no-argument UKHLS preparation remains available separately.
* BU stocks and Person pension data are imported separately, avoiding repeated
* BU wealth. Consumption income/expenditure retain the original float storage
* before income ranking; other simulation numeric fields use double precision.
* Statistics are calculated within run first, then equally averaged across runs.
* Bands show mean +/-1.96 sample SD BETWEEN runs, not a CI for the run mean.
* A single run has no between-run band. All line figures include zero.
*
* This file creates data/, graphs/ and checks/ under wealth_output_root. Its
* parent must exist. Re-running replaces named outputs; choose another output
* root for another scenario. The 14 reported JPGs are consumption (3), housing/
* mortgage (5), pensions (3), and high-cost debt (3). Prepared inputs and 20
* calculation datasets remain saved; supplementary figures are not drawn.
* checks/ records the log and wealth_runs_used.csv automatically for auditing.
* Survey files are read only; licensed data and generated outputs stay local.
*
* HIGH-COST DEBT INITIALISATION
* The high-cost proxy is unavailable in WAS R5 (midpoint 2015), first observed
* in R6 (midpoint 2017). A corresponding 2017 initial population built through
* SimPathsStart is recommended for this comparison. A 2015 baseline can still
* be assessed with that limitation, which is also stated on the debt figures.
* Debt amounts retain the original survey CPIH / simulation CPI conventions,
* documented in the WAS preparation and debt plotting do-files.
*******************************************************************************/
version 18
clear all
set more off
args obsolete_config
if `"`obsolete_config'"'!="" {
    display as error "Edit section 1 of this master and run it without a configuration argument."
    exit 198
}
* Avoid carrying wealth settings over from a previous Stata invocation.
capture macro drop wealth_*

/*******************************************************************************
* 1 - STATIC SETUP: edit paths and parameters here
*******************************************************************************/
* >>> EDIT <<< Code/output roots. Relative paths use Stata's working directory;
* absolute paths containing spaces are also supported.
global wealth_code_root "./validation/02_simulated_output_validation"
global wealth_output_root "./validation/02_simulated_output_validation/wealth_outputs"

* >>> EDIT <<< Independent completed runs. Add/remove numbered folders as needed
* and set wealth_n_runs accordingly. These are CSV directories, not their parents.
global wealth_n_runs 3
global wealth_csv_dir_1 "/path/to/first_completed_run/csv"
global wealth_csv_dir_2 "/path/to/second_completed_run/csv"
global wealth_csv_dir_3 "/path/to/third_completed_run/csv"
global wealth_initial_year 2015

* >>> EDIT <<< Enable modules independently (1=yes, 0=no).
global wealth_consumption 1
global wealth_housing 1
global wealth_pensions 1
global wealth_debt 1

* >>> EDIT <<< Compiled survey inputs. Required definitions are in the header.
global wealth_LCF_dir "/path/to/compiled_LCFS"
global wealth_WAS_file "/path/to/compiled_WAS/was_wealthdata_5_8_longitudinal.dta"
global wealth_UKHLS_file "/path/to/UKHLS_pooled_ipop.dta"

* >>> EDIT <<< Comparison windows. Defaults preserve the original exercise.
global wealth_consumption_first 2016
global wealth_consumption_last 2023
global wealth_housing_first 2015
global wealth_housing_last 2021
global wealth_pensions_first 2015
global wealth_pensions_last 2021
global wealth_pensions_rate_first 2016
global wealth_debt_first 2017
global wealth_debt_last 2021

/*******************************************************************************
* 2 - Check settings, prepare survey targets and import completed simulations
*******************************************************************************/
foreach setting in code_root output_root initial_year n_runs {
    if `"${wealth_`setting'}"'=="" {
        display as error "Missing setting in section 1: wealth_`setting'."
        exit 198
    }
}
if missing(real("${wealth_n_runs}")) | real("${wealth_n_runs}")<1 | ///
    real("${wealth_n_runs}")!=floor(real("${wealth_n_runs}")) {
    display as error "wealth_n_runs must be a positive integer."
    exit 198
}
local code "${wealth_code_root}/do_files"
local output "${wealth_output_root}"
local data `"`output'/data"'
local graphs `"`output'/graphs"'
local enabled = 0
foreach module in consumption housing pensions debt {
    if "${wealth_`module'}" == "" global wealth_`module' 1
    if !inlist("${wealth_`module'}","0","1") {
        display as error "wealth_`module' must be 0 or 1."
        exit 198
    }
    local enabled = `enabled' + ${wealth_`module'}
}
if `enabled' == 0 exit 198
if missing(real("${wealth_initial_year}")) | ///
    real("${wealth_initial_year}") != floor(real("${wealth_initial_year}")) exit 198
* The default windows reproduce the original validation settings. Survey years
* for WAS preparation stay complete (2014-2022), preserving endpoint linkage.
foreach module in housing pensions debt {
    if "${wealth_`module'_last}" == "" global wealth_`module'_last 2021
}
if "${wealth_consumption_first}" == "" global wealth_consumption_first 2016
if "${wealth_consumption_last}" == "" global wealth_consumption_last 2023
if "${wealth_housing_first}" == "" global wealth_housing_first 2015
if "${wealth_pensions_first}" == "" global wealth_pensions_first 2015
if "${wealth_pensions_rate_first}" == "" global wealth_pensions_rate_first 2016
if "${wealth_debt_first}" == "" global wealth_debt_first 2017
confirm file `"`code'/01_prepare_wealth_simulated_data.do"'
if ${wealth_consumption} confirm file "${wealth_LCF_dir}/lcfs_sorted.dta"
if ${wealth_housing} | ${wealth_pensions} | ${wealth_debt} ///
    confirm file "${wealth_WAS_file}"
if ${wealth_pensions} confirm file "${wealth_UKHLS_file}"
capture mkdir `"`output'"'
capture mkdir `"`data'"'
capture mkdir `"`graphs'"'
capture mkdir `"`output'/checks"'
* Record the inline run folders as an OUTPUT, not an external input template.
clear
set obs ${wealth_n_runs}
gen long run_id=_n
gen str2045 csv_dir=""
local n_runs=real("${wealth_n_runs}")
forvalues n=1/`n_runs' {
    local directory "${wealth_csv_dir_`n'}"
    if `"`directory'"'=="" {
        display as error "Missing completed-run directory: wealth_csv_dir_`n'."
        exit 198
    }
    replace csv_dir=`"`directory'"' in `n'
    assert csv_dir==`"`directory'"' in `n'
}
compress csv_dir
* Prevent one simulation being counted twice. The importer also checks this.
isid csv_dir
global wealth_runs_file `"`output'/checks/wealth_runs_used.csv"'
export delimited using "${wealth_runs_file}", replace

capture log close wealth_validation
log using `"`output'/checks/wealth_validation.log"', text replace name(wealth_validation)

* 03 files prepare supplied survey data; model estimation remains upstream.
if ${wealth_consumption} {
    do `"`code'/03_create_LCF_validation_targets.do"' ///
        "${wealth_LCF_dir}" `"`data'"' 2015 2023
}
if ${wealth_housing} | ${wealth_pensions} | ${wealth_debt} {
    local was_targets "all"
    if !${wealth_debt} local was_targets "microdata"
    if ${wealth_debt} & !${wealth_housing} & !${wealth_pensions} local was_targets "debt"
    do `"`code'/03_create_WAS_validation_targets.do"' ///
        "${wealth_WAS_file}" `"`data'"' 2014 2022 "`was_targets'"
}
if ${wealth_pensions} {
    do `"`code'/03_create_UKHLS_validation_targets.do"' "pensions" ///
        "${wealth_UKHLS_file}" `"`data'"' ${wealth_pensions_first} ${wealth_pensions_last}
}
* BU stocks remain at BU level; Person pension stocks remain at person level.
* The separate importer avoids joining repeated BU wealth onto person records.
do `"`code'/01_prepare_wealth_simulated_data.do"' ///
    "${wealth_runs_file}" `"`data'"' "${wealth_initial_year}" ///
    ${wealth_consumption} ${wealth_housing} ${wealth_pensions} ${wealth_debt}

if ${wealth_consumption} {
    do `"`code'/04_19_plot_consumption.do"' ///
        `"`data'/lcfs_validation_sample.dta"' `"`data'/consumption_simulation_sample.dta"' ///
        `"`graphs'/consumption"' `"`data'"' ///
        ${wealth_consumption_first} ${wealth_consumption_last} ${wealth_initial_year}
}
if ${wealth_housing} {
    do `"`code'/04_20_plot_housing_mortgage.do"' ///
        `"`data'/was_bu_validation_sample.dta"' `"`data'/housing_simulation_sample.dta"' ///
        `"`graphs'/housing_mortgage"' `"`data'"' ///
        ${wealth_housing_first} ${wealth_housing_last} ${wealth_initial_year} "as_recorded"
}
if ${wealth_pensions} {
    do `"`code'/04_21_plot_pensions.do"' ///
        `"`data'/ukhls_pension_targets.dta"' `"`data'/was_validation_sample.dta"' ///
        `"`data'/pension_person_simulation_sample.dta"' `"`graphs'/pensions"' `"`data'"' ///
        ${wealth_pensions_first} ${wealth_pensions_last} ${wealth_pensions_rate_first}
}
if ${wealth_debt} {
    do `"`code'/04_22_plot_unsecured_debt.do"' ///
        `"`data'/unsecured_debt_WAS_levels.dta"' `"`data'/unsecured_debt_WAS_transitions.dta"' ///
        `"`data'/unsecured_debt_simulation_sample.dta"' `"`data'/unsecured_debt_cpi.dta"' ///
        `"`graphs'/unsecured_debt"' `"`data'"' ///
        ${wealth_debt_first} ${wealth_debt_last} ${wealth_initial_year}
}
display as result "WEALTH VALIDATION COMPLETE: configured modules, supplied survey targets and completed runs."
log close wealth_validation
