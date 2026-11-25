/*******************************************************************************
* PROJECT:        SimPaths UK
* SECTION:        ALIGNMENT PROCEDURES
*
* AUTHORS:        Mariia Vartuzova (MV)
* LAST UPDATE:    24/11/2026 (MV)
* COUNTRY:        UK
*
* DATA:           Initial populations, official population (align_popProjections.xlsx)
*
* DESCRIPTION:    This do-file constructs the in-school alignment target, the
*                share of students among people aged 16-29. It:
*                  - Imports initial population CSV files by year
*                  - Reweights them to the official population by gender x
*                    region x single year of age (00_popproj_weights.do)
*                  - Computes the student share for each year
*                  - Exports the results to input/alignment_files
*
*                The previous version, weighted by wgthhcross only, is
*                02_inSchool_targets_initpopdata_NotPopProjAgeNormalised.do and
*                wrote alignment_targets_inSchool_NotPopProjAgeNormalised.xlsx.
*
* SET-UP:         Set global dir_w to the root of this SimPaths project
*
*******************************************************************************/

clear all


* --- DEFINE GLOBALS -------------------------------------------------------- *

* Working directory (project root)
global dir_w "/Users/pineapple/IdeaProjects/SimPathsUK_NOV"


* Country code and time span for which targets are produced
global country = "UK"
global min_year 2011
global max_year 2023

* Directory structure
global dir_input_data   "$dir_w/input/InitialPopulations"
global dir_do_files     "$dir_w/input/alignment_files/DoFilesTarget"
global dir_working_data "$dir_do_files/working_data"
global dir_output       "$dir_w/input/alignment_files"

* Create the working folder if it does not exist yet
capture mkdir "${dir_working_data}"

* Official population and the program popproj_weight
do "${dir_do_files}/00_popproj_weights.do"


* Initialise file that will store student shares for all years
clear
save "${dir_working_data}/student_shares_${country}_popproj.dta", emptyok replace

* ========================================================================== *

// Loop over all years in the requested range
foreach y of numlist $min_year/$max_year {

	* Build file name for the given year and import initial population data
	local file = subinstr("population_initial_${country}_YYYY.csv","YYYY","`y'",.)
	import delimited using "${dir_input_data}/`file'", clear

	bys idpers: keep if _n == 1 // keep one obs per idpers

	* Weight reweighted to the official population, on the whole file
	popproj_weight, year(`y')

	// Alignment target: share of students among 16-29 age group, defined in SimPathsModel.java
	gen double isStudent = (labc4 == 2 & demage >= 16 & demage <= 29)
	collapse (mean) student_share = isStudent if (labc4 != . & demage >= 16 & demage <= 29) [pw = wgt_popproj]

	gen year = `y'

	* Append to cumulative file for all years
	append using "${dir_working_data}/student_shares_${country}_popproj.dta"
	save "${dir_working_data}/student_shares_${country}_popproj.dta", replace

}

* -------------------------------------------------------------------------- *
* POST-PROCESSING: export aggregated results to Excel
* -------------------------------------------------------------------------- *


use "${dir_working_data}/student_shares_${country}_popproj.dta", clear

* Sort by year for neat export
sort year

* Build separate matrices for year and share (written with explicit Excel formats)
mkmat year,          matrix(Yr)
mkmat student_share, matrix(Sh)

* Create/overwrite the workbook, starting with the metadata sheet so that
* workbook provenance survives regeneration
local today "`c(current_date)'"
putexcel set "${dir_output}/alignment_targets_inSchool.xlsx", sheet("Info") replace
putexcel A1=("Field") B1=("Value")
putexcel A2=("Workbook") B2=("alignment_targets_inSchool.xlsx")
putexcel A3=("Data sheet") B3=("students")
putexcel A4=("Relevant do file") B4=("input/alignment_files/DoFilesTarget/02_inSchool_targets_initpopdata.do")
putexcel A5=("Helper do file") B5=("input/alignment_files/DoFilesTarget/00_popproj_weights.do")
putexcel A6=("Source data") B6=("input/InitialPopulations/population_initial_${country}_${min_year}.csv to population_initial_${country}_${max_year}.csv; input/alignment_files/align_popProjections.xlsx (sheet ${country})")
putexcel A7=("Years covered") B7=("${min_year}-${max_year}")
putexcel A8=("Unit of analysis") B8=("Person-year")
putexcel A9=("Eligible population") B9=("People aged 16-29 with nonmissing labc4; one record kept per idpers in each annual file")
putexcel A10=("Student definition") B10=("isStudent = 1 when labc4 == 2 and age is between 16 and 29 inclusive")
putexcel A11=("Weighting") B11=("${popproj_weight_info}")
putexcel A12=("How target is computed") B12=("For each year: collapse (mean) student_share = isStudent if labc4 != . and age is 16-29 [pw = wgt_popproj]")
putexcel A13=("Output meaning") B13=("Annual share of students among people aged 16-29, on the official gender x region x age mix")
putexcel A14=("Previous version") B14=("alignment_targets_inSchool_NotPopProjAgeNormalised.xlsx: weighted by wgthhcross only")
putexcel A15=("Produced") B15=("`today'")

* Data sheet
putexcel set "${dir_output}/alignment_targets_inSchool.xlsx", sheet("students") modify

* Write headers
putexcel A1=("year") B1=("student_share")

* Write data: years as integers, shares with 6 decimal places
putexcel A2=matrix(Yr), nformat("0")
putexcel B2=matrix(Sh), nformat("0.000000")
