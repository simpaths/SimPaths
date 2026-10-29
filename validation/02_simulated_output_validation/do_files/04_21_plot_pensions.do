/*******************************************************************************
* PROJECT:      SimPaths UK - simulated output validation
* OBJECT:       Original pension validation settings with new simulation results
* AUTHOR:       Liang Shi
* LAST UPDATE:  28 October 2026
*
* Run from validation root: do "do_files/04_21_plot_pensions.do"
* Optional arguments:
*   ukhls_targets was_person simulation_person graph_dir stats_dir first_year last_year rate_first_year
*
* SURVEY BENCHMARKS
* - Contributor shares and PERSONAL wealth: UKHLS_pooled_ipop.dta, prepared by
*   03_create_UKHLS_validation_targets.do (pensions mode) following pension_wealth_surv.do.
*   No fixed Excel values are imported. Actual UKHLS sample counts are retained.
* - Contribution rates: WAS person targets from 03_create_WAS_validation_targets.
*   Missing rate components become zero; PP is capped at 100%; contributors are
*   positive combined OP rates or positive PP rate with earnings >5000 GBP.
*   Weighted means and own-rate quintiles preserve pension_wealth_surv2.do.
*
* SIMULATION INPUT
* - pension_person_simulation_sample.dta: numeric run year idPers employed,
*   contRateOPEe contRateOPEr contRatePP (FRACTIONS), wealthPensValue (PERSONAL).
*   Import completed Person.csv upstream; no BU totals or stock/adult allocation
*   on the simulation side. The separate test runner supplies one completed run.
*
* DEFINITIONS
* - Shares reproduce pension wealth_sim.do and pension_wealth_surv.do: OP rates
*   sum >0; PP rate >0; overall and employed persons. No WAS earnings screen on
*   these UKHLS participation benchmarks. The original names say "members", but
*   these flags measure positive contributions, not reported scheme membership.
* - Personal wealth statistics use positive holders, all ages as in the original
*   simulation validator; own-wealth quintiles, not income groups. Survey wealth
*   uses the reference's equal-adult proxy, rather than observed personal holdings.
*   Wealth means total private pension wealth PER PERSON (OP + PP), not PP-only.
* - Both OP rate distributions condition on a positive combined OP rate, so an
*   individual component can be zero. Each rate has its own contributor ranking.
* - WAS uses integer frequency weights. Simulation people have unit weight and
*   independent runs have equal weight. Quintile ties may leave empty groups;
*   missing remains missing. Group zero denotes the directly calculated mean.
* - Percentages = simulation fractions *100, exactly once. Amounts are as
*   recorded, no new deflator, trimming or inferred price-base label.
* - Default participation/wealth window: 2015-2021, including initial year.
*   Default contribution-rate window: 2016-2021, as in the original WAS validation.
* - Period means average years WITHIN RUN before computing between-run sample SD.
*   A missing year/group/run is not silently omitted from a comparison. Bands
*   show mean +/-1.96 SD across runs, not a CI of the mean; no band for one run.
*
* OUTPUTS
* Three reported JPGs: contributor shares, mean/top-quintile personal wealth,
* and contribution-rate quintiles. Supplementary plotting code is omitted.
* Six .dta statistics files retain additional annual/period statistics and
* benchmark_source so UKHLS and WAS cannot be confused.
* No simulation or estimation is run; no particular output folder is embedded.
*******************************************************************************/
version 18
set more off
args ukhls_targets was_person simulation_person graph_dir stats_dir first_year last_year rate_first_year
local data_root "${dir_data}"
if `"`data_root'"' == "" local data_root "./data"
local graph_root "${dir_output_files}"
if `"`graph_root'"' == "" local graph_root "./graphs"
if `"`ukhls_targets'"' == "" local ukhls_targets `"`data_root'/ukhls_pension_targets.dta"'
if `"`was_person'"' == "" local was_person `"`data_root'/was_validation_sample.dta"'
if `"`simulation_person'"' == "" local simulation_person `"`data_root'/pension_person_simulation_sample.dta"'
if `"`graph_dir'"' == "" local graph_dir `"`graph_root'/pensions"'
if `"`stats_dir'"' == "" local stats_dir `"`data_root'"'
if "`first_year'" == "" local first_year 2015
if "`last_year'" == "" local last_year 2021
if "`rate_first_year'" == "" local rate_first_year 2016
foreach parameter in first_year last_year rate_first_year {
    if missing(real("``parameter''")) | real("``parameter''") != floor(real("``parameter''")) exit 198
}
if `first_year' > `rate_first_year' | `rate_first_year' > `last_year' exit 198
local period_years = `last_year' - `rate_first_year' + 1
foreach input in ukhls_targets was_person simulation_person {
    confirm file `"``input''"'
}
capture mkdir `"`graph_root'"'
capture mkdir `"`graph_dir'"'
capture mkdir `"`stats_dir'"'

* Canonical calculations at person level. Outcomes 1-4 shares (%), 5-7 rates
* (%), 8 personal wealth (GBP). source=0 survey, source=1 simulation.
capture program drop _pension_summary
program define _pension_summary
    syntax, HANDLE(name) QHANDLE(name) SOURCE(integer) RUN(real) FIRST(integer) LAST(integer) RATEFIRST(integer)
    local outcomes "5 6 7"
    if `source' local outcomes "1 2 3 4 5 6 7 8"
    forvalues yy = `first'/`last' {
        foreach oo of local outcomes {
            if inrange(`oo',5,7) & `yy' < `ratefirst' continue
            preserve
            keep if year == `yy'
            if inlist(`oo',3,4) keep if employed == 1
            quietly count
            local n_all = r(N)
            local value op
            if inlist(`oo',2,4) local value pp_flag
            if `oo' == 5 local value ee
            if `oo' == 6 local value er
            if `oo' == 7 local value pp
            if `oo' == 8 local value wealth
            local condition "1"
            if inrange(`oo',5,6) local condition "op == 1"
            if `oo' == 7 local condition "pp_flag == 1"
            if `oo' == 8 local condition "wealth > 0"
            keep if `condition' & `value' < .
            quietly count
            local n = r(N)
            tempvar amount
            gen double `amount' = `value'
            if `oo' <= 4 replace `amount' = 100 * `amount'
            local average = .
            local weighted_n = 0
            if `n' > 0 {
                quietly summarize `amount' [fw=weight], meanonly
                local average = r(mean)
                quietly summarize weight, meanonly
                local weighted_n = r(sum)
            }
            post `handle' (`source') (`run') (`yy') (`oo') (`n_all') (`n') (`weighted_n') (`average')
            if `oo' >= 5 {
                tempvar q
                gen byte `q' = .
                if `n' > 0 {
                    drop `q'
                    quietly xtile `q' = `amount' [fw=weight], nq(5)
                }
                forvalues qq = 0/5 {
                    local group "1"
                    if `qq' > 0 local group "`q' == `qq'"
                    quietly count if `group'
                    local nq = r(N)
                    local avg = .
                    if `nq' > 0 {
                        quietly summarize `amount' if `group' [fw=weight], meanonly
                        local avg = r(mean)
                    }
                    post `qhandle' (`source') (`run') (`yy') (`oo') (`qq') (`nq') (`avg')
                }
            }
            restore
        }
    }
