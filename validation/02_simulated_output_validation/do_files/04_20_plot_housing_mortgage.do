/*******************************************************************************
* PROJECT:      SimPaths UK
* SECTION:      Simulated output validation
* OBJECT:       Housing and mortgage statistics and comparison figures
* AUTHORS:      Liang Shi
* LAST UPDATE:  27 October 2026
*
* INPUTS:
* - was_bu_validation_sample.dta from 03_create_WAS_validation_targets.do.
*   Retain complete WAS R5-R8 (interviews 2014-2022) for linked transitions,
*   even when the annual comparison window is 2015-2021.
* - housing_simulation_sample.dta: unique numeric run year idBu, with numeric
*   wealthPrptyValue and wealthMortgageDebtValue. Import from completed BU CSVs
*   upstream, keeping the initial year and stable within-run IDs.
*
* OUTPUTS: five JPG comparisons and reusable .dta statistics. Run-folder names,
* seeds and a fixed number of runs do not belong in this reusable calculation.
* The separate test runner supplies one recorded simulation as a test fixture.
*
* RUN from the validation root: do "do_files/04_20_plot_housing_mortgage.do"
* Optional arguments, in order:
*   survey_file simulation_file graph_dir stats_dir first_year last_year initial_year price_basis
* Empty arguments use the defaults below or the master's dir_data,
* dir_output_files, min_sim_year, max_sim_year and housing_initial_year globals.
* price_basis defaults to "as_recorded": no price conversion. This replicates
* the existing workbook convention. Verify source units before using a common
* real-price label; the supplied WAS amounts are interview-year GBP.
*
* DEFINITIONS:
* - BU holding shares use observed property/mortgage stocks, not just holders.
* - Net housing = property minus mortgage among owners, including negative or
*   zero equity. Mortgage amounts condition on a positive mortgage balance.
* - WAS is weighted; simulation BUs are unweighted within each run. Means and
*   percentiles use analytic weights; amount quintiles use frequency weights,
*   following the legacy housing validator. Positive integer weights required.
* - Quintiles rank each outcome among its holders, separately by source/year/run.
* - Run means receive equal weight. Period quintile summaries also weight years
*   equally, and their SD is calculated from RUN-SPECIFIC period averages.
* - Linked WAS transitions follow was_pid across adjacent rounds with two-year
*   interview gaps, labelled by round midpoints. Simulation links idBu within
*   run at two-year calendar endpoints starting in initial_year.
* - Entry conditions on no holding at the start; persistence on holding at the
*   start. Additional mortgage rates condition on END-period homeownership.
*   Endpoint persistence does not mean uninterrupted holding between endpoints.
* - Use destination-round WAS weights, without a longitudinal attrition adjustment.
* - Mean +/- 1.96*sample SD describes between-run variation, not a confidence
*   interval. A single run has no band. Missing outcomes/risk sets stay missing.
*******************************************************************************/
version 18
set more off
args survey_file simulation_file graph_dir stats_dir first_year last_year initial_year price_basis

