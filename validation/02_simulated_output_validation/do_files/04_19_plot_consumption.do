/*******************************************************************************
* PROJECT:      SimPaths UK
* SECTION:      Simulated output validation
* OBJECT:       Consumption-to-income comparison figures
* AUTHORS:      Liang Shi
* LAST UPDATE:  27 October 2026
*
* INPUTS:
*  1. lcfs_validation_sample.dta from 03_create_LCF_validation_targets.do.
*  2. consumption_simulation_sample.dta, one BU per run and year, containing:
*       run year idBu ageRefPerson yDispMonth xDiscConsumptionAnnual
*     All fields must be numeric. Missing reference ages are allowed. Monthly
*     income and annual consumption must be on the same price basis.
*     Upstream simulation preparation should import these fields from completed
*     BenefitUnit.csv files. This plotting file contains no run-folder names.
*
* OUTPUTS:
*  - Three JPG figures: overall, reference-age panels and income-quintile panels.
*  - Survey statistics, run-specific statistics and comparison statistics (.dta).
*
* RUN from the validation root after preparing both input datasets:
*   do "do_files/04_19_plot_consumption.do"
* Optional arguments, in order:
*   survey_file simulation_file graph_dir stats_dir first_year last_year initial_year
* Quote paths containing spaces. Empty arguments select the defaults below.
* Alternatively a master may set dir_data, dir_output_files, min_sim_year,
* max_sim_year and consumption_initial_year. Arguments take precedence.
*
* DEFINITIONS:
* - Mean of BU-level C/Y, not the ratio of aggregate mean consumption and income.
* - Survey uses weighta (dwt_valid) and the prepared sample_consumption flag.
* - Simulation is unweighted and retains monthly disposable income >= GBP10.
*   This follows the existing consumption validation; the survey threshold is
*   GBP10 nominal WEEKLY. The two screens have not been harmonised here.
* - Income quintiles are national within each year and dataset/run, calculated
*   before age restrictions. Do not pool simulation records across runs.
* - Age bands refer to the reference adult: <45, 45-64 and 65-84.
* - The initial simulation year (default 2015) and earlier years are excluded.
* - Bands are mean +/- 1.96*sample SD of independent run-specific statistics.
*   They describe between-run variation, not confidence intervals for the mean.
*   Any number of runs is accepted; a single run produces no variation band.
*******************************************************************************/
version 18
set more off
args survey_file simulation_file graph_dir stats_dir first_year last_year initial_year

/*******************************************************************************
* 1 - Directories and comparison period
*******************************************************************************/
local data_root "${dir_data}"
if `"`data_root'"' == "" local data_root "./data"
local graph_root "${dir_output_files}"
if `"`graph_root'"' == "" local graph_root "./graphs"
if `"`survey_file'"' == "" local survey_file `"`data_root'/lcfs_validation_sample.dta"'
if `"`simulation_file'"' == "" local simulation_file `"`data_root'/consumption_simulation_sample.dta"'
if `"`graph_dir'"' == "" local graph_dir `"`graph_root'/consumption"'
if `"`stats_dir'"' == "" local stats_dir `"`data_root'"'
if "`first_year'" == "" local first_year "${min_sim_year}"
if "`last_year'" == "" local last_year "${max_sim_year}"
if "`initial_year'" == "" local initial_year "${consumption_initial_year}"
if "`first_year'" == "" local first_year 2016
if "`last_year'" == "" local last_year 2023
if "`initial_year'" == "" local initial_year 2015
foreach parameter in first_year last_year initial_year {
    if missing(real("``parameter''")) | real("``parameter''") != floor(real("``parameter''")) {
        display as error "`parameter' must be an integer year."
        exit 198
    }
}
if `first_year' > `last_year' | `last_year' <= `initial_year' {
    display as error "Select an ordered comparison period after initialisation."
    exit 198
}
confirm file `"`survey_file'"'
confirm file `"`simulation_file'"'
capture mkdir `"`graph_root'"'
capture mkdir `"`graph_dir'"'
capture mkdir `"`stats_dir'"'

/*******************************************************************************
* 2 - Shared calculation: overall, age means and national-quintile means
*******************************************************************************/
* age_group=0 means all ages; quintile=0 means all incomes. The selected outputs
* contain nine statistics per year: 1 overall, 3 age groups and 5 quintiles.
capture program drop _consumption_groups
program define _consumption_groups
    syntax, BYVARS(varlist) [WEIGHT(varname)]
    local wgt
    if "`weight'" != "" local wgt "[aw=`weight']"
    tempfile observations summaries
    save "`observations'", replace

    collapse (mean) mean_ratio=ratio `wgt', by(`byvars')
    gen byte age_group = 0
    gen byte quintile = 0
    save "`summaries'", replace

    use "`observations'", clear
    keep if inrange(age_group, 1, 3)
    collapse (mean) mean_ratio=ratio `wgt', by(`byvars' age_group)
    gen byte quintile = 0
    append using "`summaries'"
    save "`summaries'", replace

    use "`observations'", clear
    collapse (mean) mean_ratio=ratio `wgt', by(`byvars' quintile)
    gen byte age_group = 0
    append using "`summaries'"
    isid `byvars' age_group quintile
end

/*******************************************************************************
* 3 - Survey statistics from the prepared LCF targets
*******************************************************************************/
use `"`survey_file'"', clear
confirm numeric variable year sample_consumption valid_consToInc ///
    valid_incomeQuintile ageGroupConsumption dwt_valid
