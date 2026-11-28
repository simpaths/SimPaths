// =========================================================================
// PARAMETER CALIBRATION (Quarterly Frequency) - UNITED KINGDOM
// =========================================================================
// Data sources:
//   - Labor market: ONS LFS via OECD (DSD_LFS: labour force, unemployment,
//     participation); OECD QNA employees and hours
//   - National accounts: ONS Quarterly National Accounts (chained volumes,
//     re-referenced to 2015 and converted at the fixed 2015 rate; see
//     stata/make_datasets.do)
//   - Monetary policy: Bank of England (the UK is OUTSIDE the euro area)
//
// NOTE: UK sample is 2000q1-2025q1 (101 obs), bounded by the OECD LFS stocks
// (they begin 1999q2), as for Spain and Sweden. The whole sample is one
// monetary regime: the Bank of England has set rates independently since 1997.
// Ramsey calibration: alpha_K=0.33, delta=0.05 annual, g_A=0.51% annual,
// K_0 on the balanced-growth K/Y (see UK_growth_params.json).
//
// Eurostat stopped publishing UK data after Brexit, so every observable comes
// from the ONS or the OECD rather than from Eurostat (see the UK series block
// in make_datasets.do); the concepts are the ones the other countries use.
//
// Two features distinguish the UK from the four other countries: the most
// lightly regulated labour market of the five (fastest employment
// adjustment, most flexible wage setting), and, as for Sweden, an INDEPENDENT
// central bank, so the Taylor rule is a genuine national rule.
// =========================================================================

// ----- PREFERENCES & TECHNOLOGY -----
beta     = 0.99;    // quarterly discount factor -> ~4% annual real rate
sigma    = 2.0;     // IES = 1/sigma = 0.5 (same as the other four)
alpha_n  = 0.67;    // labor share; set to 1 - alpha_k so the production block is CRS
                    // (see alpha_k below, which matches the Ramsey alpha_K=0.33)

// ----- LABOR MARKET STRUCTURE -----
alpha_m  = 0.5;     // matching function elasticity (structural, same across countries)
eta      = 0.5;     // symmetric Nash bargaining (Hosios condition)
kappa    = 0.20;    // vacancy posting cost (Shimer 2005 range, same starting point)
delta    = 0.045;   // quarterly separation rate ~4.5%, the HIGHEST of the five
                    // (Germany 2.5%, Sweden 3.0%, Poland 3.5%, Spain 4.0%). The UK has
                    // the weakest employment protection of the five on the OECD EPL
                    // index for regular contracts, so employment adjusts fastest.
                    // Spain's churn comes from a temporary-contract segment the UK
                    // does not have; the UK's comes from easy dismissal throughout.

// ----- MONETARY POLICY (BANK OF ENGLAND) -----
phi_pi   = 1.5;     // Taylor principle (standard)
phi_y    = 0.125;   // output response (standard)
rho_r    = 0.85;    // interest rate smoothing - the Bank of England is a persistent,
                    // forecast-based inflation targeter (Bank Rate stayed at or below
                    // 0.75% from 2009 into 2022; fourteen consecutive increases from
                    // December 2021 to August 2023 then took it to 5.25%).
                    // NOTE: as for Sweden, this is a GENUINE national policy rule. The
                    // UK has its own currency and central bank, so the rule responds
                    // to UK output and inflation, not to an area-wide aggregate.

// ----- SHOCK PERSISTENCE -----
rho_a    = 0.95;    // technology persistence (standard)
rho_L    = 0.95;    // labor force persistence (calibrated, same identification logic)
rho_v    = 0.9;     // vacancy persistence (as Germany, Spain, Sweden; advanced-economy hiring)
rho_sep  = 0.8;     // separation shock persistence
rho_d    = 0.8;     // preference/demand shock persistence

// ----- HABIT PERSISTENCE -----
hab      = 0.5;     // external habit formation (standard advanced-economy range)

// ----- NEW KEYNESIAN PARAMETERS -----
lambda_p = 0.02;    // Phillips curve slope (as Germany, Spain and Sweden)
phi_mc   = 1.0;     // output gap effect on marginal cost (same structural mechanism)
phi_v_y  = 5.0;     // vacancy response to demand conditions (same mechanism)

// ----- INFLATION INDEXATION (Smets-Wouters style) -----
// As Germany and Spain, between Sweden (0.30) and Poland (0.45). Inflation
// targeting since 1992, with operational independence since 1997, anchors
// expectations, but the UK keeps RPI-linked administered prices (regulated rail
// fares, among others) that add a backward-looking component, and the 2022-23
// energy-driven surge in sample was persistent.
iota_p   = 0.35;    // partial indexation

