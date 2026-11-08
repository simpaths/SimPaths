/*******************************************************************************
* PROJECT: SimPaths UK - wealth validation simulation preparation
* AUTHOR: Liang Shi
* LAST UPDATE: 28 October 2026
* Arguments: manifest_file output_dir initial_year consumption housing pensions debt
* The wealth master generates the manifest from its inline run folders.
* No manually maintained run-list CSV is required for the master workflow.
* Manifest CSV: run_id,csv_dir. One unique integer label per completed run;
* csv_dir names that run's CSV directory (quote paths containing commas).
* Labels are assigned consistently across BU and Person files, independently of
* exported run numbers. IDs need only be unique WITHIN each run and year.
* Numbers are imported as double; consumption retains its legacy float income/
* expenditure storage below, preserving the original income-quintile boundaries.
* Stocks are not summed across people, transformed or deflated here.
* All enabled runs must start in initial_year. Filtering years cannot reinitialise.
*******************************************************************************/
version 18
set more off
args manifest_file output_dir initial_year consumption housing pensions debt
confirm file `"`manifest_file'"'
import delimited using `"`manifest_file'"', varnames(1) case(preserve) asdouble clear
confirm numeric variable run_id
confirm string variable csv_dir
isid run_id
* Listing the same directory twice would falsely count one simulation as two.
isid csv_dir
assert run_id == floor(run_id) & run_id >= 0 & run_id < .
assert csv_dir != ""
assert _N > 0
sort run_id
local n_runs = _N
forvalues n = 1/`n_runs' {
    local label`n' = run_id[`n']
    local directory`n' = csv_dir[`n']
    if `consumption' | `housing' | `debt' confirm file `"`directory`n''/BenefitUnit.csv"'
    if `pensions' confirm file `"`directory`n''/Person.csv"'
}
capture mkdir `"`output_dir'"'
tempfile benefitunits people
forvalues n = 1/`n_runs' {
    local label = `label`n''
    if `consumption' | `housing' | `debt' {
        import delimited using `"`directory`n''/BenefitUnit.csv"', ///
            varnames(1) case(preserve) asdouble clear
        local needed time id_BenefitUnit
        if `consumption' local needed `needed' ageRefPerson yDispMonth xDiscConsumptionAnnual
        if `housing' local needed `needed' wealthPrptyValue wealthMortgageDebtValue
        if `debt' local needed `needed' ageRefPerson wealthUnsecuredDebtHighValue
        local needed : list uniq needed
        keep `needed'
        rename (time id_BenefitUnit) (year idBu)
        gen double run = `label'
        if `consumption' | `debt' {
            capture confirm numeric variable ageRefPerson
            if _rc {
                replace ageRefPerson = "" if ageRefPerson == "null"
                destring ageRefPerson, replace
            }
        }
        confirm numeric variable run year idBu
        assert year == floor(year) & year < .
        quietly summarize year, meanonly
        assert r(min) == `initial_year'
        isid run year idBu
        if `n' > 1 append using "`benefitunits'"
        save "`benefitunits'", replace
    }
    if `pensions' {
        import delimited using `"`directory`n''/Person.csv"', ///
            varnames(1) case(preserve) asdouble clear
        keep time id_Person idBu labC4 contRateOPEe contRateOPEr contRatePP wealthPensValue
        rename (time id_Person) (year idPers)
        gen double run = `label'
        confirm string variable labC4
        gen byte employed = labC4 == "EmployedOrSelfEmployed"
        drop labC4
        confirm numeric variable run year idPers idBu employed contRateOPEe contRateOPEr contRatePP wealthPensValue
        assert year == floor(year) & year < .
        quietly summarize year, meanonly
        assert r(min) == `initial_year'
        isid run year idPers
        if `n' > 1 append using "`people'"
        save "`people'", replace
    }
}
if `consumption' | `housing' | `debt' {
    use "`benefitunits'", clear
    isid run year idBu
    sort run year idBu
    char _dta[run_manifest] `"`manifest_file'"'
    if `consumption' {
        preserve
        keep run year idBu ageRefPerson yDispMonth xDiscConsumptionAnnual
        * The original consumption importer used floats for these two fields.
        * Preserve that convention before ranking incomes: double precision can
        * split former ties and change quintile composition near the cut-offs.
        recast float yDispMonth xDiscConsumptionAnnual, force
        save `"`output_dir'/consumption_simulation_sample.dta"', replace
        restore
    }
    if `housing' {
        preserve
        keep run year idBu wealthPrptyValue wealthMortgageDebtValue
        save `"`output_dir'/housing_simulation_sample.dta"', replace
        restore
    }
    if `debt' {
        keep run year idBu ageRefPerson wealthUnsecuredDebtHighValue
        save `"`output_dir'/unsecured_debt_simulation_sample.dta"', replace
    }
}
if `pensions' {
    use "`people'", clear
    isid run year idPers
    sort run year idPers
    char _dta[run_manifest] `"`manifest_file'"'
    save `"`output_dir'/pension_person_simulation_sample.dta"', replace
}
display as result "WEALTH SIMULATION INPUTS READY: `n_runs' completed run(s), initial year `initial_year'."
