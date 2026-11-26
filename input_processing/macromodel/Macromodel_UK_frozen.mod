// =========================================================================
// FROZEN MODEL - AUTO-GENERATED
// Generated: 2026-11-09 14:07:33
// Source: Macromodel_UK (estimation results)
// DO NOT EDIT MANUALLY - regenerate with generate_frozen_model.m
// =========================================================================

// =========================================================================
// DSGE Model - UNITED KINGDOM
// =========================================================================
// Thin wrapper: defines country and includes shared template.
// All structural equations are in Macromodel_template.mod.
// Country-specific parameters are in country_params/UK_*.mod.
//
// Usage:  >> dynare Macromodel_UK use_dll
// =========================================================================

// =========================================================================
// DSGE Model for Microsimulation Coupling - FROZEN (estimated parameters) — COUNTRY-AGNOSTIC TEMPLATE
// Based on Search & Matching Framework with Production
// =========================================================================
//
// This is the shared template. Country-specific parameters, priors, and
// shock calibrations are loaded via @#include from country_params/.
//
// USAGE:
//   Do NOT run this file directly. Use the country wrapper instead:
//     >> dynare Macromodel_Germany use_dll
//     >> dynare Macromodel_Poland use_dll
//
//   Each wrapper defines @#define country = "..." and includes this template.
//
// ADDING A NEW COUNTRY:
//   1. Create country_params/{Country}_calibration.mod  (parameter values)
//   2. Create country_params/{Country}_estimation.mod   (shocks, priors, data)
//   3. Create Macromodel_{Country}.mod  (2-line wrapper)
// =========================================================================

// =========================================================================
// USER SWITCHES (Dynare macro preprocessor)
// =========================================================================
@#define do_parameter_estimation = false  // FROZEN: estimation disabled
@#define use_real_data = true
@#define use_custom_irfs = false   // FROZEN: use standard IRFs for export

//
// PURPOSE:
// This model is the business-cycle layer of the macro model that serves as
// "economic guardrails" for the SimPaths dynamic microsimulation: it gives
// the cyclical deviation of the real wage around the Ramsey trend.
//
// INTERFACE WITH SIMPATHS (one-way):
//   scenario shocks (sigma-units) --> DSGE --> w (cycle wage) --> SimPaths
//   pi is computed and recorded but reaches no agent. The DSGE takes no
//   input from SimPaths: L and h are exogenous AR(1) processes, observed
//   in estimation.
//
// FREQUENCY: Quarterly; SimPaths averages four quarterly steps per year
// =========================================================================

// Variable declarations
// Endogenous variables (all in log-deviations from steady state)
var 
    y       // output (production function)    [SUPPLY-DETERMINED]
    d       // aggregate demand (Euler equation) [DEMAND-DETERMINED]
    c       // consumption (Euler equation)          [OBSERVED]
    gap     // demand-supply gap (d - y)
    R       // nominal interest rate
    pi      // inflation                        [recorded only]
    w       // real wage (aggregate)            [OUTPUT TO SIMPATHS]
    w_star  // reset wage (newly negotiated)    [Gertler-Trigari]
    u       // unemployment level (log-deviation, for internal dynamics)
    u_rate  // unemployment RATE gap (pp, for observation)
    v       // vacancies
    n       // employment
    h       // hours per worker (intensive margin) [exogenous AR(1)]
    m       // matches
    mc      // marginal cost
    x       // technology (TFP)
    theta   // labor market tightness (v/u)
    L       // labor force (participation)     [exogenous AR(1)]
    k       // capital stock (predetermined)    [CAPITAL EXTENSION]
    inv     // investment (reduced-form rule)    [CAPITAL EXTENSION]
    sep_shock    // separation shock process
    d_shock;     // demand/preference shock AR(1) state

// Exogenous shocks
varexo 
    eps_a       // technology shock
    eps_r       // monetary policy shock
    eps_d       // PREFERENCE/DEMAND shock (confidence, fiscal, exports)
    eps_p       // cost-push / price markup shock (energy, imports, markup)
    eps_L       // labor force shock
    eps_sep     // separation rate shock
    eps_h       // hours shock (e.g. short-time work)
    eps_inv;    // investment shock [CAPITAL EXTENSION]

