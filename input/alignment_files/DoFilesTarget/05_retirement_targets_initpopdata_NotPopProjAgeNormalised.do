* SUPERSEDED (24/11/2026). Kept for reference: these targets are not reweighted to the
* official population by gender x region x age (align_popProjections.xlsx). This file
* writes alignment_targets_*_NotPopProjAgeNormalised.xlsx. Current version: 05_retirement_targets_initpopdata.do

/*******************************************************************************
* PROJECT:        SimPaths UK
* SECTION:        ALIGNMENT PROCEDURES
*
* AUTHORS:        Mariia Vartuzova (MV)
* LAST UPDATE:    19/11/2026 (MV)
* COUNTRY:        UK
*
* DATA:           Initial populations
*
* DESCRIPTION:    This do-file constructs retirement targets using initial
*                population data. It:
*                  - Imports initial population CSV files by year
*                  - Computes the share of retired persons (labc4 == 4)
*                    among those aged min_age_to_retire (50) or older with
*                    non-missing labour status
*                  - Exports the results to Excel
*
*                The age limit is MIN_AGE_TO_RETIRE in Parameters.java, the
*                age from which SimPaths considers retirement. The simulation
*                measures the retired share on the same population, both when
*                aligning (RetirementAlignment.java) and in the output
*                (AlignmentStatistics.java).
*
*                Persons above MAX_AGE_FLEXIBLE_LABOUR_SUPPLY (75) are retired
*                in the simulation. The initial populations already code all of
*                them as retired (labc4 == 4), so no recode is needed here.
*
* SET-UP:         1. Set global dir_w to the root of this SimPaths project
*                 2. The initial populations are read from
*                    input/InitialPopulations of that project. The workbook is
*                    written to input/alignment_files/DoFilesTarget; copy it to
*                    input/alignment_files/ for SimPaths to use it
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

* Target population: persons aged min_age_to_retire or older. Must equal
* MIN_AGE_TO_RETIRE in src/main/java/simpaths/data/Parameters.java.
global min_age_to_retire 50

* Directory structure
global dir_input_data   "$dir_w/input/InitialPopulations"
global dir_working_data "$dir_w/input/alignment_files/DoFilesTarget/working_data"
global dir_output       "$dir_w/input/alignment_files/DoFilesTarget"

* Create the working folder if it does not exist yet
capture mkdir "${dir_working_data}"


* Initialise file that will store retirement shares for all years. Start from
* an empty file, so that shares saved by an earlier run are not exported again.
clear
save "${dir_working_data}/retirement_shares_${country}_initpopdata.dta", emptyok replace

* ========================================================================== *

// Loop over all years in the requested range
foreach y of numlist $min_year/$max_year {

	* Build file name for the given year and import initial population data
	local file = subinstr("population_initial_${country}_YYYY.csv","YYYY","`y'",.)
	import delimited using "${dir_input_data}/`file'", clear

	bys idpers: keep if _n == 1 // keep one obs per idpers

	// Alignment target: share of retired persons among those aged
	// min_age_to_retire or older with non-missing labc4, defined in RetirementAlignment.java
	gen byte isRetired = (labc4 == 4)
	collapse (mean) retired_share = isRetired ///
		if (labc4 != . & demage != . & demage >= ${min_age_to_retire}) [pw = wgthhcross]

	gen year = `y'

	* Append to cumulative file for all years
	append using "${dir_working_data}/retirement_shares_${country}_initpopdata.dta"
	duplicates drop
	save "${dir_working_data}/retirement_shares_${country}_initpopdata.dta", replace

}

* -------------------------------------------------------------------------- *
* POST-PROCESSING: export aggregated results to Excel
* -------------------------------------------------------------------------- *


use "${dir_working_data}/retirement_shares_${country}_initpopdata.dta", clear

* Sort by year for neat export
sort year

* Build separate matrices for year and share (written with explicit Excel formats)
mkmat year,          matrix(Yr)
mkmat retired_share, matrix(Sh)

* Create/overwrite the workbook, starting with the metadata sheet so that
* workbook provenance survives regeneration
local today "`c(current_date)'"
putexcel set "${dir_output}/alignment_targets_retirement_NotPopProjAgeNormalised.xlsx", sheet("Info") replace
putexcel A1=("Field") B1=("Value")
putexcel A2=("Workbook") B2=("alignment_targets_retirement_NotPopProjAgeNormalised.xlsx")
putexcel A3=("Data sheet") B3=("retirement")
putexcel A4=("Relevant do file") B4=("input/alignment_files/DoFilesTarget/05_retirement_targets_initpopdata_NotPopProjAgeNormalised.do")
putexcel A5=("Source data") B5=("input/InitialPopulations/population_initial_${country}_${min_year}.csv to population_initial_${country}_${max_year}.csv")
putexcel A6=("Years covered") B6=("${min_year}-${max_year}")
putexcel A7=("Unit of analysis") B7=("Person-year")
putexcel A8=("Population age") B8=("${min_age_to_retire}+")
putexcel A9=("Eligible population") B9=("People aged ${min_age_to_retire} and over with nonmissing labc4; one record is kept per idpers in each annual file")
putexcel A10=("Retired definition") B10=("isRetired = 1 when labc4 == 4 (Retired)")
putexcel A11=("Weighting") B11=("Weighted mean using wgthhcross")
putexcel A12=("How target is computed") B12=("For each year: collapse (mean) retired_share = isRetired if labc4 != . and demage >= ${min_age_to_retire} [pw = wgthhcross]")
putexcel A13=("Output meaning") B13=("Annual share of retired persons among people aged ${min_age_to_retire} and over")
putexcel A14=("Model use") B14=("Parameters.getTargetShare(year, TargetShares.Retirement); RetirementAlignment and AlignmentStatistics measure the simulated share on the same population (age >= MIN_AGE_TO_RETIRE, nonmissing labour status)")
putexcel A15=("Age limit") B15=("${min_age_to_retire} = MIN_AGE_TO_RETIRE in Parameters.java; change both together")
putexcel A16=("Produced") B16=("`today'")

* Data sheet
putexcel set "${dir_output}/alignment_targets_retirement_NotPopProjAgeNormalised.xlsx", sheet("retirement") modify

* Write headers
putexcel A1=("year") B1=("retired_share")

* Write data: years as integers, shares with 6 decimal places
putexcel A2=matrix(Yr), nformat("0")
putexcel B2=matrix(Sh), nformat("0.000000")
