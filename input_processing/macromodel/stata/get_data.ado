*! version 1.3.0  08nov2026
program define get_data
    // set trace on
    // set tracedepth 1

    version 18.0
    #d ;
    syntax ,
        series_codes(name)
        tframe(string) // target frame
        startdate(string) // a quarter (2005q1); a year (2004) with freq(annual)
        enddate(string)
        [
            replace // to replace existing target frame
            FREQuency(string)   // default is quarterly; annual, monthly and daily also possible
            AGGregation(string) // default is no aggregation; currently available: quarter_mean, quarter_end
            KEEPPartial         // keep quarters whose source months are not all published
        ]
    ; #d cr

    // Check if target frame exists
    qui pwf
    if "`tframe'" == "`r(currentframe)'" {
        di as error "Target data frame is currently active. Switch frame before running `get_data`."
        exit 198
    }

    cap confirm frame `tframe'
    if !_rc {
        if "`replace'" == "" {
            di as error "Target frame `tframe' already exists. Use option 'replace' to overwrite."
            exit 110
        }
        else frame drop `tframe'
    }

    // Create local macros from the series_codes matrix
    mata: st_local("no_code", strofreal(findexternal("`series_codes'") == NULL))
    if `no_code' {
        display as error "Matrix `series_codes' not found."
        exit 111
    }

    if ("`frequency'" == "") {
        local frequency "quarterly"
    }
    local aggtest = inlist("`aggregation'", "", "quarter_mean", "quarter_end")
    if (!`aggtest') {
        display as error "Unsupported aggregation method: `aggregation'. Supported methods are: quarter_mean, quarter_end."
        exit 198
    }

    // Initialize the target frame: yearly for an annual series, whose startdate() and
    // enddate() are years; quarterly otherwise, with the dates given as quarters.
    if inlist("`frequency'", "annual", "a") {
        local t0 = real("`startdate'")
        local t1 = real("`enddate'")
        if missing(`t0') | missing(`t1') {
            display as error "freq(annual) takes startdate() and enddate() as years, e.g. startdate(2004); got `startdate' and `enddate'."
            exit 198
        }
        local tfmt %ty
    }
    else {
        local t0 = tq(`startdate')
        local t1 = tq(`enddate')
        local tfmt %tq
    }
    cap frame drop `tframe'
    frame create `tframe'
    local length = `t1' - `t0' + 1
    frame `tframe': set obs `length'
    frame `tframe': gen time = `t0' + _n - 1
    frame `tframe': format time `tfmt'

    // Now loop through the series and fetch them one by one
    tempname temp_series
    cap frame drop `temp_series'
    frame create `temp_series'
    mata: st_local("n_series", strofreal(rows(`series_codes')))
    
    forvalues i = 1/`n_series' {
        mata: st_local("provider", `series_codes'[`i', 1])
        mata: st_local("dataset", `series_codes'[`i', 2])
        mata: st_local("series", `series_codes'[`i', 3])
        mata: st_local("internal", `series_codes'[`i', 4])
        mata: st_local("varlabel", `series_codes'[`i', 5])
        
        // Display progress
        di "Fetching series `i' of `n_series': `series' from provider `provider', dataset `dataset'"
        
        frame `temp_series' {    
            clear
            dbnomics import , provider(`provider') dataset(`dataset') seriesids(`series') clear
            rename value `internal'
            cap confirm numeric variable `internal', exact
            if _rc {
                destring `internal', replace force
            }
            
            cap confirm variable period_start_day
            if _rc {
                display as error "Variable period_start_day not found in fetched data for series `series'."
                exit 111
            }
            
            // Apply variable label if provided
            label variable `internal' "`varlabel'"
            
            // Time variable creation based on frequency
            if "`frequency'" == "quarterly" | "`frequency'" == "q" {
                cap drop time
                gen time = qofd(date(period_start_day, "YMD"))
                format time %tq
            }
            else if "`frequency'" == "annual" | "`frequency'" == "a" {
                cap drop time
                gen time = yofd(date(period_start_day, "YMD"))
                format time %ty
                quietly tsset time, yearly
            }
            else if "`frequency'" == "monthly" | "`frequency'" == "m" {
                cap drop time
                gen time = mofd(date(period_start_day, "YMD"))
                format time %tm
            }
            else if "`frequency'" == "daily" | "`frequency'" == "d" {
                cap drop time
                gen time = date(period_start_day, "YMD")
                format time %td
            }
            else {
                display as error "Unsupported frequency: `frequency'. Use 'quarterly', 'annual', 'monthly', or 'daily'."
                exit 198
            }

            // Apply aggregation if specified
            if inlist("`aggregation'", "quarter_mean", "quarter_end") {
                if "`frequency'" == "monthly" | "`frequency'" == "m" {
                    gen timeq = qofd(dofm(time))
                }
                else if "`frequency'" == "daily" | "`frequency'" == "d" {
                    gen timeq = qofd(time)
                }
                else {
                    display as error "Aggregation method `aggregation' requires monthly or daily frequency."
                    exit 198
                }
                format timeq %tq

                // A quarter whose source months are not all published aggregates to a
                // silently wrong value: quarter_mean averages the published subset, and
                // quarter_end returns a MID-quarter observation labelled end-of-quarter.
                // Both look like ordinary data downstream. Drop such quarters unless the
                // caller opts in, and always say which ones went.
                tempvar srcmonth monthtag nmonths lastmonth qpartial
                if "`frequency'" == "monthly" | "`frequency'" == "m" {
                    gen `srcmonth' = month(dofm(time)) if !mi(`internal')
                }
                else {
                    gen `srcmonth' = month(time) if !mi(`internal')
                }
                // A complete quarter has all three calendar months represented and runs
                // through the quarter's third month. The first test catches a truncated
                // start, the second a not-yet-published tail.
                egen `monthtag' = tag(timeq `srcmonth') if !mi(`srcmonth')
                bysort timeq: egen `nmonths' = total(`monthtag')
                bysort timeq: egen `lastmonth' = max(`srcmonth')
                gen byte `qpartial' = (`nmonths' < 3) | (`lastmonth' < month(dofq(timeq + 1) - 1))
                qui count if `qpartial' & !mi(`internal')
                if r(N) > 0 {
                    qui levelsof timeq if `qpartial', local(partialq)
                    foreach pq of local partialq {
                        local pqs : display %tq `pq'
                        display as text "get_data: `internal' — quarter `=trim("`pqs'")' is incomplete at the source"
                    }
                    if "`keeppartial'" == "" {
                        display as text "get_data: dropping the incomplete quarter(s) above for `internal' (use keeppartial to keep them)."
                        qui drop if `qpartial'
                    }
                    else {
                        display as error "get_data: keeppartial in effect — `internal' carries a PARTIAL-quarter value above."
                    }
                }
                drop `srcmonth' `monthtag' `nmonths' `lastmonth' `qpartial'

                if "`aggregation'" == "quarter_mean" {
                    // Aggregate to quarterly by mean
                    collapse (mean) `internal', by(timeq) fast
                    label variable `internal' "`varlabel' - quarterly mean"
                    rename timeq time
                }
                else if "`aggregation'" == "quarter_end" {
                    // Keep only the last observation of each quarter
                    tempvar n
                    bysort timeq (time): gen `n' = _N - _n + 1
                    keep if `n' == 1 // Keep only the last observation of each quarter
                    label variable `internal' "`varlabel' - end of quarter value"
                    cap drop time
                    rename timeq time
                    
                }
                else {
                    cap drop time
                    rename timeq time
                }
            }

            if `i' == 1 {
                qui frame `tframe': frlink 1:1 time, frame(`temp_series') gen(`temp_series')
                qui frame `tframe': frget `internal', from(`temp_series')
                continue
            }
            
            qui frame `tframe': frlink rebuild `temp_series', frame(`temp_series')
            qui frame `tframe': frget `internal', from(`temp_series')
        }
    }
    frame `tframe': order time, first
end

// Change log
// v1.3.0: freq(annual): a yearly target frame, with startdate() and enddate() given as years.
// v1.1.0:
//      - Previously, the length of the created dateframe was arbitrarily set to the length of the first
//        series fetched. Now, it is determined based on the start and end dates.
// v1.0.0: Initial version