// Parameters
parameters 
    beta        // discount factor
    sigma       // inverse IES (risk aversion)
    alpha_m     // elasticity of matching w.r.t. unemployment
    alpha_n     // labor elasticity of output (production function)
    eta         // worker bargaining power
    kappa       // vacancy posting cost parameter
    delta       // separation rate (steady state)
    phi_pi      // Taylor rule: inflation response
    phi_y       // Taylor rule: output response
    rho_a       // persistence of technology shock
    rho_r       // interest rate smoothing
    rho_v       // vacancy persistence
    rho_L       // persistence of labor force changes
    rho_sep     // persistence of separation shock
    rho_d       // persistence of preference/demand shock
    lambda_p    // slope of Phillips curve (prices)
    lambda_w    // Calvo wage stickiness (Gertler-Trigari)
    phi_mc      // output gap sensitivity of marginal cost
    phi_v_y     // vacancy sensitivity to output gap (demand conditions)
    chi_n       // steady state employment rate (N/pop)
    chi_u       // steady state unemployment SHARE (U/pop) = chi_L - chi_n
                // NOT the U/L unemployment rate - see eq. 10 below
    chi_L       // steady state labor force rate (L/pop)
    // INTENSIVE MARGIN (hours) parameters
    rho_h       // hours persistence (Kurzarbeit/contract duration)
    omega_h     // hours weight in production function
    // DIRECT LABOR FORCE EFFECT ON WAGES (reduced-form wage drift)
    psi_wL      // wage response to labor force (bypasses slow θ channel)
    // DEMAND COMPOSITION
    s_inv       // steady-state investment share of GDP (I_ss/Y_ss)
    // CAPITAL PARAMETERS
    alpha_k     // capital share in production (Cobb-Douglas)
    delta_k     // quarterly capital depreciation rate
    rho_inv     // investment persistence
    phi_inv_y   // investment sensitivity to output gap
    phi_inv_r   // investment sensitivity to interest rate
    // HABIT PERSISTENCE
    hab         // external habit formation parameter (0 = no habits, 1 = full)
    // INFLATION INDEXATION
    iota_p;     // backward-looking inflation indexation (0 = no indexation, 1 = full)

// =========================================================================
// COUNTRY-SPECIFIC PARAMETER CALIBRATION
// =========================================================================
// Loaded from country_params/{country}_calibration.mod
// Contains: all parameter values, steady-state labor market ratios
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
omega_h  = 0.326054;  // ESTIMATED (posterior mode)    // hours weight in production (same starting point; ESTIMATED)

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
phi_inv_r = 0.054197;  // ESTIMATED (posterior mode)    // investment sensitivity to interest rate

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


// =========================================================================
// MODEL BLOCK (Linear)
// All equations are log-linearized around the deterministic steady state
// Variables are in % deviations: x_hat = (X - X_ss) / X_ss
// =========================================================================