/*******************************************************************************
* 1 - Settings and input paths
*******************************************************************************/
local data_root "${dir_data}"
if `"`data_root'"' == "" local data_root "./data"
local graph_root "${dir_output_files}"
if `"`graph_root'"' == "" local graph_root "./graphs"
if `"`survey_file'"' == "" local survey_file `"`data_root'/was_bu_validation_sample.dta"'
if `"`simulation_file'"' == "" local simulation_file `"`data_root'/housing_simulation_sample.dta"'
if `"`graph_dir'"' == "" local graph_dir `"`graph_root'/housing_mortgage"'
if `"`stats_dir'"' == "" local stats_dir `"`data_root'"'
if "`first_year'" == "" local first_year "${min_sim_year}"
if "`last_year'" == "" local last_year "${max_sim_year}"
if "`initial_year'" == "" local initial_year "${housing_initial_year}"
if "`first_year'" == "" local first_year 2015
if "`last_year'" == "" local last_year 2021
if "`initial_year'" == "" local initial_year 2015
if "`price_basis'" == "" local price_basis "as_recorded"
foreach parameter in first_year last_year initial_year {
    if missing(real("``parameter''")) | real("``parameter''") != floor(real("``parameter''")) {
        display as error "`parameter' must be an integer year."
        exit 198
    }
}
if `first_year' > `last_year' | `last_year' <= `initial_year' {
    display as error "Select an ordered period including projected years."
    exit 198
}
if "`price_basis'" != "as_recorded" {
    display as error "This file implements as_recorded amounts only; no implicit deflation."
    exit 198
}
confirm file `"`survey_file'"'
confirm file `"`simulation_file'"'
capture mkdir `"`graph_root'"'
capture mkdir `"`graph_dir'"'
capture mkdir `"`stats_dir'"'

/*******************************************************************************
* 2 - Annual statistics and outcome-ranked quintiles for one source/run
*******************************************************************************/
capture program drop _housing_annual
program define _housing_annual
    syntax, HANDLE(name) QHANDLE(name) SOURCE(integer) RUN(real) FIRST(integer) LAST(integer)
    keep if inrange(year, `first', `last')
    quietly levelsof year, local(years)
    foreach yy of local years {
        forvalues oo = 1/2 {
            preserve
            keep if year == `yy'
            quietly count
            local n_all = r(N)
            * Pairwise observed stocks provide a consistent denominator. Record
            * exclusions explicitly; missing balances are never non-holding zeros.
            keep if !missing(property, mortgage)
            quietly count
            local n_total = r(N)
            local amount housing
            local holder owner
            if `oo' == 2 {
                local amount mortgage
                local holder mortgagor
            }
            local prevalence = .
            if `n_total' > 0 {
                quietly summarize `holder' [aw=weight], meanonly
                local prevalence = r(mean)
            }
            keep if `holder' == 1
            quietly count
            local n_hold = r(N)
            foreach metric in mean p50 p90 p99 p999 nonpositive {
                local `metric' = .
            }
            if `n_hold' > 0 {
                quietly summarize `amount' [aw=weight], meanonly
                local mean = r(mean)
                quietly _pctile `amount' [aw=weight], p(50 90 99 99.9)
                local p50 = r(r1)
                local p90 = r(r2)
                local p99 = r(r3)
                local p999 = r(r4)
                if `oo' == 1 {
                    tempvar nonpositive_flag
                    gen byte `nonpositive_flag' = housing <= 0
                    quietly summarize `nonpositive_flag' [aw=weight], meanonly
                    local nonpositive = r(mean)
                }
            }
            post `handle' (`source') (`run') (`yy') (`oo') (`n_all') (`n_total') (`n_hold') ///
                (`prevalence') (`mean') (`p50') (`p90') (`p99') (`p999') (`nonpositive')
            * All six groups are posted even when ties leave a quintile empty.
            * quintile=0 is a direct overall holder mean, not a mean of five means.
            tempvar q
            gen byte `q' = .
            if `n_hold' > 0 {
                drop `q'
                quietly xtile `q' = `amount' [fw=weight], nq(5)
            }
            forvalues qq = 0/5 {
                local condition "1"
                if `qq' > 0 local condition "`q' == `qq'"
                quietly count if `condition'
                local nq = r(N)
                local weighted_n = 0
                local avg = .
                if `nq' > 0 {
                    quietly summarize weight if `condition', meanonly
                    local weighted_n = r(sum)
                    quietly summarize `amount' [fw=weight] if `condition', meanonly
                    local avg = r(mean)
                }
                post `qhandle' (`source') (`run') (`yy') (`oo') (`qq') ///
                    (`nq') (`weighted_n') (`avg')
            }
            restore
        }
    }
end

