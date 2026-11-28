*! version 1.0.0  08nov2026
program define splice_breaks, rclass
    // splice_breaks varname, breaks(numlist) gen(newvar)
    // On a tsset frame (yearly or quarterly), chain `newvar' backwards from the
    // last observation of `varname' with its period-to-period growth, replacing
    // the growth into each break period b (the first period on the new basis) by
    // the mean of the growth into b-1 and the growth into b+1; at the first or
    // last period only the available neighbour is used. Levels are therefore on
    // the latest vintage throughout. Returns r(replaced): one "b:old->new" token
    // per break, growth in percent, for PROVENANCE.
    //
    // breaks() is in the frame's time units: 2023 for a 1 January 2023 figure on a
    // yearly frame, the value of tq(2011q2) for a quarterly series. Without breaks()
    // `newvar' is a copy of `varname'. A neighbour that is itself a break does not
    // count as available. b is written in r(replaced) in the time variable's format
    // (2023, 2011q2).
    //
    // The backward chain is applied as one factor per break on every period before
    // it, (1 + old growth) / (1 + new growth), which is the same chain without
    // accumulating rounding: every period from the last break on keeps its published
    // value, and every growth rate other than those into the breaks is unchanged.
    //
    // The series must be observed without gaps between its first and last
    // observation, and each break must lie after the first observation and no later
    // than the last; anything else is an error rather than a guess.

    version 18.0
    syntax varname(numeric), GENerate(name) [BREAKs(numlist integer)]
    confirm new variable `generate'

    quietly tsset
    local tvar `r(timevar)'
    local delta = r(tdelta)
    local tfmt : format `tvar'

    tempvar g
    tempname g_old g_new g_sum
    quietly {
        summarize `tvar' if !missing(`varlist'), meanonly
        if (r(N) < 2) {
            noisily display as error "splice_breaks: `varlist' has fewer than two observations."
            exit 2001
        }
        local tfirst = r(min)
        local tlast = r(max)

        sort `tvar'
        gen double `g' = `varlist' / L.`varlist' - 1
        count if missing(`g') & (`tvar' > `tfirst') & (`tvar' <= `tlast')
        if (r(N) > 0) {
            noisily display as error "splice_breaks: `varlist' has gaps between its first and last observation; splice a gap-free series."
            exit 498
        }
        gen double `generate' = `varlist'
    }

    local replaced ""
    foreach b of local breaks {
        local bs : display `tfmt' `b'
        local bs = strtrim("`bs'")
        if (`b' <= `tfirst') | (`b' > `tlast') {
            local fs : display `tfmt' `tfirst'
            local fs = strtrim("`fs'")
            local ls : display `tfmt' `tlast'
            local ls = strtrim("`ls'")
            display as error "splice_breaks: break `bs' is not a period with a growth rate of `varlist', which is observed from `fs' to `ls'."
            exit 498
        }
        quietly summarize `g' if `tvar' == `b', meanonly
        scalar `g_old' = r(mean)

        scalar `g_sum' = 0
        local n_nb = 0
        foreach nb in `=`b' - `delta'' `=`b' + `delta'' {
            if `: list nb in breaks' continue
            quietly summarize `g' if `tvar' == `nb', meanonly
            if (r(N) == 1) {
                scalar `g_sum' = `g_sum' + r(mean)
                local ++n_nb
            }
        }
        if (`n_nb' == 0) {
            display as error "splice_breaks: break `bs' of `varlist' has no neighbouring growth rate to replace its own."
            exit 498
        }
        scalar `g_new' = `g_sum' / `n_nb'

        quietly replace `generate' = `generate' * (1 + `g_old') / (1 + `g_new') if `tvar' < `b'

        local old_s = strtrim(string(100 * `g_old', "%9.3f"))
        local new_s = strtrim(string(100 * `g_new', "%9.3f"))
        display as text "splice_breaks: `varlist', growth into `bs' of `old_s'% replaced by `new_s'% (mean of `n_nb' neighbour(s))"
        local replaced "`replaced' `bs':`old_s'->`new_s'"
    }

    return local replaced = strtrim("`replaced'")
    return scalar n_breaks = `: word count `breaks''
end