model(linear);

    // -----------------------------------------------------------------------
    // SUPPLY SIDE: PRODUCTION FUNCTION WITH INTENSIVE MARGIN
    // -----------------------------------------------------------------------
    
    // 1. Output from production function with capital and labor (extensive + intensive)
    // Y = A * K^alpha_k * (N * H)^alpha_n
    // Log-linearized: y = x + alpha_k*k(-1) + alpha_n*(n + omega_h*h) + phi_mc*gap
    y = x + alpha_k * k(-1) + alpha_n * (n + omega_h * h) + phi_mc * gap;
    
    // -----------------------------------------------------------------------
    // INTENSIVE MARGIN: HOURS EQUATION (pure AR(1))
    // -----------------------------------------------------------------------
    
    // 2. Hours per worker - exogenous AR(1), observed in estimation
    h = rho_h * h(-1) + eps_h;
    
    // -----------------------------------------------------------------------
    // DEMAND SIDE: Smets-Wouters-style C/I decomposition
    // -----------------------------------------------------------------------
    
    // 2a. Consumption Euler equation with EXTERNAL HABITS
    c - hab*c(-1) = (c(+1) - hab*c) - (1/sigma) * (R - pi(+1)) + d_shock;

    // 2b. Aggregate demand identity (GDP expenditure decomposition)
    d = (1 - s_inv) * c + s_inv * inv;
    
    // 3. Demand-supply gap
    gap = d - y;
    
    // -----------------------------------------------------------------------
    // SUPPLY SIDE: MARGINAL COST
    // -----------------------------------------------------------------------
    
    // 4. Marginal cost with demand-supply gap
    mc = (w - (x + alpha_k * k(-1) + (alpha_n - 1) * n + alpha_n * omega_h * h)) + phi_mc * gap;
    
    // -----------------------------------------------------------------------
    // PRICING
    // -----------------------------------------------------------------------
    
    // 5. New Keynesian Phillips Curve with INFLATION INDEXATION
    pi = (iota_p / (1 + iota_p * beta)) * pi(-1)
       + (beta / (1 + iota_p * beta)) * pi(+1)
       + (lambda_p / (1 + iota_p * beta)) * mc + eps_p;
    
    // 6. Monetary policy (Taylor rule)
    R = rho_r * R(-1) + (1 - rho_r) * (phi_pi * pi + phi_y * y) + eps_r;
    
    // -----------------------------------------------------------------------
    // LABOR MARKET: SEARCH AND MATCHING
    // -----------------------------------------------------------------------
    
    // 7. Matching function (Cobb-Douglas)
    m = alpha_m * u + (1 - alpha_m) * v;
    
    // 8. Labor market tightness
    theta = v - u;
    
    // 9. Employment law of motion
    n = (1 - delta) * n(-1) + delta * m - sep_shock;
    
    // 10. Unemployment identity
    // Exact linearisation of U = L - N: u_hat = (Lbar/Ubar)*L - (Nbar/Ubar)*n.
    // Requires chi_u = U/pop = chi_L - chi_n. Using the U/L rate instead
    // inflates chi_u and weakens the whole latent u channel proportionally.
    u = (chi_L / chi_u) * L - (chi_n / chi_u) * n;
    
    // 10b. Unemployment RATE gap (for observation)
    u_rate = (chi_n / chi_L) * (L - n);
    
    // 11. Vacancy posting rule (reduced-form with forward-looking profitability)
    v = rho_v * v(-1) + (1 - rho_v) * (((x + alpha_k * k(-1) + (alpha_n - 1) * n + alpha_n * omega_h * h) - w) + beta * lambda_w * ((x(+1) + alpha_k * k + (alpha_n - 1) * n(+1) + alpha_n * omega_h * h(+1)) - w(+1)) + phi_v_y * gap);
    
    // -----------------------------------------------------------------------
    // WAGE SETTING: Gertler-Trigari (2009) Staggered Wage Bargaining
    // -----------------------------------------------------------------------
    
    // 12a. Reset wage (forward-looking Nash bargaining)
    w_star = (1 - beta * lambda_w) * (eta * (x + alpha_k * k(-1) + (alpha_n - 1) * n + alpha_n * omega_h * h) + (1 - eta) * kappa * theta)
           + beta * lambda_w * w_star(+1);
    
    // 12b. Aggregate wage = Calvo mixture + direct labor force effect
    w = lambda_w * w(-1) + (1 - lambda_w) * w_star + psi_wL * L;
    
    // -----------------------------------------------------------------------
    // SHOCK PROCESSES
    // -----------------------------------------------------------------------
    
    // 13. Technology (AR(1))
    x = rho_a * x(-1) + eps_a;
    
    // 14. Labor force (AR(1)) - observed in estimation
    L = rho_L * L(-1) + eps_L;
    
    // 14b. Demand/preference shock AR(1) state
    d_shock = rho_d * d_shock(-1) + eps_d;
    
    // 15. Separation shock (AR(1))
    sep_shock = rho_sep * sep_shock(-1) + eps_sep;

    // -----------------------------------------------------------------------
    // CAPITAL: explicit K with an investment rule
    // -----------------------------------------------------------------------

    // K1. Capital accumulation (law of motion)
    k = (1 - delta_k) * k(-1) + delta_k * inv;

    // K2. Investment rule (reduced-form)
    inv = rho_inv * inv(-1) + phi_inv_y * gap - phi_inv_r * R + eps_inv;

end;

// =========================================================================
// COUNTRY-SPECIFIC SHOCKS & ESTIMATION SETUP
// =========================================================================
// Loaded from country_params/{country}_estimation.mod
// Contains: calibrated shock stdevs, estimated_params (priors),
//           estimated_params_init, measurement errors, datafile macro
// =========================================================================
// SHOCKS, PRIORS & ESTIMATION SETUP - UNITED KINGDOM
// =========================================================================

// Data file for estimation (set as Dynare macro for template)