/*******************************************************************************
* 3 - Two-year transitions for one source/run, including empty risk sets
*******************************************************************************/
capture program drop _housing_transitions
program define _housing_transitions
    syntax, HANDLE(name) SOURCE(integer) RUN(real) INITIAL(integer) LAST(integer)
    if `source' == 0 {
        xtset panel_id wave
        gen byte previous_owner = L.owner
        gen byte previous_mortgagor = L.mortgagor
        gen int previous_year = L.year
        assert year - previous_year == 2 if !missing(previous_owner, previous_mortgagor)
        gen int end_year = midpoint
    }
    else {
        keep if year >= `initial' & year <= `last' & mod(year - `initial', 2) == 0
        xtset panel_id year, delta(2)
        gen byte previous_owner = L.owner
        gen byte previous_mortgagor = L.mortgagor
        gen int end_year = year
    }
    gen byte paired = !missing(owner, mortgagor, previous_owner, previous_mortgagor)
    local first_end = `initial' + 2
    forvalues yy = `first_end'(2)`last' {
        quietly count if end_year == `yy' & paired
        local n_pairs = r(N)
        forvalues rr = 1/6 {
            local risk "previous_owner == 0"
            local event owner
            if `rr' == 2 local risk "previous_owner == 1"
            if `rr' >= 3 local event mortgagor
            if `rr' == 3 local risk "previous_mortgagor == 0"
            if `rr' == 4 local risk "previous_mortgagor == 1"
            if `rr' == 5 local risk "previous_mortgagor == 0 & owner == 1"
            if `rr' == 6 local risk "previous_mortgagor == 1 & owner == 1"
            quietly count if end_year == `yy' & paired & `risk'
            local n_risk = r(N)
            quietly count if end_year == `yy' & paired & `risk' & `event' == 1
            local n_event = r(N)
            local weighted_risk = 0
            local weighted_event = 0
            local rate = .
            if `n_risk' > 0 {
                quietly summarize weight if end_year == `yy' & paired & `risk', meanonly
                local weighted_risk = r(sum)
                if `n_event' > 0 {
                    quietly summarize weight if end_year == `yy' & paired & `risk' & `event' == 1, meanonly
                    local weighted_event = r(sum)
                }
                local rate = `weighted_event' / `weighted_risk'
            }
            post `handle' (`source') (`run') (`yy'-2) (`yy') (`rr') ///
                (`n_pairs') (`n_risk') (`n_event') (`weighted_risk') (`weighted_event') (`rate')
        }
    }
end

/*******************************************************************************
* 4 - Build small, common input schemas; preserve source-specific panel identity
*******************************************************************************/
tempfile survey_input simulation_input annual quintiles transitions survey_stats common_years
use `"`survey_file'"', clear
confirm numeric variable was_pid was_round year roundMidYear sample_bu dwt_valid ///
    valid_wealthPrptyValue valid_wealthMortgageDebtValue
keep if sample_bu == 1
isid was_pid was_round
keep was_pid was_round year roundMidYear dwt_valid valid_wealthPrptyValue valid_wealthMortgageDebtValue
rename (was_pid was_round roundMidYear dwt_valid valid_wealthPrptyValue valid_wealthMortgageDebtValue) ///
    (panel_id wave midpoint weight property mortgage)
save "`survey_input'", replace

use `"`simulation_file'"', clear
confirm numeric variable run year idBu wealthPrptyValue wealthMortgageDebtValue
isid run year idBu
assert year == floor(year) & year < .
quietly levelsof run if inrange(year, `first_year', `last_year'), local(runs)
local expected_runs : word count `runs'
if `expected_runs' == 0 {
    display as error "No simulation runs in the requested annual window."
    exit 2000
}
keep run year idBu wealthPrptyValue wealthMortgageDebtValue
rename (idBu wealthPrptyValue wealthMortgageDebtValue) (panel_id property mortgage)
gen double weight = 1
save "`simulation_input'", replace

