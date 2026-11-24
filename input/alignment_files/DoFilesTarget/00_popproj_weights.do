/*******************************************************************************
* PROJECT:        SimPaths UK
* SECTION:        ALIGNMENT PROCEDURES
*
* AUTHORS:        Mariia Vartuzova (MV)
* LAST UPDATE:    24/11/2026 (MV)
* COUNTRY:        UK
*
* DATA:           Official population (input/alignment_files/align_popProjections.xlsx)
*
* DESCRIPTION:    Helper for the alignment-target do-files 01, 02, 03b and 05.
*                 The weighted initial populations have a different age mix
*                 from the official population, while the simulated population
*                 follows the official population by gender x region x single
*                 year of age. The targets are therefore computed on the
*                 initial populations reweighted to that official mix.
*
*                 This do-file:
*                  - Saves the official population by year, gender, region and
*                    single year of age (ages ${popproj_top_age}+ pooled) to
*                    ${dir_working_data}
*                  - Defines the program popproj_weight. Called on one year's
*                    initial population (all ages, one record per idpers), it
*                    adds the variable
*                      wgt_popproj = wgthhcross x official count / survey
*                                    weighted count of the person's cell
*
* USE:            Set the globals country, min_year, max_year, dir_w and
*                 dir_working_data, then
*                   do "${dir_w}/input/alignment_files/DoFilesTarget/00_popproj_weights.do"
*                 After importing a year's initial population, call
*                   popproj_weight, year(`y')
*                 before any restriction to the target population, and weight
*                 the target with [pw = wgt_popproj].
*
*******************************************************************************/


* --- SETTINGS ------------------------------------------------------------- *

* Ages from this one up form a single cell: single-year cells above it are
* small in the survey
global popproj_top_age 90

* Official cells with no survey person cannot be represented. Stop if they hold
* more than this share of the official population (0.01-0.12% in 2011-2023)
global popproj_max_empty_share 0.005

* Text for the Info sheets of the target workbooks
global popproj_weight_info "wgthhcross reweighted to the official population (align_popProjections.xlsx) by gender x region x single year of age, ages ${popproj_top_age}+ pooled: wgt_popproj = wgthhcross x official count / survey weighted count of the person's cell, computed on the whole annual file (00_popproj_weights.do)"


* --- OFFICIAL POPULATION BY YEAR, GENDER, REGION AND AGE ------------------- *

import excel using "${dir_w}/input/alignment_files/align_popProjections.xlsx", ///
	sheet("${country}") firstrow clear

* Year columns are named by their Excel column letter; the year is their label
foreach v of varlist _all {
	if !inlist("`v'", "Gender", "Region", "Age") {
		local yr : variable label `v'
		if inrange(real("`yr'"), ${min_year}, ${max_year}) rename `v' pop`yr'
		else drop `v'
	}
}
reshape long pop, i(Gender Region Age) j(year)

* Gender and region coded as in the initial populations
assert inlist(Gender, "Male", "Female")
gen byte demmaleflag = (Gender == "Male")

* demrgn holds the UKMOD drgn1 code (simpaths.model.enums.Region)
gen byte demrgn = .
replace demrgn = 1  if Region == "UKC"
replace demrgn = 2  if Region == "UKD"
replace demrgn = 4  if Region == "UKE"
replace demrgn = 5  if Region == "UKF"
replace demrgn = 6  if Region == "UKG"
replace demrgn = 7  if Region == "UKH"
replace demrgn = 8  if Region == "UKI"
replace demrgn = 9  if Region == "UKJ"
replace demrgn = 10 if Region == "UKK"
replace demrgn = 11 if Region == "UKL"
replace demrgn = 12 if Region == "UKM"
replace demrgn = 13 if Region == "UKN"
assert !missing(demrgn)

gen int popproj_age_cell = min(Age, ${popproj_top_age})
collapse (sum) pop_official = pop, by(year demmaleflag demrgn popproj_age_cell)

save "${dir_working_data}/popproj_official_${country}.dta", replace
clear


* --- PROGRAM: popproj_weight ----------------------------------------------- *

capture program drop popproj_weight
program define popproj_weight
	syntax, Year(integer)

	confirm new variable wgt_popproj popproj_age_cell pop_official
	confirm variable idpers demmaleflag demrgn demage wgthhcross
	isid idpers
	assert !missing(demmaleflag, demrgn, demage, wgthhcross)

	* Official population of the year
	tempfile official
	preserve
		use "${dir_working_data}/popproj_official_${country}.dta", clear
		quietly keep if year == `year'
		quietly count
		if r(N) == 0 {
			display as error "popproj_weight: no official population for `year'"
			exit 459
		}
		quietly summarize pop_official
		local pop_total = r(sum)
		drop year
		quietly save `official'
	restore

	gen int popproj_age_cell = min(demage, ${popproj_top_age})
	quietly merge m:1 demmaleflag demrgn popproj_age_cell using `official'

	* Every survey person falls in an official cell
	assert _merge != 1

	* Official cells with no survey person
	quietly summarize pop_official if _merge == 2
	local empty_share = r(sum) / `pop_total'
	display as text "popproj_weight, `year': " as result %5.3f 100 * `empty_share' ///
		as text "% of the official population is in cells with no survey person"
	if `empty_share' > ${popproj_max_empty_share} {
		display as error "popproj_weight: more than " 100 * ${popproj_max_empty_share} ///
			"% of the official population is in empty cells in `year'"
		exit 459
	}
	quietly drop if _merge == 2
	drop _merge

	* Survey weighted count of each cell, on the whole annual file
	tempvar survey_count
	bysort demmaleflag demrgn popproj_age_cell: egen double `survey_count' = total(wgthhcross)
	gen double wgt_popproj = wgthhcross * pop_official / `survey_count'
	assert !missing(wgt_popproj) & wgt_popproj > 0
	label variable wgt_popproj "wgthhcross reweighted to the official population by gender x region x age"

	drop popproj_age_cell pop_official
end
