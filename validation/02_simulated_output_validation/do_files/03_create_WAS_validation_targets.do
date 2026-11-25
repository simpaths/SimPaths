/*******************************************************************************
* PROJECT:      SimPaths UK
* SECTION:      Simulated output validation - survey target preparation
* OBJECT:       Prepare WAS validation microdata
* Authors:      Liang Shi, Justin van der Ven
* LAST UPDATE:  28 October 2026
*
* INPUT:  One consolidated was_wealthdata_5_8_longitudinal.dta, compiled from
*         WAS R5-R8 with finalised debt/wealth definitions AND pension-rate fields.
*         See 02_compile_WAS_wealth_data.do for reproducible raw-data construction.
* OUTPUT: was_validation_sample.dta (person records with sample flags), and
*         was_bu_validation_sample.dta (one reference record per benefit unit).
*         High-cost levels/transitions and legacy simulation CPI targets are
*         prepared in section 8 from the same source file.
*
* This is the WAS counterpart of 03_create_UKHLS_validation_targets.do.
* It constructs comparable variables; later validation scripts calculate summary
* statistics, read completed simulations, and draw figures. No model is estimated
* or simulation launched here. The source dataset is never overwritten.
*
* RUN: do "03_create_WAS_validation_targets.do" "compiled WAS.dta" "target folder"
* Optional third/fourth arguments select first/last INTERVIEW year (inclusive).
* Optional fifth argument selects all (default), microdata or debt.
* all: WAS person/BU samples plus high-cost summaries; microdata: samples only;
* debt: high-cost summaries only, requiring only their documented source fields.
* Debt targets always use full observed rounds, independently of sample years.
* With no arguments, use file_WAS_data, dir_data, min_sim_year, max_sim_year,
* or the portable survey_inputs defaults in section 1.
*
* MEASUREMENT:
* - Person-level pension rates and BU-level wealth coexist in the source.
*   Use sample_person for personal statistics; use the BU output or sample_bu
*   for wealth. Repeated BU wealth on person rows must not be counted twice.
* - dwt is the original positive cross-sectional weight (already rounded/scaled
*   by the upstream compiler). Its scale cancels from means and proportions.
* - year is interview year; roundMidYear is a separate round-midpoint label.
*   No annual scaffold, interpolated records, or imputed ages are created.
* - Monetary levels remain in the source's INTERVIEW-YEAR pounds. Inspection of
*   the supplied compiler found no inflation adjustment. Do not label these as
*   2015 pounds; apply an agreed deflator later if a comparison requires one.
* - Rates have percentage units: 5 means 5%, NOT a fraction of 0.05.
*******************************************************************************/
version 18
set more off
args source_file target_dir first_year last_year target_set
if "`target_set'"=="" local target_set "all"
if !inlist("`target_set'","all","microdata","debt") {
    display as error "WAS target set must be all, microdata or debt."
    exit 198
}
if "`target_set'"!="debt" {

    /*******************************************************************************
    * 1 - Directories and validation years
    *******************************************************************************/
    * Relative defaults assume the validation root is the working directory.
    local validation_root "."
    if `"`source_file'"' == "" local source_file "${file_WAS_data}"
    if `"`source_file'"' == "" local source_file ///
        `"`validation_root'/survey_inputs/WAS/was_wealthdata_5_8_longitudinal.dta"'
    if `"`target_dir'"' == "" local target_dir "${dir_data}"
    if `"`target_dir'"' == "" local target_dir `"`validation_root'/data"'
    if "`first_year'" == "" local first_year "${min_sim_year}"
    if "`last_year'" == "" local last_year "${max_sim_year}"
    if "`first_year'" == "" local first_year 2014
    if "`last_year'" == "" local last_year 2022
    if missing(real("`first_year'"), real("`last_year'")) | ///
        real("`first_year'") > real("`last_year'") {
        display as error "Specify numeric first/last years in ascending order."
        exit 198
    }
    confirm file `"`source_file'"'
    capture mkdir `"`target_dir'"'
    use `"`source_file'"', clear

    /*******************************************************************************
    * 2 - Check source structure before constructing any targets
    *******************************************************************************/
    confirm numeric variable was_pid was_round case bu bu_rp year dwt sex dvage17 ///
        na nk emp wealth tot_pen tot_open tot_pp dvhvalue main_mort ///
        pp_contrate ocdc_contrate_emee ocdc_contrate_emer earnings op_memb
    isid was_pid was_round
    assert inrange(was_round, 5, 8) & was_round == floor(was_round)
    assert !missing(year, case, bu) & year == floor(year)
    assert inlist(bu_rp, 0, 1)
    * Check before year filtering: each source BU must have exactly one reference row.
    * Restricting by interview year can leave some person rows without their reference
    * row in that same year. The BU output below intentionally uses reference rows.
    tempvar n_ref
    bysort was_round case bu: egen `n_ref' = total(bu_rp == 1)
    assert `n_ref' == 1
    drop `n_ref'

    * Debt components and pension rates must coexist in this single input file.
    * Fail explicitly on an older pension-only or finalised debt-only compilation;
    * never fill absent debt/rate fields with zero or merge a different specification.
    confirm string variable was_link_key
    confirm numeric variable cc_debt cc_debt_trans cc_debt_pers hp_debt loan_debt ///
        loan_debt_exc_slc loan_debt_slc mail_debt highcost_loan_debt ///
        highcost_debt_proxy unsec_debt_total loan_doorstep loan_pawnbroker loan_payday
    * Source compilation deliberately leaves the high-cost proxy missing in R5.
    assert missing(highcost_debt_proxy) if was_round == 5
    keep if inrange(year, `first_year', `last_year')
    assert _N > 0

    /*******************************************************************************
    * 3 - Identifiers, demographics and analysis weights
    *******************************************************************************/
    clonevar idPers = was_pid
    clonevar statCollectionWave = was_round
    * These grouped IDs identify cross-sectional units WITHIN this output only.
    * For longitudinal checks link was_pid across adjacent was_round values.
    * was_pid follows a person, so it is not a stable benefit-unit ID after a split.
    egen long idHh = group(was_round case)
    egen long idBu = group(was_round case bu)
    gen int roundMidYear = 2005 + 2 * was_round
    label var roundMidYear "WAS round midpoint: R5=2015, R6=2017, R7=2019, R8=2021"
    clonevar dwt_valid = dwt
    gen byte sample_person = dwt > 0 & dwt < .
    gen byte sample_bu = sample_person & bu_rp == 1
    label var sample_person "Valid positive weight; no imposed age or estimation screen"
    label var sample_bu "One weighted reference-person row per benefit unit"
    * The source supplies grouped age only; do not manufacture an exact age.
    clonevar ageGroupWAS = dvage17
    gen byte demMaleFlag = sex == 1 if inlist(sex, 1, 2)
    clonevar demNAdult = na
    clonevar demNChild = nk
    gen byte valid_employed = emp if inlist(emp, 0, 1)

    /*******************************************************************************
    * 4 - Benefit-unit wealth stocks and holding indicators
    *******************************************************************************/
    * These are already BU totals. Do not sum them across members again.
    clonevar valid_wealthTotValue = wealth
    clonevar valid_wealthPensValue = tot_pen
    clonevar valid_wealthPensOccValue = tot_open
    clonevar valid_wealthPensPPValue = tot_pp
    clonevar valid_wealthPrptyValue = dvhvalue
    clonevar valid_wealthMortgageDebtValue = main_mort
    gen double valid_wealthHousingNetValue = dvhvalue - main_mort
    label var valid_wealthHousingNetValue "BU main-home value less mortgage, interview-year GBP"
    gen byte valid_homeowner = dvhvalue > 0 if dvhvalue < .
    gen byte valid_mortgagor = main_mort > 0 if main_mort < .
    gen byte valid_pensHolder = tot_pen > 0 if tot_pen < .
    * Preserve nonpositive equity among owners; negative net wealth is not a missing
    * value code. No trimming, winsorisation, or positivity restriction is applied.
    gen byte valid_nonpositiveEquity = valid_wealthHousingNetValue <= 0 ///
        if valid_homeowner == 1 & valid_wealthHousingNetValue < .
    * An equal split is an allocation proxy for comparisons requiring personal wealth,
    * not observed personal pension holdings. Use only where documented downstream.
    gen double valid_pensEqualAdultValue = tot_pen / na if na > 0 & na < .
    label var valid_pensEqualAdultValue "Proxy: BU pension stock divided by source adult count"

    /*******************************************************************************
    * 5 - Personal pension contribution targets (legacy validation definitions)
    *******************************************************************************/
    * Preserve the raw fields. Existing pension_wealth_surv2.do recodes missing
    * contribution rates to zero, caps the PP rate at 100%, and defines a PP
    * contributor only above GBP5,000 annual earnings. Make those choices explicit.
    clonevar valid_contRatePPPct = pp_contrate
    clonevar valid_contRateOPEePct = ocdc_contrate_emee
    clonevar valid_contRateOPErPct = ocdc_contrate_emer
    gen byte ratePPMissing = missing(pp_contrate)
    gen byte rateOPEeMissing = missing(ocdc_contrate_emee)
    gen byte rateOPErMissing = missing(ocdc_contrate_emer)
    foreach rate in valid_contRatePPPct valid_contRateOPEePct valid_contRateOPErPct {
        replace `rate' = 0 if missing(`rate')
    }
    replace valid_contRatePPPct = 100 if valid_contRatePPPct > 100
    * Occupational rates retain their source values; the legacy file does not cap them.
    gen byte valid_ppContribFlag = valid_contRatePPPct > 0 & ///
        earnings > 5000 & earnings < .
    gen byte valid_opContribFlag = valid_contRateOPEePct + valid_contRateOPErPct > 0
    * Membership is reported membership, distinct from a positive DC contribution.
    gen byte valid_opMemberFlag = op_memb if inlist(op_memb, 0, 1)
    label var valid_ppContribFlag "PP rate positive and annual earnings >5000 nominal GBP"
    label var valid_opContribFlag "Positive sum of employee/employer DC contribution rates"
    label var valid_opMemberFlag "Reported occupational membership, including DB schemes"

    /*******************************************************************************
    * 6 - Unsecured debt: observed component balances and four-state status
    *******************************************************************************/
    * Use the finalised unsecured_debt.do balance definitions, without its
    * regression sample restrictions or estimation steps.
    * These are BU balances repeated across members, in source interview-year GBP.
    * Student loans are excluded from the low-cost component. Explicit high-cost
    * loans are removed from that component to avoid counting them twice.
    gen double lowcost_debt = hp_debt + loan_debt_exc_slc - highcost_loan_debt
    replace lowcost_debt = 0 if lowcost_debt < 0 & lowcost_debt < .
    clonevar highcost_debt = highcost_debt_proxy
    label var lowcost_debt "BU HP plus non-student loans less explicit high-cost loans"
    label var highcost_debt "BU persistent-card, mail-order and explicit high-cost debt"

    * Total debt has a broader boundary (including student loans and transient card
    * balances); do not assume total equals the sum of these low/high-cost proxies.
    gen byte debtTargetsAvailable = 1
    clonevar valid_unsecDebtTotal = unsec_debt_total
    clonevar valid_unsecDebtHighProxy = highcost_debt
    clonevar valid_unsecDebtLowProxy = lowcost_debt
    label var valid_unsecDebtTotal "BU total unsecured debt, interview-year GBP"
    label var valid_unsecDebtHighProxy "BU high-cost debt proxy, interview-year GBP; R6-R8"
    label var valid_unsecDebtLowProxy "BU low-cost debt proxy, interview-year GBP"

    * Missing balances must stay missing, rather than being interpreted as no debt.
    * In particular, R5 has no high-cost proxy; its four-state status is unknown.
    gen byte valid_lowDebtHolder = lowcost_debt > 0 if lowcost_debt < .
    gen byte valid_highDebtHolder = highcost_debt > 0 if highcost_debt < .
    gen byte valid_unsecuredState = valid_lowDebtHolder + 2 * valid_highDebtHolder ///
        if !missing(valid_lowDebtHolder, valid_highDebtHolder)
    label define was_debt_states 0 "No low/high-cost debt" 1 "Low-cost only" ///
        2 "High-cost only" 3 "Mixed low/high-cost", replace
    label values valid_unsecuredState was_debt_states
    label var valid_unsecuredState "Observed low/high-cost proxy state; missing if either unknown"

    * Select reference-person records with observed outcomes when calculating BU
    * statistics. Low-cost and total debt are available in R5 too; the joint state
    * and high-cost sample begin in R6. No interpolation or estimation filter applies.
    gen byte sample_debtTotal = sample_bu & valid_unsecDebtTotal < .
    gen byte sample_debtLow = sample_bu & lowcost_debt < .
    gen byte sample_debtHigh = sample_bu & highcost_debt < .
    gen byte sample_debt = sample_bu & valid_unsecuredState < .
    label var sample_debtTotal "BU reference sample with observed total unsecured debt"
    label var sample_debtLow "BU reference sample with observed low-cost balance"
    label var sample_debtHigh "BU reference sample with observed high-cost proxy (R6-R8)"
    label var sample_debt "BU reference sample with both components and observed debt state"

    /*******************************************************************************
    * 7 - Save reusable microdata; aggregate statistics belong in comparison scripts
    *******************************************************************************/
    char _dta[target_source] "Compiled observed WAS R5-R8; no annual interpolation"
    char _dta[money_basis] "Source interview-year GBP for core wealth stocks; no deflation here"
    char _dta[rate_units] "Percentage points; raw source rates retained"
    char _dta[bu_selection] "Use sample_bu==1; person-level wealth repeats within BU"
    char _dta[debt_available] "1"
    char _dta[debt_source] `"`source_file'"'
    char _dta[WAS_source] `"`source_file'"'
    char _dta[WAS_specification] "Consolidated finalised debt/wealth and historical pension validation rates"
    char _dta[debt_money_basis] "Interview-year GBP, no deflation; high-cost proxy missing in R5"
    char _dta[debt_sample] "Use sample_debtTotal/Low/High or sample_debt for the joint state"
    sort idPers statCollectionWave
    save `"`target_dir'/was_validation_sample.dta"', replace
    preserve
    keep if sample_bu == 1
    isid statCollectionWave case bu
    save `"`target_dir'/was_bu_validation_sample.dta"', replace
    restore
    display as result "Saved WAS person and benefit-unit validation samples to `target_dir'."
}
if "`target_set'"=="microdata" exit