tempname a q t
postfile `a' byte source double run int year byte outcome long n_all long n_total long n_hold ///
    double prevalence double mean double p50 double p90 double p99 double p999 double nonpositive_share ///
    using "`annual'", replace
postfile `q' byte source double run int year byte outcome byte quintile long n double weighted_n double mean ///
    using "`quintiles'", replace
postfile `t' byte source double run int start_year int end_year byte rate_id long n_pairs long n_risk long n_event ///
    double weighted_risk double weighted_event double rate using "`transitions'", replace

* source=0 denotes WAS; its run=-1 is only a placeholder, not a simulation seed.
local series "survey `runs'"
foreach entry of local series {
    local source = ("`entry'" != "survey")
    local run = -1
    if `source' == 0 use "`survey_input'", clear
    else {
        local run = real("`entry'")
        use "`simulation_input'", clear
        keep if run == `run'
    }
    assert weight > 0 & weight < . & weight == floor(weight)
    assert mortgage >= 0 if mortgage < .
    assert mortgage == 0 if property <= 0 & !missing(property, mortgage)
    gen double housing = property - mortgage
    gen byte owner = property > 0 if !missing(property, mortgage)
    gen byte mortgagor = mortgage > 0 if !missing(property, mortgage)
    preserve
    _housing_annual, handle(`a') qhandle(`q') source(`source') run(`run') ///
        first(`first_year') last(`last_year')
    restore
    _housing_transitions, handle(`t') source(`source') run(`run') initial(`initial_year') last(`last_year')
}
postclose `a'
postclose `q'
postclose `t'

/*******************************************************************************
* 5 - Annual survey comparison and equal-weight run summaries
*******************************************************************************/
local metrics prevalence mean p50 p90 p99 p999 nonpositive_share
use "`annual'", clear
isid source run year outcome
sort source run outcome year
char _dta[price_basis] "as_recorded: no additional price conversion"
save `"`stats_dir'/housing_annual_statistics.dta"', replace
preserve
keep if source == 0
drop source run
foreach vv in n_all n_total n_hold `metrics' {
    rename `vv' was_`vv'
}
save "`survey_stats'", replace
restore
keep if source == 1
bysort outcome year: assert _N == `expected_runs'
gen int n_runs = 1
local means
local sds
local counts
foreach vv of local metrics {
    local means `means' sim_`vv'=`vv'
    local sds `sds' sd_`vv'=`vv'
    local counts `counts' nr_`vv'=`vv'
}
collapse (sum) n_runs (mean) n_all n_total n_hold `means' (sd) `sds' (count) `counts', by(outcome year)
foreach vv of local metrics {
    * Do not average only the runs that happened to have nonempty holder samples.
    replace sim_`vv' = . if nr_`vv' != `expected_runs'
    gen double low_`vv' = sim_`vv' - 1.96 * sd_`vv' if nr_`vv' > 1
    gen double high_`vv' = sim_`vv' + 1.96 * sd_`vv' if nr_`vv' > 1
    gen double gap_`vv' = .
}
* Survey columns enter here, after all run-specific calculations are complete.
merge 1:1 outcome year using "`survey_stats'", keep(match) nogen
assert _N > 0
foreach vv of local metrics {
    replace gap_`vv' = 100 * (sim_`vv' / was_`vv' - 1) if was_`vv' != 0 & was_`vv' < .
}
sort outcome year
save `"`stats_dir'/housing_annual_comparison.dta"', replace
preserve
keep if year > `initial_year'
keep outcome year
isid outcome year
save "`common_years'", replace
restore
quietly summarize year, meanonly
local plot_first = r(min)
local plot_last = r(max)