// =========================================================================
// CALIBRATED SHOCK STANDARD DEVIATIONS
// =========================================================================
// Initial calibration for the UK, 2000q1-2025q1.
//
// The UK is a large open economy with its own floating currency, which sets
// the starting values much as for Sweden:
//   - cost-push starts HIGH, because a floating GBP passes import prices
//     through to domestic inflation (the 2008-09 and 2016 sterling
//     depreciations and the 2022-23 energy shock are in sample).
//   - monetary starts MODERATE-HIGH: Bank Rate sat at or near its lower
//     bound for most of 2009-2021, then rose fast in 2021-23.
//   - labour force starts MODERATE: the UK took large and variable migration
//     inflows over the sample (EU accession 2004, non-EU after 2021).
//
// These are STARTING values; Bayesian estimation will update them.
shocks;
    var eps_a;      stderr 0.5701;  // ESTIMATED    // technology
    var eps_d;      stderr 0.4135;  // ESTIMATED    // demand
    var eps_r;      stderr 0.5076;  // ESTIMATED    // monetary: active, independent Bank of England
    var eps_L;      stderr 0.2517;  // ESTIMATED    // labor force: migration-driven swings
    var eps_sep;    stderr 0.10;    // separation: high turnover, flexible market
    var eps_h;      stderr 0.3894;  // ESTIMATED    // hours shock (no standing short-time work scheme)
    var eps_p;      stderr 1.4866;  // ESTIMATED    // cost-push: floating GBP pass-through, energy
    var eps_inv;    stderr 2.3953;  // ESTIMATED    // investment: volatile business investment
end;

// =========================================================================
// BAYESIAN ESTIMATION: PARAMETER PRIORS
// =========================================================================
// Same prior STRUCTURE as the other four countries. Every structural
// parameter stays fixed at its calibrated value: the identification problems
// are structural, not country-specific.
//
// PRIOR WIDTHS: the diffuse priors Sweden uses, not the tighter ones of the
// first Spain and Poland files. Spain's tight priors (eps_L 0.15+/-0.08,
// eps_inv 0.80+/-0.35) were plainly misspecified once the posterior landed far
// outside them, and there is no stronger independent prior information about
// UK shock volatilities than about Swedish ones, so the data are left to speak.
//
// OUTCOME (2026-10): as in every country, the posterior mode lands above the prior
// MEAN for eps_inv (2.54 vs 1.0), eps_p (1.45 vs 0.8) and eps_a (0.59 vs 0.5).
// That is not a prior that binds: re-estimating the mode with infinite-variance
// inverse-gamma priors on all seven shock stds moves no mode by more than 1.1%
// (eps_inv 2.543 -> 2.570; Sweden 1.2%, Spain 2.7%, Poland 3.1%). Do not
// re-centre these on the posterior (double-dipping); widen only if that check
// starts to move the modes.

estimated_params;
    // -----------------------------------------------------------------------
    // TIER 1: CRITICAL PARAMETERS
    // -----------------------------------------------------------------------
    // All Tier 1 parameters FIXED at calibrated values (identification issues
    // are structural, not data-specific):
    // kappa, eta, lambda_p, phi_mc, phi_v_y, lambda_w
    // -> see calibration file for values

    // -----------------------------------------------------------------------
    // TIER 2: IMPORTANT PARAMETERS (same issues)
    // -----------------------------------------------------------------------
    // sigma, phi_pi, phi_y, rho_r, rho_a, rho_v, rho_sep
    // -> all FIXED at calibrated values

    // -----------------------------------------------------------------------
    // ESTIMATED PARAMETERS
    // -----------------------------------------------------------------------

    // Hours weight in production
    omega_h,        beta_pdf,   0.35,   0.10;   // hours weight (0-1)

    // -----------------------------------------------------------------------
    // SHOCK STANDARD DEVIATIONS (diffuse; see note above)
    // -----------------------------------------------------------------------

    // Technology shock
    stderr eps_a,       inv_gamma_pdf,  0.50,   0.30;   // technology shock (%)

    // Demand/preference shock
    stderr eps_d,       inv_gamma_pdf,  0.50,   0.30;   // demand shock (%)

    // Monetary shock (Bank of England)
    stderr eps_r,       inv_gamma_pdf,  0.40,   0.25;   // monetary shock (%)

    // Cost-push shock (floating GBP pass-through, energy)
    stderr eps_p,       inv_gamma_pdf,  0.80,   0.40;   // cost-push shock (%)

    // Hours shock
    stderr eps_h,       inv_gamma_pdf,  0.35,   0.20;   // hours shock (%)

    // Labor force shock (migration swings)
    stderr eps_L,       inv_gamma_pdf,  0.30,   0.20;   // labor force shock (%)

    // -----------------------------------------------------------------------
    // CAPITAL EXTENSION: INVESTMENT PARAMETERS
    // -----------------------------------------------------------------------

    // Cost-of-capital channel, centred as for Sweden: Spain's posterior mean
    // came in at 0.12, so the 0.5 of the Spain and Poland files is a poor centre.
    phi_inv_r,      gamma_pdf,      0.30,   0.20;   // cost-of-capital (positive, unbounded)

    // Investment shock
    stderr eps_inv,     inv_gamma_pdf,  1.00,   0.50;   // investment shock (%)
