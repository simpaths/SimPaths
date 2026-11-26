// =========================================================================
// SHOCKS, PRIORS & ESTIMATION SETUP - UNITED KINGDOM
// =========================================================================

// Data file for estimation (set as Dynare macro for template)
@#define datafile = "data/data_for_dynare_UK.csv"

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
    var eps_a;      stderr 0.40;    // technology
    var eps_d;      stderr 5.00;    // demand
    var eps_r;      stderr 0.50;    // monetary: active, independent Bank of England
    var eps_L;      stderr 0.20;    // labor force: migration-driven swings
    var eps_sep;    stderr 0.10;    // separation: high turnover, flexible market
    var eps_h;      stderr 0.30;    // hours shock (no standing short-time work scheme)
    var eps_p;      stderr 0.80;    // cost-push: floating GBP pass-through, energy
    var eps_inv;    stderr 1.00;    // investment: volatile business investment
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
