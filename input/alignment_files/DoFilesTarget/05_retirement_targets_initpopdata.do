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
* DESCRIPTION:    This do-file constructs retirement targets using initial
*                population data. It:
*                  - Imports initial population CSV files by year
*                  - Reweights them to the official population by gender x
*                    region x single year of age (00_popproj_weights.do)
*                  - Computes the share of retired persons (labc4 == 4)
*                    among those aged min_age_to_retire (50) or older with
*                    non-missing labour status
*                  - Exports the results to input/alignment_files
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
*                The previous version, weighted by wgthhcross only, is
*                05_retirement_targets_initpopdata_NotPopProjAgeNormalised.do and
*                wrote alignment_targets_retirement_NotPopProjAgeNormalised.xlsx.
*
* SET-UP:         Set global dir_w to the root of this SimPaths project. The
*                 initial populations are read from input/InitialPopulations
*                 and the workbook is written to input/alignment_files
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
global dir_do_files     "$dir_w/input/alignment_files/DoFilesTarget"
global dir_working_data "$dir_do_files/working_data"
global dir_output       "$dir_w/input/alignment_files"

* Create the working folder if it does not exist yet
capture mkdir "${dir_working_data}"

* Official population and the program popproj_weight
do "${dir_do_files}/00_popproj_weights.do"


* Initialise file that will store retirement shares for all years. Start from
* an empty file, so that shares saved by an earlier run are not exported again.
clear
save "${dir_working_data}/retirement_shares_${country}_popproj.dta", emptyok replace

* ========================================================================== *

// Loop over all years in the requested range
foreach y of numlist $min_year/$max_year {

	* Build file name for the given year and import initial population data
	local file = subinstr("population_initial_${country}_YYYY.csv","YYYY","`y'",.)
	import delimited using "${dir_input_data}/`file'", clear

	bys idpers: keep if _n == 1 // keep one obs per idpers

	* Weight reweighted to the official population, on the whole file
	popproj_weight, year(`y')

	// Alignment target: share of retired persons among those aged
	// min_age_to_retire or older with non-missing labc4, defined in RetirementAlignment.java
	gen double isRetired = (labc4 == 4)
	collapse (mean) retired_share = isRetired ///
		if (labc4 != . & demage != . & demage >= ${min_age_to_retire}) [pw = wgt_popproj]

	gen year = `y'

	* Append to cumulative file for all years
	append using "${dir_working_data}/retirement_shares_${country}_popproj.dta"
	save "${dir_working_data}/retirement_shares_${country}_popproj.dta", replace

}

* -------------------------------------------------------------------------- *
* POST-PROCESSING: export aggregated results to Excel
* -------------------------------------------------------------------------- *


use "${dir_working_data}/retirement_shares_${country}_popproj.dta", clear

* Sort by year for neat export
sort year

* Build separate matrices for year and share (written with explicit Excel formats)
mkmat year,          matrix(Yr)
mkmat retired_share, matrix(Sh)

* Create/overwrite the workbook, starting with the metadata sheet so that
* workbook provenance survives regeneration
local today "`c(current_date)'"
putexcel set "${dir_output}/alignment_targets_retirement.xlsx", sheet("Info") replace
putexcel A1=("Field") B1=("Value")
putexcel A2=("Workbook") B2=("alignment_targets_retirement.xlsx")
putexcel A3=("Data sheet") B3=("retirement")
putexcel A4=("Relevant do file") B4=("input/alignment_files/DoFilesTarget/05_retirement_targets_initpopdata.do")
putexcel A5=("Helper do file") B5=("input/alignment_files/DoFilesTarget/00_popproj_weights.do")
putexcel A6=("Source data") B6=("input/InitialPopulations/population_initial_${country}_${min_year}.csv to population_initial_${country}_${max_year}.csv; input/alignment_files/align_popProjections.xlsx (sheet ${country})")
putexcel A7=("Years covered") B7=("${min_year}-${max_year}")
putexcel A8=("Unit of analysis") B8=("Person-year")
putexcel A9=("Population age") B9=("${min_age_to_retire}+")
putexcel A10=("Eligible population") B10=("People aged ${min_age_to_retire} and over with nonmissing labc4; one record is kept per idpers in each annual file")
putexcel A11=("Retired definition") B11=("isRetired = 1 when labc4 == 4 (Retired)")
putexcel A12=("Weighting") B12=("${popproj_weight_info}")
putexcel A13=("How target is computed") B13=("For each year: collapse (mean) retired_share = isRetired if labc4 != . and demage >= ${min_age_to_retire} [pw = wgt_popproj]")
putexcel A14=("Output meaning") B14=("Annual share of retired persons among people aged ${min_age_to_retire} and over, on the official gender x region x age mix")
putexcel A15=("Model use") B15=("Parameters.getTargetShare(year, TargetShares.Retirement); RetirementAlignment and AlignmentStatistics measure the simulated share on the same population (age >= MIN_AGE_TO_RETIRE, nonmissing labour status)")
putexcel A16=("Age limit") B16=("${min_age_to_retire} = MIN_AGE_TO_RETIRE in Parameters.java; change both together")
putexcel A17=("Previous version") B17=("alignment_targets_retirement_NotPopProjAgeNormalised.xlsx: weighted by wgthhcross only")
putexcel A18=("Produced") B18=("`today'")

* Data sheet
putexcel set "${dir_output}/alignment_targets_retirement.xlsx", sheet("retirement") modify

* Write headers
putexcel A1=("year") B1=("retired_share")

* Write data: years as integers, shares with 6 decimal places
putexcel A2=matrix(Yr), nformat("0")
putexcel B2=matrix(Sh), nformat("0.000000")