end;

// =========================================================================
// INITIAL VALUES FOR ESTIMATION
// =========================================================================

estimated_params_init(use_calibration);
    omega_h,      0.35;     // hours weight in production

    // Shock std devs
    stderr eps_a,     0.50;     // technology
    stderr eps_d,     0.50;     // demand/preference
    stderr eps_r,     0.40;     // monetary (Bank of England)
    stderr eps_p,     0.80;     // cost-push
    stderr eps_L,     0.30;     // labor force
    stderr eps_h,     0.35;     // hours shock

    // Capital extension
    phi_inv_r,    0.10;     // cost-of-capital
    stderr eps_inv,   1.00;     // investment shock
end;

// =========================================================================
// MEASUREMENT ERRORS
// =========================================================================
// ONS national accounts are of high quality, so most of these sit at the
// German and Swedish end. Two exceptions:
//   - pi, because the seasonal adjustment itself is uncertain here: X-13 and
//     TRAMO/SEATS, both valid, move UK quarterly inflation by an RMSE of
//     0.54 pp against each other (see stata/make_datasets_hicp_uk.do)
//   - L, because the UK LFS lost much of its response over the sample's last
//     years, and ONS reweighted its 2023-24 estimates after flagging them as
//     unreliable

shocks;
    var y;      stderr 0.3;      // GDP
    var c;      stderr 0.3;      // Consumption
    var inv;    stderr 0.5;      // Investment
    var pi;     stderr 0.3;      // Inflation: CPI, seasonal-adjustment uncertainty
    var R;      stderr 0.2;      // Interest rate: Bank Rate-linked
    var n;      stderr 0.3;      // Employment
    var L;      stderr 0.25;     // Labor force: LFS response collapse and reweighting
    var h;      stderr 0.3;      // Hours per person employed
end;


// =========================================================================
// OBSERVABLE VARIABLES
// =========================================================================
varobs y inv c pi R n L h;
// y      = Output gap (detrended log GDP, % deviation)
// inv    = Investment gap (detrended log investment, % deviation)
// c      = Consumption gap (detrended log private consumption, % deviation)
// pi     = Inflation rate (QoQ annualized, %)
// R      = Nominal interest rate (3m interbank rate, %)
// n      = Employment gap (detrended log employment, all persons in employment, % deviation)
// L      = Labor force gap (detrended log labor force, % deviation)
// h      = Hours per person employed gap (detrended, % deviation)
//
// 8 observables, 8 shocks → just-identified

// =========================================================================
// PRE-ESTIMATION CHECKS
// =========================================================================
steady;
check;

// =========================================================================
// STOCHASTIC SIMULATION (IRFs at calibrated values)
// =========================================================================
stoch_simul(order=1, irf=40, nograph) y d c gap pi R n h u u_rate v w theta x L k inv d_shock;

// Custom IRF plotting
@#if use_custom_irfs
    if exist('plot_custom_irfs', 'file')
        plot_custom_irfs(5, 4);
    else
        disp('Note: plot_custom_irfs.m not found. Using Dynare default IRF plots.');
    end
@#else
    disp('Custom IRF plotting disabled (use_custom_irfs = false).');
@#endif

// =========================================================================
// BAYESIAN ESTIMATION (conditional on do_parameter_estimation switch)
// =========================================================================

@#if do_parameter_estimation

    identification(advanced=1, max_dim_cova_group=3);

    @#if use_real_data
        estimation(
            datafile='@{datafile}',
            first_obs=1,
            prefilter=1,
            
            mode_compute=4,
            mode_check,
            
            diffuse_filter,
            fast_kalman_filter,
            lyapunov=doubling,
            
            mh_replic=20000,
            mh_nblocks=2,
            mh_drop=0.5,
            
            bayesian_irf,
            smoother,
            moments_varendo
        ) y d c gap pi R n h u u_rate v w theta x L k inv d_shock;
        
    @#else
        estimation(
            datafile=data_synthetic,
            first_obs=1,
            prefilter=1,
        
            mode_compute=6,
            mode_check,
            
            diffuse_filter,
            fast_kalman_filter,
            lyapunov=doubling,
            
            mh_replic=20000,
            mh_nblocks=2,
            mh_drop=0.5,
            
            bayesian_irf,
            smoother,
            moments_varendo
        ) y d c gap pi R n h u u_rate v w theta x L k inv d_shock;
        
    @#endif

@#endif

// =========================================================================
// POST-ESTIMATION CHECKS
// =========================================================================
steady;
check;
model_diagnostics;