/*******************************************************************************
* 6 - Annual amount quintiles and equal-year period comparisons
*******************************************************************************/
use "`quintiles'", clear
isid source run year outcome quintile
sort source run outcome year quintile
save `"`stats_dir'/housing_quintile_statistics.dta"', replace
merge m:1 outcome year using "`common_years'", keep(match) nogen
assert _N > 0
bysort source run outcome quintile: gen int expected_years = _N
collapse (mean) mean (count) n_years=mean (max) expected_years, by(source run outcome quintile)
replace mean = . if n_years != expected_years
save `"`stats_dir'/housing_quintile_period_by_run.dta"', replace
preserve
keep if source == 0
keep outcome quintile mean n_years
rename (mean n_years) (was_mean was_n_years)
save "`survey_stats'", replace
restore
keep if source == 1
bysort outcome quintile: assert _N == `expected_runs'
collapse (mean) sim_mean=mean (sd) sim_sd=mean (count) n_runs=mean, by(outcome quintile)
replace sim_mean = . if n_runs != `expected_runs'
gen double sim_low = sim_mean - 1.96 * sim_sd if n_runs > 1
gen double sim_high = sim_mean + 1.96 * sim_sd if n_runs > 1
merge 1:1 outcome quintile using "`survey_stats'", assert(match) nogen
gen double gap_pct = 100 * (sim_mean / was_mean - 1) if was_mean != 0 & was_mean < .
sort outcome quintile
save `"`stats_dir'/housing_quintile_comparison.dta"', replace

/*******************************************************************************
* 7 - Matched transition comparisons; unavailable intervals remain identifiable
*******************************************************************************/
use "`transitions'", clear
isid source run rate_id start_year end_year
sort source run rate_id start_year
save `"`stats_dir'/housing_transition_statistics.dta"', replace
local have_transitions = (_N > 0)
if `have_transitions' {
    preserve
    keep if source == 0
    keep rate_id start_year end_year n_pairs n_risk n_event weighted_risk weighted_event rate
    foreach vv in n_pairs n_risk n_event weighted_risk weighted_event rate {
        rename `vv' was_`vv'
    }
    save "`survey_stats'", replace
    restore
    keep if source == 1
    bysort rate_id start_year: assert _N == `expected_runs'
    collapse (mean) sim_rate=rate n_pairs n_risk n_event ///
        (sd) sim_sd=rate (count) n_runs=rate, by(rate_id start_year end_year)
    replace sim_rate = . if n_runs != `expected_runs'
    gen double sim_low = sim_rate - 1.96 * sim_sd if n_runs > 1
    gen double sim_high = sim_rate + 1.96 * sim_sd if n_runs > 1
    merge 1:1 rate_id start_year end_year using "`survey_stats'", assert(match) nogen
    gen double gap_pp = 100 * (sim_rate - was_rate)
    gen byte comparable = !missing(sim_rate, was_rate)
    quietly count if comparable
    local have_transitions = (r(N) > 0)
    sort rate_id start_year
}
save `"`stats_dir'/housing_transition_comparison.dta"', replace