end

tempfile annual quintiles was_input sim_input survey_stats
tempname a q
postfile `a' byte source double run int year byte outcome long n_all long n_eligible ///
    double weighted_n double mean using "`annual'", replace
postfile `q' byte source double run int year byte outcome byte quintile long n double mean ///
    using "`quintiles'", replace

* UKHLS annual means and actual counts rebuilt from the pooled microdata.
* Shares already have percentage units. The wealth proxy and quintiles follow
* pension_wealth_surv.do; empty groups remain missing, with a recorded zero count.
use `"`ukhls_targets'"', clear
isid year
confirm numeric variable op_share pp_share op_employed_share pp_employed_share ///
    wealth_mean wealth_q1 wealth_q2 wealth_q3 wealth_q4 wealth_q5 ///
    n_all n_employed n_wealth n_wealth_q1 n_wealth_q2 n_wealth_q3 n_wealth_q4 n_wealth_q5
assert benchmark_source=="UKHLS"
local target_basis : char _dta[benchmark_basis]
if "`target_basis'"!="" & "`target_basis'"!="microdata" {
    display as error "Pension validation requires UKHLS microdata targets from pension_wealth_surv.do definitions."
    exit 198
}
* Counts must be observed, nonnegative integers, not unavailable workbook counts.
foreach count in n_all n_employed n_wealth n_wealth_q1 n_wealth_q2 n_wealth_q3 n_wealth_q4 n_wealth_q5 {
    assert `count'>=0 & `count'<. & `count'==floor(`count')
}
assert n_employed<=n_all & n_wealth<=n_all
assert n_wealth_q1+n_wealth_q2+n_wealth_q3+n_wealth_q4+n_wealth_q5==n_wealth
keep if inrange(year, `first_year', `last_year')
assert _N == `last_year' - `first_year' + 1
sort year
forvalues i = 1/`=_N' {
    local yy = year[`i']
    forvalues oo = 1/4 {
        local variable op_share
        local count n_all
        if `oo' == 2 local variable pp_share
        if `oo' == 3 local variable op_employed_share
        if `oo' == 4 local variable pp_employed_share
        if inlist(`oo',3,4) local count n_employed
        assert inrange(`variable',0,100) if `count'>0
        assert missing(`variable') if `count'==0
        post `a' (0) (-1) (`yy') (`oo') (`count'[`i']) (`count'[`i']) (`count'[`i']) (`variable'[`i'])
    }
    post `a' (0) (-1) (`yy') (8) (n_all[`i']) (n_wealth[`i']) (n_wealth[`i']) (wealth_mean[`i'])
    forvalues qq = 0/5 {
        local variable wealth_mean
        local count n_wealth
        if `qq' > 0 {
            local variable wealth_q`qq'
            local count n_wealth_q`qq'
        }
        assert `variable'>0 & `variable'<. if `count'>0
        assert missing(`variable') if `count'==0
        post `q' (0) (-1) (`yy') (8) (`qq') (`count'[`i']) (`variable'[`i'])
    }
}

* WAS is used ONLY for contribution-rate comparisons. Keep legacy missing-rate
* recodes and contributor screens already constructed by the WAS target file.
use `"`was_person'"', clear
confirm numeric variable year was_pid was_round sample_person dwt_valid ///
    valid_contRateOPEePct valid_contRateOPErPct valid_contRatePPPct ///
    valid_opContribFlag valid_ppContribFlag
isid was_pid was_round
keep if sample_person == 1 & inrange(year, `rate_first_year', `last_year')
rename (dwt_valid valid_contRateOPEePct valid_contRateOPErPct valid_contRatePPPct ///
    valid_opContribFlag valid_ppContribFlag) (weight ee er pp op pp_flag)
assert weight > 0 & weight < . & weight == floor(weight)
quietly _pension_summary, handle(`a') qhandle(`q') source(0) run(-1) ///
    first(`first_year') last(`last_year') ratefirst(`rate_first_year')

* Simulation: same flags, personal stock, positive-holder restriction and
* per-year rankings as the original Person.csv validator. Assert complete
* exported rates rather than letting Stata's missing>0 rule create contributors.
use `"`simulation_person'"', clear
confirm numeric variable run year idPers employed contRateOPEe contRateOPEr contRatePP wealthPensValue
isid run year idPers
assert year == floor(year) & year < .
keep if inrange(year, `first_year', `last_year')
quietly levelsof run, local(runs)
local expected_runs : word count `runs'
if `expected_runs' == 0 exit 2000
assert inlist(employed,0,1)
foreach rate in contRateOPEe contRateOPEr contRatePP {
    assert inrange(`rate',0,1)
    replace `rate' = 100 * `rate'
}
rename (contRateOPEe contRateOPEr contRatePP wealthPensValue) (ee er pp wealth)
gen byte op = ee + er > 0
gen byte pp_flag = pp > 0
gen double weight = 1
save "`sim_input'", replace
foreach run of local runs {
    use "`sim_input'", clear
    keep if run == `run'
    quietly _pension_summary, handle(`a') qhandle(`q') source(1) run(`run') ///
        first(`first_year') last(`last_year') ratefirst(`rate_first_year')
}
postclose `a'
postclose `q'

capture program drop _pension_labels
program define _pension_labels
    label define pension_outcome 1 "OP contributors: all persons" 2 "PP contributors: all persons" ///
        3 "OP contributors: employed" 4 "PP contributors: employed" ///
        5 "OP employee contribution rate" 6 "OP employer contribution rate" ///
        7 "Personal pension contribution rate" 8 "Private pension wealth per person: positive holders", replace
    label define pension_source 0 "Survey benchmark" 1 "SimPaths", replace
    label values outcome pension_outcome
    label values source pension_source
    gen str16 benchmark_source = "UKHLS"
    replace benchmark_source = "WAS" if inrange(outcome,5,7)
    char _dta[units] "Shares/rates percent; personal wealth GBP as recorded; no added deflation"
    char _dta[UKHLS_provenance] "Rebuilt from UKHLS_pooled_ipop.dta using pension_wealth_surv.do; actual unweighted counts"
end
use "`annual'", clear
_pension_labels
isid source run year outcome
label var n_all "Record count before contributor/positive-wealth screen"
label var n_eligible "Record count in mean"
sort source run outcome year
save `"`stats_dir'/pension_annual_statistics.dta"', replace
preserve
keep if source == 0
drop source run
rename (n_all n_eligible weighted_n mean) (survey_n_all survey_n_eligible survey_weighted_n survey_mean)
save "`survey_stats'", replace
restore
keep if source == 1
collapse (mean) n_all n_eligible sim_mean=mean (sd) sim_sd=mean (count) n_runs=mean, by(outcome year)
gen int expected_runs = `expected_runs'
replace sim_mean = . if n_runs != expected_runs
gen double sim_low = sim_mean - 1.96 * sim_sd if n_runs > 1 & n_runs == expected_runs
gen double sim_high = sim_mean + 1.96 * sim_sd if n_runs > 1 & n_runs == expected_runs
merge 1:1 outcome year using "`survey_stats'", assert(match) nogen
gen double gap = sim_mean - survey_mean
label var gap "Simulation minus survey; percentage points except wealth (GBP)"
sort outcome year
save `"`stats_dir'/pension_annual_comparison.dta"', replace

use "`quintiles'", clear
_pension_labels
isid source run year outcome quintile
sort source run outcome year quintile
save `"`stats_dir'/pension_quintile_statistics.dta"', replace
preserve
keep if source == 0
keep outcome year quintile benchmark_source mean
rename mean survey_mean
save "`survey_stats'", replace
restore
preserve
keep if source == 1
collapse (mean) sim_mean=mean (sd) sim_sd=mean (count) n_runs=mean, by(outcome year quintile)
gen int expected_runs = `expected_runs'
replace sim_mean = . if n_runs != expected_runs
gen double sim_low = sim_mean - 1.96 * sim_sd if n_runs > 1 & n_runs == expected_runs
gen double sim_high = sim_mean + 1.96 * sim_sd if n_runs > 1 & n_runs == expected_runs
merge 1:1 outcome year quintile using "`survey_stats'", assert(match) nogen
sort outcome year quintile
save `"`stats_dir'/pension_quintile_annual_comparison.dta"', replace
restore
* Retain period statistics (including unplotted wealth quintiles) for reuse.
* As in the reported rate-quintile comparison, average years within each run.
keep if inrange(year, `rate_first_year', `last_year')
collapse (mean) mean (count) n_years=mean, by(source run outcome quintile benchmark_source)
gen int expected_years = `period_years'
replace mean = . if n_years != expected_years
save `"`stats_dir'/pension_quintile_period_by_run.dta"', replace
preserve
keep if source == 0
keep outcome quintile benchmark_source mean n_years
rename (mean n_years) (survey_mean survey_n_years)
save "`survey_stats'", replace
restore
keep if source == 1
collapse (mean) sim_mean=mean (sd) sim_sd=mean (count) n_runs=mean, by(outcome quintile)
gen int expected_runs = `expected_runs'
replace sim_mean = . if n_runs != expected_runs
gen double sim_low = sim_mean - 1.96 * sim_sd if n_runs > 1 & n_runs == expected_runs
gen double sim_high = sim_mean + 1.96 * sim_sd if n_runs > 1 & n_runs == expected_runs
merge 1:1 outcome quintile using "`survey_stats'", assert(match) nogen
sort outcome quintile
save `"`stats_dir'/pension_quintile_comparison.dta"', replace

* Display helpers: amounts are scaled to thousands only for plotting. The
* benchmark label is explicit, since the validation uses two survey sources.
capture program drop _pension_line
program define _pension_line
    syntax [if], TITLE(string) UNIT(string) NAME(name) FIRST(integer) LAST(integer) [SCALE(real 1)]
    marksample selected
    tempvar sim survey low high
    gen double `sim' = sim_mean * `scale'
    gen double `survey' = survey_mean * `scale'
    gen double `low' = sim_low * `scale'
    gen double `high' = sim_high * `scale'
    * Show zero as the baseline for these nonnegative pension outcomes.
    twoway (rarea `low' `high' year if `selected', sort color(green%18) lwidth(none)) ///
        (connected `sim' year if `selected', sort lcolor(green) mcolor(green) msymbol(O) msize(small)) ///
        (connected `survey' year if `selected', sort lcolor(navy) mcolor(navy) lpattern(dash) msymbol(D) msize(small)), ///
        title("`title'", size(medium) color(black)) xtitle("Year", size(small)) ///
        ytitle("`unit'", size(small)) xlabel(`first'(1)`last', labsize(small)) ///
        yscale(range(0)) ylabel(#6, angle(horizontal) labsize(small) format(%9.1fc)) ///
        legend(off) graphregion(color(white)) plotregion(color(white)) name(`name', replace)
    drop `sim' `survey' `low' `high'
end
capture program drop _pension_bars
program define _pension_bars
    syntax [if], TITLE(string) UNIT(string) NAME(name) BENCHMARK(string) [SCALE(real 1)]
    marksample selected
    tempvar sx wx sm wm lo hi
    gen double `sx' = quintile - .17
    gen double `wx' = quintile + .17
    gen double `sm' = sim_mean * `scale'
    gen double `wm' = survey_mean * `scale'
    gen double `lo' = sim_low * `scale'
    gen double `hi' = sim_high * `scale'
    twoway (bar `sm' `sx' if `selected', barwidth(.30) color(green)) ///
        (bar `wm' `wx' if `selected', barwidth(.30) color(navy)) ///
        (rcap `lo' `hi' `sx' if `selected', lcolor(gs6)), ///
        title("`title'", size(medium) color(black)) xtitle("") ytitle("`unit'", size(small)) ///
        xlabel(0 "Overall" 1 "Q1" 2 "Q2" 3 "Q3" 4 "Q4" 5 "Q5", labsize(small)) ///
        ylabel(, angle(horizontal) labsize(small) format(%9.1fc)) ///
        legend(order(1 "SimPaths" 2 "`benchmark'") rows(1) position(6) size(small) region(lstyle(none))) ///
        graphregion(color(white)) plotregion(color(white)) name(`name', replace)
    drop `sx' `wx' `sm' `wm' `lo' `hi'
end
local variation_note "Mean +/- 1.96 SD across `expected_runs' runs; between-run variation."
if `expected_runs' == 1 local variation_note "One simulation run; no between-run variation band."

* Only the three selected reported figure groups are drawn below.
use `"`stats_dir'/pension_annual_comparison.dta"', clear
forvalues oo = 1/4 {
    local title "Occupational pension: all persons"
    if `oo' == 2 local title "Personal pension: all persons"
    if `oo' == 3 local title "Occupational pension: employed persons"
    if `oo' == 4 local title "Personal pension: employed persons"
    _pension_line if outcome == `oo', title("`title'") unit("Contributors (%)") ///
        name(pension_share`oo') first(`first_year') last(`last_year')
}
graph combine pension_share1 pension_share2 pension_share3 pension_share4, cols(2) ///
    title("Pension contributor shares: UKHLS benchmarks", size(medium)) ///
    note("Green: SimPaths. Dashed navy: UKHLS microdata targets." ///
         "`variation_note'" ///
         "Positive OP/PP contribution rates, unweighted persons; no WAS earnings screen." ///
         "Comparison `first_year'-`last_year'; the default window includes initial year 2015.", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(8) name(pension_shares, replace)
graph export `"`graph_dir'/validation_UK_pension_contributors.jpg"', replace width(2500) height(1750) quality(100)

use `"`stats_dir'/pension_quintile_annual_comparison.dta"', clear
_pension_line if outcome == 8 & quintile == 0, title("Mean among positive holders") ///
    unit("GBP thousands, as recorded") scale(.001) name(pension_wealth_mean) first(`first_year') last(`last_year')
_pension_line if outcome == 8 & quintile == 5, title("Mean within top wealth quintile") ///
    unit("GBP thousands, as recorded") scale(.001) name(pension_wealth_q5) first(`first_year') last(`last_year')
graph combine pension_wealth_mean pension_wealth_q5, cols(2) ///
    title("Private pension wealth per person: UKHLS benchmarks", size(medium)) ///
    note("Green: SimPaths. Dashed navy: UKHLS microdata targets." ///
         "Positive personal-wealth holders; UKHLS retains the original equal-adult allocation proxy." ///
         "`variation_note' No additional price adjustment or trimming.", size(small)) ///
    graphregion(color(white)) xsize(12) ysize(6) name(pension_wealth, replace)
graph export `"`graph_dir'/validation_UK_pension_wealth.jpg"', replace width(2500) height(1350) quality(100)

use `"`stats_dir'/pension_quintile_comparison.dta"', clear
forvalues oo = 5/7 {
    local title "Occupational DC: employee"
    if `oo' == 6 local title "Occupational DC: employer"
    if `oo' == 7 local title "Personal pension"
    _pension_bars if outcome == `oo', title("`title'") unit("Mean contribution rate (%)") ///
        benchmark("WAS") name(pension_q`oo')
}
graph combine pension_q5 pension_q6 pension_q7, cols(3) ///
    title("Contribution rates by own-rate quintile: original WAS settings", size(medium)) ///
    subtitle("Equal-year means, `rate_first_year'-`last_year'", size(small)) ///
    note("Each rate is ranked separately among its scheme's contributors, within source, year and run." ///
         "Overall is the direct contributor mean. WAS PP retains earnings > GBP 5,000." ///
         "`variation_note' Tied/empty quintiles stay missing.", size(small)) ///
    graphregion(color(white)) xsize(14) ysize(6) name(pension_rate_quintiles, replace)
graph export `"`graph_dir'/validation_UK_pension_rate_quintiles.jpg"', replace width(2800) height(1250) quality(100)

display as result "PENSION VALIDATION COMPLETE: UKHLS/WAS targets, three reported figures, `expected_runs' run(s)."