/*******************************************************************************
* PROJECT:      SimPaths UK - simulated output validation
* OBJECT:       Rebuild high-cost unsecured-debt targets from longitudinal WAS
* AUTHOR:       Liang Shi
* LAST UPDATE:  28 October 2026
*
* Run: do "03_create_WAS_validation_targets.do" "WAS.dta" "data" 2014 2022 "debt"
* Section 8: high-cost debt summaries. No Excel workbook is read or required.
* Supply the CONSOLIDATED was_wealthdata_5_8_longitudinal.dta containing the BU-level
* highcost_debt_proxy. The earlier pension-only compilation lacks this field.
* Optional globals: file_WAS_data, dir_data; otherwise use relative defaults.
*
* DEFINITIONS
* - One observed BU reference person per round (bu_rp==1), positive integer dwt,
*   observed nonnegative high-cost proxy. Missing debt is excluded, never zero.
* - Proxy: persistent card balances + mail order + explicit high-cost loans,
*   already compiled at BU level. Do not sum repeated stocks across people.
* - R6/R7/R8 are labelled by midpoints 2017/2019/2021. Age groups: all BUs;
*   reference-person ages 15-29, 30-54, 55-74, directly from dvage17 bands.
* - Means, prevalence and empirical quantiles use dwt. Amount distributions
*   condition on positive holders; prevalence/unconditional means include zeros.
* - Reconstruct the original survey price convention: nominal source balances
*   divided by INTERVIEW-year CPIH (ONS L522, 2015=100). Annual CPIH constants
*   are embedded below; no external price-index file is needed.
* - Only the annual summary statistics are interpolated for 2018/2020, as the
*   midpoint of adjacent observed-round estimates. Counts/weights stay missing.
* - Transitions link the same was_pid at adjacent rounds, with both records BU
*   reference persons and interviews exactly two years apart. Use origin age,
*   destination cross-sectional dwt; no estimation-sample restrictions.
* - The simulation's existing annual CPI (D7BT) constants are retained separately,
*   preserving the historical validator. Survey CPIH and simulation CPI are
*   different series; both conventions are explicit rather than silently changed.
*
* OUTPUTS: unsecured_debt_WAS_levels.dta (20 annual/age groups),
*   unsecured_debt_WAS_transitions.dta (8 interval/origin-age groups), and
*   unsecured_debt_cpi.dta (legacy simulation deflator). The source is read only.
*******************************************************************************/
local output_dir `"`target_dir'"'
if `"`source_file'"' == "" local source_file "${file_WAS_data}"
if `"`source_file'"' == "" local source_file "./survey_inputs/WAS/was_wealthdata_5_8_longitudinal.dta"
if `"`output_dir'"' == "" local output_dir "${dir_data}"
if `"`output_dir'"' == "" local output_dir "./data"
confirm file `"`source_file'"'
capture mkdir `"`output_dir'"'