/*******************************************************************************
* 8 - Figure helpers and annual figures
*******************************************************************************/
local variation_note "Simulation mean +/- 1.96 SD across `expected_runs' runs."
if `expected_runs' == 1 local variation_note "One simulation run; no between-run variation band."
local source_note "Green: SimPaths. Dashed navy: WAS."
capture program drop _housing_line
program define _housing_line
    syntax [if], METRIC(name) TITLE(string) UNIT(string) NAME(name) ///
        FIRST(integer) LAST(integer) [SCALE(real 1) INITIAL(integer -9999)]
    marksample selected
    tempvar sim survey low high
    gen double `sim' = sim_`metric' * `scale'
    gen double `survey' = was_`metric' * `scale'
    gen double `low' = low_`metric' * `scale'
    gen double `high' = high_`metric' * `scale'
    local initial_line
    if inrange(`initial', `first', `last') local initial_line "xline(`initial', lcolor(gs10) lpattern(shortdash))"
    twoway (rarea `low' `high' year if `selected', sort color(green%18) lwidth(none)) ///
        (connected `sim' year if `selected', sort lcolor(green) mcolor(green) msymbol(O) msize(small)) ///
        (connected `survey' year if `selected', sort lcolor(navy) mcolor(navy) lpattern(dash) msymbol(D) msize(small)), ///
        title("`title'", size(medium) color(black)) ///
        xtitle("Year", size(small)) ytitle("`unit'", size(small)) ///
        xlabel(`first'(1)`last', labsize(small)) ///
        yscale(range(0)) ylabel(#6, angle(horizontal) labsize(small) format(%9.0fc)) ///
        `initial_line' legend(off) graphregion(color(white)) plotregion(color(white)) name(`name', replace)
    drop `sim' `survey' `low' `high'
end

use `"`stats_dir'/housing_annual_comparison.dta"', clear
forvalues oo = 1/2 {
    local title "Homeownership"
    if `oo' == 2 local title "Mortgage holding"
    _housing_line if outcome == `oo', metric(prevalence) title("`title'") ///
        unit("Benefit units (%)") scale(100) name(housing_hold`oo') ///
        first(`plot_first') last(`plot_last') initial(`initial_year')
}
graph combine housing_hold1 housing_hold2, cols(2) ///
    title("Housing and mortgage holding", size(medium) color(black)) ///
    note("`source_note' `variation_note'" ///
         "Shares of BUs with observed stocks; `initial_year' is the initial simulation year.", size(small)) ///
    graphregion(color(white)) xsize(11) ysize(5) name(housing_holding, replace)
graph export `"`graph_dir'/validation_UK_housing_mortgage_holding.jpg"', replace width(2400) height(1150) quality(100)

keep if year > `initial_year'
quietly summarize year, meanonly
local projected_first = r(min)
local projected_last = r(max)
forvalues oo = 1/2 {
    foreach metric in mean p50 p90 p99 {
        local title "Mean"
        if "`metric'" == "p50" local title "Median"
        if "`metric'" == "p90" local title "90th percentile"
        if "`metric'" == "p99" local title "99th percentile"
        _housing_line if outcome == `oo', metric(`metric') title("`title'") ///
            unit("GBP thousands, as recorded") scale(.001) name(housing_`metric') ///
            first(`projected_first') last(`projected_last')
    }
    local subject "Net housing wealth among homeowners"
    local filename "validation_UK_housing_net_amounts.jpg"
    if `oo' == 2 {
        local subject "Mortgage balances among mortgage holders"
        local filename "validation_UK_mortgage_amounts.jpg"
    }
    graph combine housing_mean housing_p50 housing_p90 housing_p99, cols(2) ///
        title("`subject'", size(medium) color(black)) ///
        note("`source_note' `variation_note'" ///
             "Amounts as recorded; no additional price adjustment. Initial year excluded; no trimming.", size(small)) ///
        graphregion(color(white)) xsize(11) ysize(8) name(housing_amounts, replace)
    graph export `"`graph_dir'/`filename'"', replace width(2400) height(1750) quality(100)
}

/*******************************************************************************
* 9 - Period quintile bars and optional between-run error bars
*******************************************************************************/
use `"`stats_dir'/housing_quintile_comparison.dta"', clear
foreach vv in sim_mean was_mean sim_low sim_high {
    replace `vv' = `vv' / 1000
}
gen double sim_x = quintile - .17
gen double was_x = quintile + .17
forvalues oo = 1/2 {
    local title "Net housing wealth among homeowners"
    if `oo' == 2 local title "Mortgage balances among mortgage holders"
    twoway (bar sim_mean sim_x if outcome == `oo', barwidth(.30) color(green)) ///
        (bar was_mean was_x if outcome == `oo', barwidth(.30) color(navy)) ///
        (rcap sim_low sim_high sim_x if outcome == `oo', lcolor(gs6)), ///
        title("`title'", size(medsmall) color(black)) ///
        xtitle("") ytitle("Mean amount, GBP thousands", size(small)) ///
        xlabel(0 "Overall" 1 "Q1" 2 "Q2" 3 "Q3" 4 "Q4" 5 "Q5", labsize(small)) ///
        ylabel(, angle(horizontal) labsize(small) format(%9.0fc)) ///
        legend(order(1 "SimPaths" 2 "WAS") rows(1) position(6) size(small) region(lstyle(none))) ///
        graphregion(color(white)) plotregion(color(white)) name(housing_quintile`oo', replace)
}
graph combine housing_quintile1 housing_quintile2, cols(2) ///
    title("Housing and mortgage amounts by quintile", size(medium) color(black)) ///
    subtitle("Equal-year means, `projected_first'-`projected_last'", size(small)) ///
    note("Quintiles rank each amount among its holders, separately by source, year and run; they are not income quintiles." ///
         "`variation_note' Overall holder means are calculated directly. Amounts as recorded.", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(6) name(housing_quintiles, replace)
graph export `"`graph_dir'/validation_UK_housing_mortgage_quintiles.jpg"', replace width(2400) height(1250) quality(100)

/*******************************************************************************
* 10 - Six linked endpoint transition rates, when comparable pairs are available
*******************************************************************************/
if `have_transitions' {
    use `"`stats_dir'/housing_transition_comparison.dta"', clear
    local interval_labels
    quietly levelsof end_year, local(ends)
    foreach yy of local ends {
        local start = `yy' - 2
        local short_end = substr("`yy'", 3, 2)
        local interval_labels `"`interval_labels' `yy' "`start'-`short_end'""'
    }
    foreach vv in sim_rate was_rate sim_low sim_high {
        replace `vv' = `vv' * 100
    }
    forvalues rr = 1/6 {
        local title "Homeownership entry"
        if `rr' == 2 local title "Homeownership persistence"
        if `rr' == 3 local title "Mortgage entry: all linked BUs"
        if `rr' == 4 local title "Mortgage persistence: all linked BUs"
        if `rr' == 5 local title "Mortgage entry: end-period owners"
        if `rr' == 6 local title "Mortgage persistence: end-period owners"
        twoway (rcap sim_low sim_high end_year if rate_id == `rr', lcolor(green)) ///
            (connected sim_rate end_year if rate_id == `rr', sort lcolor(green) mcolor(green) msymbol(O) msize(small)) ///
            (connected was_rate end_year if rate_id == `rr', sort lcolor(navy) mcolor(navy) lpattern(dash) msymbol(D) msize(small)), ///
            title("`title'", size(medsmall) color(black)) ///
            xtitle("") ytitle("Two-year rate (%)", size(small)) ///
            xlabel(`interval_labels', labsize(small)) ///
            yscale(range(0)) ylabel(#6, angle(horizontal) labsize(small) format(%5.1f)) ///
            legend(off) graphregion(color(white)) plotregion(color(white)) name(housing_transition`rr', replace)
    }
    graph combine housing_transition1 housing_transition2 housing_transition3 ///
        housing_transition4 housing_transition5 housing_transition6, cols(2) ///
        title("Two-year housing and mortgage transitions", size(medium) color(black)) ///
        note("`source_note' `variation_note'" ///
             "WAS round-midpoint labels; destination-round weights. Linked endpoints only." ///
             "Persistence means holding at both endpoints, not uninterrupted holding.", size(small)) ///
        graphregion(color(white)) xsize(12) ysize(10) name(housing_transitions, replace)
    graph export `"`graph_dir'/validation_UK_housing_mortgage_transitions.jpg"', replace width(2400) height(2050) quality(100)
}
else display as text "No comparable two-year intervals; transition figure not updated. See saved risk-set counts."

capture program drop _housing_annual
capture program drop _housing_transitions
capture program drop _housing_line
display as result "Housing validation complete: `expected_runs' run(s), annual years `plot_first'-`plot_last'."
