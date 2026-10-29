/*******************************************************************************
* PROJECT:      SimPaths UK - simulated output validation
* OBJECT:       High-cost unsecured debt: holdings, amounts and linked transitions
* AUTHOR:       Liang Shi
* LAST UPDATE:  28 October 2026
*
* Run from validation root: do "do_files/04_22_plot_unsecured_debt.do"
* Optional arguments:
*   survey_levels survey_transitions simulation_file cpi_file graph_dir stats_dir first_year last_year initial_year
*
* INPUTS
* - WAS summary targets and CPI from 03_create_WAS_validation_targets.do.
*   Rebuilt from enriched longitudinal WAS .dta; no workbook is required.
*   Keep the documented round-midpoint and interview-year CPIH conventions.
*   The person/BU WAS microdata targets use interview-year nominal amounts and
*   are NOT interchangeable with these fixed 2015-GBP midpoint summaries.
* - unsecured_debt_simulation_sample.dta: unique numeric run year idBu,
*   ageRefPerson and wealthUnsecuredDebtHighValue. Import completed BenefitUnit.csv
*   upstream. Debts must be nominal GBP, as assumed by the original validator;
*   they are deflated exactly once below. Do not supply already-deflated values.
*   Keep stable within-run IDs and the initial simulation year in prepared input.
*
* DEFINITIONS
* - Focus is HIGH-COST debt, matching the existing validation. Low-cost/total
*   debt benchmarks are not supplied here; do not equate high-cost debt with
*   total unsecured debt, or overwrite the separate WAS microdata debt targets.
* - Prevalence and mean_all use all BUs in each group; conditional means and
*   empirical percentiles use positive holders. No trimming or estimation screen.
* - Simulation BUs have equal weight within a run; independent runs have equal
*   weight. WAS weights are already reflected in the fixed benchmark summaries.
* - Age groups 1/2/3: reference-person ages 15-29/30-54/55-74. Group 0 includes
*   ALL BUs, including other/missing ages. The three age groups are not exhaustive.
* - Annual years 2017-2021 preserve observed WAS midpoint estimates in 2017/2019/
*   2021 and adjacent-round summary interpolations in 2018/2020 (open markers).
* - Two-year entry conditions on no high-cost debt at the start; persistence
*   conditions on positive debt at the start. Both endpoints must exist. Ages
*   refer to the origin BU reference person. Persistence is endpoint holding,
*   not uninterrupted holding. Surviving BU IDs define the simulation panel.
* - Default transition endpoints are read from survey targets (2017-2019 and
*   2019-2021), not inferred from the simulation's initial year. The new test run
*   starts in 2015, so 2017 is already projected, unlike the old 2017-start runs.
* - initial_year defaults to the earliest year in the supplied simulation input.
* - Missing risk sets/holder groups stay missing, not zero. All requested runs
*   must contribute for a simulation comparison. One run has no variation band.
* - Bands show mean +/-1.96 sample SD BETWEEN runs, not a CI of the mean. The
*   original validator used seed min/max ranges; those are a different statistic.
* - All line charts include zero on the y-axis, consistently with other modules.
*
* RECOMMENDED INITIAL POPULATION FOR THIS VALIDATION
* - To reproduce the historical HIGH-COST unsecured-debt validation, a simulation
*   genuinely initialised from the corresponding 2017 population is strongly
*   recommended. The relevant proxy is first observed in WAS round 6 (interviews
*   2016-2018, midpoint 2017); this does not mean all unsecured-debt data start then.
* - The current 2015 initial population may not be suitable for this comparison:
*   the high-cost proxy is unavailable in round 5, and the current wealth-matching
*   preparation recodes missing proxy values to zero. Initial holding can therefore
*   be understated, with the deficit persisting into later projected years.
* - The existing 2015-start fixture verifies the calculations, but does not
*   reproduce the historical 2017-start initial conditions. Simply filtering its
*   output to 2017 onward, or changing initial_year here, cannot reset debt stocks.
* - This recommendation concerns the historical debt-validation exercise; it does
*   not require changing the overall model's 2015 baseline. Check donor coverage
*   and missing-value handling even for a 2017 population; the start year alone
*   does not guarantee a representative initial debt distribution or a good fit.
* - See unsecured_debt_initialisation_review.md for the inspected input evidence.
*
* OUTPUTS: three JPG figures, four reusable statistics .dta files. Survey targets
* and model CSVs are read only. No simulation or estimation is run.
* No recorded run folder, seed or fixed number of runs belongs in this file.
*******************************************************************************/
version 18
set more off
args survey_levels survey_transitions simulation_file cpi_file graph_dir stats_dir first_year last_year initial_year
local data_root "${dir_data}"
if `"`data_root'"' == "" local data_root "./data"
local graph_root "${dir_output_files}"
if `"`graph_root'"' == "" local graph_root "./graphs"
if `"`survey_levels'"' == "" local survey_levels `"`data_root'/unsecured_debt_WAS_levels.dta"'
if `"`survey_transitions'"' == "" local survey_transitions `"`data_root'/unsecured_debt_WAS_transitions.dta"'
if `"`simulation_file'"' == "" local simulation_file `"`data_root'/unsecured_debt_simulation_sample.dta"'
if `"`cpi_file'"' == "" local cpi_file `"`data_root'/unsecured_debt_cpi.dta"'
if `"`graph_dir'"' == "" local graph_dir `"`graph_root'/unsecured_debt"'
if `"`stats_dir'"' == "" local stats_dir `"`data_root'"'
if "`first_year'" == "" local first_year 2017
if "`last_year'" == "" local last_year 2021
foreach parameter in first_year last_year {
    if missing(real("``parameter''")) | real("``parameter''") != floor(real("``parameter''")) exit 198
}
if `first_year' > `last_year' exit 198
foreach input in survey_levels survey_transitions simulation_file cpi_file {
    confirm file `"``input''"'
}
capture mkdir `"`graph_root'"'
capture mkdir `"`graph_dir'"'
capture mkdir `"`stats_dir'"'
tempfile levels_benchmark transition_benchmark endpoints cpi sim annual transitions
use `"`survey_levels'"', clear
isid year age_group
keep if inrange(year,`first_year',`last_year')
assert _N == 4*(`last_year'-`first_year'+1)
save "`levels_benchmark'", replace
use `"`survey_transitions'"', clear
isid end_year age_group
keep if start_year >= `first_year' & end_year <= `last_year'
if _N == 0 {
    display as error "The comparison window must contain at least one benchmark two-year interval."
    exit 2000
}
save "`transition_benchmark'", replace
quietly levelsof end_year, local(end_years)
preserve
keep start_year end_year
duplicates drop
assert end_year == start_year + 2
save "`endpoints'", replace
restore
use `"`cpi_file'"', clear
isid year
assert cpi > 0 & cpi < .
keep if inrange(year,`first_year',`last_year')
assert _N == `last_year'-`first_year'+1
save "`cpi'", replace

use `"`simulation_file'"', clear
confirm numeric variable run year idBu ageRefPerson wealthUnsecuredDebtHighValue
isid run year idBu
assert year == floor(year) & year < .
quietly summarize year, meanonly
if "`initial_year'" == "" local initial_year = r(min)
if missing(real("`initial_year'")) | real("`initial_year'") != floor(real("`initial_year'")) exit 198
if `initial_year' > `first_year' exit 198
keep if inrange(year,`first_year',`last_year')
quietly levelsof run, local(runs)
local expected_runs : word count `runs'
if `expected_runs' == 0 exit 2000
rename wealthUnsecuredDebtHighValue debt_nominal
assert debt_nominal >= 0 & debt_nominal < .
merge m:1 year using "`cpi'", keep(master match) assert(match) nogen
gen double debt = debt_nominal * 100 / cpi
gen byte holder = debt_nominal > 0
gen byte age_group = .
replace age_group = 1 if inrange(ageRefPerson,15,29)
replace age_group = 2 if inrange(ageRefPerson,30,54)
replace age_group = 3 if inrange(ageRefPerson,55,74)
save "`sim'", replace

tempname a t
postfile `a' double run int year byte age_group long n_total long n_hold double cpi ///
    double prevalence double mean_all double mean double p50 double p90 double p99 ///
    double mean_nominal double p50_nominal double p90_nominal double p99_nominal using "`annual'", replace
postfile `t' double run int start_year int end_year byte age_group ///
    long n_matched long n_entry_risk long n_persist_risk long n_entry_event long n_persist_event ///
    double entry double persistence using "`transitions'", replace
foreach run of local runs {
    use "`sim'", clear
    keep if run == `run'
    forvalues yy = `first_year'/`last_year' {
        forvalues ag = 0/3 {
            local sample "year == `yy'"
            if `ag' > 0 local sample "`sample' & age_group == `ag'"
            quietly count if `sample'
            local n_total = r(N)
            quietly count if `sample' & holder
            local n_hold = r(N)
            foreach metric in prevalence mean_all mean p50 p90 p99 mean_nominal p50_nominal p90_nominal p99_nominal {
                local `metric' = .
            }
            local index = .
            if `n_total' > 0 {
                local prevalence = `n_hold'/`n_total'
                quietly summarize cpi if `sample', meanonly
                local index = r(mean)
                quietly summarize debt if `sample', meanonly
                local mean_all = r(mean)
            }
            if `n_hold' > 0 {
                quietly summarize debt if `sample' & holder, meanonly
                local mean = r(mean)
                quietly _pctile debt if `sample' & holder, p(50 90 99)
                local p50 = r(r1)
                local p90 = r(r2)
                local p99 = r(r3)
                quietly summarize debt_nominal if `sample' & holder, meanonly
                local mean_nominal = r(mean)
                quietly _pctile debt_nominal if `sample' & holder, p(50 90 99)
                local p50_nominal = r(r1)
                local p90_nominal = r(r2)
                local p99_nominal = r(r3)
            }
            post `a' (`run') (`yy') (`ag') (`n_total') (`n_hold') (`index') ///
                (`prevalence') (`mean_all') (`mean') (`p50') (`p90') (`p99') ///
                (`mean_nominal') (`p50_nominal') (`p90_nominal') (`p99_nominal')
        }
    }
    xtset idBu year
    gen byte previous_holder = L2.holder
    gen byte previous_age_group = L2.age_group
    foreach yy of local end_years {
        forvalues ag = 0/3 {
            local sample "year == `yy' & !missing(previous_holder)"
            if `ag' > 0 local sample "`sample' & previous_age_group == `ag'"
            quietly count if `sample'
            local n = r(N)
            quietly count if `sample' & previous_holder == 0
            local ne = r(N)
            quietly count if `sample' & previous_holder == 1
            local np = r(N)
            assert `n' == `ne' + `np'
            quietly count if `sample' & previous_holder == 0 & holder == 1
            local ee = r(N)
            quietly count if `sample' & previous_holder == 1 & holder == 1
            local ep = r(N)
            local entry = .
            local persistence = .
            if `ne' > 0 local entry = `ee'/`ne'
            if `np' > 0 local persistence = `ep'/`np'
            post `t' (`run') (`yy'-2) (`yy') (`ag') (`n') (`ne') (`np') (`ee') (`ep') (`entry') (`persistence')
        }
    }
}
postclose `a'
postclose `t'

* Means/SDs are across independently calculated RUN statistics, never pooled BUs.
use "`annual'", clear
label define debt_age 0 "All ages" 1 "15-29" 2 "30-54" 3 "55-74", replace
label values age_group debt_age
isid run year age_group
char _dta[price_basis] "Nominal simulation balances *100/CPI once; comparison amounts in 2015 GBP"
sort run year age_group
save `"`stats_dir'/unsecured_debt_annual_statistics.dta"', replace
local means
local sds
local counts
foreach metric in prevalence mean_all mean p50 p90 p99 {
    local means `means' sim_`metric'=`metric'
    local sds `sds' sd_`metric'=`metric'
    local counts `counts' nr_`metric'=`metric'
}
collapse (mean) n_total n_hold `means' (sd) `sds' (count) `counts', by(year age_group)
gen int expected_runs = `expected_runs'
foreach metric in prevalence mean_all mean p50 p90 p99 {
    replace sim_`metric' = . if nr_`metric' != expected_runs
    gen double low_`metric' = sim_`metric' - 1.96*sd_`metric' if nr_`metric' > 1 & nr_`metric' == expected_runs
    gen double high_`metric' = sim_`metric' + 1.96*sd_`metric' if nr_`metric' > 1 & nr_`metric' == expected_runs
}
merge 1:1 year age_group using "`levels_benchmark'", assert(match) nogen
gen double prevalence_gap_pp = 100*(sim_prevalence-was_prevalence)
gen double mean_gap_pct = 100*(sim_mean/was_mean-1) if was_mean > 0 & was_mean < .
sort year age_group
save `"`stats_dir'/unsecured_debt_annual_comparison.dta"', replace

use "`transitions'", clear
label define debt_age 0 "All ages" 1 "15-29" 2 "30-54" 3 "55-74", replace
label values age_group debt_age
isid run end_year age_group
char _dta[age_basis] "Age of reference person at START endpoint"
sort run end_year age_group
save `"`stats_dir'/unsecured_debt_transition_statistics.dta"', replace
collapse (mean) n_matched n_entry_risk n_persist_risk n_entry_event n_persist_event ///
    sim_entry=entry sim_persistence=persistence (sd) sd_entry=entry sd_persistence=persistence ///
    (count) nr_entry=entry nr_persistence=persistence, by(start_year end_year age_group)
gen int expected_runs = `expected_runs'
foreach metric in entry persistence {
    replace sim_`metric' = . if nr_`metric' != expected_runs
    gen double low_`metric' = sim_`metric' - 1.96*sd_`metric' if nr_`metric' > 1 & nr_`metric' == expected_runs
    gen double high_`metric' = sim_`metric' + 1.96*sd_`metric' if nr_`metric' > 1 & nr_`metric' == expected_runs
}
merge 1:1 start_year end_year age_group using "`transition_benchmark'", assert(match) nogen
gen double entry_gap_pp = 100*(sim_entry-was_entry)
gen double persistence_gap_pp = 100*(sim_persistence-was_persistence)
sort end_year age_group
save `"`stats_dir'/unsecured_debt_transition_comparison.dta"', replace

capture program drop _debt_line
program define _debt_line
    syntax [if], METRIC(name) TITLE(string) UNIT(string) NAME(name) FIRST(integer) LAST(integer) [SCALE(real 1)]
    marksample selected
    tempvar sim survey low high
    gen double `sim' = sim_`metric'*`scale'
    gen double `survey' = was_`metric'*`scale'
    gen double `low' = low_`metric'*`scale'
    gen double `high' = high_`metric'*`scale'
    twoway (rarea `low' `high' year if `selected', sort color(green%18) lwidth(none)) ///
        (connected `sim' year if `selected', sort lcolor(green) mcolor(green) msymbol(O) msize(small)) ///
        (line `survey' year if `selected', sort lcolor(navy) lpattern(dash)) ///
        (scatter `survey' year if `selected' & !was_interpolated, mcolor(navy) msymbol(D) msize(small)) ///
        (scatter `survey' year if `selected' & was_interpolated, mcolor(navy) msymbol(Dh) msize(small)), ///
        title("`title'", size(medium) color(black)) xtitle("Year", size(small)) ///
        ytitle("`unit'", size(small)) xlabel(`first'(1)`last', labsize(small)) ///
        yscale(range(0)) ylabel(#6, angle(horizontal) labsize(small) format(%9.0fc)) ///
        legend(off) graphregion(color(white)) plotregion(color(white)) name(`name', replace)
    drop `sim' `survey' `low' `high'
end
local variation_note "Mean +/-1.96 SD across `expected_runs' runs; between-run variation."
if `expected_runs' == 1 local variation_note "One simulation run; no between-run variation band."
local source_note "Green: SimPaths. Dashed navy: WAS; open markers indicate interpolated years."
* Keep the initial-population limitation visible when figures are read alone.
local initialisation_note "The high-cost debt proxy is unavailable in WAS R5 (midpoint 2015); first observed in R6 (midpoint 2017)."
local recommendation_note "Therefore, a corresponding 2017 initial population built via SimPathsStart is recommended for this validation."
use `"`stats_dir'/unsecured_debt_annual_comparison.dta"', clear
_debt_line if age_group == 0, metric(prevalence) title("All benefit units") unit("High-cost debt holders (%)") ///
    scale(100) name(debt_all) first(`first_year') last(`last_year')
_debt_line if age_group == 2, metric(prevalence) title("Reference-person ages 30-54") unit("High-cost debt holders (%)") ///
    scale(100) name(debt_age30) first(`first_year') last(`last_year')
graph combine debt_all debt_age30, cols(2) ///
    title("High-cost unsecured debt holding", size(medium)) ///
    note("`source_note'" "`variation_note' Initial simulation year: `initial_year'." ///
         "`initialisation_note'" "`recommendation_note'", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(6) name(debt_holding, replace)
graph export `"`graph_dir'/validation_UK_unsecured_debt_holding.jpg"', replace width(2500) height(1350) quality(100)
foreach metric in mean_all mean p90 p99 {
    local title "Mean across all benefit units"
    if "`metric'" == "mean" local title "Mean among positive holders"
    if "`metric'" == "p90" local title "90th percentile among holders"
    if "`metric'" == "p99" local title "99th percentile among holders"
    _debt_line if age_group == 0, metric(`metric') title("`title'") unit("2015 GBP") ///
        name(debt_`metric') first(`first_year') last(`last_year')
}
graph combine debt_mean_all debt_mean debt_p90 debt_p99, cols(2) ///
    title("High-cost unsecured debt balances", size(medium)) ///
    note("`source_note'" "`variation_note'" ///
         "Legacy CPI conversion applied once to nominal simulation amounts; WAS amounts already in 2015 GBP." ///
         "`initialisation_note'" "`recommendation_note'", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(8) name(debt_amounts, replace)
graph export `"`graph_dir'/validation_UK_unsecured_debt_amounts.jpg"', replace width(2500) height(1750) quality(100)

use `"`stats_dir'/unsecured_debt_transition_comparison.dta"', clear
keep if age_group == 0
local interval_labels
foreach yy of local end_years {
    local start = `yy'-2
    local short = mod(`yy',100)
    local interval_labels `"`interval_labels' `yy' "`start'-`short'""'
}
foreach metric in entry persistence {
    tempvar sim survey low high
    gen double `sim' = 100*sim_`metric'
    gen double `survey' = 100*was_`metric'
    gen double `low' = 100*low_`metric'
    gen double `high' = 100*high_`metric'
    local title "Entry: no high-cost debt at origin"
    if "`metric'" == "persistence" local title "Persistence: high-cost debt at origin"
    twoway (rcap `low' `high' end_year, lcolor(green)) ///
        (connected `sim' end_year, sort lcolor(green) mcolor(green) msymbol(O)) ///
        (connected `survey' end_year, sort lcolor(navy) mcolor(navy) lpattern(dash) msymbol(D)), ///
        title("`title'", size(medium) color(black)) xtitle("") ytitle("Two-year rate (%)", size(small)) ///
        xlabel(`interval_labels', labsize(small)) ///
        yscale(range(0)) ylabel(#6, angle(horizontal) labsize(small) format(%5.1f)) ///
        legend(off) graphregion(color(white)) plotregion(color(white)) name(debt_`metric', replace)
}
graph combine debt_entry debt_persistence, cols(2) ///
    title("High-cost unsecured debt: two-year transitions", size(medium)) ///
    note("Green: SimPaths. Dashed navy: WAS. `variation_note'" ///
         "Linked endpoints only; persistence does not require continuous holding between endpoints." ///
         "Simulation uses surviving BU IDs; WAS benchmarks use observed round pairs." ///
         "`initialisation_note'" "`recommendation_note'", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(6) name(debt_transitions, replace)
graph export `"`graph_dir'/validation_UK_unsecured_debt_transitions.jpg"', replace width(2500) height(1350) quality(100)
display as result "UNSECURED DEBT VALIDATION COMPLETE: three figures, four statistics datasets, `expected_runs' run(s)."