* Survey CPIH annual averages (2015=100), ONS series L522, frozen historical
* values from the 20 March 2024 release; these reproduce the old WAS targets.
* https://www.ons.gov.uk/economy/inflationandpriceindices/timeseries/l522/mm23/previous/v103
tempfile survey_prices sample observed_levels transitions
clear
input int year double cpih
2016 101.0
2017 103.6
2018 106.0
2019 107.8
2020 108.9
2021 111.6
2022 120.5
end
save "`survey_prices'", replace

use `"`source_file'"', clear
confirm numeric variable was_pid was_round case bu bu_rp year dwt dvage17 highcost_debt_proxy
isid was_pid was_round
assert inlist(bu_rp,0,1)
assert missing(highcost_debt_proxy) if was_round==5
keep if inrange(was_round,6,8) & bu_rp==1
isid was_round case bu
assert year==floor(year) & year<.
assert highcost_debt_proxy>=0 if highcost_debt_proxy<.
* Eligibility here concerns validation coverage, not the regression sample.
keep if dwt>0 & dwt<. & highcost_debt_proxy<.
assert dwt==floor(dwt)
quietly levelsof was_round, local(rounds)
if "`rounds'" != "6 7 8" {
    display as error "Supply observed WAS rounds 6, 7 and 8 with valid weighted BU records."
    exit 2000
}
merge m:1 year using "`survey_prices'", keep(master match) assert(match) nogen
gen double debt_2015 = highcost_debt_proxy*100/cpih
gen byte holder = highcost_debt_proxy>0
gen byte age_group = .
replace age_group=1 if inrange(dvage17,4,6)
replace age_group=2 if inrange(dvage17,7,11)
replace age_group=3 if inrange(dvage17,12,15)
* All-age statistics include other/unknown ages; do not invent exact ages.
save "`sample'", replace

