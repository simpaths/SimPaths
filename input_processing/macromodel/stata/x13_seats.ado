*! version 1.0.0  01oct2026
// Seasonally adjust one monthly or quarterly series with the Census Bureau's
// X-13ARIMA-SEATS, SEATS decomposition, and return its final seasonally adjusted
// series (table s11). The data must be tsset; the series must be contiguous.
//
// spec() holds every spec after series{}, except seats{}, which this program
// writes itself because it depends on the s11 table being saved.
// X-13's own files (.spc .out .err .s11 ...) are kept in workdir() so the chosen
// ARIMA model and outliers can be inspected after a run.
program define x13_seats
    version 18.0
    #d ;
    syntax varname(numeric) ,
        GENerate(name)
        exe(string)
        WORKdir(string)
        [
            spec(string)
        ]
    ; #d cr

    confirm new variable `generate'
    confirm file "`exe'"
    capture mkdir "`workdir'"
    mata: st_local("workdir_ok", strofreal(direxists(st_local("workdir"))))
    if !`workdir_ok' {
        display as error "Cannot create workdir `workdir' (mkdir is not recursive; does its parent exist?)."
        exit 693
    }

    quietly tsset
    local timevar `r(timevar)'
    local unit    `r(unit1)'
    if !inlist("`unit'", "m", "q") | "`r(panelvar)'" != "" {
        display as error "x13_seats needs a monthly or quarterly time series (tsset, no panel)."
        exit 459
    }
    local period = cond("`unit'" == "m", 12, 4)

    quietly summarize `timevar' if !missing(`varlist')
    if r(N) == 0 {
        display as error "`varlist' has no observations."
        exit 2000
    }
    local t0 = r(min)
    local t1 = r(max)
    if r(N) != `t1' - `t0' + 1 {
        display as error "`varlist' has interior gaps; X-13 needs a contiguous span."
        exit 459
    }
    local start_year = year(dof`unit'(`t0'))
    local start_per  = `t0' - `unit'ofd(mdy(1, 1, `start_year')) + 1

    local stem "`workdir'/`varlist'"
    foreach ext in spc s11 out err log console {
        capture erase "`stem'.`ext'"
    }

    sort `timevar'
    tempname fh
    quietly file open `fh' using "`stem'.spc", write text replace
    file write `fh' `"series{ title="`varlist'" period=`period' start=`start_year'.`start_per'"' _n
    file write `fh' "  data=(" _n
    forvalues i = 1/`=_N' {
        if inrange(`timevar'[`i'], `t0', `t1') {
            file write `fh' "    " %21.15g (`varlist'[`i']) _n
        }
    }
    file write `fh' "  )" _n "}" _n
    file write `fh' `"`spec'"' _n
    file write `fh' "seats{ save=(s11) }" _n
    file close `fh'

    // This X-13 build prints SEATS debug lines to the console; keep them out of the log.
    shell "`exe'" "`stem'" > "`stem'.console" 2>&1

    // X-13 reports failure only through its .err file and a missing output table.
    local failed = !fileexists("`stem'.s11")
    if !`failed' & fileexists("`stem'.err") {
        tempname eh
        file open `eh' using "`stem'.err", read text
        file read `eh' line
        while r(eof) == 0 {
            if strpos(`"`macval(line)'"', "ERROR") local failed 1
            file read `eh' line
        }
        file close `eh'
    }
    if `failed' {
        display as error "X-13ARIMA-SEATS failed for `varlist'. Its error file:"
        capture noisily type "`stem'.err"
        display as error "Spec: `stem'.spc"
        exit 601
    }

    tempname fr
    frame create `fr'
    frame `fr' {
        // Header: column names, then a dashed rule; rows are yyyypp <tab> value.
        quietly import delimited using "`stem'.s11", delimiters(tab) rowrange(3) ///
            varnames(nonames) asdouble clear
        quietly generate `timevar' = `unit'ofd(mdy(1, 1, floor(v1 / 100))) + mod(v1, 100) - 1
        rename v2 `generate'
        keep `timevar' `generate'
    }
    tempvar link
    quietly frlink 1:1 `timevar', frame(`fr') generate(`link')
    quietly frget `generate', from(`link')
    drop `link'

    quietly count if !missing(`varlist') & missing(`generate')
    if r(N) {
        display as error "X-13 output for `varlist' does not cover the input span."
        exit 498
    }
    label variable `generate' "`varlist', X-13ARIMA-SEATS seasonally adjusted"
end
