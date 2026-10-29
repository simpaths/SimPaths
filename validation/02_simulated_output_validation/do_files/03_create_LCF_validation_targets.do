/*******************************************************************************
* PROJECT:      SimPaths UK
* SECTION:      Simulated output validation - survey target preparation
* OBJECT:       Prepare Living Costs and Food Survey (LCFS / LCF) targets
* Author:       Liang Shi
* LAST UPDATE:  27 October 2026
*
* INPUT:  lcfs_sorted.dta from the revised collect_lcfs.do (compiled separately).
* OUTPUT: lcfs_validation_sample.dta, one row per observed benefit unit and wave.
*
* Like 03_create_UKHLS_validation_targets.do, this file prepares microdata for
* later comparison scripts. It does not estimate consumption equations, rebuild
* raw LCFS waves, read simulations, modify workbooks, or export figures.
*
* RUN: do "03_create_LCF_validation_targets.do" "survey folder" "target folder"
* Optional third/fourth arguments select first/last survey year (inclusive).
* With no arguments, use dir_LCF_data, dir_data, min_sim_year and max_sim_year
* from a validation master if defined; otherwise use survey_inputs defaults.
*
* VALIDATION CONVENTIONS (from consumption/consumption_surv.do):
* - One benefit unit is one observation. Use weighta, NOT weighta/na (wght).
* - C is broad expenditure after allocated Council Tax / NI domestic rates.
* - Y is the sum of personal net incomes after the SAME local-tax allocation.
* - Keep complete-income-scope BUs with one/two adults and nominal weekly Y>=10.
* - The reported ratio is mean(C/Y), not mean(C)/mean(Y).
* - Define national income quintiles within year BEFORE selecting age groups.
* - Reference-adult age bands are <45, 45-64, and 65-84 (NOT an unlimited 65+).
* - 2015 may be retained as a survey benchmark; existing simulated consumption
*   validation starts in 2016 because simulated 2015 consumption is initialised.
*******************************************************************************/
version 18
set more off
args source_dir target_dir first_year last_year

/*******************************************************************************
* 1 - Directories and validation years
*******************************************************************************/
* Relative defaults assume the validation root is the working directory.
local validation_root "."
if `"`source_dir'"' == "" local source_dir "${dir_LCF_data}"
if `"`source_dir'"' == "" local source_dir `"`validation_root'/survey_inputs/LCF"'
if `"`target_dir'"' == "" local target_dir "${dir_data}"
if `"`target_dir'"' == "" local target_dir `"`validation_root'/data"'
if "`first_year'" == "" local first_year "${min_sim_year}"
if "`last_year'" == "" local last_year "${max_sim_year}"
if "`first_year'" == "" local first_year 2015
if "`last_year'" == "" local last_year 2023
if missing(real("`first_year'"), real("`last_year'")) | ///
    real("`first_year'") > real("`last_year'") {
    display as error "Specify numeric first/last years in ascending order."
    exit 198
}
confirm file `"`source_dir'/lcfs_sorted.dta"'
capture mkdir `"`target_dir'"'
use `"`source_dir'/lcfs_sorted.dta"', clear

/*******************************************************************************
* 2 - Verify the revised source definition; do not silently accept an older build
*******************************************************************************/
confirm numeric variable year caseno bu age female na nk weighta wght ///
    consmpn disp_inc income_scope_ok
