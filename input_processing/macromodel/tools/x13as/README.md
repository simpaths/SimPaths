# X-13ARIMA-SEATS for the UK CPI

The UK Stata refresh uses the tracked `stata/hicp_q_sa_uk.dta` by default.
X-13 is needed when regenerating the seasonally adjusted ONS CPI (D7BT).
`stata/make_datasets_hicp_uk.do` calls it through `x13_settings.do`.

Put the Windows ASCII-output executable `x13as_ascii.exe` in this directory
in the rebuild workspace. The original adjustment used [X-13ARIMA-SEATS 1.1
Build 62 from the U.S. Census Bureau](https://www2.census.gov/software/x-13arima-seats/x13as/windows/program-archives/x13as_ascii-v1-1-b62.zip).

| Download | SHA-256 |
|---|---|
| Archive | `C6BD65132A3219555D00ABF794649E75C133F23C0DB066ED03C0B5CA30E694C2` |
| Executable | `2E43194361EE096797F0431765193C316196EA6776F11535E76281A413D49669` |

On Windows, use `certutil -hashfile x13as_ascii.exe SHA256` to check the
executable before use. Record changes to the adjustment method or binary
as part of the refreshed data vintage.
