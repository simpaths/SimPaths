*! version 1.0.0  02nov2025
program define rebase
    version 18.0
    // set trace on
    // set tracedepth 1

    #d ;
    syntax varname(numeric min=1 max=1) , 
        timevar(varlist numeric min=1 max=1) 
        baseyear(integer) 
        GENerate(namelist min=1 max=1) 
        [
            replace
        ]
    ; #d cr
    
    local orgvar `varlist'
    local newvar `generate'

    cap confirm variable `newvar'
    local gen_or_repl = cond(_rc, "generate", "replace")
    if !_rc & "`replace'" == "" {
        di as error "Target variable `newvar' already exists. Use option 'replace' to overwrite."
        exit 110
    }


    
    // Create temporary variable for year
    tempvar year mean_base
    
    // Extract year from quarterly time variable
    qui gen `year' = yofd(dofq(`timevar'))
    
    // Calculate mean of variable in base year
    qui bysort `year' (`timevar'): egen `mean_base' = mean(`orgvar')
    qui sum `mean_base' if `year' == `baseyear', meanonly
    
    if r(N) == 0 {
        display as error "Base year `baseyear' not found in data"
        exit 111
    }
    
    local base_value = r(mean)
    
    // Generate rebased index (base year = 100)
    qui `gen_or_repl' `newvar' = (`orgvar' / `base_value') * 100
    
    // Label the new variable
    label variable `newvar' "`orgvar' rebased to `baseyear'=100"
    
    display as text "Variable `newvar' created: `orgvar' rebased to `baseyear' = 100"
end