confirm string variable income_definition expenditure_definition
assert income_definition == "personal_net_ct"
assert expenditure_definition == "broad_net_ct"
isid year caseno bu
assert year == floor(year) & year < .
keep if inrange(year, `first_year', `last_year')
assert _N > 0
* Keep original variables, including scope and tax-allocation audit fields, so
* anyone using the targets can trace them back to collect_lcfs.do.

/*******************************************************************************
* 3 - Identifiers, demographics and analysis weights
*******************************************************************************/
* LCFS is a repeated cross-section: grouped IDs are not longitudinal links.
egen long idHh = group(year caseno)
egen long idBu = group(year caseno bu)
clonevar dwt_valid = weighta
clonevar demAge = age
gen byte demMaleFlag = 1 - female if inlist(female, 0, 1)
clonevar demNAdult = na
clonevar demNChild = nk
gen byte ageGroupConsumption = .
replace ageGroupConsumption = 1 if inrange(age, 0, 44)
replace ageGroupConsumption = 2 if inrange(age, 45, 64)
replace ageGroupConsumption = 3 if inrange(age, 65, 84)
label define lcfs_age_groups 1 "Under 45" 2 "45-64" 3 "65-84", replace
label values ageGroupConsumption lcfs_age_groups
label var ageGroupConsumption "Reference-adult age band; missing outside 0-84"

/*******************************************************************************
* 4 - Expenditure, disposable income, and budget ratios
*******************************************************************************/
* Weekly levels are nominal GBP for the survey wave. Keep these units explicit.
* Annual values use 52 weeks and monthly values use 52/12. This conversion alone
* is NOT an inflation adjustment. C/Y needs no deflator because it cancels.
gen double valid_xConsumptionBuWeek = consmpn
gen double valid_yDispBuWeek = disp_inc
gen double valid_xConsumptionBuYear = consmpn * 52
gen double valid_yDispBuYear = disp_inc * 52
gen double valid_xConsumptionBuMonth = consmpn * 52 / 12
gen double valid_yDispBuMonth = disp_inc * 52 / 12
label var valid_xConsumptionBuWeek "BU broad expenditure after local tax, nominal GBP/week"
label var valid_yDispBuWeek "BU own disposable income after local tax, nominal GBP/week"
label var valid_xConsumptionBuYear "BU broad expenditure, nominal GBP/year (52 weeks)"
label var valid_yDispBuYear "BU disposable income, nominal GBP/year (52 weeks)"
* Preserve legitimate negative expenditure; the legacy validation screen is on
* income and structural scope, NOT an estimation positivity screen for ln(C).
gen byte sample_consumption = income_scope_ok == 1 & inrange(na, 1, 2) & ///
    disp_inc >= 10 & disp_inc < . & consmpn < . & weighta > 0 & weighta < .
label var sample_consumption "Complete scope, 1/2 adults, weekly Y>=10, observed C and valid weight"
gen double valid_consToInc = consmpn / disp_inc if sample_consumption == 1
label var valid_consToInc "Unit-level broad expenditure / disposable income (C/Y)"
* Excluded rows stay in this preparation output for transparent sample accounting.
* All aggregate statistics MUST select sample_consumption==1.

/*******************************************************************************
* 5 - Weighted NATIONAL income quintiles, separately within each survey year
*******************************************************************************/
* Do not reuse a consumption-estimation rank or recalculate quintiles by age.
* The eligible sample matches consumption_surv.do. In later comparisons the
* simulation must define its own national quintiles separately within each run.
gen byte valid_incomeQuintile = .
quietly levelsof year if sample_consumption == 1, local(years)
foreach yy of local years {
    tempvar annual_quintile
    quietly xtile `annual_quintile' = disp_inc if year == `yy' & ///
        sample_consumption == 1 [aw=weighta], nq(5)
    replace valid_incomeQuintile = `annual_quintile' if year == `yy'
    drop `annual_quintile'
}
label var valid_incomeQuintile "Annual national income quintile (weighta; eligible BUs)"
assert inrange(valid_incomeQuintile, 1, 5) if sample_consumption == 1
assert missing(valid_consToInc) & missing(valid_incomeQuintile) if sample_consumption == 0

/*******************************************************************************
* 6 - Save reusable microdata; statistics and figures are a later validation step
*******************************************************************************/
char _dta[target_source] "Compiled LCFS repeated cross-sections; observed BUs only"
char _dta[money_basis] "Nominal GBP/week; annual=weekly*52; monthly=weekly*52/12"
char _dta[analysis_weight] "weighta, copied to dwt_valid; do not use wght=weighta/na"
char _dta[analysis_sample] "Select sample_consumption==1; excluded rows retained for audit"
char _dta[ratio_statistic] "Weighted mean of unit-level C/Y; not ratio of aggregate means"
char _dta[income_quintiles] "National within survey year, before any age restriction"
char _dta[comparison_years] "Survey 2015 allowed; existing simulation C/Y comparison uses 2016-2023"
sort year idBu
save `"`target_dir'/lcfs_validation_sample.dta"', replace
display as result "Saved LCFS validation sample to `target_dir'."
