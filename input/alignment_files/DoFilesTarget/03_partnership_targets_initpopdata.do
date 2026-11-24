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
* DESCRIPTION:    This do-file constructs the partnership alignment target, the
*                share of partnered adults (aged 18+). It:
*                  - Imports initial population CSV files by year
*                  - Reweights them to the official population by gender x
*                    region x single year of age (00_popproj_weights.do)
*                  - Flags partnered adults from benefit-unit membership
*                  - Computes the weighted partnered share for each year
*                  - Exports the results to input/alignment_files
*
*                The previous version, weighted by wgthhcross only, is
*                03_partnership_targets_initpopdata_NotPopProjAgeNormalised.do and
*                wrote alignment_targets_partnered_share_NotPopProjAgeNormalised.xlsx.
*
* SET-UP:         Set global dir_w to the root of this SimPaths project
*
*******************************************************************************/

version 17.0
clear all
set more off


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


* ============================================================
* 1) Load and append all initial population files
* ============================================================
tempfile appended
clear
save `appended', emptyok replace

forval yr = $min_year/$max_year {
    import delimited using "${dir_input_data}/population_initial_${country}_`yr'.csv", ///
        clear varnames(1) bindquote(strict)
    destring demage idpers idbu idmother idfather demmaleflag demrgn wgthhcross, replace force

    * Weight reweighted to the official population, on the whole file
    popproj_weight, year(`yr')

    keep idbu idpers idmother idfather demage wgt_popproj
    gen int file_year = `yr'
    append using `appended'
    save `appended', replace
}

use `appended', clear

* ============================================================
* 2) Define partnered share — BU membership logic
*
*  Eligible  : demage >= 18
*  Partnered : there exists at least one OTHER adult (age >= 18)
*              in the same benefit unit (file_year x idbu)
*              who is NOT this person's mother or father
*
*  idpartner is NOT used — partnership is inferred purely from
*  BU co-residency, consistent with getPartner() logic.
* ============================================================
keep if demage >= 18

* --- Step A: build list of BU adult members to self-join against ---
preserve
    keep file_year idbu idpers
    rename idpers other_idpers
    tempfile bu_adults
    save `bu_adults', replace
restore

* --- Step B: self-join on (file_year, idbu) ---
* Each person is expanded against all adults in their BU
joinby file_year idbu using `bu_adults'

* Remove self-matches
drop if other_idpers == idpers

* Remove cases where the co-resident is this person's parent
drop if other_idpers == idmother | other_idpers == idfather

* Each remaining row means a qualifying co-resident exists
gen byte has_partner = 1

* Reduce to one row per person: partnered = 1 if any qualifying row
collapse (max) partnered = has_partner, by(file_year idbu idpers)

* --- Step C: save partnered flags, then merge into full eligible pop ---
* joinby drops persons with NO qualifying co-resident, so restore them from
* the full eligible population and assign partnered = 0.
tempfile partnered_flags
save `partnered_flags', replace

use `appended', clear
keep if demage >= 18
keep file_year idbu idpers wgt_popproj

merge 1:1 file_year idbu idpers using `partnered_flags', ///
    keep(master match) nogen
replace partnered = 0 if missing(partnered)
recast double partnered

* ============================================================
* 3) Collapse to annual weighted share
* ============================================================

* Unweighted counts, for reference
preserve
    collapse (count) n_eligible = idpers (sum) n_partnered = partnered, by(file_year)
    tempfile counts
    save `counts'
restore

collapse (mean) partnered_share = partnered [pw = wgt_popproj], by(file_year)
merge 1:1 file_year using `counts', assert(match) nogen

rename file_year year

format partnered_share %12.7f

label var n_eligible      "Eligible persons (age >= 18, unweighted N)"
label var n_partnered     "Partnered persons (unweighted N)"
label var partnered_share "Partnered share (weighted by wgt_popproj)"

order year n_eligible n_partnered partnered_share
list, sep(0)

* ============================================================
* 4) Export: year + partnered_share
* ============================================================
sort year

* Build separate matrices for year and share (written with explicit Excel formats)
mkmat year,            matrix(Yr)
mkmat partnered_share, matrix(Sh)

* Create/overwrite the workbook, starting with the metadata sheet so that
* workbook provenance survives regeneration
local today "`c(current_date)'"
putexcel set "${dir_output}/alignment_targets_partnered_share.xlsx", sheet("Info") replace
putexcel A1=("Field") B1=("Value")
putexcel A2=("Workbook") B2=("alignment_targets_partnered_share.xlsx")
putexcel A3=("Data sheet") B3=("partnered_share")
putexcel A4=("Relevant do file") B4=("input/alignment_files/DoFilesTarget/03_partnership_targets_initpopdata.do")
putexcel A5=("Helper do file") B5=("input/alignment_files/DoFilesTarget/00_popproj_weights.do")
putexcel A6=("Source data") B6=("input/InitialPopulations/population_initial_${country}_${min_year}.csv to population_initial_${country}_${max_year}.csv; input/alignment_files/align_popProjections.xlsx (sheet ${country})")
putexcel A7=("Years covered") B7=("${min_year}-${max_year}")
putexcel A8=("Unit of analysis") B8=("Adult person-year")
putexcel A9=("Eligible population") B9=("Adults aged 18+")
putexcel A10=("Partner definition") B10=("Partnered = 1 when another adult exists in the same BU after excluding self, mother, and father")
putexcel A11=("Partner identifier use") B11=("idpartner is not used; partnership is inferred from adult BU co-residency")
putexcel A12=("Weighting") B12=("${popproj_weight_info}")
putexcel A13=("How target is computed") B13=("Build adult BU roster, self-join by year and BU, drop self and parents, mark qualifying co-residents, merge flags back to the full adult population, then collapse (mean) partnered_share = partnered [pw = wgt_popproj] by year")
putexcel A14=("Output meaning") B14=("Annual partnered share among adults aged 18+ under BU-logic partner inference, on the official gender x region x age mix")
putexcel A15=("Previous version") B15=("alignment_targets_partnered_share_NotPopProjAgeNormalised.xlsx: weighted by wgthhcross only")
putexcel A16=("Produced") B16=("`today'")

* Data sheet
putexcel set "${dir_output}/alignment_targets_partnered_share.xlsx", sheet("partnered_share") modify

* Write headers
putexcel A1=("year") B1=("partnered_share")

* Write data: years as integers, shares with 6 decimal places
putexcel A2=matrix(Yr), nformat("0")
putexcel B2=matrix(Sh), nformat("0.000000")
