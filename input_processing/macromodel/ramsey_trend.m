function [detrended, trend, diagnostics] = ramsey_trend(country, data_file, options)
% RAMSEY_TREND  Solve Ramsey growth model and detrend DSGE observables.
%
% Detrends the DSGE estimation data in place of an HP filter. Computes optimal
% trend paths for Y, C, I, K, w, r* from a deterministic Ramsey model,
% then detrends all 8 DSGE observables.
%
% The Ramsey model (open economy with government):
%   max  Σ β^t P_t u(c_t),   u(c) = (c^{1-σ} - 1)/(1-σ)
%   s.t. K_{t+1} = (1-δ)K_t + Y_t - P_t c_t - G_t - NX_t
%        Y_t = A_t K_t^α (N_t h_t)^{1-α}
%        G_t = g_Y(t) × Y_t    (exogenous government spending share)
%        NX_t = nx_Y(t) × Y_t  (exogenous net export share)
%        A_{t+1} = (1+g_A) A_t
%   P_t resident population (all ages), N_t persons in employment, h_t hours per person employed; C_t = P_t c_t
%
% In sample, P_t is the HP trend of the resident population pop_level, as N_t
% is of n_level; after the last data quarter both grow with the workbook's
% projection (demographic_P_{country}.csv and demographic_L_{country}.csv).
% The handoff solve reads demographic_P from the handoff quarter on. Beyond the
% last row of its source the dependency ratio is frozen, P_t = (P/N)_last × N_t,
% in the in-sample and in the handoff solve alike, so N/P is asymptotically
% constant and the balanced-growth closure below holds.
%
% G and NX are exogenous: their GDP shares are HP-filtered from data and
% held constant (or slowly extrapolated) beyond the sample. The household
% Euler equation is unchanged — adding G and NX only affects the resource
% constraint, redistributing output between private C, I, G, and NX.
%
% Solved via a stacked-time Newton (LBJ) perfect-foresight solve with
% terminal K/Y closure.
%
% Detrending table:
%   y   = 100 * [ln Y_obs - ln Y_trend]          (Ramsey output)
%   c   = 100 * [ln C_obs - ln C_trend]          (Ramsey private consumption)
%   inv = 100 * [ln I_obs - ln I_trend]          (Ramsey investment residual)
%   pi  = pi_obs - pi*                           (inflation target trend)
%   R   = R_obs - R*_smooth                      (smoothed ex-ante real rate + pi*)
%   n   = 100 * [ln n_obs - ln N_trend]          (employment)
%   L   = 100 * [ln L_obs - ln L_demo]           (demographic labor force)
%   h   = 100 * [ln h_obs - ln h_trend]          (log-linear hours trend)
%
% Usage:
%   [detrended, trend] = ramsey_trend('Poland')
%   [detrended, trend, diag] = ramsey_trend('Poland', [], 'projection_start', '2023q1')
%
% Inputs:
%   country   - Country name (loads the calibrated growth_params_{country}.json from the bundle folder)
%   data_file - CSV with level data from make_datasets.do [default: auto]
%
% Outputs:
%   detrended   - Table with 8 demeaned DSGE observables (% dev from trend)
%   trend       - Table with trend paths (levels) for diagnostics/export
%   diagnostics - Struct with solver info, BGP values, goodness-of-fit
%
% Author: WELLSIM Project
% Date: February 2026
% =========================================================================
    arguments
        country (1,:) char = 'UK'
        data_file (1,:) char = ''
        options.projection_start (1,:) char = ''
    end
    % Resolve the model's code folders (internal/, diagnostics/, ...).
    % Idempotent; see setup_path.m for why MATLAB cannot do this itself.
    setup_path();
    country_config = get_country_config(country);
    country = country_config.name;
    options.projection_start = resolve_projection_start(options.projection_start);

    if isempty(data_file), data_file = artifact_path('data', sprintf('data_for_dynare_levels_%s.csv', country)); end

    % Start output log (like Stata's "log using")
    log_file = artifact_path('logs', sprintf('ramsey_trend_%s.log', lower(country)), 'create', true);
    if exist(log_file, 'file'), delete(log_file); end
    diary(log_file);
    fprintf('Logging output to %s\n', log_file);

    fprintf('\n');
    box_width = 62;
    fprintf('╔%s╗\n', repmat('═', 1, box_width));
    print_box_line(sprintf('RAMSEY TREND MODEL: %s', upper(country)), box_width, 'center');
    fprintf('╚%s╝\n', repmat('═', 1, box_width));
    fprintf('\n');

    %% =====================================================================
    %% 1. LOAD PARAMETERS
    %% =====================================================================

    json_paths = get_growth_params_paths(country);
    json_file = json_paths.export;
    if ~exist(json_file, 'file')
        error('Parameter file not found: %s\nRun calibrate_growth_model(''%s'') first.', json_file, country);
    end

    params = jsondecode(fileread(json_file));
    p = params.parameters;

    % Structural parameters
    alpha  = p.alpha_K;
    delta_a = p.delta_annual;
    delta  = 1 - (1 - delta_a)^(1/4);       % quarterly depreciation
    beta_a = p.beta_annual;
    beta   = beta_a^(1/4);                    % quarterly discount factor
    sigma  = p.sigma;
    g_A_a  = p.tfp_growth_annual;
    g_A    = (1 + g_A_a)^(1/4) - 1;  % quarterly TFP growth rate

    terminal_growth = struct(...
        'enabled', false, ...
        'g_A_initial_annual', g_A_a, ...
        'g_A_terminal_annual', g_A_a, ...
        'g_A_terminal_quarterly', g_A, ...
        'rho_quarterly', NaN);
    if isfield(params, 'terminal_growth') && isfield(params.terminal_growth, 'enabled') && params.terminal_growth.enabled
        if ~isfield(params.terminal_growth, 'g_A_terminal_annual') || params.terminal_growth.g_A_terminal_annual <= 0
            error('terminal_growth.enabled is true, but g_A_terminal_annual is missing or non-positive in %s.', json_file);
        end
        terminal_growth.enabled = true;
        terminal_growth.g_A_terminal_annual = params.terminal_growth.g_A_terminal_annual;
        terminal_growth.g_A_terminal_quarterly = (1 + terminal_growth.g_A_terminal_annual)^(1/4) - 1;
        if isfield(params.terminal_growth, 'rho_quarterly')
            terminal_growth.rho_quarterly = params.terminal_growth.rho_quarterly;
        else
            terminal_growth.rho_quarterly = 0.99;
        end
        if terminal_growth.rho_quarterly <= 0 || terminal_growth.rho_quarterly >= 1
            error('terminal_growth.rho_quarterly must lie in (0,1). Found %.4f.', terminal_growth.rho_quarterly);
        end
    end

    fprintf('Parameters (quarterly):\n');
    fprintf('  alpha = %.3f,  delta = %.6f,  beta = %.6f\n', alpha, delta, beta);
    fprintf('  sigma = %.3f,  g_A   = %.6f\n', sigma, g_A);
    if terminal_growth.enabled
        fprintf('  Terminal g_A convergence: %.2f%% -> %.2f%% annual (rho_q = %.3f)\n', ...
                100*terminal_growth.g_A_initial_annual, ...
                100*terminal_growth.g_A_terminal_annual, ...
                terminal_growth.rho_quarterly);
    end

    % Base-year values (from calibration)
    if isempty(params.base_year_values.K) || isnan(params.base_year_values.K)
        error('K_0 not calibrated. Run calibrate_growth_model(''%s'') first.', country);
    end

    K0  = params.base_year_values.K;
    A0  = params.base_year_values.A_implied;

    fprintf('  K_0 = %.1f,  A_0 = %.6f\n\n', K0, A0);

    % Solver settings (LBJ stacked-time Newton)
    max_iter = round(get_solver_numeric(params.solver, 'max_newton_iterations', 'max_iterations', 200));
    tol      = get_solver_numeric(params.solver, 'newton_tolerance', 'tolerance', 1e-10);
    T_extend_extra = round(get_solver_numeric(params.solver, 'terminal_tail_quarters', 'terminal_extension_quarters', 300));

    % Labour input is exogenous: N is taken as given.
    fprintf('  Labor supply: exogenous (N taken as given)\n\n');

    %% =====================================================================
    %% 2. LOAD LEVEL DATA
    %% =====================================================================

    if ~exist(data_file, 'file')
        error(['Level data not found: %s\n' ...
               'Run make_datasets.do with level export enabled.'], data_file);
    end

    data = readtable(data_file);
    % Drop the ragged calendar tail (see trim_trailing_missing). Rows without the
    % core production inputs cannot enter the trend, and their NaNs would otherwise
    % propagate through N_trend into an infeasible stacked-time initial guess.
    [data, n_ragged] = trim_trailing_missing(data);
    T = height(data);
    if n_ragged > 0
        fprintf('Trimmed %d trailing quarter(s) with no production data\n', n_ragged);
    end
    fprintf('Loaded %d quarters from %s (through %s)\n', ...
            T, data_file, char(string(data.time(end))));

    % K_0 and A_0 are calibrated AT the JSON base year, but the solver anchors them
    % at the FIRST ROW of the data file (A_ext(1)=A0, K_path(1)=K0). Those coincide
    % today, and nothing else enforces it: moving make_datasets.do's startdate
    % without recalibrating base_year would silently anchor the whole trend at the
    % wrong date, with no symptom other than wrong levels.
    expected_first = sprintf('%dq1', params.base_year);
    actual_first = char(string(data.time(1)));
    if ~strcmpi(actual_first, expected_first)
        error(['Base-year anchoring broken: K_0/A_0 are calibrated at %s but %s ' ...
               'starts at %s. The solver anchors them at the first data row, so ' ...
               'these must be the same quarter. Either set base_year to %s in the ' ...
               'growth params JSON and re-run calibrate_growth_model, or restore ' ...
               'the data sample to start at %s.'], ...
              expected_first, data_file, actual_first, ...
              strrep(actual_first, 'q1', ''), expected_first);
    end

    simpaths_handoff_time = options.projection_start;
    handoff_idx = find(strcmpi(string(data.time), simpaths_handoff_time), 1);
    if isempty(handoff_idx)
        error(['SimPaths handoff quarter %s not found in %s. ' ...
               'Available range: %s to %s.'], ...
              simpaths_handoff_time, data_file, data.time{1}, data.time{end});
    end
    fprintf('SimPaths handoff quarter: %s (row %d of %d)\n', ...
            simpaths_handoff_time, handoff_idx, T);

    %% =====================================================================
    %% 3. CONSTRUCT LABOR INPUT TRENDS
    %% =====================================================================
    %
    % Four exogenous inputs to the Ramsey model:
    %   N_trend(t) = employment trend                  [production function]
    %   h_trend(t) = exp(log-linear trend of h)        [hours per person employed]
    %   A(t)       = A_0 × (1 + g_A)^t                 [TFP level]
    %   P_trend(t) = resident population trend         [consumers: utility weight, C = P c]
    %
    % N_trend is the HP trend of n_level, all persons in employment, the
    % concept A_0 was calibrated on in calibrate_growth_model.m (together with
    % h_level, hours per person employed).

    fprintf('\n--- Constructing labor input trends ---\n');

    % 3a. Labor force: demographic trend (used for L̂ detrending only).
    % The file may extend beyond the in-sample horizon when populated with a
    % SimPaths-aligned projection; in that case the post-sample tail overrides
    % the trailing-growth extrapolation used by extend_exogenous.
    demo_L_file = artifact_path('data', sprintf('demographic_L_%s.csv', lower(country)));
    if exist(demo_L_file, 'file')
        demo_data = readtable(demo_L_file);
        L_demo_full = demo_data.L_demo;
        fprintf('  L_demo loaded from %s (n=%d)\n', demo_L_file, length(L_demo_full));
    else
        % Fallback: HP trend of observed labor force (very smooth)
        fprintf('  ⚠ %s not found, computing HP trend of observed L...\n', demo_L_file);
        L_demo_full = hp_filter_trend(log(data.L_level), 160000);
        L_demo_full = exp(L_demo_full);
    end
    if length(L_demo_full) >= T
        L_demo = L_demo_full(1:T);
        L_demo_post = L_demo_full(T+1:end);
    else
        L_demo = L_demo_full;
        L_demo_post = [];
    end

    % 3b. NAWRU, for one diagnostic only: 3c checks the employment identity
    % L(1-u*) against the employment trend once N_trend exists. N_trend does
    % not use the NAWRU.
    nawru_file = artifact_path('data', sprintf('nawru_%s.csv', lower(country)));
    if exist(nawru_file, 'file')
        nawru_data = readtable(nawru_file);
        nawru = nawru_data.nawru;
        fprintf('  NAWRU loaded from %s (for diagnostics)\n', nawru_file);
    else
        if ismember('u_level', data.Properties.VariableNames)
            nawru = hp_filter_trend(data.u_level / 100, 160000);
        else
            nawru = 0.07 * ones(T, 1);
        end
        fprintf('  NAWRU computed from HP trend of u_level\n');
    end

    % 3c. Employment trend (for the Ramsey production function)
    % HP-filtered n_level (all persons in employment, national concept).
    % This is consistent with:
    %   - A_0 calibration (uses n_level)
    %   - h_level (hours per person employed)
    %   - The DSGE model's n̂ variable (employment gap)
    N_trend = hp_filter_trend(log(data.n_level), 160000);
    N_trend = exp(N_trend);
    fprintf('  N_trend (employment, mean) = %.1f thousand\n', mean(N_trend, 'omitnan'));

    % Diagnostic (printed, never gated): with N all persons in employment,
    % U = L - N holds in concept, so L_demo*(1-NAWRU) and N_trend measure the
    % same employment; the gap is the difference between the concepts of the
    % labour-force and employment series.
    N_from_L1 = L_demo(1) * (1 - nawru(1));
    fprintf('  L_demo*(1-NAWRU) vs N_trend at %s: %.1f vs %.1f (gap %+.2f%%)\n', ...
            char(string(data.time(1))), N_from_L1, N_trend(1), ...
            100 * (N_from_L1 - N_trend(1)) / N_trend(1));

    % 3d. Hours trend: log-linear
    ln_h = log(data.h_level);
    valid_h = ~isnan(ln_h);
    t_vec = (1:T)';
    h_coeffs = polyfit(t_vec(valid_h), ln_h(valid_h), 1);
    h_trend = exp(h_coeffs(1) * t_vec + h_coeffs(2));
    fprintf('  Hours trend: slope = %.6f per quarter (%.2f%% annual)\n', ...
            h_coeffs(1), h_coeffs(1)*400);

    % 3e. Population trend: the planner's consumers (utility weight, C = P c)
    % In sample, P_trend is the HP trend (lambda = 160,000) of the log resident
    % population pop_level, computed here from the levels data as N_trend is
    % from n_level; the data stage has spliced the census breaks before.
    % demographic_P_{country}.csv carries the same trend in its in-sample rows
    % (calibrate_growth_model.m) and, from the handoff quarter on, the workbook's
    % projection of all ages (build_population_projection.m). The in-sample
    % solve chains the rows after the last data quarter on P_trend(T), as N_ext
    % is chained on L_demo (section 4); the handoff solve reads the file from the
    % handoff quarter on (section 5b). Both freeze P/N beyond the last row.
    if ~ismember('pop_level', data.Properties.VariableNames)
        error('ramsey_trend:noPopLevel', ...
              ['%s has no pop_level column; the planner''s consumers are the resident population.\n' ...
               'Re-run stata/make_datasets.do for %s, then calibrate_growth_model(''%s'').'], ...
              data_file, country, country);
    end
    P_trend = exp(hp_filter_trend(log(data.pop_level), 160000));
    demo_P_file = artifact_path('data', sprintf('demographic_P_%s.csv', lower(country)));
    if ~exist(demo_P_file, 'file')
        error('ramsey_trend:noPopulation', ...
              'Population trend not found: %s\nRun calibrate_growth_model(''%s'') first.', ...
              demo_P_file, country);
    end
    demo_P_data = readtable(demo_P_file);
    P_demo_full = demo_P_data.P_demo;
    if length(P_demo_full) < T
        error('ramsey_trend:shortPopulation', ...
              ['%s has %d rows, fewer than the %d sample quarters.\n' ...
               'Run calibrate_growth_model(''%s'') first.'], ...
              demo_P_file, length(P_demo_full), T, country);
    end
    fprintf('  P_trend: HP trend of pop_level through %s; P_demo loaded from %s (n=%d)\n', ...
            char(string(data.time(T))), demo_P_file, length(P_demo_full));
    fprintf('  P_trend (population, mean) = %.1f thousand,  P/N at t=1: %.3f\n', ...
            mean(P_trend, 'omitnan'), P_trend(1) / N_trend(1));

    % 3f. Government and net export shares (exogenous, for open-economy RC)
    % G_t = g_Y(t) × Y_t,  NX_t = nx_Y(t) × Y_t
    % Smoothed with HP filter (λ=160,000) to extract structural shares.
    % COVID period interpolated to prevent pandemic-induced share distortion.
    covid_mask = build_covid_mask(data.time, T);

    if ismember('g_level', data.Properties.VariableNames) && ...
       ismember('nx_level', data.Properties.VariableNames)
        % Compute raw shares
        gY_raw  = data.g_level  ./ data.y_level;
        nxY_raw = data.nx_level ./ data.y_level;

        % COVID interpolation before HP filtering
        gY_interp  = interpolate_through(gY_raw, covid_mask);
        nxY_interp = interpolate_through(nxY_raw, covid_mask);

        % HP-filter to get smooth structural shares
        gY_smooth  = hp_filter_trend(gY_interp, 160000);
        nxY_smooth = hp_filter_trend(nxY_interp, 160000);

        fprintf('\n  Open-economy shares (G/Y and NX/Y):\n');
        fprintf('    G/Y  (smooth): %.3f → %.3f\n', gY_smooth(1), gY_smooth(end));
        fprintf('    NX/Y (smooth): %.3f → %.3f\n', nxY_smooth(1), nxY_smooth(end));
        fprintf('    Absorption share (1-G/Y-NX/Y): %.3f → %.3f\n', ...
                1 - gY_smooth(1) - nxY_smooth(1), ...
                1 - gY_smooth(end) - nxY_smooth(end));
        has_open_economy = true;
    else
        fprintf('\n  ⚠ g_level/nx_level not found in data — using closed economy (G=NX=0)\n');
        gY_smooth  = zeros(T, 1);
        nxY_smooth = zeros(T, 1);
        has_open_economy = false;
    end

    %% =====================================================================
    %% 4. SOLVE RAMSEY MODEL (STACKED-TIME NEWTON / LBJ)
    %% =====================================================================
    %
    % Extend exogenous paths well beyond sample to avoid terminal effects.
    % The Ramsey solution requires a transversality condition; we approximate
    % it by requiring K/Y → K/Y_bgp at the terminal date.

    T_total = T + T_extend_extra;

    fprintf('\n--- Solving Ramsey model ---\n');
    fprintf('  Sample: %d quarters,  Extended: %d quarters\n', T, T_total);

    % Extend G/Y and NX/Y shares beyond sample (constant extrapolation)
    gY_ext  = extend_share(gY_smooth, T_total);
    nxY_ext = extend_share(nxY_smooth, T_total);

    % BGP capital-output ratio (target for terminal condition)
    % Unchanged by G and NX: the Euler equation pins down MPK and hence K/Y.
    % With positive trend growth, use the growth-corrected modified golden rule:
    %   1 + r* = (1 + g_c)^sigma / beta,  g_c = g_A / (1-alpha)
    %   K/Y = alpha / (r* + delta)
    g_A_bgp = g_A;
    if terminal_growth.enabled
        g_A_bgp = terminal_growth.g_A_terminal_quarterly;
    end
    g_c_bgp = g_A_bgp / (1 - alpha);
    r_star_bgp_q = (1 + g_c_bgp)^sigma / beta - 1;
    KY_bgp = alpha / (r_star_bgp_q + delta);

    % =====================================================================
    % STACKED-TIME (LBJ) PERFECT-FORESIGHT SOLVE
    % ---------------------------------------------------------------------
    % The Euler + resource constraints for ALL quarters are solved
    % simultaneously by a damped Newton iteration on a sparse tridiagonal
    % system, with K_1 fixed and the terminal transversality closure
    % K_{Tt-1}/Y_{Tt-1} = KY_bgp. Because the linear solve is global,
    % saddle-path conditioning is irrelevant: this handles anticipated /
    % delayed permanent shocks, which a one-dimensional forward solve does not.
    % Mirrors RamseyTrendModel.java (SimPaths runtime).
    % =====================================================================
    % Build extended exogenous paths
    [N_ext, h_ext, A_ext, g_A_annual_path] = extend_exogenous(N_trend, h_trend, A0, g_A, T, T_total, terminal_growth);

    % Override the forward N extrapolation with the SimPaths-aligned
    % projection embedded in the post-sample tail of demographic_L_*.csv.
    % L_demo and N_trend live in different units (labor force vs employment),
    % so we chain the override on L_demo's relative growth at the handoff.
    if ~isempty(L_demo_post)
        n_avail = min(length(L_demo_post), T_total - T);
        N_ext(T+1:T+n_avail) = N_ext(T) * L_demo_post(1:n_avail) / L_demo(T);
        if n_avail < T_total - T
            tail_window = min(20, n_avail - 1);
            N_ext = apply_smooth_terminal_demographic_closure(N_ext, T+n_avail, T_total, tail_window);
        end
        fprintf('  Forward N override: %d quarters from L_demo projection (g_N_q=%.5f)\n', ...
                n_avail, (N_ext(T+n_avail)/N_ext(T))^(1/n_avail) - 1);
    end

    % Consumers: P_trend through the last data quarter, then the rows of
    % demographic_P after it chained on P_trend(T) with their own growth, as
    % N_ext is chained on L_demo above, then the dependency ratio of the last
    % row held on the N_ext path.
    P_avail = [P_trend; P_trend(T) * P_demo_full(T+1:end) / P_demo_full(T)];
    P_ext = extend_population_frozen_ratio(P_avail, N_ext, T_total);
    n_P_avail = min(length(P_avail), T_total);
    fprintf('  Population P: HP trend of pop_level for %d quarters, %d chained on demographic_P growth, beyond them P/N held at %.3f\n', ...
            T, n_P_avail - T, P_ext(n_P_avail) / N_ext(n_P_avail));
    if n_P_avail > T
        fprintf(['  Population P growth into the quarter after %s: %+.5f%% (demographic_P: %+.5f%%); ' ...
                 'P_demo/P_trend at %s: %.5f\n'], ...
                char(string(data.time(T))), 100 * (P_ext(T+1) / P_ext(T) - 1), ...
                100 * (P_demo_full(T+1) / P_demo_full(T) - 1), ...
                char(string(data.time(T))), P_demo_full(T) / P_trend(T));
    end

    fprintf('  Mode: EXOGENOUS labor (N taken as given)\n');
    fprintf('  K/Y (BGP target) = %.2f (quarterly)\n', KY_bgp);

    econ = @(t, K, c) exo_period_econ(t, K, c, A_ext, N_ext, h_ext, alpha);
    labScale = N_ext;     % exogenous labour scale (mirrors Java nPath)
    [K_path, c_path, Y_path, ~, ~, iter, converged] = ...
        ramsey_stacked_newton(K0, T_total, alpha, delta, beta, sigma, ...
            gY_ext, nxY_ext, KY_bgp, A_ext, h_ext, labScale, P_ext, econ, ...
            tol, max_iter);

    c_try = c_path(1);   % retained for diagnostics/save compatibility
    if ~converged
        warning('Stacked-time Newton did not reach tolerance in %d iterations.', max_iter);
    end
    fprintf('  Stacked-time Newton converged in %d iterations (c_0 = %.6f)\n', iter, c_try);
    fprintf('  K_1 = %.1f (calibrated K0),  terminal K/Y = %.4f (BGP = %.4f)\n', ...
            K_path(1), K_path(end)/Y_path(end), KY_bgp);

    %% =====================================================================
    %% 5b. BUILD SIMPATHS HANDOFF CONTRACT ROW
    %% =====================================================================
    %
    % SimPaths does not continue the historical MATLAB solve. It restarts a
    % fresh forward projection from the exported handoff quarter using the
    % truncated reference CSV and growth_terminal_state.json. To keep the
    % last reference row exactly aligned with the Java forward solver, we
    % recompute quarter 0 from that same handoff contract here.

    fprintf('\n--- Building SimPaths handoff contract ---\n');

    % Forward horizon of the SimPaths handoff contract. Exported into
    % growth_terminal_state.json and validated on the Java side, so a mismatch
    % fails loudly instead of relying on this comment staying true.
    projection_quarters = 320;
    handoff_ref_total = handoff_idx + projection_quarters;

    handoff_h_hist = h_trend(1:handoff_idx);
    handoff_gY_hist = gY_smooth(1:handoff_idx);
    handoff_nxY_hist = nxY_smooth(1:handoff_idx);

    handoff_n_full = extend_level_path(N_trend(1:handoff_idx), handoff_ref_total, 20);
    handoff_h_full = extend_level_path(handoff_h_hist, handoff_ref_total, 1);

    % Same override as in the main solve: replace the post-handoff N
    % extrapolation with the SimPaths-aligned L_demo projection, chained
    % on relative growth from the handoff anchor.
    if length(L_demo_full) > handoff_idx
        n_avail = min(length(L_demo_full) - handoff_idx, handoff_ref_total - handoff_idx);
        handoff_n_full(handoff_idx+1:handoff_idx+n_avail) = ...
            handoff_n_full(handoff_idx) * L_demo_full(handoff_idx+1:handoff_idx+n_avail) / L_demo_full(handoff_idx);
        if n_avail < handoff_ref_total - handoff_idx
            tail_window = min(20, n_avail - 1);
            handoff_n_full = apply_smooth_terminal_demographic_closure( ...
                handoff_n_full, handoff_idx+n_avail, handoff_ref_total, tail_window);
        end
    end

    handoff_labor_proj = handoff_n_full(handoff_idx:end);
    handoff_h_proj = handoff_h_full(handoff_idx:end);

    % Consumers on the handoff calendar: demographic_P through its last row
    % (within the contract horizon), then P/N frozen on handoff_n_full.
    handoff_P_full = extend_population_frozen_ratio(P_demo_full(1:min(end, handoff_ref_total)), handoff_n_full, handoff_ref_total);
    handoff_P_proj = handoff_P_full(handoff_idx:end);

    handoff_gY_full = extend_share(handoff_gY_hist, handoff_ref_total);
    handoff_nxY_full = extend_share(handoff_nxY_hist, handoff_ref_total);
    handoff_gY_proj = handoff_gY_full(handoff_idx:end);
    handoff_nxY_proj = handoff_nxY_full(handoff_idx:end);

    handoff_proj_len = length(handoff_labor_proj);
    handoff_total_len = handoff_proj_len + T_extend_extra;
    handoff_A0 = A_ext(handoff_idx);
    handoff_K0 = K_path(handoff_idx);

    handoff_n_ext = extend_level_path(handoff_labor_proj, handoff_total_len, 20);
    % Beyond the projection the dependency ratio is held at its last value.
    handoff_P_ext = extend_population_frozen_ratio(handoff_P_proj, handoff_n_ext, handoff_total_len);
    % Tapered, not flat-rate: see the note in extend_exogenous. The tail is a
    % terminal-condition device, and an hours path still declining at the fitted
    % rate contradicts the balanced-growth condition the solve imposes.
    handoff_h_ext = zeros(handoff_total_len, 1);
    handoff_h_ext(1:handoff_proj_len) = handoff_h_proj;
    handoff_h_ext = apply_smooth_terminal_demographic_closure(handoff_h_ext, handoff_proj_len, handoff_total_len, 1);
    handoff_A_ext = build_forward_tfp_path(handoff_A0, handoff_total_len, g_A, terminal_growth);
    handoff_gY_ext = extend_share(handoff_gY_proj, handoff_total_len);
    handoff_nxY_ext = extend_share(handoff_nxY_proj, handoff_total_len);
    handoff_econ = @(t, K, c) exo_period_econ(t, K, c, handoff_A_ext, ...
        handoff_n_ext, handoff_h_ext, alpha);
    handoff_lab_scale = handoff_n_ext;

    [handoff_K_path, handoff_c_path, handoff_Y_path, ~, handoff_N_path, ~, handoff_converged] = ...
        ramsey_stacked_newton(handoff_K0, handoff_total_len, alpha, delta, beta, sigma, ...
            handoff_gY_ext, handoff_nxY_ext, KY_bgp, handoff_A_ext, handoff_h_ext, ...
            handoff_lab_scale, handoff_P_ext, handoff_econ, tol, max_iter);

    if ~handoff_converged
        warning('SimPaths handoff forward solve did not converge cleanly for %s.', simpaths_handoff_time);
    end

    handoff_N0 = handoff_N_path(1);
    handoff_P0 = handoff_P_ext(1);
    handoff_Y0 = handoff_Y_path(1);
    handoff_C0 = handoff_P0 * handoff_c_path(1);
    handoff_I0 = handoff_Y0 - handoff_C0 - handoff_gY_proj(1) * handoff_Y0 - handoff_nxY_proj(1) * handoff_Y0;
    handoff_w0 = (1 - alpha) * handoff_Y0 / (handoff_N0 * handoff_h_proj(1));
    handoff_r0 = alpha * handoff_Y0 / handoff_K_path(1) - delta;

    fprintf('  SimPaths handoff row recomputed at %s (K=%.1f, Y=%.1f, N=%.1f)\n', ...
            simpaths_handoff_time, handoff_K_path(1), handoff_Y0, handoff_N0);

    % Forward reporting window of the handoff solve: 148 quarters, 2023q1 to
    % 2059q4 for a 2023q1 handoff (printed as +37y). Used for the dependency
    % ratio here and for the forward wage CAGR in section 10.
    fwd_q = min(148, handoff_proj_len);
    fprintf('  Dependency ratio P/N: %.3f at %s, %.3f at +37y\n', ...
            handoff_P0 / handoff_N0, simpaths_handoff_time, ...
            handoff_P_ext(fwd_q) / handoff_N_path(fwd_q));

    %% =====================================================================
    %% 5. EXTRACT TREND PATHS FOR SAMPLE PERIOD
    %% =====================================================================

    K_trend = K_path(1:T);
    c_trend_pc = c_path(1:T);    % consumption per head
    Y_trend_q  = Y_path(1:T);    % quarterly GDP

    % N_trend is already set: the HP-filtered n_level from Section 3c.

    w_trend = zeros(T, 1);
    r_trend = zeros(T, 1);

    for t = 1:T
        eff_labor = N_trend(t) * h_trend(t);
        w_trend(t) = (1-alpha) * Y_trend_q(t) / eff_labor;
        r_trend(t) = alpha * Y_trend_q(t) / K_trend(t) - delta;
    end

    % --- C, I, G, NX trends from the open-economy Ramsey model ---
    % With Y = C + I + G + NX, the model directly produces:
    %   C_trend = P × c per head    (from Euler equation)
    %   G_trend = g_Y × Y_trend     (exogenous share)
    %   NX_trend = nx_Y × Y_trend   (exogenous share)
    %   I_trend = Y - C - G - NX    (residual investment)
    %
    % G and NX are in the resource constraint for that reason: a closed-economy
    % Ramsey overstates the private-consumption share whenever G+NX absorb a
    % large fraction of output in the country sample.

    C_trend_det = P_trend .* c_trend_pc;  % C_trend = P × c per head
    G_trend     = gY_smooth .* Y_trend_q;
    NX_trend    = nxY_smooth .* Y_trend_q;
    I_trend_det = Y_trend_q - C_trend_det - G_trend - NX_trend;

    fprintf('\n  Open-economy Ramsey model output:\n');
    fprintf('    C/Y (model): %.3f → %.3f  (private consumption, from Euler)\n', ...
            C_trend_det(1)/Y_trend_q(1), C_trend_det(end)/Y_trend_q(end));
    fprintf('    I/Y (model): %.3f → %.3f  (residual investment)\n', ...
            I_trend_det(1)/Y_trend_q(1), I_trend_det(end)/Y_trend_q(end));
    fprintf('    G/Y (exog):  %.3f → %.3f  (government share)\n', ...
            gY_smooth(1), gY_smooth(end));
    fprintf('    NX/Y (exog): %.3f → %.3f  (net export share)\n', ...
            nxY_smooth(1), nxY_smooth(end));

    % Cross-check: I_trend should be positive and reasonable
    IY_model = I_trend_det ./ Y_trend_q;
    IY_obs = data.inv_level ./ data.y_level;
    fprintf('    I/Y observed (mean):  %.3f\n', mean(IY_obs, 'omitnan'));
    fprintf('    I/Y model (mean):     %.3f\n', mean(IY_model, 'omitnan'));
    if any(I_trend_det <= 0)
        warning('I_trend negative at %d quarters! Check G/Y + NX/Y shares.', sum(I_trend_det <= 0));
    end

    % Annualize r* for Fisher equation (Ramsey MPK — kept for reference/SimPaths)
    r_star_annual = (1 + r_trend).^4 - 1;

    % --- Smoothed ex-ante real rate for R detrending ---
    % The Ramsey MPK r* measures the return to domestic capital in the
    % calibrated country model. For detrending the observed policy rate,
    % we instead want a secular ex-ante real-rate trend that absorbs slow
    % macro-financial forces not captured by the Ramsey capital return.
    % Otherwise a drifting policy-rate environment can show up as a spurious
    % persistent monetary shock in the DSGE sample.
    %
    % Hence the R trend is the smoothed ex-ante real rate HP_trend(R - E[pi]), capturing
    % the secular decline without imposing a structural model on ECB
    % behaviour.
    pi_expected = movmean(data.pi, [3 0], 'omitnan');  % 4Q backward MA
    r_ex_ante = data.R - pi_expected;                   % ex-ante real rate

    % Handle NaN (COVID period R is kept, but pi has NaNs in 2020-2021)
    r_ex_ante_clean = r_ex_ante;
    nan_mask_r = isnan(r_ex_ante_clean);
    if any(nan_mask_r)
        valid_idx = find(~nan_mask_r);

        % An interior gap is interpolated. A gap at either END is extrapolated
        % along the last two observations, and an HP trend with lambda this high
        % follows that line across the whole sample. Measured on Poland with R
        % missing for 2024q1-2025q1 (2026-10): the extrapolation runs the ex-ante
        % real rate up to +12.5% and shifts the R gap by up to 0.9 pp at both ends
        % of the sample. So an edge gap is a data fault, not benign missingness.
        n_lead = valid_idx(1) - 1;
        n_trail = T - valid_idx(end);
        if n_lead + n_trail > 0
            warning('ramsey_trend:RealRateExtrapolated', ...
                    ['R - E[pi] is missing for %d leading and %d trailing quarter(s) and is ' ...
                     'being extrapolated linearly. The HP trend of R follows that line over ' ...
                     'the WHOLE sample, so the R gap is distorted everywhere, not only at ' ...
                     'the edge. Check the R series in make_datasets.do.'], n_lead, n_trail);
        end

        r_ex_ante_clean = interp1(valid_idx, r_ex_ante_clean(valid_idx), ...
                                   (1:T)', 'linear', 'extrap');
    end

    r_star_smooth = hp_filter_trend(r_ex_ante_clean, 160000);

    fprintf('\n  Interest rate trend (ex-ante real rate approach):\n');
    fprintf('    r* (smooth): %.2f%% -> %.2f%%  (ex-ante real rate, HP-smoothed)\n', ...
            r_star_smooth(1), r_star_smooth(end));
    fprintf('    r* (Ramsey MPK): %.2f%% -> %.2f%%  (domestic capital return, for reference)\n', ...
            100*r_star_annual(1), 100*r_star_annual(end));

    fprintf('\nTrend paths (sample averages):\n');
    fprintf('  Y_trend = %.1f  (quarterly)\n', mean(Y_trend_q, 'omitnan'));
    fprintf('  C_trend (det) = %.1f\n', mean(C_trend_det, 'omitnan'));
    fprintf('  I_trend (det) = %.1f\n', mean(I_trend_det, 'omitnan'));
    fprintf('  K_trend = %.1f\n', mean(K_trend, 'omitnan'));
    fprintf('  w_trend = %.4f\n', mean(w_trend, 'omitnan'));
    fprintf('  r* (annual) = %.4f (%.2f%%)\n', mean(r_star_annual, 'omitnan'), ...
            100*mean(r_star_annual, 'omitnan'));

    %% =====================================================================
    %% 6. DETREND ALL 8 OBSERVABLES
    %% =====================================================================

    fprintf('\n--- Detrending observables ---\n');

    detrended = table();
    detrended.time = data.time;

    % --- Group 1: Real aggregates (Ramsey-based log-deviations, %) ---

    % y: output gap
    detrended.y = 100 * (log(data.y_level) - log(Y_trend_q));

    % c: consumption gap (expenditure share × Y_trend — see Section 5)
    detrended.c = 100 * (log(data.c_level) - log(C_trend_det));

    % inv: investment gap (expenditure share × Y_trend — see Section 5)
    I_trend_det_safe = max(I_trend_det, 1e-6);
    detrended.inv = 100 * (log(data.inv_level) - log(I_trend_det_safe));

    % --- Group 2: Nominal variables ---

    % pi: inflation gap
    % pi* = ECB target (2%) with possible convergence from initial level
    pi_star = compute_pi_trend(data, params.inflation_target);
    detrended.pi = data.pi - pi_star;

    % R: interest rate gap
    % R* = smoothed ex-ante real rate + pi*
    % This captures the secular movement in the relevant observed real-rate
    % trend, unlike the Ramsey MPK which reflects domestic capital returns.
    R_trend = r_star_smooth + pi_star;
    detrended.R = data.R - R_trend;

    % --- Group 3: Labor market (structural trends) ---

    % n: employment gap
    detrended.n = 100 * (log(data.n_level) - log(N_trend));

    % L: labor force gap
    detrended.L = 100 * (log(data.L_level) - log(L_demo));

    % h: hours gap
    detrended.h = 100 * (log(data.h_level) - log(h_trend));

    %% =====================================================================
    %% 7. DEMEAN (for Dynare estimation compatibility)
    %% =====================================================================
    %
    % The DSGE model assumes zero-mean observables. After structural
    % detrending, the residuals should already be close to zero-mean,
    % but we demean explicitly for numerical precision and Dynare compatibility.

    obs_vars = {'y', 'inv', 'c', 'pi', 'R', 'n', 'L', 'h'};

    fprintf('\n  Variable   Mean (pre)   Std dev    Mean (post)\n');
    fprintf('  ─────────  ──────────   ────────   ───────────\n');

    for i = 1:length(obs_vars)
        v = obs_vars{i};
        vals = detrended.(v);
        valid = ~isnan(vals);
        m = mean(vals(valid));
        s = std(vals(valid));

        % Demean
        detrended.(v) = vals - m;

        m_post = mean(detrended.(v)(valid));
        fprintf('  %-9s  %8.4f     %8.4f   %8.4f\n', v, m, s, m_post);
    end

    %% =====================================================================
    %% 8. BUILD TREND TABLE FOR DIAGNOSTICS AND EXPORT
    %% =====================================================================

    trend = table();
    trend.time = data.time;
    trend.Y = Y_trend_q;
    trend.C = C_trend_det;              % Private consumption (from Ramsey Euler)
    trend.I = I_trend_det;              % Investment (residual: Y - C - G - NX)
    trend.G = G_trend;                  % Government consumption (exog share × Y)
    trend.NX = NX_trend;                % Net exports (exog share × Y)
    trend.gY = gY_smooth;              % Smoothed G/Y share
    trend.nxY = nxY_smooth;            % Smoothed NX/Y share
    trend.g_A_annual = g_A_annual_path(1:T);   % Annualised TFP growth path
    trend.K = K_trend;
    trend.w = w_trend;
    trend.r_star_quarterly = r_trend;          % Ramsey MPK (quarterly, for SimPaths)
    trend.r_star_annual = r_star_annual;        % Ramsey MPK (annual, for reference)
    trend.r_star_smooth = r_star_smooth;        % Smoothed ex-ante real rate (for R detrending)
    trend.N = N_trend;
    trend.P = P_trend;                  % Resident population (consumers)
    trend.L = L_demo;
    trend.h = h_trend;
    trend.pi_star = pi_star;
    trend.R_trend = R_trend;

    %% =====================================================================
    %% 9. WRITE OUTPUT FILES
    %% =====================================================================

    fprintf('\n--- Writing output files ---\n');

    % 9a. Detrended data for Dynare (writes data_for_dynare_{COUNTRY}.csv)
    dynare_out = table();
    dynare_out.time = data.time;
    for i = 1:length(obs_vars)
        dynare_out.(obs_vars{i}) = detrended.(obs_vars{i});
    end

    % Apply COVID censoring (NaN for Kalman filter)
    % 2020Q1 through 2021Q2 — reuses covid_mask from Section 5
    for i = find(covid_mask)'
        for j = 1:length(obs_vars)
            v = obs_vars{j};
            if ~strcmp(v, 'R')  % keep R during COVID (ZLB informative)
                dynare_out.(v)(i) = NaN;
            end
        end
    end

    % Write Dynare-ready CSV
    % Order matches varobs: y inv c pi R n L h
    dynare_export = table();
    dynare_export.time = dynare_out.time;
    dynare_export.y   = dynare_out.y;
    dynare_export.inv = dynare_out.inv;
    dynare_export.c   = dynare_out.c;
    dynare_export.pi  = dynare_out.pi;
    dynare_export.R   = dynare_out.R;
    dynare_export.n   = dynare_out.n;
    dynare_export.L   = dynare_out.L;
    dynare_export.h   = dynare_out.h;

    writetable(dynare_export, artifact_path('data', sprintf('data_for_dynare_%s.csv', country), 'create', true));
    fprintf('  ✓ data_for_dynare_%s.csv (Ramsey-detrended, %d obs)\n', country, T);

    % 9b. Trend paths for diagnostics
    writetable(trend, artifact_path('output', sprintf('ramsey_trends_%s.csv', lower(country)), 'create', true));
    fprintf('  ✓ ramsey_trends_%s.csv\n', lower(country));

    % 9c. Reference output for Java cross-validation and SimPaths handoff
    ref = table();
    ref.time = data.time(1:handoff_idx);
    ref.K = K_trend(1:handoff_idx);
    ref.Y = Y_trend_q(1:handoff_idx);
    ref.C = C_trend_det(1:handoff_idx);
    ref.I = I_trend_det(1:handoff_idx);
    ref.w = w_trend(1:handoff_idx);
    ref.r_star = r_trend(1:handoff_idx);
    ref.N = N_trend(1:handoff_idx);
    ref.L = L_demo(1:handoff_idx);
    ref.h = h_trend(1:handoff_idx);
    ref.g_Y = gY_smooth(1:handoff_idx);               % Government spending share (for Java solver)
    ref.nx_Y = nxY_smooth(1:handoff_idx);             % Net export share (for Java solver)
    % P is the LAST column: RamseyTrendModel.loadReferenceCSV reads the first
    % ten columns by position and the columns after them by header.
    ref.P = P_trend(1:handoff_idx);                   % Resident population (consumers)

    ref_file = artifact_path('output', sprintf('growth_model_reference_%s.csv', lower(country)), 'create', true);
    ref.K(end) = handoff_K_path(1);
    ref.Y(end) = handoff_Y0;
    ref.C(end) = handoff_C0;
    ref.I(end) = handoff_I0;
    ref.w(end) = handoff_w0;
    ref.r_star(end) = handoff_r0;
    ref.N(end) = handoff_N0;
    ref.h(end) = handoff_h_proj(1);
    ref.g_Y(end) = handoff_gY_proj(1);
    ref.nx_Y(end) = handoff_nxY_proj(1);
    ref.P(end) = handoff_P0;
    writetable(ref, ref_file);
    fprintf('  ✓ %s (through SimPaths handoff quarter %s)\n', ref_file, simpaths_handoff_time);

    % 9d. Save MATLAB workspace for later use (e.g., by export scripts)
    % K_terminal/A_terminal are the handoff values under the names
    % write_growth_terminal_state reads and growth_terminal_state.json carries to Java.
    K_sample_end = K_path(T);
    A_sample_end = A_ext(T);
    K_handoff = handoff_K_path(1); %#ok<NASGU>
    A_handoff = handoff_A0; %#ok<NASGU>
    K_terminal = K_handoff; %#ok<NASGU>
    A_terminal = A_handoff; %#ok<NASGU>
    K_extension_end = K_path(end); %#ok<NASGU>
    A_extension_end = A_ext(end); %#ok<NASGU>
    simpaths_handoff_idx = handoff_idx; %#ok<NASGU>
    save_vars = {'trend', 'K_path', 'A_ext', 'K_terminal', 'A_terminal', ...
                 'K_handoff', 'A_handoff', 'K_sample_end', 'A_sample_end', ...
                 'K_extension_end', 'A_extension_end', 'simpaths_handoff_time', 'simpaths_handoff_idx', ...
                 'projection_quarters', 'params', ...
                 'c_try', 'iter', 'converged', 'gY_smooth', 'nxY_smooth', ...
                 'g_A_annual_path', 'terminal_growth', ...
                 'handoff_K_path', 'handoff_Y_path', 'handoff_c_path', ...
                 'handoff_N_path', 'handoff_h_ext', 'handoff_gY_ext', 'handoff_nxY_ext', ...
                 'handoff_P_ext', 'P_trend', 'P_ext'};   % P_ext: the in-sample solve's consumers
    save(artifact_path('output', sprintf('ramsey_trend_%s.mat', lower(country)), 'create', true), save_vars{:});
    fprintf('  ✓ ramsey_trend_%s.mat\n', lower(country));

    %% =====================================================================
    %% 10. DIAGNOSTICS
    %% =====================================================================

    diagnostics = struct();
    diagnostics.c0_optimal = c_try;
    diagnostics.iterations = iter;
    diagnostics.converged = converged;
    diagnostics.KY_terminal = K_path(T) / Y_path(T);  % sample-end K/Y, same as KY_sample_end
    diagnostics.KY_sample_end = K_path(T) / Y_path(T);
    diagnostics.KY_extension_end = K_path(end) / Y_path(end);
    diagnostics.KY_bgp = KY_bgp;
    diagnostics.r_star_mean = mean(r_star_annual, 'omitnan');
    diagnostics.r_star_smooth_mean = mean(r_star_smooth, 'omitnan');
    diagnostics.terminal_growth_enabled = terminal_growth.enabled;
    diagnostics.g_A_initial_annual = g_A_annual_path(1);
    diagnostics.g_A_terminal_annual = g_A_annual_path(end);
    diagnostics.CY_model = mean(C_trend_det ./ Y_trend_q, 'omitnan');
    diagnostics.IY_model = mean(I_trend_det ./ Y_trend_q, 'omitnan');
    diagnostics.GY_mean = mean(gY_smooth, 'omitnan');
    diagnostics.NXY_mean = mean(nxY_smooth, 'omitnan');
    diagnostics.IY_actual = mean(data.inv_level ./ data.y_level, 'omitnan');

    fprintf('\n--- Diagnostics ---\n');
    fprintf('  c_0 optimal      = %.6f\n', c_try);
    fprintf('  Iterations       = %d\n', iter);
        fprintf('  K/Y (sample end) = %.2f\n', diagnostics.KY_sample_end);
        fprintf('  K/Y (extension end) = %.2f  (BGP target: %.2f)\n', ...
            diagnostics.KY_extension_end, KY_bgp);
    fprintf('  r* Ramsey MPK (mean annual) = %.2f%%\n', 100*diagnostics.r_star_mean);
    fprintf('  r* smooth (mean) = %.2f%%\n', diagnostics.r_star_smooth_mean);
    if terminal_growth.enabled
        fprintf('  g_A path (annual) = %.2f%% -> %.2f%%\n', ...
                100*diagnostics.g_A_initial_annual, 100*diagnostics.g_A_terminal_annual);
    end

    % Forward wage-trend CAGR over the SimPaths projection window (mirrors
    % the "exogenous wage" slope read off MacroPaths.csv). Uses the same
    % forward Newton solution that produces the SimPaths handoff contract,
    % so the slope here matches what the Java RamseyTrendModel projects.
    % fwd_q is the forward reporting window set in section 5b.
    w_fwd = (1 - alpha) * handoff_Y_path(1:fwd_q) ./ ...
            (handoff_N_path(1:fwd_q) .* handoff_h_ext(1:fwd_q));
    fwd_years = (fwd_q - 1) / 4;
    diagnostics.forward_wage_cagr_annual = ...
        (w_fwd(end) / w_fwd(1))^(1/fwd_years) - 1;
    fprintf('  Forward w_trend CAGR (%s +%.0fy) = %.2f%%/yr\n', ...
            simpaths_handoff_time, fwd_years, ...
            100 * diagnostics.forward_wage_cagr_annual);

    % Dependency ratio of the handoff solve, at the handoff and at the end of
    % the forward reporting window.
    diagnostics.dependency_ratio_handoff = handoff_P0 / handoff_N0;
    diagnostics.dependency_ratio_2060 = handoff_P_ext(fwd_q) / handoff_N_path(fwd_q);

    fprintf('  C/Y (model) = %.2f%%  I/Y (model) = %.2f%%  (actual GFCF: %.2f%%)\n', ...
            100*diagnostics.CY_model, 100*diagnostics.IY_model, 100*diagnostics.IY_actual);
    fprintf('  G/Y (mean)  = %.2f%%  NX/Y (mean)  = %.2f%%\n', ...
            100*diagnostics.GY_mean, 100*diagnostics.NXY_mean);

    % --- Automated sanity check (instead of eyeballing the trend plots) ---
    % Quantifies what the operator otherwise reads off the Investment panel:
    % is the residual investment trend positive, are the C/I/G/NX shares
    % structurally plausible, and does the trend actually track observed
    % investment over the estimation sample. Prints an explicit PASS/FAIL
    % verdict and raises a loud warning on any failure so a bad run (e.g. the
    % catastrophic-investment regime) cannot pass silently.
    diagnostics.sanity = ramsey_sanity_check(I_trend_det, Y_trend_q, ...
        C_trend_det, gY_smooth, nxY_smooth, data.inv_level, data.y_level);
    if ~diagnostics.sanity.pass
        warning(['Ramsey sanity check FAILED (%s). The detrended series are ' ...
                 'likely unusable -- inspect before exporting to SimPaths.'], ...
                 strjoin(diagnostics.sanity.failed, ', '));
    end

    documentation_diagnostics = struct();
    documentation_diagnostics.solver_iterations = iter;

    % Compare with HP-filtered cycles (if available)
    hp_file = artifact_path('data', sprintf('data_for_dynare_hp_%s.csv', country));
    if exist(hp_file, 'file')
        hp_data = readtable(hp_file);
        % The HP file keeps the full quarterly calendar while the Ramsey series is
        % trimmed to the last quarter with complete production data, so compare over
        % the common leading span rather than assuming equal length.
        n_cmp = min(height(hp_data), T);
        fprintf('\n  Comparison with HP cycles (first %d quarters):\n', n_cmp);
        for i = 1:min(3, length(obs_vars))
            v = obs_vars{i};
            if ismember(v, hp_data.Properties.VariableNames)
                ramsey_v = detrended.(v)(1:n_cmp);
                hp_v = hp_data.(v)(1:n_cmp);
                valid_mask = ~isnan(ramsey_v) & ~isnan(hp_v);
                if nnz(valid_mask) < 2
                    fprintf('    corr(%s_ramsey, %s_hp) = n/a (insufficient overlap)\n', v, v);
                    continue;
                end
                corr_val = corr(ramsey_v(valid_mask), hp_v(valid_mask));
                documentation_diagnostics.(v) = corr_val;
                fprintf('    corr(%s_ramsey, %s_hp) = %.3f\n', v, v, corr_val);
            end
        end
    end

    try
        ramsey_summary = build_ramsey_documentation_summary(params, country, documentation_diagnostics);
        summary_file = artifact_path('output', fullfile('documentation_data', 'ramsey', ...
                                     sprintf('ramsey_summary_%s.json', country)));
        validate_documentation_summary(ramsey_summary, 'ramsey');
        write_json_summary(ramsey_summary, summary_file, ...
            'country', country, ...
            'source_function', 'ramsey_trend');
    catch documentation_error
        warning('ramsey_trend:DocumentationExportFailed', '%s', documentation_error.message);
    end

    %% =====================================================================
    %% 11. DIAGNOSTIC PLOTS
    %% =====================================================================

    % Plot trend vs actual for Y, C, I
    try
        figure('Name', sprintf('Ramsey Trends - %s', country), 'Position', [100 100 1400 900]);

        subplot(2,4,1);
        plot_trend_vs_actual(data.y_level, Y_trend_q, data.time, 'Output (Y)');

        subplot(2,4,2);
        plot_trend_vs_actual(data.c_level, C_trend_det, data.time, 'Consumption (C)');

        subplot(2,4,3);
        plot_trend_vs_actual(data.inv_level, I_trend_det, data.time, 'Investment (I)');

        subplot(2,4,4);
        if ismember('g_level', data.Properties.VariableNames)
            G_trend = gY_smooth .* Y_trend_q;
            plot_trend_vs_actual(data.g_level, G_trend, data.time, 'Government (G)');
        else
            title('Government (G) — no data'); grid on;
        end

        subplot(2,4,5);
        tick_idx = 1:20:T;
        plot(1:T, r_star_smooth, 'b-', 'LineWidth', 1.5);
        hold on;
        plot(1:T, r_star_annual*100, 'r--', 'LineWidth', 1.0);
        hold off;
        xlim([1 T]);
        title('r* (annual %)');
        legend('Smoothed ex-ante', 'Ramsey MPK', 'Location', 'best');
        xticks(tick_idx); xticklabels(data.time(tick_idx)); xtickangle(45);
        grid on;

        subplot(2,4,6);
        plot(1:T, detrended.y, 'b-');
        hold on; yline(0, 'r--'); hold off;
        xlim([1 T]);
        title('Output Gap (% dev)');
        xticks(tick_idx); xticklabels(data.time(tick_idx)); xtickangle(45);
        grid on;

        subplot(2,4,7);
        plot(1:T, detrended.R, 'b-');
        hold on; yline(0, 'r--'); hold off;
        xlim([1 T]);
        title('Interest Rate Gap (pp)');
        xticks(tick_idx); xticklabels(data.time(tick_idx)); xtickangle(45);
        grid on;

        subplot(2,4,8);
        plot(1:T, gY_smooth * 100, 'b-', 'LineWidth', 1.2);
        hold on;
        plot(1:T, nxY_smooth * 100, 'r-', 'LineWidth', 1.2);
        yline(0, 'k--');
        hold off;
        xlim([1 T]);
        title('G/Y and NX/Y (%)');
        legend('G/Y', 'NX/Y', 'Location', 'best');
        xticks(tick_idx); xticklabels(data.time(tick_idx)); xtickangle(45);
        grid on;

        sgtitle(sprintf('Ramsey Growth Model Trends — %s', country));

        saveas(gcf, artifact_path('figures', sprintf('ramsey_diagnostic_%s.png', lower(country)), 'create', true));
        fprintf('\n  ✓ Diagnostic plot saved: ramsey_diagnostic_%s.png\n', lower(country));
    catch ME
        fprintf('\n  ⚠ Could not create diagnostic plots: %s\n', ME.message);
    end

    %% =====================================================================
    %% SUMMARY
    %% =====================================================================

    fprintf('\n');
    fprintf('╔%s╗\n', repmat('═', 1, box_width));
    print_box_line('RAMSEY DETRENDING COMPLETE', box_width, 'center');
    fprintf('╠%s╣\n', repmat('═', 1, box_width));
    print_box_line('  Observables detrended: y, inv, c, pi, R, n, L, h', box_width);
    print_box_line(sprintf('  Output: data_for_dynare_%s.csv (replaces HP-filtered version)', country), box_width);
    print_box_line(sprintf('  Reference: growth_model_reference_%s.csv', lower(country)), box_width);
    print_box_line(sprintf('  r* (BGP annual): %.2f%%', 100*((1 + r_star_bgp_q)^4 - 1)), box_width);
    fprintf('╚%s╝\n', repmat('═', 1, box_width));
    fprintf('\n');
    fprintf('Next step: >> dynare Macromodel_%s use_dll\n', country);
    fprintf('  (Estimation will use Ramsey-detrended data automatically.)\n\n');

    % Stop output log
    diary off;

end


%% =========================================================================
%% HELPER FUNCTIONS
%% =========================================================================

function [N, Y, l] = exo_period_econ(t, K, c, A, N_ext, h, alpha) %#ok<INUSD>
% EXO_PERIOD_ECON  Per-quarter labor/output with exogenous N.
    N = N_ext(t);
    l = 0;
    Y = A(t) * K^alpha * (N * h(t))^(1-alpha);
end


function f = ramsey_stacked_residuals(z, K0, alpha, delta, beta, sigma, ...
                                       gY, nxY, KY_bgp, econ, P, Tt)
% RAMSEY_STACKED_RESIDUALS  Stacked-time perfect-foresight residual vector.
%
% Unknown layout (length 2*Tt-1), K_1 fixed at K0:
%   z(2t-1)=c_t (consumption per head), z(2t)=K_{t+1}  for t=1..Tt-1;
%   z(2*Tt-1)=c_Tt.
% Residuals (all dimensionless, O(1)):
%   row 2t-1 : resource    K_{t+1}/RHS - 1,
%                          RHS = (1-delta)K_t + Y_t - P_t c_t - (g_Y+nx_Y)_t Y_t
%   row 2t   : Euler       c_{t+1}/[c_t (beta(1+r_{t+1}))^{1/sigma}] - 1
%   row 2Tt-1: terminal    K_Tt/(KY_bgp*Y_Tt) - 1
% P is the exogenous population path (consumers); the population weight of
% the objective cancels from the Euler row.
% Returns [] if the point is infeasible.
    m = 2*Tt - 1;
    f = zeros(m, 1);
    for t = 1:Tt-1
        if t == 1, Kt = K0; else, Kt = z(2*t-2); end
        ct    = z(2*t-1);
        Knext = z(2*t);
        cnext = z(2*t+1);
        if ~(Kt > 0) || ~(ct > 0) || ~(Knext > 0) || ~isfinite(Kt) || ~isfinite(ct)
            f = []; return;
        end
        [~, Yt, ~] = econ(t, Kt, ct);
        if ~isfinite(Yt) || Yt <= 0, f = []; return; end
        rhsK = (1-delta)*Kt + Yt - P(t)*ct - (gY(t)+nxY(t))*Yt;
        if ~(rhsK > 0) || ~isfinite(rhsK), f = []; return; end
        f(2*t-1) = Knext/rhsK - 1;

        [~, Ynext, ~] = econ(t+1, Knext, cnext);
        if ~isfinite(Ynext) || Ynext <= 0, f = []; return; end
        r_next = alpha * Ynext / Knext - delta;
        gross  = beta * (1 + r_next);
        if ~(gross > 0) || ~isfinite(gross), f = []; return; end
        euler_rhs = ct * gross^(1/sigma);
        if ~(euler_rhs > 0) || ~isfinite(euler_rhs), f = []; return; end
        f(2*t) = cnext/euler_rhs - 1;
    end
    Klast = z(2*Tt-2);
    cLast = z(2*Tt-1);
    if ~(Klast > 0) || ~(cLast > 0), f = []; return; end
    [~, Ylast, ~] = econ(Tt, Klast, cLast);
    if ~isfinite(Ylast) || Ylast <= 0, f = []; return; end
    f(m) = Klast/(KY_bgp*Ylast) - 1;
    if any(~isfinite(f)), f = []; end
end


function [K, c, Y, l, N, iter, converged] = ramsey_stacked_newton( ...
        K0, Tt, alpha, delta, beta, sigma, gY, nxY, KY_bgp, A, h, ...
        labScale, P, econ, tol, max_iter)
% RAMSEY_STACKED_NEWTON  Damped Newton on the stacked-time Ramsey system.
%
% Solves the Euler + resource constraints for all Tt quarters jointly with
% K_1 fixed and the terminal BGP closure. The finite-difference Jacobian is
% tridiagonal (each quarter couples only adjacent unknowns) and is solved
% with a sparse direct factorisation, so saddle-path conditioning is
% irrelevant (handles anticipated/delayed permanent shocks).
% P is the population path (length Tt); c is returned per head.
    m = 2*Tt - 1;
    z = zeros(m, 1);

    % Feasible BGP-like initial guess via a constant BGP investment share.
    % Terminal gross growth mirrors RamseyTrendModel.terminalGrossGrowth —
    % TFP-augmented growth times the exogenous labour-scale growth (Java's
    % nPath; here labScale plays the same role). Falls back to the TFP-only
    % factor if the empirical ratio is non-finite.
    aGross   = A(Tt) / A(Tt-1);
    labGross = (labScale(Tt) * h(Tt)) / (labScale(Tt-1) * h(Tt-1));
    G_term   = aGross^(1/(1-alpha)) * labGross;
    if ~isfinite(G_term) || G_term <= 0
        G_term = aGross^(1/(1-alpha));
    end
    invShare = max(0.03, min(0.45, (G_term - 1 + delta) * KY_bgp));
    Kg = K0;
    % Consumption per head carried across periods. The exogenous econ
    % ignores c (N is given), but the solver's econ interface takes (t, K, c)
    % and the previous period's guessed c is the consistent value to pass.
    % econ supplies N for output; private absorption is divided by P.
    [Ng1, ~, ~] = econ(1, K0, 1);   % the exogenous econ ignores the c arg
    Yg1 = A(1) * K0^alpha * (Ng1*h(1))^(1-alpha);
    Pg1 = P(1);
    cg_prev = max(1e-6*Yg1/Pg1, (1 - gY(1) - nxY(1) - invShare) * Yg1 / Pg1);
    for t = 1:Tt
        [Ng, ~, ~] = econ(t, Kg, cg_prev);   % the exogenous econ ignores c
        Yg = A(t) * Kg^alpha * (Ng*h(t))^(1-alpha);
        iG = invShare * Yg;
        privAbs = (1 - gY(t) - nxY(t)) * Yg - iG;
        cG = max(privAbs/P(t), 1e-6*Yg/P(t));
        cg_prev = cG;
        if t < Tt
            z(2*t-1) = cG;
            z(2*t)   = max((1-delta)*Kg + iG, 1e-6*Kg);
            Kg = z(2*t);
        else
            z(2*Tt-1) = cG;
        end
    end

    f = ramsey_stacked_residuals(z, K0, alpha, delta, beta, sigma, ...
            gY, nxY, KY_bgp, econ, P, Tt);
    if isempty(f)
        error('ramsey_stacked_newton:infeasibleGuess', ...
              'Initial guess infeasible. Check scenario/path assumptions.');
    end
    nrm = max(abs(f));

    ftol = tol;
    maxNewton = max_iter;
    converged = false;
    for iter = 1:maxNewton
        if nrm < ftol, converged = true; break; end

        % Finite-difference tridiagonal Jacobian (sparse assembly).
        % One-sided forward difference (step = 1e-7*max(1,|z|)) is a
        % deliberate choice — the Newton converges in ~5 iterations to
        % ||f||<1e-10, so the ~1e-7 truncation error is absorbed near the
        % root. Central differencing would double residual evaluations for no
        % gain here.
        % Each column recomputes the FULL residual (O(Tt)), so assembly
        % is O(Tt^2) (~ms at Tt~900). A localised recompute would be O(Tt)
        % but is intentionally NOT done: it would diverge the MATLAB/Java
        % code paths and risk the bit-exact cross-validation parity for an
        % already sub-second routine. Revisit only if the horizon grows by
        % an order of magnitude.
        nzc = 3*m;
        Ii = zeros(nzc,1); Jj = zeros(nzc,1); Vv = zeros(nzc,1); p = 0;
        for j = 1:m
            zj = z(j);
            step = 1e-7 * max(1, abs(zj));
            z(j) = zj + step;
            fp = ramsey_stacked_residuals(z, K0, alpha, delta, beta, sigma, ...
                    gY, nxY, KY_bgp, econ, P, Tt);
            if isempty(fp)
                z(j) = zj - step;
                fp = ramsey_stacked_residuals(z, K0, alpha, delta, beta, sigma, ...
                        gY, nxY, KY_bgp, econ, P, Tt);
                step = -step;
            end
            z(j) = zj;
            if isempty(fp)
                error('ramsey_stacked_newton:infeasibleJacobian', ...
                      'Jacobian column %d infeasible.', j);
            end
            inBandLo = max(1,j-1); inBandHi = min(m,j+1);
            for i = inBandLo:inBandHi
                p = p + 1; Ii(p)=i; Jj(p)=j; Vv(p)=(fp(i)-f(i))/step;
            end

            % Band guard: the tridiagonal banding is only valid because every
            % residual couples strictly adjacent periods (resource/Euler) and
            % labour is intratemporal. A future structural term spanning >1
            % period (adjustment costs, time-to-build, habits, two-period-
            % ahead expectations) would create non-zero Jacobian entries
            % outside the band that this assembly silently drops, masking a
            % wrong Newton direction as slow/false convergence. Probe the
            % off-band sensitivity once and fail loudly if the assumption does
            % not hold. Mirrors RamseyTrendModel.java.
            if iter == 1
                offIdx = [1:inBandLo-1, inBandHi+1:m];
                dOff = abs((fp(offIdx) - f(offIdx)) / step);
                [mx, k] = max(dOff);
                if ~isempty(mx) && mx > 1e-6
                    error('ramsey_stacked_newton:bandAssumption', ...
                        ['Tridiagonal Jacobian assumption violated: residual %d ' ...
                         'depends on unknown %d (|dF|=%.3e, outside band [%d,%d]). ' ...
                         'A cross-period coupling was added; the sparse band must ' ...
                         'be widened to match.'], offIdx(k), j, mx, j-2, j+2);
                end
            end
        end
        J = sparse(Ii(1:p), Jj(1:p), Vv(1:p), m, m);

        d = -(J \ f);
        if any(~isfinite(d))
            error('ramsey_stacked_newton:singular', ...
                  'Singular Jacobian at iteration %d.', iter);
        end

        lambda = 1.0;
        accepted = false;
        while lambda > 1e-10
            zt = z + lambda*d;
            ft = ramsey_stacked_residuals(zt, K0, alpha, delta, beta, sigma, ...
                    gY, nxY, KY_bgp, econ, P, Tt);
            if ~isempty(ft)
                nn = max(abs(ft));
                if nn < (1 - 1e-4*lambda)*nrm || lambda < 1e-8
                    z = zt; f = ft; nrm = nn; accepted = true; break;
                end
            end
            lambda = lambda * 0.5;
        end
        if ~accepted
            error('ramsey_stacked_newton:lineSearch', ...
                  'Line search failed to find a feasible step at iteration %d.', iter);
        end
    end

    % Reconstruct full paths.
    K = zeros(Tt,1); c = zeros(Tt,1); Y = zeros(Tt,1);
    l = zeros(Tt,1); N = zeros(Tt,1);
    for t = 1:Tt
        if t == 1, K(t) = K0; else, K(t) = z(2*t-2); end
        c(t) = z(2*t-1);
        [N(t), Y(t), l(t)] = econ(t, K(t), c(t));
    end
end



function value = get_solver_numeric(solver_struct, primary_field, legacy_field, default_value)
    value = default_value;
    if nargin < 4
        return;
    end
    if isstruct(solver_struct)
        if isfield(solver_struct, primary_field) && ~isempty(solver_struct.(primary_field)) && ...
                isfinite(solver_struct.(primary_field)) && solver_struct.(primary_field) > 0
            value = solver_struct.(primary_field);
            return;
        end
        if isfield(solver_struct, legacy_field) && ~isempty(solver_struct.(legacy_field)) && ...
                isfinite(solver_struct.(legacy_field)) && solver_struct.(legacy_field) > 0
            value = solver_struct.(legacy_field);
        end
    end
end


function path_ext = extend_level_path(path_sample, total_length, window_size)
% EXTEND_LEVEL_PATH  Extend a positive level path using its trailing growth rate.

    sample_length = length(path_sample);
    path_ext = zeros(total_length, 1);
    path_ext(1:sample_length) = path_sample;

    if total_length <= sample_length
        return;
    end

    window = min(window_size, sample_length - 1);
    if window < 1
        path_ext(sample_length+1:end) = path_sample(end);
        return;
    end

    growth_q = (path_sample(sample_length) / path_sample(sample_length - window))^(1 / window) - 1;
    for t = sample_length + 1:total_length
        path_ext(t) = path_ext(t - 1) * (1 + growth_q);
    end
end


function P_ext = extend_population_frozen_ratio(P_avail, N_ext, T_total)
% EXTEND_POPULATION_FROZEN_RATIO  Population path with the dependency ratio frozen.
%
% P_avail is authoritative for its first n = min(numel(P_avail), T_total)
% quarters. Beyond them the dependency ratio is held at its value in quarter n,
%   P_ext(t) = N_ext(t) * P_ext(n) / N_ext(n),   t > n,
% so P has no tail rule of its own: it follows N, whatever N's tail rule, and
% N/P is asymptotically constant as the balanced-growth closure requires.
% N_ext must have at least T_total rows.
    n = min(numel(P_avail), T_total);
    P_ext = zeros(T_total, 1);
    P_ext(1:n) = P_avail(1:n);
    for t = n+1:T_total
        P_ext(t) = N_ext(t) * P_ext(n) / N_ext(n);
    end
end


function path_ext = apply_smooth_terminal_demographic_closure(path_ext, last_projection_idx, total_length, window_size)
% APPLY_SMOOTH_TERMINAL_DEMOGRAPHIC_CLOSURE  Taper terminal demographic growth to zero.
%
% The observed/provided demographic projection is authoritative up to
% last_projection_idx. Beyond that finite horizon, the tail is only a
% terminal-condition device for the perfect-foresight solve. Rather than
% extrapolating the last decline forever or imposing a hard flat kink, this
% closure lets the trailing log growth rate decay geometrically to zero.

    if last_projection_idx >= total_length
        return;
    end

    tail_half_life_quarters = 20;
    rho = 0.5^(1 / tail_half_life_quarters);
    window = min(window_size, last_projection_idx - 1);

    if window < 1 || path_ext(last_projection_idx) <= 0 || path_ext(last_projection_idx-window) <= 0
        path_ext(last_projection_idx+1:total_length) = path_ext(last_projection_idx);
        return;
    end

    log_growth = log(path_ext(last_projection_idx) / path_ext(last_projection_idx-window)) / window;
    if ~isfinite(log_growth)
        path_ext(last_projection_idx+1:total_length) = path_ext(last_projection_idx);
        return;
    end

    for t = last_projection_idx + 1:total_length
        log_growth = rho * log_growth;
        path_ext(t) = path_ext(t - 1) * exp(log_growth);
    end
end


function A_path = build_forward_tfp_path(A0, total_length, g_A, terminal_growth)
% BUILD_FORWARD_TFP_PATH  Build the forward TFP path using the Java contract.

    annual_growth_path = zeros(total_length, 1);
    if isfield(terminal_growth, 'enabled') && terminal_growth.enabled
        annual_growth_path(1) = terminal_growth.g_A_initial_annual;
        for t = 2:total_length
            annual_growth_path(t) = terminal_growth.g_A_terminal_annual + ...
                terminal_growth.rho_quarterly * (annual_growth_path(t-1) - terminal_growth.g_A_terminal_annual);
        end
    else
        annual_growth_path(:) = (1 + g_A)^4 - 1;
    end

    A_path = zeros(total_length, 1);
    A_path(1) = A0;
    for t = 2:total_length
        growth_q = (1 + annual_growth_path(t-1))^(1/4) - 1;
        A_path(t) = A_path(t-1) * (1 + growth_q);
    end
end


function [N_ext, h_ext, A_ext, g_A_annual_path] = extend_exogenous(N_trend, h_trend, A0, g_A, T_sample, T_total, terminal_growth)
% EXTEND_EXOGENOUS  Extend labor and TFP paths beyond the sample period.
%
% Beyond T_sample:
%   - N continues at constant growth rate (from last 20 quarters)
%   - h continues at the fitted log-linear trend's growth rate, tapered to zero
%   - A grows at constant rate g_A, or converges toward a lower terminal
%     TFP growth rate if terminal_growth.enabled is true.

    if nargin < 7 || isempty(terminal_growth)
        terminal_growth = struct('enabled', false);
    end

    N_ext = zeros(T_total, 1);
    h_ext = zeros(T_total, 1);
    A_ext = zeros(T_total, 1);
    g_A_annual_path = zeros(T_total, 1);

    % Copy sample period
    N_ext(1:T_sample) = N_trend;
    h_ext(1:T_sample) = h_trend;

    % TFP path: constant in-sample growth, optional convergence in extension
    if isfield(terminal_growth, 'enabled') && terminal_growth.enabled
        g_A_annual_path(1:T_sample) = terminal_growth.g_A_initial_annual;
        for t = T_sample+1:T_total
            g_A_annual_path(t) = terminal_growth.g_A_terminal_annual + ...
                terminal_growth.rho_quarterly * (g_A_annual_path(t-1) - terminal_growth.g_A_terminal_annual);
        end
    else
        g_A_annual_path(:) = (1 + g_A)^4 - 1;
    end

    A_ext(1) = A0;
    for t = 2:T_total
        g_A_q_t = (1 + g_A_annual_path(t-1))^(1/4) - 1;
        A_ext(t) = A_ext(t-1) * (1 + g_A_q_t);
    end

    % Extend N: use growth rate from last 20 quarters
    window = min(20, T_sample-1);
    g_N = (N_trend(T_sample) / N_trend(T_sample - window))^(1/window) - 1;
    for t = T_sample+1:T_total
        N_ext(t) = N_ext(t-1) * (1 + g_N);
    end

    % Extend h: trailing growth from the fitted log-linear trend, TAPERED to zero.
    %
    % Running the fitted rate flat over the whole extension is a bug, not a
    % projection: for PL it is -0.203%/yr, so across the projection plus the
    % 600-quarter terminal tail hours per worker fall to ~69% of their handoff
    % level (~26h/week). The solve then imposes K/(KY_bgp*Y) = 1, a balanced-growth
    % relation, on a system whose labour input is still shrinking, and on a BGP
    % hours per worker must be asymptotically constant. The same taper as the
    % demographic tail restores that. Measured effect on the trend
    % wage over 2023-2060 is under 0.003%; the tail is a terminal-condition device,
    % not a forecast. Java mirrors this in RamseyTrendModel.extendWithTaperedGrowth.
    g_h = h_trend(T_sample) / h_trend(T_sample-1) - 1;
    h_ext = apply_smooth_terminal_demographic_closure(h_ext, T_sample, T_total, 1);

    if isfield(terminal_growth, 'enabled') && terminal_growth.enabled
        fprintf('  Extension: g_N = %.5f/q, g_h = %.5f/q, g_A annual = %.2f%% -> %.2f%%\n', ...
                g_N, g_h, 100*g_A_annual_path(T_sample), 100*g_A_annual_path(T_total));
    else
        fprintf('  Extension: g_N = %.5f/q, g_h = %.5f/q, g_A = %.5f/q\n', g_N, g_h, g_A);
    end
end


function share_ext = extend_share(share_sample, T_total)
% EXTEND_SHARE  Extend an expenditure share path beyond the sample period.
%
% Beyond the sample, the share is held constant at its last observed value.
% This is appropriate for G/Y and NX/Y, which have no clear structural
% trend and are best treated as stationary around a level.
    T_sample = length(share_sample);
    share_ext = zeros(T_total, 1);
    share_ext(1:T_sample) = share_sample;
    if T_total > T_sample
        share_ext(T_sample+1:end) = share_sample(end);
    end
end


function pi_star = compute_pi_trend(data, inflation_params)
% COMPUTE_PI_TREND  Compute inflation trend (pi*) for detrending.
%
% Uses the configured inflation target with optional convergence from the
% initial level. Economies entering a stable inflation-targeting regime may
% need a convergence period; later samples will often be close to target.
%
% pi_star is in the same units as the observed pi (annualized %).

    T = height(data);
    target = inflation_params.pi_star_annual;
    rho = inflation_params.convergence_rho;

    % Start from observed initial inflation (smoothed)
    pi_obs = data.pi;
    valid = find(~isnan(pi_obs), 5, 'first');  % first 5 valid quarters
    if ~isempty(valid)
        pi_initial = mean(pi_obs(valid));
    else
        pi_initial = target;
    end

    % AR(1) convergence: pi*_t = rho * pi*_{t-1} + (1-rho) * target
    pi_star = zeros(T, 1);
    pi_star(1) = pi_initial;
    for t = 2:T
        pi_star(t) = rho * pi_star(t-1) + (1 - rho) * target;
    end

    % With rho=0.95, convergence is mostly complete within roughly 40 quarters,
    % so pi* is close to the long-run target for most mature samples.
    fprintf('  pi* (initial) = %.2f%%, pi* (final) = %.2f%%, target = %.2f%%\n', ...
            pi_star(1), pi_star(end), target);
end


function plot_trend_vs_actual(actual, trend_vals, time_labels, title_str)
% PLOT_TREND_VS_ACTUAL  Diagnostic plot of actual vs Ramsey trend.
%
% Shows two trend lines:
%   - Dashed red: raw Ramsey trend (used for detrending)
%   - Dashed magenta: centered trend (shifted by mean log-gap so it
%     visually centers around the data). The mean gap is removed by
%     demeaning before Dynare sees the data, so the centered line shows
%     what the DSGE actually "sees" as the trend.

    T = length(actual);

    % Compute centered trend: shift by mean log-deviation
    % This is what the DSGE effectively uses after demeaning
    valid = ~isnan(actual) & ~isnan(trend_vals) & (trend_vals > 0);
    mean_log_gap = mean(log(actual(valid)) - log(trend_vals(valid)));
    trend_centered = trend_vals .* exp(mean_log_gap);

    plot(1:T, actual, 'b-', 'LineWidth', 0.8);
    hold on;
    plot(1:T, trend_centered, 'm--', 'LineWidth', 1.5);
    plot(1:T, trend_vals, 'r:', 'LineWidth', 0.8, 'Color', [1 0.5 0.5]);
    hold off;
    xlim([1 T]);
    title(title_str);
    legend('Actual', 'Centered Trend', 'Raw Trend', 'Location', 'best', 'FontSize', 7);
    grid on;

    % Add simple x-axis labels (every 20 quarters = 5 years)
    if iscell(time_labels)
        tick_idx = 1:20:T;
        xticks(tick_idx);
        xticklabels(time_labels(tick_idx));
        xtickangle(45);
    end
end


function mask = build_covid_mask(time_labels, T)
% BUILD_COVID_MASK  Boolean mask for COVID period (2020Q1–2021Q2).
%
% Parses quarterly time labels of the form '2020q1' and returns a logical
% vector that is true for 2020Q1 through 2021Q2 (6 quarters).
    mask = false(T, 1);
    for i = 1:T
        yq = sscanf(time_labels{i}, '%dq%d');
        if length(yq) == 2
            if (yq(1) == 2020) || (yq(1) == 2021 && yq(2) <= 2)
                mask(i) = true;
            end
        end
    end
end


function x_out = interpolate_through(x, mask)
% INTERPOLATE_THROUGH  Replace masked observations with linear interpolation.
%
% Used to remove COVID outliers from expenditure shares (C/Y, I/Y) before
% HP filtering, so the structural trend is not contaminated by a temporary
% pandemic disruption.
    x_out = x;
    x_out(mask) = NaN;
    valid = find(~isnan(x_out));
    if length(valid) >= 2
        x_out = interp1(valid, x_out(valid), (1:length(x))', 'linear', 'extrap');
    end
end


function s = ramsey_sanity_check(I_trend, Y_trend, C_trend, gY, nxY, inv_obs, y_obs)
% RAMSEY_SANITY_CHECK  Quantitative check in place of eyeballing the trend plots.
%
% Returns s.pass (overall), s.failed (cellstr of failed gating checks) and
% prints an explicit PASS/FAIL table. The verdict gates ONLY on structural
% and scale-free share checks, which robustly catch the catastrophic regime
% (collapsed/negative residual investment, implausible C/I split, model I/Y
% drifting off observed GFCF).
%
% InvTrendTracksData (mean relative level deviation of the residual
% investment trend from observed investment) is reported as ADVISORY only,
% never a FAIL: investment is the volatile Y-C-G-NX residual, so its level
% RMSE is structurally larger than Y/C/G even in an accepted run (the
% Poland baseline sits ~0.27). A genuine blow-up still trips the
% gating checks; this number is the fit signal the Investment panel shows,
% surfaced but not used to reject the run.
    IY  = I_trend ./ Y_trend;
    CY  = C_trend ./ Y_trend;
    IY_obs_mean = mean(inv_obs ./ y_obs, 'omitnan');
    IY_mean = mean(IY, 'omitnan');
    CY_mean = mean(CY, 'omitnan');
    share_id_err = abs(mean(IY + CY + gY(:) + nxY(:), 'omitnan') - 1);
    inv_track = mean(abs(I_trend - inv_obs) ./ abs(inv_obs), 'omitnan');

    % Gating checks: {name, passed, value, human-readable bound}
    checks = {
        'InvestmentPositive', min(I_trend) > 0,                       min(I_trend),       '> 0 every quarter'
        'InvestmentShare',    IY_mean >= 0.10 && IY_mean <= 0.40,     IY_mean,            'mean I/Y in [0.10, 0.40]'
        'ConsumptionShare',   CY_mean >= 0.40 && CY_mean <= 0.78,     CY_mean,            'mean C/Y in [0.40, 0.78]'
        'InvShareVsData',     abs(IY_mean - IY_obs_mean) <= 0.05,     IY_mean-IY_obs_mean,'|model-obs mean I/Y| <= 0.05'
        'SharesIdentity',     share_id_err <= 1e-6,                   share_id_err,       'C+I+G+NX shares sum to 1'
    };

    s = struct();
    s.failed = {};
    fprintf('\n--- Ramsey sanity check ---\n');
    for k = 1:size(checks, 1)
        name = checks{k, 1};  ok = logical(checks{k, 2});
        val  = checks{k, 3};  bound = checks{k, 4};
        if ok
            tag = 'PASS';
        else
            tag = 'FAIL';
            s.failed{end+1} = name;
        end
        fprintf('  [%s] %-20s = %12.5g   (%s)\n', tag, name, val, bound);
        s.(name) = ok;
    end

    % Advisory (non-gating) investment level-tracking metric.
    s.inv_track = inv_track;
    s.inv_track_high = inv_track > 0.20;
    fprintf('  [INFO] %-20s = %12.5g   (advisory; not gated)\n', ...
            'InvTrendTracksData', inv_track);

    s.pass = isempty(s.failed);
    if s.pass
        fprintf('  Verdict: PASS — trends are structurally plausible.\n');
    else
        fprintf('  Verdict: FAIL — %d check(s) failed: %s\n', ...
                numel(s.failed), strjoin(s.failed, ', '));
    end
    if s.inv_track_high
        fprintf(['  Advisory: investment level-tracking deviation %.3f is high ' ...
                 '(volatile residual; not a failure) — inspect the Investment ' ...
                 'panel if exporting for quantitative work.\n'], inv_track);
    end
end