// ----- WAGE RIGIDITY (Gertler-Trigari 2009) -----
// The LOWEST of the five. UK wage setting is decentralised: private-sector
// bargaining is almost entirely single-employer, collective agreements cover
// roughly a quarter of employees overall and far fewer in the private sector,
// and pay is reset employer by employer. Germany and Sweden sit at 0.75 for
// their coordinated multi-year rounds, Spain at 0.70, Poland at 0.65.
// lambda_w = 0.60 -> average wage duration = 2.5 quarters.
lambda_w = 0.60;

// ----- INTENSIVE MARGIN (HOURS) PARAMETERS -----
// The UK sits with Poland and Spain here: it has no standing short-time work
// scheme. The 2020-21 Coronavirus Job Retention Scheme (furlough) was a one-off,
// and its quarters are censored in the estimation data.
rho_h    = 0.80;    // hours persistence (as Poland and Spain, below Germany/Sweden's 0.85)
omega_h  = 0.35;    // hours weight in production (same starting point; ESTIMATED)

// ----- DIRECT LABOR FORCE -> WAGE EFFECT (CALIBRATED) -----
psi_wL   = -0.05;   // direct L->w elasticity (negative: more L -> lower w)
                    // PROVISIONAL: the advanced-economy default, as for Spain and
                    // Sweden. The UK took large migration inflows over the sample
                    // (EU accession 2004, non-EU after 2021), which could argue for
                    // Poland's stronger -0.08, but nothing in this sample identifies
                    // it separately, so it is not invented here.

// ----- DEMAND COMPOSITION -----
// Investment share computed from this sample: mean(inv_level/y_level) = 0.1769
// over 2000q1-2025q1. With Poland, the LOWEST of the five (Germany 0.20, Spain
// 0.21, Sweden 0.23): UK investment has been weak throughout the sample.
s_inv    = 0.18;    // investment share of GDP (ONS P51G / B1GQ, sample mean)

// ----- CAPITAL EXTENSION -----
alpha_k  = 0.33;    // capital share (matching Ramsey calibration alpha_K=0.33)
                    // Note: alpha_k + alpha_n = 0.33 + 0.67 = 1.0 (CRS)
delta_k  = 0.025;   // quarterly depreciation ~9.6% annual
                    // Deliberately differs from the Ramsey layer (5% annual,
                    // ~1.3% quarterly), which sets the long-run BGP K/Y. This
                    // delta_k governs the cyclical capital law of motion only.
rho_inv  = 0.85;    // investment persistence (same starting point)
phi_inv_y = 0.3;    // investment sensitivity to output gap (structural floor)
phi_inv_r = 0.1;    // investment sensitivity to interest rate

// =========================================================================
// STEADY STATE LABOR MARKET (UK, 2000q1-2025q1 sample)
// =========================================================================
// Sources: OECD LFS participation rate 15-64, this sample's u_level column
// Working-age population: 15-64
// =========================================================================

// The identity u = (chi_L/chi_u)L - (chi_n/chi_u)n (template eq. 10) requires
// chi_u = chi_L - chi_n, all three measured as shares of working-age population.
//
// Do NOT derive chi_u as chi_L - chi_n: those are two large, similar numbers, so
// the difference is violently leveraged. Calibrate the two well-measured
// quantities (participation and the unemployment rate) and derive chi_n from
// them, so any error propagates one-for-one rather than being amplified.
//
// The rate MUST be the estimation-sample mean, not a recent window. The UK
// sample runs from 3.75% (2022q1) to 8.45% (2011q4), averaging 5.44%; the
// 2015q1-2025q1 window alone would give 4.4%.

chi_L    = 0.775;   // labor force participation rate, L/WAP (77.5%)
                    // OECD LFS participation rate 15-64, SA
                    // (DSD_LFS@DF_IALFS_LF_WAP_Q, GBR ... Y15T64), 2000q1-2025q1
                    // mean = 77.53% (range 76.4-79.4). Eurostat's lfsi_emp_a, the
                    // source for Sweden, has no UK data after Brexit.

chi_u    = 0.0422;  // unemployment SHARE of WAP, U/WAP = chi_L * 0.054446
                    // NOT the U/L unemployment rate (that is 5.44%)
                    // Implied U/L = chi_u/chi_L = 5.45%, vs sample mean 5.44%

chi_n    = chi_L - chi_u;   // = 0.7328 -> TOTAL employment / WAP
                            // The observable feeding n is total employment, the same concept as this share.
