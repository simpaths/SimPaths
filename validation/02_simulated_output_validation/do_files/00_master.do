/*******************************************************************************
* PROJECT:  		SimPaths UK
* SECTION:			Validation - Internal 
* OBJECT: 			Master file
* AUTHORS:			Ashley Burdett
* LAST UPDATE:		Aug 2026
* COUNTRY: 			UK
* DESCRIPTION: 		Entry point for the SimPaths UK validation pipeline. Sets
* 					globals and parameters, then loops over each alignment
* 					configuration listed in `alignments' to: (1) prepare
* 					simulated output data, (2) build UKHLS validation
* 					targets from initial-populations (UID) data, and
* 					(3) produce comparison graphs for the 18 validation
* 					modules (04_01-04_18). 
********************************************************************************
* REQUIRES: 		- UK UID data "UKHLS_pooled_ipop.dta".
* 					- SimPaths simulated output CSVs, one folder per run, at:
* 					  simulated_outputs/[alignment]/[folder]_[run_n]/csv/xx.csv
********************************************************************************
* SETUP:			1. In the DEFINE DIRECTORIES block below, point `dir_ind'
* 					   to the main folder that contains "do_files" (this
* 					   file's own folder). Its "data" and "graphs" output
* 					   subfolders are created automatically if they don't
* 					   already exist (see CREATE TOP-LEVEL OUTPUT FOLDERS).
*					   Only need to update the file paths where you see	
*  					   ">>> EDIT <<<".
* 					2. In the DEFINE PARAMETERS block, set `folder' to the
* 					   condensed run-date stamp of the simulation you want to
* 					   validate (e.g. 20260327) -- it must match an existing
* 					   `[folder]_[run_n]' subfolder contained in the folder 
* 					   containing the simulated output csv files. Set 
* 					   `max_n_runs' to how many run folders exist under that 
* 					   stamp (runs are numbered from 0).
* 					3. In the DYNAMIC SET UP block, set local `alignments' to
* 					   list of folder names of outputs from different model 
* 	 				   set-ups that  you want to validate (e.g. "0_default").  
* 					   Each set-up gets its own graphs/[alignment]/ output tree, 
* 					   created automatically.
* 					4. Run the whole file: do "do_files/00_master.do"
********************************************************************************
* NOTES: 
* 	- Folders only needs to be created once. "mkdir" does not overwrite folders. 
* 	- Income amounts throughout are in annual terms.
*******************************************************************************/
clear all

set logtype smcl
set more off
set type double


/*******************************************************************************
* 1 - STATIC SET UP 
*******************************************************************************/

/*******************************************************************************
* DEFINE COUNTRY & RUN GLOBALS
*******************************************************************************/

global country = "UK"		 						
global country_lower = "uk"


/*******************************************************************************
* DEFINE DIRECTORIES
*******************************************************************************/

* Individual directory 
* >>> EDIT <<<
global dir_ind ".../_SimPathsUK"

* Main folder
* >>> EDIT <<<
global path "$dir_ind/validation/02_simulated_output_validation"

* Do files folder 
* Folder contianing this do-file 
global dir_do_files "$path/do_files" 

* Output data folder 
global dir_work "$path/data" 

global dir_data "$path/data"

* Input data: UID dataset folder 
* >>> EDIT <<<
global dir_UKHLS_data "$dir_ind/input_processing/initial_populations/data"


/*******************************************************************************
* DEFINE PARAMETERS
*******************************************************************************/

global use_assert "0"

* Trim outliers (top and bottom percentiles)
global trim_outliers true

* Observations up to and including this simulated year to be kept in the sample
global min_sim_year 2013
global max_sim_year 2023

* Define age to become fully responsible as defined in the simulation i.e.
* can form a partnership, work, have children etc. 
global age_become_responsible 18

* Set labour supply categories 
/*
Note: This works because the categories are symmetric across the genders.
*/ 
global ls_cat "ZERO TEN TWENTY THIRTY THIRTY_EIGHT FORTY_FIVE FIFTY_FIVE" 

global ls_cat_labour ///
	"TEN TWENTY THIRTY THIRTY_EIGHT FORTY_FIVE FIFTY_FIVE" 

* Number of runs (N-1 since numbering starts at 0)
global max_n_runs 4

