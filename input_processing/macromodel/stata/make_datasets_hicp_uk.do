// Seasonally adjusts the UK price level with X-13ARIMA-SEATS and aggregates it to
// quarterly means. Writes hicp_q_sa_uk.dta, which make_datasets.do merges as the UK's
// DSGE price level. make_datasets.do runs this file when country is UK and
// do_HICP_seasonal_adjustment = 1; Stata's working directory must be
// input_processing/macromodel/stata/. The X-13 binary and spec are in x13_settings.do,
// shared with make_datasets_hicp.do, so the UK is adjusted like every other country.
//
// WHY THE UK HAS ITS OWN FILE. Eurostat's UK HICP (the HICP_UK column of HICP.dta,
// written by make_datasets_hicp.do, and hicp_uk_sa in hicp_q_sa.dta) stops at 2020m11:
// Eurostat stopped publishing UK data after Brexit. The ONS CPI is the UK's HICP under
// its national name, the series Eurostat itself published as the UK HICP: in all 299
// months of 1996m1-2020m11 ONS D7BT equals Eurostat's M.HICP2015.NSA.CP-HI00.UK exactly. The UK therefore takes D7BT (CPI INDEX 00: ALL ITEMS 2015=100, not seasonally
// adjusted) for the whole sample, with no splicing. The UK columns of HICP.dta and
// hicp_q_sa.dta are not read.
//
// AGAINST TRAMO/SEATS. X-13 pre-adjusts with regARIMA rather than TRAMO, and the two
// pick different models. Measured on Eurostat's UK HICP over 2000q1-2020q3 (2026-10
// vintage), Q-o-Q annualised inflation from this X-13 path and from the EViews
// TRAMO/SEATS series differs by a mean of 0.00 pp and an RMSE of 0.54 pp, against
// 0.001 pp (Sweden) to 0.26 pp (Spain) for the other four countries
// over their estimation samples (make_datasets_hicp.do). The gap is noise between two
// valid adjustments, not a bias: neither leaves residual seasonality (regressing
// quarterly inflation on quarter dummies gives p > 0.9 for both, over 2000q1-2020q3 and
// over 2010q1-2020q3), and X-13's own test finds none either.
//
// SPAN. 1996m1-2025m12, the span make_datasets_hicp.do adjusts the other countries on,
// so a new ONS release does not move the fit unless the span is moved deliberately.

include x13_settings.do

dbnomics import, provider(ONS) dataset(MM23) seriesids(D7BT.M) clear
keep period value
destring value, replace force
generate time = monthly(period, "YM")
format time %tm
drop period
rename value HICP_UK
keep if inrange(time, tm(1996m1), tm(2025m12))
sort time
quietly count if missing(HICP_UK)
if (r(N) > 0) | (_N != tm(2025m12) - tm(1996m1) + 1) {
    display as error "ONS D7BT does not cover 1996m1-2025m12 without gaps (N = `=_N', missing = `r(N)')."
    exit 498
}
tsset time, monthly

x13_seats HICP_UK, generate(hicp_uk_sa) exe("`x13_exe'") ///
    workdir("`x13_workdir'") spec("`x13_spec'")

// Quarterly mean of the adjusted months, as make_datasets_hicp.do does for the other
// countries; every quarter of the span has all three months.
generate tq = qofd(dofm(time))
collapse (mean) hicp_uk_sa (count) n_months = hicp_uk_sa, by(tq)
assert n_months == 3
drop n_months
rename tq time
format time %tq
label variable hicp_uk_sa "HICP UK (ONS D7BT), X-13ARIMA-SEATS SA, quarterly mean"

// make_datasets.do copies this into PROVENANCE_UK.txt.
char _dta[sa_method] "X-13ARIMA-SEATS `x13_version' (SEATS) on ONS D7BT, 1996m1-2025m12, via make_datasets_hicp_uk.do (fetched `c(current_date)')"
save hicp_q_sa_uk, replace
