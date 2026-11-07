/*******************************************************************************
* PROJECT:  		SimPaths UK
* SECTION:			Validation
* OBJECT:			Validation data processing
* AUTHORS:			Ashley Burdett
* LAST UPDATE:		Feb 2026
* COUNTRY: 			UK  
* DESCRIPTION: 		Constructs UKHLS validation targets, mirroring the
* 					variable set built by 02_create_simulated_variables.do
* 					so simulated and survey statistics are directly
* 					comparable: demographics, education, health, labour
* 					market, income (IHS-transformed amounts converted to
* 					annual levels), and social care. Also computes
* 					equivalised disposable income via the Modified OECD
* 					equivalence scale. Saves the result to
* 					ukhls_validation_sample.dta.
********************************************************************************
* NOTES: 			Income amounts are in annual terms. Negative values in
* 					raw UKHLS variables denote missing/non-response codes
* 					and are recoded to missing throughout. Gross income is
* 					currently constructed by summing components rather than
* 					taken from a single UKHLS variable.
*******************************************************************************/

* WEALTH EXTENSION: Liang Shi, 28 October 2026.
* One UKHLS preparation entry point; retain the standard no-argument call used
* by 00_master.do. Pension preparation reads the original pooled source before
* any standard age cleaning/year filtering, matching pension_wealth_surv.do.
* Pension shares and wealth use UKHLS; contribution rates use WAS separately.
* Run pension targets only:
*   do "03_create_UKHLS_validation_targets.do" "pensions" "UKHLS_pooled_ipop.dta" "data" 2015 2021
args target_set source_file output_dir first_year last_year
if "`target_set'"=="" local target_set "standard"
if !inlist("`target_set'","standard","pensions") {
    display as error "UKHLS target set must be standard or pensions."
    exit 198
}
if "`target_set'"=="pensions" {
    /*******************************************************************************
    * PROJECT:      SimPaths UK - simulated output validation
    * OBJECT:       UKHLS pension contributor and personal-wealth targets
    * AUTHOR:       Liang Shi
    * SOURCE METHOD: Justin van de Ven, pension_wealth_surv.do, 12 August 2026
    * LAST UPDATE:  28 October 2026
    *
    * Run: do "do_files/03_create_UKHLS_validation_targets.do" "pensions" "UKHLS_pooled_ipop.dta" "data"
    * Optional arguments: source_file output_dir first_year last_year (2015-2021).
    * This reads UKHLS MICRODATA. No Excel workbook or fixed workbook targets are used.
    * Source variable names are converted to lower case as in the reference do-file.
    *
    * PRESERVED REFERENCE DEFINITIONS
    * - Unweighted person statistics. OP contributor: contrateopee+contrateoper>0;
    *   PP contributor: contratepp>0; employed: les_c4==1. No WAS earnings screen.
    * - Adult: dag>17. Count adults by idbenefitunit ACROSS THE FULL POOLED FILE,
    *   before year selection, exactly as in the reference. Do not silently change
    *   the grouping to idbenefitunit/year or recompute it on a selected subperiod.
    * - Personal wealth proxy: total_pensions / adult count for adults; zero for
    *   children. The proxy is float, reproducing the reference's default storage.
    * - Positive-holder mean and within-year unweighted own-wealth quintile means.
    * - The reference's literal >0 and >17 comparisons are retained. In Stata,
    *   numeric missing values sort above finite values: missing contributions/age
    *   can therefore satisfy those comparisons. Annual missing-input counts are
    *   saved for audit; no undocumented missing-to-zero or complete-case change.
    * - Shares are saved as percentages for 04_21; wealth remains GBP as recorded.
    *   No new deflator, survey weight, estimation restriction or simulation run.
    *
    * OUTPUTS: ukhls_pension_targets.dta (+ CSV), with annual means and actual counts;
    *   ukhls_pension_validation_sample.dta, a thin person-level audit dataset.
    * The input is read only. Constants and counts are not obtained from Excel.
    *******************************************************************************/
    version 18
    set more off
    if `"`source_file'"' == "" & "${dir_UKHLS_data}" != "" ///
        local source_file "${dir_UKHLS_data}/UKHLS_pooled_ipop.dta"
    if `"`source_file'"' == "" local source_file "./survey_inputs/UKHLS/UKHLS_pooled_ipop.dta"
    if `"`output_dir'"' == "" local output_dir "${dir_data}"
    if `"`output_dir'"' == "" local output_dir "./data"
    if "`first_year'" == "" local first_year 2015
    if "`last_year'" == "" local last_year 2021
    foreach parameter in first_year last_year {
        if missing(real("``parameter''")) | real("``parameter''")!=floor(real("``parameter''")) exit 198
    }
    if `first_year'>`last_year' exit 198
    confirm file `"`source_file'"'
    capture mkdir `"`output_dir'"'
    use `"`source_file'"', clear
    rename *, lower
    confirm variable idbenefitunit pno
    confirm numeric variable stm dag les_c4 contrateopee contrateoper contratepp total_pensions
    keep idbenefitunit pno stm dag les_c4 contrateopee contrateoper contratepp total_pensions
    rename stm year
    gen byte opMemb=((contrateopee+contrateoper)>0.0)
    gen byte ppMemb=(contratepp>0.0)
    gen byte emp=(les_c4==1)
    gen byte adulth=dag>17
    gsort idbenefitunit pno
    by idbenefitunit: egen float na=sum(adulth)
    gen float wealthpensvalue=0
    replace wealthpensvalue=total_pensions/na if adulth
    label var wealthpensvalue "Reference proxy: BU total pensions / full-pool adult count"
    label var na "Adult count by idbenefitunit across full pooled source"
    tempfile sample targets
    save "`sample'", replace

    tempname results
    postfile `results' int year ///
        double op_share double pp_share double op_employed_share ///
        double pp_employed_share double wealth_mean double wealth_q1 ///
        double wealth_q2 double wealth_q3 double wealth_q4 ///
        double wealth_q5 ///
        long n_all long n_employed long n_wealth ///
        long n_wealth_q1 long n_wealth_q2 long n_wealth_q3 ///
        long n_wealth_q4 long n_wealth_q5 long n_missing_op ///
        long n_missing_pp long n_missing_age using "`targets'", replace
    forvalues yy=`first_year'/`last_year' {
        quietly count if year==`yy'
        local n_all=r(N)
        if `n_all'==0 {
            display as error "No UKHLS person records for `yy'. Check the source and validation window."
            exit 2000
        }
        quietly count if year==`yy' & emp
        local n_employed=r(N)
        quietly count if year==`yy' & wealthpensvalue>0 & wealthpensvalue<.
        local n_wealth=r(N)
        quietly count if year==`yy' & missing(contrateopee,contrateoper)
        local n_missing_op=r(N)
        quietly count if year==`yy' & missing(contratepp)
        local n_missing_pp=r(N)
        quietly count if year==`yy' & missing(dag)
        local n_missing_age=r(N)
        quietly summarize opMemb if year==`yy', meanonly
        local op=100*r(mean)
        quietly summarize ppMemb if year==`yy', meanonly
        local pp=100*r(mean)
        quietly summarize opMemb if year==`yy' & emp, meanonly
        local op_emp=100*r(mean)
        quietly summarize ppMemb if year==`yy' & emp, meanonly
        local pp_emp=100*r(mean)
        quietly summarize wealthpensvalue if wealthpensvalue>0 & year==`yy', meanonly
        local wealth=r(mean)
        tempvar quintile
        if `n_wealth'>0 {
            quietly xtile `quintile'=wealthpensvalue if wealthpensvalue>0 & year==`yy', nq(5)
        }
        else gen byte `quintile'=.
        forvalues qq=1/5 {
            quietly summarize wealthpensvalue if `quintile'==`qq', meanonly
            local q`qq'=r(mean)
            local n_q`qq'=r(N)
        }
        post `results' (`yy') (`op') (`pp') (`op_emp') (`pp_emp') ///
            (`wealth') (`q1') (`q2') (`q3') (`q4') (`q5') ///
            (`n_all') (`n_employed') (`n_wealth') (`n_q1') (`n_q2') (`n_q3') (`n_q4') (`n_q5') ///
            (`n_missing_op') (`n_missing_pp') (`n_missing_age')
        drop `quintile'
    }
    postclose `results'
    use "`targets'", clear
    isid year
    gen str16 benchmark_source="UKHLS"
    char _dta[benchmark_basis] "microdata"
    char _dta[provenance] "Rebuilt from UKHLS_pooled_ipop.dta following pension_wealth_surv.do, 12 Aug 2026"
    char _dta[input_file] `"`source_file'"'
    char _dta[units] "Contributor shares percent; wealth GBP as recorded; unweighted actual person counts"
    char _dta[wealth_definition] "Positive holders of float equal-adult proxy; adult counts across full pooled idbenefitunit"
    sort year
    save `"`output_dir'/ukhls_pension_targets.dta"', replace
    export delimited using `"`output_dir'/ukhls_pension_targets.csv"', replace
    use "`sample'", clear
    keep if inrange(year,`first_year',`last_year')
    char _dta[provenance] "Thin UKHLS audit sample; adult counts were computed before year filtering"
    char _dta[input_file] `"`source_file'"'
    save `"`output_dir'/ukhls_pension_validation_sample.dta"', replace
    display as result "UKHLS PENSION TARGETS READY FROM MICRODATA: `first_year'-`last_year'; no workbook required."
    exit
}

* Generate/Tidy required variables

* Load UKHLS data
use "${dir_UKHLS_data}/UKHLS_pooled_ipop.dta", clear


** IDENTIFIERS
rename idhh idHh
rename idbenefitunit idBu
rename idperson idPers
rename idpartner idPartner
rename idmother idMother
rename idfather idFather
rename swv statCollectionWave


** TIME

fre stm 

gen year = stm 
//replace year = year + 2000


** DEMOGRAPHICS

* Gender 
rename dgn demMaleFlag

replace demMaleFlag = . if demMaleFlag < 0


* Age 
rename dag demAge

replace demAge = . if demAge < 0

* Define age groups
gen ageGroup = .
replace ageGroup = 0 if demAge >= 0 & demAge < 15
replace ageGroup = 1 if demAge >= 15 & demAge < 20
replace ageGroup = 2 if demAge >= 20 & demAge < 25
replace ageGroup = 3 if demAge >= 25 & demAge < 30
replace ageGroup = 4 if demAge >= 30 & demAge < 35
replace ageGroup = 5 if demAge >= 35 & demAge < 40
replace ageGroup = 6 if demAge >= 40 & demAge < 60
replace ageGroup = 7 if demAge >= 60 & demAge < 80
replace ageGroup = 8 if demAge >= 80 & demAge <= 100

label def ageGroup /// 
	0 "ageGroup_0_14" ///
	1 "ageGroup_15_19" ///
	2 "ageGroup_20_24" ///
	3 "ageGroup_25_29" ///
	4 "ageGroup_30_34" ///
	5 "ageGroup_35_39" ///
	6 "ageGroup_40_59" ///
	7 "ageGroup_60_79" ///
	8 "ageGroup_80_100" ///
	
label val ageGroup ageGroup
fre ageGroup

gen ageGroup2 = .
replace ageGroup2 = 0 if demAge >= 16 & demAge < 25
replace ageGroup2 = 1 if demAge >= 25 & demAge < 30
replace ageGroup2 = 2 if demAge >= 30 & demAge < 35
replace ageGroup2 = 3 if demAge >= 35 & demAge < 40
replace ageGroup2 = 4 if demAge >= 40 & demAge < 45
replace ageGroup2 = 5 if demAge >= 45 & demAge < 50
replace ageGroup2 = 6 if demAge >= 50 & demAge < 55
replace ageGroup2 = 7 if demAge >= 55 & demAge < 60
replace ageGroup2 = 8 if demAge >= 60 & demAge <= 65

label def ageGrouplb2 /// 
	0 "ageGroup_16_24" ///
	1 "ageGroup_25_29" ///
	2 "ageGroup_30_34" ///
	3 "ageGroup_35_39" ///
	4 "ageGroup_40_44" ///
	5 "ageGroup_45_49" ///
	6 "ageGroup_50_54" ///
	7 "ageGroup_55_59" ///
	8 "ageGroup_60_65" ///
	
label val ageGroup2 ageGrouplb2
fre ageGroup2


* Partnership status  
rename dcpst demPartnerStatus

gen valid_partnered = (demPartnerStatus == 1) 
gen valid_single = (demPartnerStatus == 2) 

replace valid_partnered = . if demPartnerStatus == . | demPartnerStatus < 0 
replace valid_single = . if demPartnerStatus == . | demPartnerStatus < 0 
	
	
* Number of children
rename dnc demNChild
rename dnc02 demNChild0to2	// same within a BU 

gen new_born = (demAge == 0 & demNChild0to2 != 0)
bysort idBu year (new_born): gen demNChild0 = (new_born[_N] == 1)

gen children_0 = (demNChild == 0)
gen children_1 = (demNChild == 1)
gen children_2 = (demNChild == 2)
gen children_3plus = (demNChild >= 3 & demNChild != .)


* Interaction of partnership status and number of children
foreach var1 in valid_partnered valid_single {
	
	foreach var2 in children_0 children_1 children_2 children_3plus {
	
		gen `var1'_`var2' = (`var1' & `var2')
	
	}

}


** EDUCATION
* Education dummies
rename deh_c4 eduHighestC4

gen valid_edu_na = (eduHighestC4 == 0)
gen valid_edu_high = (eduHighestC4 == 1)
gen valid_edu_med = (eduHighestC4 == 2)
gen valid_edu_low = (eduHighestC4 == 3) 

replace valid_edu_na = . if eduHighestC4 == . | eduHighestC4 < 0 
replace valid_edu_high = . if eduHighestC4 == . | eduHighestC4 < 0 
replace valid_edu_med = . if eduHighestC4 == . | eduHighestC4 < 0 
replace valid_edu_low = . if eduHighestC4 == . | eduHighestC4 < 0 


** HEALTH 

* Disabed / LT sick 
rename dlltsd01 healthDsblLongtermFlag

gen valid_healthDsblLongtermFlag = healthDsblLongtermFlag
replace valid_healthDsblLongtermFlag = . if valid_healthDsblLongtermFlag < 0 


* Self rated health
rename dhe valid_healthSelfRated 

replace valid_healthSelfRated = . if valid_healthSelfRated < 0

* MCS
rename dhe_mcs valid_healthMentalMcs

replace valid_healthMentalMcs = . if valid_healthMentalMcs < 0 

* PCS
rename dhe_pcs valid_healthPhysicalPcs

replace valid_healthPhysicalPcs = . if valid_healthPhysicalPcs < 0


** LABOUR MARKET 

* Economic activity dummies
rename les_c3 labC3
rename les_c4 labC4

gen valid_employed = (labC4 == 1)
gen valid_student = (labC4 == 2)
gen valid_inactive = (labC4 == 3)
gen valid_retired = (labC4 == 4)

replace valid_employed = . if labC4 < 0 | labC4 == . 
replace valid_student = . if labC4 < 0 | labC4 == . 
replace valid_inactive = . if labC4 < 0 | labC4 == . 
replace valid_retired = . if labC4 < 0 | labC4 == . 

* Hours worked weekly (continuous)
rename lhw labHrsWorkWeek

gen valid_labHrsWorkWeek = labHrsWorkWeek

* Hours worked weekly (categories)
/*
"This version uses 7 labour supply alternatives:")
("0 hours ==> non-employment alternative.")
("10 hours ==> 6–15 hours bracket.")
("20 hours ==> 16–25 hours bracket.")
("30 hours ==> 26–35 hours bracket.")
("38 hours ==> 36–40 hours bracket.")
("45 hours ==> 41–49 hours bracket.")
("55 hours ==> 50+ hours bracket.")
*/
gen valid_labHrsWorkEnumWeek = "ZERO" 
replace valid_labHrsWorkEnumWeek = "TEN" if ///
	labHrsWorkWeek >= 6 & labHrsWorkWeek <= 15 
replace valid_labHrsWorkEnumWeek = "TWENTY" if ///
	labHrsWorkWeek > 15 & labHrsWorkWeek <= 25
replace valid_labHrsWorkEnumWeek = "THIRTY" if ///
	labHrsWorkWeek > 25 & labHrsWorkWeek <= 35 
replace valid_labHrsWorkEnumWeek = "THIRTY_EIGHT" if ///
	labHrsWorkWeek > 35 & labHrsWorkWeek <= 40 
replace valid_labHrsWorkEnumWeek = "FORTY_FIVE" if ///
	labHrsWorkWeek > 40 & labHrsWorkWeek <= 49
replace valid_labHrsWorkEnumWeek = "FIFTY_FIVE" if ///
	labHrsWorkWeek > 49 & labHrsWorkWeek != .

gen valid_labHrsWorkEnum_no = .
replace valid_labHrsWorkEnum_no = 0 if valid_labHrsWorkEnumWeek == "ZERO" 
replace valid_labHrsWorkEnum_no = 10 if valid_labHrsWorkEnumWeek == "TEN" 
replace valid_labHrsWorkEnum_no = 20 if valid_labHrsWorkEnumWeek == "TWENTY" 
replace valid_labHrsWorkEnum_no = 30 if valid_labHrsWorkEnumWeek == "THIRTY" 
replace valid_labHrsWorkEnum_no = 38 if valid_labHrsWorkEnumWeek == "THIRTY_EIGHT" 
replace valid_labHrsWorkEnum_no = 45 if valid_labHrsWorkEnumWeek == "FORTY_FIVE" 
replace valid_labHrsWorkEnum_no = 55 if valid_labHrsWorkEnumWeek == "FIFTY_FIVE" 

* Categorical variable 
gen valid_cat_hours = .
replace valid_cat_hours = 1 if valid_labHrsWorkEnumWeek == "ZERO" 
replace valid_cat_hours = 2 if valid_labHrsWorkEnumWeek == "TEN" 
replace valid_cat_hours = 3 if valid_labHrsWorkEnumWeek == "TWENTY" 
replace valid_cat_hours = 4 if valid_labHrsWorkEnumWeek == "THIRTY" 
replace valid_cat_hours = 5 if valid_labHrsWorkEnumWeek == "THIRTY_EIGHT" 
replace valid_cat_hours = 6 if valid_labHrsWorkEnumWeek == "FORTY_FIVE" 
replace valid_cat_hours = 7 if valid_labHrsWorkEnumWeek == "FIFTY_FIVE" 

* Hourly wage 
// obs_earnings_hourly - alternative only containing observed wages
// pred_hourly_wage - alternative containing predicted wages
gen valid_wage =  obs_earnings_hourly


** INCOME (ANNUAL)
/*
Amounts of personal income stored with the IHS transformation. 
Benefit Unit level measure (gross and disposable income) are stored without the 
transformation. 

No missing observations in income amounts.
*/

* Annual individual employment gross income 
rename yplgrs_dv yEmpPersGrossMonth

sum yEmpPersGrossMonth
count if yEmpPersGrossMonth == . 

* Convert to levels 
gen yEmpPersGrossLevelMonth = sinh(yEmpPersGrossMonth) 
* Convert to annual 
gen valid_yEmpPersGrossLevelYear = yEmpPersGrossLevelMonth * 12

* Annual benefit unit gross employment income
bys year idBu: egen valid_yEmpBuGrossLevelYear = ///
	total(valid_yEmpPersGrossLevelYear)

	
* Annual individual capital income 
rename ypncp yCapitalPersMonth

sum yCapitalPersMonth
count if yCapitalPersMonth == . 

* Convert to levels
gen yCapitalPersLevelMonth = sinh(yCapitalPersMonth) 
* Convert to annual 
gen valid_yCapitalPersLevelYear = yCapitalPersLevelMonth * 12

* Annual benefit unit capital income
bys year idBu: egen valid_yCapitalBuLevelYear = ///
	total(valid_yCapitalPersLevelYear)


* Annual individual gross private pension income 
rename ypnoab yPensPersGrossMonth

sum yPensPersGrossMonth
count if yPensPersGrossMonth == . 

* Convert to levels
gen yPensPersGrossLevelMonth = sinh(yPensPersGrossMonth) 
* Convert to annual 
gen valid_yPensPersGrossLevelYear = yPensPersGrossLevelMonth * 12

* Annual benefit unit gross private pension income
bys year idBu: egen valid_yPensBuGrossLevelYear = ///
	total(valid_yPensPersGrossLevelYear)


* Annual individual gross non-benefit income 
/*
rename ypnbihs_dv yNonBenPersGrossMonth
* Convert to levels
gen yNonBenPersGrossLevelMonth = sinh(yNonBenPersGrossMonth) 
* Convert to annual
gen valid_yNonBenPersGrossLevelYear = yNonBenPersGrossLevelMonth * 12
*/
egen valid_yNonBenPersGrossLevelYear = ///
	rowtotal(valid_yPensPersGrossLevelYear valid_yCapitalPersLevelYear ///
	valid_yEmpPersGrossLevelYear)

* Annual benefit unit gross non-benefit income 
bys year idBu: egen valid_yNonBenBuGrossLevelYear = ///
	total(valid_yNonBenPersGrossLevelYear)

	
* Annual benefit unit gross income (level, non-benefit)
/*
Gross income is the same as non-benefit private income. 
*/
gen valid_yGrossBuLevelYear = valid_yNonBenBuGrossLevelYear

gen valid_yGrossPersLevelYear = valid_yNonBenPersGrossLevelYear
	
	
* Annual benefit unit disposable income (level)
rename ydisp yDispPersMonth		
* Convert to annual 
gen valid_yDispPersYear = yDispPersMonth * 12 

* Convert to benefit unit
bys year idBu: egen valid_yDispBuLevelYear = total(valid_yDispPersYear)

	
* Benefit unit - Net transfers 	
gen valid_net_transfers = valid_yDispBuLevelYear - valid_yNonBenBuGrossLevelYear
	
	
* Equivalised disposable income per benefit unit 

* Compute equivalence scale 
* Idenifty types of children 
gen is_older_child = 1 if inrange(demAge,14,18) & (idMother < . | idFather < .)
gen is_child = 1 if demAge < 14 & (idMother < . | idFather < .)

* Sum up number in hh 
bysort idHh: egen num_older_children = total(is_older_child)
bysort idHh: egen num_children = total(is_child)

* Compute Modified OECD equivalence scale 

gen moecd_eq = . 
replace moecd_eq = 1.5 if dhhtp_c4 == 1
replace moecd_eq = 0.3 * num_children + 0.5 * num_older_children + 1.5 if ///
	dhhtp_c4 == 2
replace moecd_eq = 1 if dhhtp_c4 == 3
replace moecd_eq = 0.3 * num_children + 0.5 * num_older_children + 1 if ///
	dhhtp_c4 == 4 

	
* Apply equivalence scale   
gen valid_yDispBuEquivYear = valid_yDispBuLevelYear / moecd_eq 

drop is_older_child is_child moecd_eq

	
** SOCIAL CARE 
/*
To align with the simulation set valud of those who aren't eligable equal to 0. 
Demand variables populated with 0 if age < 65.
Supply variables populated with 0 if age < 18. 
Missing = . 
Note: If missing any care demand info age > 64, then missing all. 
*/

* Care need flag 
rename need_socare valid_careNeedFlag 

replace valid_careNeedFlag = 0 if demAge < 65
replace valid_careNeedFlag = . if valid_careNeedFlag == -9

* Hours of informal care received 
/*
replace partner_socare_hrs = 0 if demAge < 65
replace daughter_socare_hrs = 0 if demAge < 65
replace son_socare_hrs = 0 if demAge < 65
replace other_socare_hrs = 0 if demAge < 65

replace partner_socare_hrs = . if partner_socare_hrs == -9 
replace daughter_socare_hrs = . if daughter_socare_hrs == -9 
replace son_socare_hrs = . if son_socare_hrs == -9 
replace other_socare_hrs = . if other_socare_hrs == -9 

egen valid_careHrsInformal = rowtotal(partner_socare_hrs ///
	daughter_socare_hrs son_socare_hrs other_socare_hrs)
	
replace valid_careHrsInformal = . if partner_socare_hrs == . | ///
	daughter_socare_hrs == . | son_socare_hrs == . | other_socare_hrs == . 
*/
rename informal_socare_hrs valid_careHrsInformal
replace valid_careHrsInformal = 0 if demAge < 65 
replace valid_careHrsInformal = . if valid_careHrsInformal == -9 
	
* Hours of formal care 	
rename formal_socare_hrs valid_careHrsFormal 
replace valid_careHrsFormal = 0 if demAge < 65
replace valid_careHrsFormal = 0 if valid_careHrsFormal == -9 

* Formal care cost 
rename formal_socare_cost valid_careFormalX 
replace valid_careFormalX = 0 if demAge < 65
replace valid_careFormalX = . if valid_careFormalX < 0 

* Hours of care provided
rename careHoursProvidedWeekly valid_careHrsProvidedWeek 
replace valid_careHrsProvidedWeek = 0 if demAge < 16
replace valid_careHrsProvidedWeek = . if valid_careHrsProvidedWeek == -9  


* Receive care flag 
gen valid_careReceiveFlag = . 
replace valid_careReceiveFlag = 0 if valid_careHrsInformal != .  | ///
	valid_careHrsFormal != . 
replace valid_careReceiveFlag = 1 if ///
	(valid_careHrsInformal > 0  & valid_careHrsInformal != .)| ///
	(valid_careHrsFormal > 0 & valid_careHrsFormal != . )
replace valid_careReceiveFlag = 0 if demAge < 65

	
* Receive only formal care flag 	
gen valid_careRecFormalOnly = ///
	(valid_careHrsInformal == 0 & valid_careHrsFormal > 0 & ///
		valid_careHrsFormal != .)
	
* Receive only informal care flag 	
gen valid_careRecInformalOnly = ///
	(valid_careHrsInformal > 0 & valid_careHrsInformal != . & ///
	valid_careHrsFormal == 0)		
	
* Receive both informal and formal care flag 	
gen valid_careRecMix = (valid_careHrsInformal > 0 & ///
	valid_careHrsFormal > 0 & valid_careHrsInformal != . & ///
	valid_careHrsFormal != .)
	
* Total care hours received 	
egen valid_careReceiveHrs = rowtotal(valid_careHrsInformal valid_careHrsFormal)
replace valid_careReceiveHrs = 0 if demAge < 65 
replace valid_careReceiveHrs = . if valid_careHrsInformal == . | ///
	valid_careHrsFormal == . 


* Provide care flag 
gen valid_careProvideFlag = (valid_careHrsProvidedWeek > 0 & ///
	valid_careHrsProvidedWeek != . ) 
replace valid_careProvideFlag = . if valid_careHrsProvidedWeek == . 

* Restrict sample to relevant validation years 
drop if year < ${min_sim_year}
drop if year > ${max_sim_year}
	
save "$dir_data/ukhls_validation_sample.dta", replace


graph drop _all 