tempname annual transition
postfile `annual' int year byte was_round byte age_group long was_n_total ///
    double was_weight_sum double was_prevalence double was_mean_all ///
    double was_mean double was_p50 double was_p90 double was_p99 using "`observed_levels'", replace
forvalues rr=6/8 {
    local yy=2005+2*`rr'
    forvalues ag=0/3 {
        local selected "was_round==`rr'"
        if `ag'>0 local selected "`selected' & age_group==`ag'"
        quietly count if `selected'
        local n=r(N)
        local weights=0
        foreach v in prevalence mean_all mean p50 p90 p99 {
            local `v'=.
        }
        if `n'>0 {
            quietly summarize dwt if `selected', meanonly
            local weights=r(sum)
            quietly summarize holder [fw=dwt] if `selected', meanonly
            local prevalence=r(mean)
            quietly summarize debt_2015 [fw=dwt] if `selected', meanonly
            local mean_all=r(mean)
            quietly count if `selected' & holder
            if r(N)>0 {
                quietly summarize debt_2015 [fw=dwt] if `selected' & holder, meanonly
                local mean=r(mean)
                quietly _pctile debt_2015 [fw=dwt] if `selected' & holder, p(50 90 99)
                local p50=r(r1)
                local p90=r(r2)
                local p99=r(r3)
            }
        }
        post `annual' (`yy') (`rr') (`ag') (`n') (`weights') ///
            (`prevalence') (`mean_all') (`mean') (`p50') (`p90') (`p99')
    }
}
postclose `annual'

* Use a complete year/age grid so interpolation never fills observed missing
* groups from distant rounds. Each interpolated value needs both adjacent rounds.
clear
set obs 20
gen int year=2017+floor((_n-1)/4)
gen byte age_group=mod(_n-1,4)
merge 1:1 year age_group using "`observed_levels'", assert(master match) nogen
gen byte was_interpolated=inlist(year,2018,2020)
foreach metric in prevalence mean_all mean p50 p90 p99 {
    bysort age_group (year): replace was_`metric' = ///
        (was_`metric'[_n-1]+was_`metric'[_n+1])/2 if was_interpolated
}
label define debt_age 0 "All ages" 1 "15-29" 2 "30-54" 3 "55-74", replace
label values age_group debt_age
char _dta[provenance] "Weighted observed WAS BU reference persons; rebuilt from longitudinal .dta, not Excel"
char _dta[input_file] `"`source_file'"'
char _dta[money_basis] "2015 GBP: interview-year nominal debt *100 / ONS L522 annual CPIH"
char _dta[time_basis] "R6/R7/R8 midpoints 2017/2019/2021; adjacent-summary interpolation 2018/2020"
sort year age_group
save `"`output_dir'/unsecured_debt_WAS_levels.dta"', replace

