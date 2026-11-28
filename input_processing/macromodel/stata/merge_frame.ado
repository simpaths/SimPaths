*! version 1.0.0  02nov2025
program define merge_frame
    version 14.0
    syntax , linkvar(string) use(string)
    
    // Get list of variables from the source frame, excluding the linkvar
    frame `use': qui ds
    local vars `r(varlist)'
    local vars: list vars - linkvar
    
    // Drop any existing variables from the source frame in the current frame
    foreach var in `vars' {
        cap drop `var'
    }
    
    // Create a temporary link variable
    tempvar link
    
    // Link the two frames by the linkvar
    qui frlink 1:1 `linkvar', frame(`use') gen(`link')
    
    // Fetch all variables from the source frame
    frget *, from(`link')
    
    // Drop the temporary link variable
    drop `link'
end
