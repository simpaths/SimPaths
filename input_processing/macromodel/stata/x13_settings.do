// X-13ARIMA-SEATS settings shared by make_datasets_hicp.do (the Eurostat HICP of every
// country) and make_datasets_hicp_uk.do (the UK's ONS CPI), so that every price level
// is adjusted by one binary with one spec. Run with -include-, not -do-, so the locals
// land in the caller; Stata's working directory must be input_processing/macromodel/stata/.
//
// THE SPEC. X-13 in its SEATS mode, called through x13_seats.ado, with the X-13
// counterparts of these EViews TRAMO/SEATS options:
//   lam=-1           -> transform{ function=auto }    log/level choice
//   inic=3 idif=3    -> automdl{ }                    automatic ARIMA identification
//   iatip=1          -> outlier{ types=(ao ls tc) }   TRAMO's default outlier set
//   ieast=0 itrad=0  -> no regression{ }              no Easter or trading-day effects
//   seats=2          -> seats{ }                      written by x13_seats.ado
//
// THE BINARY. X-13ARIMA-SEATS 1.1 build 62, Windows, ASCII output, is expected at
// ../tools/x13as/x13as_ascii.exe; ../tools/x13as/README.md says where to get it and its
// SHA-256. It is not tracked. The tracked hicp_q_sa.dta and hicp_q_sa_uk.dta are the
// cached results, so X-13 is needed only to regenerate them.

local x13_exe     "`c(pwd)'\..\tools\x13as\x13as_ascii.exe"
local x13_version "1.1 b62"
local x13_workdir "`c(pwd)'\x13_work"
local x13_spec    "transform{ function=auto } automdl{ } outlier{ types=(ao ls tc) }"

if !fileexists("`x13_exe'") {
    display as error "X-13ARIMA-SEATS not found at `x13_exe'."
    display as error "See ../tools/x13as/README.md, or set do_HICP_seasonal_adjustment = 0 in make_datasets.do to reuse the tracked hicp_q_sa.dta and hicp_q_sa_uk.dta."
    exit 601
}