assert income_definition == "personal_net_ct"
assert expenditure_definition == "broad_net_ct"
keep if sample_consumption == 1 & inrange(year, `first_year', `last_year') ///
    & year > `initial_year'
assert _N > 0
assert !missing(valid_consToInc, dwt_valid) & dwt_valid > 0
assert inrange(valid_incomeQuintile, 1, 5)
rename (valid_consToInc valid_incomeQuintile ageGroupConsumption) ///
    (ratio quintile age_group)
_consumption_groups, byvars(year) weight(dwt_valid)
rename mean_ratio survey_mean
sort year age_group quintile
save `"`stats_dir'/consumption_survey_statistics.dta"', replace
tempfile survey_stats
save "`survey_stats'", replace

/*******************************************************************************
* 4 - Simulation statistics, separately within every run
*******************************************************************************/
use `"`simulation_file'"', clear
confirm numeric variable run year idBu ageRefPerson yDispMonth xDiscConsumptionAnnual
isid run year idBu
assert year == floor(year) & year < .
keep if inrange(year, `first_year', `last_year') & year > `initial_year'
assert _N > 0
quietly levelsof run, local(runs)
local expected_runs : word count `runs'
* Keep missing ages in overall and national-quintile results. Invalid/missing
* income or consumption cannot define C/Y and must not enter the quintile ranks.
keep if yDispMonth >= 10 & yDispMonth < . & xDiscConsumptionAnnual < .
assert _N > 0
gen double ratio = xDiscConsumptionAnnual / yDispMonth / 12
gen byte age_group = .
replace age_group = 1 if inrange(ageRefPerson, 0, 44)
replace age_group = 2 if inrange(ageRefPerson, 45, 64)
replace age_group = 3 if inrange(ageRefPerson, 65, 84)
* Calculate the four cutpoints on each run/year partition. This is equivalent
* to default xtile, nq(5), including ties, and avoids repeatedly sorting the
* entire multi-run panel. Values equal to a cutpoint stay in the lower group.
tempfile eligible_simulation income_cutpoints
save "`eligible_simulation'", replace
statsby q20=r(r1) q40=r(r2) q60=r(r3) q80=r(r4), by(run year) ///
    saving("`income_cutpoints'", double replace): ///
    _pctile yDispMonth, nq(5)
use "`eligible_simulation'", clear
merge m:1 run year using "`income_cutpoints'", assert(match) nogen
assert !missing(q20, q40, q60, q80)
gen byte quintile = 1 + (yDispMonth > q20) + (yDispMonth > q40) + ///
    (yDispMonth > q60) + (yDispMonth > q80)
drop q20 q40 q60 q80
assert inrange(quintile, 1, 5)
_consumption_groups, byvars(run year)
rename mean_ratio sim_mean
sort run year age_group quintile
save `"`stats_dir'/consumption_simulation_by_run.dta"', replace

/*******************************************************************************
* 5 - Equal-weight run means and between-run variation; match survey years
*******************************************************************************/
collapse (mean) sim_mean (sd) sim_sd=sim_mean (count) n_runs=sim_mean, ///
    by(year age_group quintile)
* A missing subgroup in one run must not silently change the set of runs.
assert n_runs == `expected_runs'
bysort year: assert _N == 9
gen double sim_low = sim_mean - 1.96 * sim_sd if n_runs > 1
gen double sim_high = sim_mean + 1.96 * sim_sd if n_runs > 1
merge 1:1 year age_group quintile using "`survey_stats'", keep(match) nogen
assert _N > 0
bysort year: assert _N == 9
quietly summarize year, meanonly
local plot_first = r(min)
local plot_last = r(max)
label var sim_mean "Equal-weight mean of run-specific mean C/Y"
label var sim_sd "Sample SD of run-specific mean C/Y"
label var survey_mean "LCF weighted mean of BU-level C/Y"
char _dta[band] "Mean +/- 1.96*SD across runs, not a confidence interval"
char _dta[quintiles] "National within year and source/run; formed before age grouping"
char _dta[sim_source] `"`simulation_file'"'
char _dta[survey_source] `"`survey_file'"'
sort year age_group quintile
save `"`stats_dir'/consumption_comparison.dta"', replace

/*******************************************************************************
* 6 - Figures: common axes, consistent colours, explicit variation-band notes
*******************************************************************************/
local band_note "Shading: simulation mean +/- 1.96 SD across `expected_runs' runs."
if `expected_runs' == 1 local band_note "One simulation run; no between-run variation band."
capture program drop _consumption_panel
program define _consumption_panel
    syntax [if], SUBTITLE(string) NAME(name) FIRST(integer) LAST(integer) [SMALL NOTE(string)]
    marksample selected
    local legend legend(order(2 "SimPaths" 3 "LCF") rows(1) position(6) size(small) region(lstyle(none)))
    local ticks "`first'(1)`last'"
    if "`small'" != "" local legend "legend(off)"
    if "`small'" != "" {
        local ticks
        forvalues yy = `first'(2)`last' {
            if `yy' == `first' | `yy' <= `last' - 2 local ticks "`ticks' `yy'"
        }
        local ticks "`ticks' `last'"
    }
    twoway ///
        (rarea sim_low sim_high year if `selected', sort color(green%18) lwidth(none)) ///
        (connected sim_mean year if `selected', sort lcolor(green) mcolor(green) ///
            msymbol(O) msize(small) lwidth(medthick)) ///
        (connected survey_mean year if `selected', sort lcolor(navy) mcolor(navy) ///
            lpattern(dash) msymbol(D) msize(small) lwidth(medthick)), ///
        title("`subtitle'", size(medium) color(black)) ///
        xtitle("Year", size(small)) ytitle("Mean consumption / income", size(small)) ///
        xlabel(`ticks', labsize(small)) ///
        yscale(range(0)) ylabel(#6, angle(horizontal) labsize(small) format(%4.2f)) ///
        yline(1, lcolor(gs12) lpattern(shortdash)) ///
        graphregion(color(white)) plotregion(color(white)) ///
        note("`note'", size(vsmall)) `legend' name(`name', replace)
end

* 6.1 Overall comparison.
_consumption_panel if age_group == 0 & quintile == 0, ///
    subtitle("Consumption-to-income ratio: all benefit units") ///
    name(consumption_overall) first(`plot_first') last(`plot_last') ///
    note("Weighted LCF and unweighted SimPaths means of BU-level C/Y. `band_note'")
graph export `"`graph_dir'/validation_UK_consumption_ratio_overall.jpg"', ///
    replace width(2400) height(1350) quality(100)

* 6.2 Reference-age comparisons. All panels use the same y-axis scale.
local age_titles `""Under 45" "Ages 45-64" "Ages 65-84""'
forvalues aa = 1/3 {
    local age_title : word `aa' of `age_titles'
    _consumption_panel if age_group == `aa' & quintile == 0, ///
        subtitle("`age_title'") name(consumption_age`aa') ///
        first(`plot_first') last(`plot_last') small
}
graph combine consumption_age1 consumption_age2 consumption_age3, ///
    cols(3) ycommon xcommon imargin(small) ///
    title("Consumption-to-income ratio by reference age", size(medium) color(black)) ///
    note("Green: SimPaths. Dashed navy: LCF. `band_note'" ///
         "Means of BU-level C/Y. Age is that of the reference adult.", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(5) name(consumption_by_age, replace)
graph export `"`graph_dir'/validation_UK_consumption_ratio_by_age.jpg"', ///
    replace width(2400) height(1100) quality(100)

* 6.3 National income-quintile comparisons. The quintile boundaries are allowed
* to differ between survey and simulation, and between simulation years/runs.
forvalues qq = 1/5 {
    local q_title "Income quintile `qq'"
    if `qq' == 1 local q_title "Bottom income quintile"
    if `qq' == 5 local q_title "Top income quintile"
    _consumption_panel if age_group == 0 & quintile == `qq', ///
        subtitle("`q_title'") name(consumption_q`qq') ///
        first(`plot_first') last(`plot_last') small
}
graph combine consumption_q1 consumption_q2 consumption_q3 consumption_q4 consumption_q5, ///
    cols(3) ycommon xcommon imargin(small) ///
    title("Consumption-to-income ratio by income quintile", size(medium) color(black)) ///
    note("Green: SimPaths. Dashed navy: LCF. `band_note'" ///
         "National income quintiles formed within year and source/run, before any age restriction.", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(8) name(consumption_by_quintile, replace)
graph export `"`graph_dir'/validation_UK_consumption_ratio_by_income_quintile.jpg"', ///
    replace width(2400) height(1600) quality(100)

capture program drop _consumption_groups
capture program drop _consumption_panel
display as result "Saved three consumption figures and comparison datasets; `expected_runs' run(s), `plot_first'-`plot_last'."