use "`sample'", clear
xtset was_pid was_round
gen byte previous_holder=L.holder
gen byte previous_age_group=L.age_group
gen int previous_year=L.year
postfile `transition' byte from_round byte to_round int start_year int end_year byte age_group ///
    long was_n_matched long was_n_entry_risk long was_n_persist_risk ///
    double was_entry double was_persistence using "`transitions'", replace
forvalues rr=7/8 {
    local first=2005+2*(`rr'-1)
    local last=2005+2*`rr'
    forvalues ag=0/3 {
        local selected "was_round==`rr' & !missing(previous_holder) & year-previous_year==2"
        if `ag'>0 local selected "`selected' & previous_age_group==`ag'"
        quietly count if `selected'
        local n=r(N)
        quietly count if `selected' & previous_holder==0
        local ne=r(N)
        quietly count if `selected' & previous_holder==1
        local np=r(N)
        local entry=.
        local persistence=.
        if `ne'>0 {
            quietly summarize holder [fw=dwt] if `selected' & previous_holder==0, meanonly
            local entry=r(mean)
        }
        if `np'>0 {
            quietly summarize holder [fw=dwt] if `selected' & previous_holder==1, meanonly
            local persistence=r(mean)
        }
        post `transition' (`rr'-1) (`rr') (`first') (`last') (`ag') ///
            (`n') (`ne') (`np') (`entry') (`persistence')
    }
}
postclose `transition'
use "`transitions'", clear
label define debt_age 0 "All ages" 1 "15-29" 2 "30-54" 3 "55-74", replace
label values age_group debt_age
assert was_n_matched==was_n_entry_risk+was_n_persist_risk
char _dta[provenance] "Adjacent observed WAS rounds linked by was_pid; BU reference records at both endpoints"
char _dta[input_file] `"`source_file'"'
char _dta[weight_basis] "Destination cross-sectional dwt; origin reference-person age; exact two-year interview gap"
sort end_year age_group
save `"`output_dir'/unsecured_debt_WAS_transitions.dta"', replace

* Preserve the original SIMULATION deflator, annual CPI (ONS D7BT, 2015=100).
* This is intentionally separate from the legacy survey CPIH convention above.
clear
input int year double cpi
2017 103.4
2018 105.9
2019 107.8
2020 108.7
2021 111.6
end
isid year
label var cpi "Legacy simulation annual CPI setting, 2015=100"
char _dta[source] "Annual CPI D7BT constants from historical simulation validator; survey uses CPIH L522"
save `"`output_dir'/unsecured_debt_cpi.dta"', replace
display as result "DEBT TARGETS READY FROM WAS DTA: 20 annual groups; 8 transition groups; no workbook required."