* Run's common folder name in which data across runs is deposited
global folder 20260816


/*******************************************************************************
* CREATE TOP-LEVEL OUTPUT FOLDERS
*******************************************************************************/

cap mkdir "$path/data"
cap mkdir "$path/graphs"


/*******************************************************************************
* RUN DO FILES
*******************************************************************************/

* Prepare UKHLS data
do "${dir_do_files}/03_create_UKHLS_validation_targets.do"


/*******************************************************************************
* 2 - DYNAMIC SET UP 
*******************************************************************************/

/*
This section defines which model outputs to validate. It permits multiple model 
set-ups to be validated by looping over the name of the folders containing 
the output csv file(s). The workflow automatically generates folders with the 
same names as the output data folders containing the relevant validation graphs. 
*/

* Folder name(s) to validate 
local alignments "care_provision"

foreach align in `alignments' {
	
	
/*******************************************************************************
* DEFINE DIRECTORIES
*******************************************************************************/

	* Simulated data CSV files folder
	* >>> EDIT <<<
	global dir_simulated_data "${dir_ind}/simulated_outputs/`align'"
		
	* Graphs folder 
	global dir_output_files "$path/graphs/`align'" 	


/*******************************************************************************
* CREATE OUTPUT FOLDERS
*******************************************************************************/

	cap mkdir "$path/graphs/`align'"

	cap mkdir "$path/graphs/`align'/children"
	cap mkdir "$path/graphs/`align'/correlations"
	cap mkdir "$path/graphs/`align'/disability"
	cap mkdir "$path/graphs/`align'/economic_activity"
	cap mkdir "$path/graphs/`align'/education"
	cap mkdir "$path/graphs/`align'/health"
	cap mkdir "$path/graphs/`align'/hours_worked"
	cap mkdir "$path/graphs/`align'/income"
	cap mkdir "$path/graphs/`align'/income/capital_income"
	cap mkdir "$path/graphs/`align'/income/pension_income"
	cap mkdir "$path/graphs/`align'/income/disposable_income"
	cap mkdir "$path/graphs/`align'/income/equivalised_disposable_income"
	cap mkdir "$path/graphs/`align'/income/gross_income"
	cap mkdir "$path/graphs/`align'/income/gross_labour_income"
	cap mkdir "$path/graphs/`align'/income/income_shares"
	cap mkdir "$path/graphs/`align'/inequality"
	cap mkdir "$path/graphs/`align'/partnership"
	cap mkdir "$path/graphs/`align'/poverty"
	cap mkdir "$path/graphs/`align'/wages"
	cap mkdir "$path/graphs/`align'/social_care"


/*******************************************************************************
* RUN DO FILES 
*******************************************************************************/

	* Prepare simulated data
	do "${dir_do_files}/01_prepare_simulated_data.do"
		
	do "${dir_do_files}/02_create_simulated_variables.do"


	* Plot figures	
	do "${dir_do_files}/04_01_plot_activity_status.do"
	do "${dir_do_files}/04_02_plot_education_level.do"
	do "${dir_do_files}/04_03_plot_gross_income.do"
	do "${dir_do_files}/04_04_plot_gross_labour_income.do"
	do "${dir_do_files}/04_05_plot_capital_income.do"
	do "${dir_do_files}/04_06_plot_pension_income.do"
	do "${dir_do_files}/04_07_plot_disposable_income.do"
	do "${dir_do_files}/04_08_plot_equivalised_disposable_income.do"
	do "${dir_do_files}/04_09_plot_hourly_wages.do"
	do "${dir_do_files}/04_10_0_plot_hours_worked.do"
	do "${dir_do_files}/04_10_1_plot_hours_worked_discrete.do"
	do "${dir_do_files}/04_11_plot_income_shares.do" 
	do "${dir_do_files}/04_12_plot_partnership_status.do"	
	do "${dir_do_files}/04_13_plot_health.do"
	do "${dir_do_files}/04_14_plot_at_risk_of_poverty.do"
	do "${dir_do_files}/04_15_plot_inequality.do"
	do "${dir_do_files}/04_16_plot_number_children.do"
	do "${dir_do_files}/04_17_plot_disability.do"
	do "${dir_do_files}/04_18_plot_social_care.do"

}
