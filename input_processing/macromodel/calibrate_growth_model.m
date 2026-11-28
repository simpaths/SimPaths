function calibrate_growth_model(country)
% CALIBRATE_GROWTH_MODEL  Calibrate Ramsey growth model parameters from data.
%
% Reads national accounts data (from Stata-exported levels CSV) and external
% sources (PWT, AMECO NAWRU) to fill in the base_year_values of the
% growth_params_{country}.json file in the bundle folder.
%
% What this does:
%   1. Loads level data exported by make_datasets.do
%   2. Computes initial capital stock K_0 from investment using perpetual
%      inventory method (or user-supplied value from PWT)
%   3. Computes implied TFP level A_0 from production function
%   4. Prepares NAWRU, demographic labor force and demographic population
%      (demographic_P_{country}.csv) trend files
%   5. Writes updated JSON with calibrated base_year_values
%
% Symbols: N = persons in employment (national concept, thousands), h = hours
% per person employed per quarter, P = resident population (thousands, census
% breaks spliced in the data stage), L = labour force, K = capital, A = TFP.
%
% The structural parameters (alpha, delta, beta, sigma, g_A) are set
% manually in the JSON based on literature values. This script only
% calibrates the INITIAL CONDITIONS and EXOGENOUS TREND PATHS.
%
% NOTE: User-maintained structural Ramsey parameters live in
% country_params/{Country}_growth_params.json. This function reads that
% source template, calibrates the derived growth-model fields, and writes
% the calibrated runtime copy to the bundle folder, ../../input/{CC}/MacroModel/.
%
% IMPORTANT: The growth model's delta (~5% annual) differs from the DSGE's
% delta_k (~9.6% annual = 0.025 quarterly). The growth model matches total
% GFCF (Eurostat P51G, including structures), so it needs the economy-wide
% composite depreciation rate. The DSGE's delta_k governs cyclical
% investment dynamics and implicitly represents fast-depreciating equipment.
%
% Usage:
%   >> calibrate_growth_model('Poland')
%
% Prerequisites:
%   - data_for_dynare_levels_{COUNTRY}.csv exists (from make_datasets.do)
%   - country_params/{Country}_growth_params.json exists (template with structural params)
%   - NAWRU data available (manual download from AMECO or OECD)
%
% Author: WELLSIM Project
% Date: February 2026
% =========================================================================
    arguments
        country (1,:) char = 'UK'
    end
    % Resolve the model's code folders (internal/, diagnostics/, ...).
    % Idempotent; see setup_path.m for why MATLAB cannot do this itself.
    setup_path();

    country_config = get_country_config(country);
    country = country_config.name;

    % Start output log (like Stata's "log using")
    log_file = artifact_path('logs', sprintf('calibrate_growth_model_%s.log', lower(country)), 'create', true);
    if exist(log_file, 'file'), delete(log_file); end
    diary(log_file);
    fprintf('Logging output to %s\n', log_file);

    fprintf('\n');
    box_width = 62;
    fprintf('╔%s╗\n', repmat('═', 1, box_width));
    print_box_line(sprintf('CALIBRATE RAMSEY GROWTH MODEL: %s', upper(country)), box_width, 'center');
    fprintf('╚%s╝\n', repmat('═', 1, box_width));
    fprintf('\n');

    %% 1. Load source JSON template and prepare export location
    json_paths = get_growth_params_paths(country);
    source_json_file = json_paths.source;
    export_json_file = json_paths.export;

    if ~exist(source_json_file, 'file')
        error(['Parameter file not found: %s\n' ...
               'Create template with structural parameters first.'], source_json_file);
    end

    if ~exist(json_paths.export_dir, 'dir')
        mkdir(json_paths.export_dir);
    end

    params = jsondecode(fileread(source_json_file));
    p = params.parameters;

    alpha = p.alpha_K;
    delta_a = p.delta_annual;
    g_A_a = p.tfp_growth_annual;
    base_year = params.base_year;

    fprintf('Structural parameters (from JSON):\n');
    fprintf('  alpha_K = %.2f\n', alpha);
    fprintf('  delta   = %.2f (annual)\n', delta_a);
    fprintf('  beta    = %.2f (annual)\n', p.beta_annual);
    fprintf('  sigma   = %.2f\n', p.sigma);
    fprintf('  g_A     = %.4f (annual)\n', g_A_a);
    fprintf('  Base year: %d\n\n', base_year);

    %% 2. Load level data
    level_file = artifact_path('data', sprintf('data_for_dynare_levels_%s.csv', country));
    if ~exist(level_file, 'file')
        error(['Level data not found: %s\n' ...
               'Run make_datasets.do first (with level export enabled).'], level_file);
    end

    data = readtable(level_file);
    if ~ismember('pop_level', data.Properties.VariableNames)
        error('calibrate_growth_model:noPopulation', ...
              '%s has no pop_level column. Re-run stata/make_datasets.do for %s; the planner''s consumers are the resident population.', ...
              level_file, country);
    end
    % The export carries a full quarterly calendar, so the last rows have no
    % observables yet. Averaging a "last year" window over that tail yields NaN
    % g_Y and NaN K_0, which then makes the Ramsey solve infeasible.
    [data, n_ragged] = trim_trailing_missing(data);
    T = height(data);
    if n_ragged > 0
        fprintf('Trimmed %d trailing quarter(s) with no production data from %s\n', ...
                n_ragged, level_file);
    end
    fprintf('Loaded %d quarters of level data from %s (through %s)\n', ...
            T, level_file, char(string(data.time(end))));

    % Find base year row (first quarter of base year)
    % Time column is string like "2000q1"
    base_q_str = sprintf('%dq1', base_year);
    base_idx = find(strcmp(data.time, base_q_str));
    if isempty(base_idx)
        error('Base year %dQ1 not found in data. Available range: %s to %s', ...
              base_year, data.time{1}, data.time{end});
    end

    %% 3. Extract base-year values
    % All national accounts variables (y, c, inv) are in million EUR,
    % chain-linked volumes (2015 reference), from Eurostat namq_10_gdp.
    % Persons in employment and resident population in thousands, hours per
    % person employed in hours/quarter.

    % Annualize GDP: sum of 4 quarters. The base year must have all four, or the
    % "annual" aggregates below are quietly a partial year and every level
    % calibrated from them (Y_0, A_0, K/Y) is scaled wrong.
    if base_idx + 3 > T
        error(['Base year %d has only %d quarter(s) of data in %s (needs 4). ' ...
               'Either move base_year earlier or extend the sample.'], ...
              base_year, T - base_idx + 1, level_file);
    end
    base_rows = base_idx : base_idx + 3;
    Y_annual = sum(data.y_level(base_rows));           % million EUR

    % Persons in employment: average over 4 quarters (thousands)
    N_0 = mean(data.n_level(base_rows));               % thousands

    % Hours per person employed per quarter: average over 4 quarters
    h_0 = mean(data.h_level(base_rows));               % hours per quarter

    % Resident population: average over 4 quarters (thousands)
    P_0 = mean(data.pop_level(base_rows));             % thousands

    % Labor force: average over 4 quarters (thousands)
    L_0 = mean(data.L_level(base_rows));               % thousands

    fprintf('\nBase year values (%d):\n', base_year);
    fprintf('  Y (annual GDP)  = %.1f million EUR\n', Y_annual);
    fprintf('  N (persons in employment) = %.1f thousand\n', N_0);
    fprintf('  L (labor force) = %.1f thousand\n', L_0);
    fprintf('  h (hours per person employed per quarter) = %.1f hours\n', h_0);
    fprintf('  P (population)   = %.1f thousand\n', P_0);

    %% 4. Compute initial capital stock K_0 via perpetual inventory
    %
    % PIM: K_0 = I_0 / (delta + g_Y)
    % where g_Y is average GDP growth rate and I_0 is annual investment
    %
    % Alternative: Use PWT 10.0 capital stock directly for cross-validation.

    I_annual = sum(data.inv_level(base_rows));  % million EUR (gross fixed capital formation)
    C_annual = sum(data.c_level(base_rows));     % million EUR (household consumption)

    % Government consumption and net exports (open-economy extension)
    if ismember('g_level', data.Properties.VariableNames)
        G_annual = sum(data.g_level(base_rows));     % million EUR (government consumption)
        GY = G_annual / Y_annual;
    else
        G_annual = 0;
        GY = 0;
        fprintf('  ⚠ g_level not found in data — assuming G/Y = 0 (closed economy)\n');
    end

    if ismember('nx_level', data.Properties.VariableNames)
        NX_annual = sum(data.nx_level(base_rows));   % million EUR (net exports)
        NXY = NX_annual / Y_annual;
    else
        NX_annual = 0;
        NXY = 0;
        fprintf('  ⚠ nx_level not found in data — assuming NX/Y = 0 (closed economy)\n');
    end

    % Unit consistency checks (all in million EUR from Eurostat)
    CY = C_annual / Y_annual;
    IY_raw = I_annual / Y_annual;
    fprintf('\n  Expenditure shares (base year check):\n');
    fprintf('    C/Y  = %.3f (sanity check: private absorption share)\n', CY);
    fprintf('    I/Y  = %.3f (sanity check: investment share)\n', IY_raw);
    fprintf('    G/Y  = %.3f (sanity check: government share)\n', GY);
    fprintf('    NX/Y = %.3f (sanity check: net export share)\n', NXY);
    fprintf('    Sum  = %.3f (should ≈ 1.0; gap due to chain-linking)\n', CY + IY_raw + GY + NXY);
    if CY < 0.3 || CY > 0.8
        error('C/Y = %.3f is out of range. Likely a unit mismatch between c_level and y_level.', CY);
    end
    if IY_raw < 0.05 || IY_raw > 0.5
        error('I/Y = %.3f is out of range. Likely a unit mismatch between inv_level and y_level.', IY_raw);
    end

    % Average real (chain-linked) GDP growth over the sample.
    % Y_first and Y_last are 4-quarter sums, so their midpoints are (T-4)/4 years
    % apart, not T/4. Using T/4 spreads the same total growth over a longer span
    % and understates g_Y (~1.3% relatively at T=83).
    n_years_between_windows = (T - 4) / 4;
    if n_years_between_windows >= 5
        Y_first = sum(data.y_level(1:4));
        Y_last  = sum(data.y_level(T-3:T));
        g_Y = (Y_last / Y_first)^(1 / n_years_between_windows) - 1;
    else
        g_Y = 0.02;  % fallback: 2% real growth
    end

    K_0_pim = I_annual / (delta_a + g_Y);

    fprintf('\nCapital stock calibration (perpetual inventory):\n');
    fprintf('  I (annual invest) = %.1f million EUR\n', I_annual);
    fprintf('  g_Y (avg growth)  = %.3f\n', g_Y);
    fprintf('  K_0 (PIM)         = %.1f million EUR\n', K_0_pim);
    fprintf('  K/Y ratio         = %.2f\n', K_0_pim / Y_annual);

    % Sanity check: K/Y should be 2.0-5.0 for advanced / transition economies
    KY = K_0_pim / Y_annual;
    if KY < 1.5 || KY > 5.0
        warning('K/Y ratio = %.2f looks unusual. Expected 2.0-5.0. Check data units or delta.', KY);
    end

    % Check for user-supplied K_0 override (e.g., from PWT capital stock)
    if has_positive_override(p)
        K_0 = p.K_override;
        fprintf('\n  *** K_0 OVERRIDE active (from JSON K_override field) ***\n');
        fprintf('      K_0 (override) = %.1f million (K/Y = %.2f)\n', K_0, K_0 / Y_annual);
        fprintf('      K_0 (PIM)      = %.1f million (K/Y = %.2f)\n', K_0_pim, K_0_pim / Y_annual);
        fprintf('      Override/PIM   = %.2f\n', K_0 / K_0_pim);
    else
        K_0 = K_0_pim;
    end

    % Cross-validation: compare PIM K/Y against the growth-corrected BGP K/Y.
    % With positive trend growth, the modified golden rule implies
    %   1 + r* = (1 + g_c)^sigma / beta,  g_c = g_A / (1-alpha).
    % The implied K/Y should broadly agree with the PIM value if the economy
    % is near its balanced growth path.
    g_A_bgp_a = g_A_a;
    if isfield(params, 'terminal_growth') && isfield(params.terminal_growth, 'enabled') && params.terminal_growth.enabled
        g_A_bgp_a = params.terminal_growth.g_A_terminal_annual;
        fprintf('    Using terminal g_A = %.2f%% annual for BGP consistency check\n', 100*g_A_bgp_a);
    end

    beta_q_check = p.beta_annual^(1/4);
    delta_q_check = 1 - (1 - delta_a)^(1/4);
    g_A_q_check = (1 + g_A_bgp_a)^(1/4) - 1;
    g_c_q_check = g_A_q_check / (1 - alpha);
    r_star_bgp_check = (1 + g_c_q_check)^p.sigma / beta_q_check - 1;
    KY_bgp_check = alpha / (r_star_bgp_check + delta_q_check) / 4;  % annual
    fprintf('\n  PIM vs BGP consistency check:\n');
    fprintf('    K/Y (PIM)    = %.2f\n', KY);
    fprintf('    K/Y (BGP)    = %.2f\n', KY_bgp_check);
    fprintf('    Ratio PIM/BGP= %.2f (1.0 = on balanced growth path)\n', KY / KY_bgp_check);
    if has_positive_override(p)
        KY_override = K_0 / Y_annual;
        fprintf('    K/Y (override)= %.2f\n', KY_override);
        fprintf('    Ratio Override/BGP = %.2f (operative calibration diagnostic)\n', KY_override / KY_bgp_check);
        if abs(KY_override / KY_bgp_check - 1) > 0.20
            warning(['Operative capital stock (K_override) and BGP K/Y differ by >20%%. ' ...
                     'Economy may be far from BGP, or terminal growth / delta / beta are miscalibrated.']);
        elseif abs(KY / KY_bgp_check - 1) > 0.20
            fprintf(['    Note: raw PIM K/Y differs from BGP by >20%%, but the active K_override is close to BGP.\n ' ...
                     '          This points to a low PIM capital estimate rather than a problem in the operative Ramsey calibration.\n']);
        end
    elseif abs(KY / KY_bgp_check - 1) > 0.20
        warning('PIM and BGP K/Y differ by >20%%. Economy far from BGP, or delta/beta are miscalibrated.');
    end

    %% 5. Compute implied TFP level A_0
    %
    % Y = A * K^alpha * (N*h)^(1-alpha)
    % => A = Y / (K^alpha * (N*h)^(1-alpha))
    %
    % Units: We work in quarterly terms for the Ramsey model.
    % Y_q = Y_annual / 4 (quarterly GDP in million EUR)
    % N (persons in employment) in thousands, h (hours per person employed) in
    % hours per quarter
    % Effective labor = N * h (thousands × hours = thousand-hours)

    Y_q = Y_annual / 4;  % quarterly GDP
    eff_labor_0 = N_0 * h_0;  % thousand × hours = thousand-hours per quarter

    A_0 = Y_q / (K_0^alpha * eff_labor_0^(1-alpha));

    fprintf('\nImplied TFP:\n');
    fprintf('  Effective labor (N*h) = %.1f thousand-hours\n', eff_labor_0);
    fprintf('  A_0 (implied)         = %.6f\n', A_0);

    % Cross-check: recompute Y from calibrated values
    Y_check = A_0 * K_0^alpha * eff_labor_0^(1-alpha);
    fprintf('  Y check (should = %.1f): %.1f\n', Y_q, Y_check);
    assert(abs(Y_check - Y_q) / Y_q < 1e-6, 'Production function identity check failed!');

    % Labour input is exogenous: N is taken as given.
    fprintf('\n  Labor supply mode: exogenous (N taken as given)\n');

    %% 6. Compute balanced growth path values for cross-validation
    %
    % BGP capital-output ratio under the modified golden rule:
    %   1 + r* = (1 + g_c)^sigma / beta,  g_c = g_A / (1-alpha)
    %   K/Y = alpha / (r* + delta)
    % using quarterly beta, delta, and trend growth.

        g_A_bgp_a = g_A_a;
        if isfield(params, 'terminal_growth') && isfield(params.terminal_growth, 'enabled') && params.terminal_growth.enabled
            g_A_bgp_a = params.terminal_growth.g_A_terminal_annual;
        end

        beta_q = p.beta_annual^(1/4);
        delta_q = 1 - (1 - delta_a)^(1/4);
        g_A_q = (1 + g_A_bgp_a)^(1/4) - 1;
        g_c_q = g_A_q / (1 - alpha);

    r_star_bgp = (1 + g_c_q)^p.sigma / beta_q - 1;  % quarterly
    KY_bgp = alpha / (r_star_bgp + delta_q);
    r_star_annual = (1 + r_star_bgp)^4 - 1;

    fprintf('\nBalanced growth path:\n');
    fprintf('  beta (quarterly)  = %.6f\n', beta_q);
    fprintf('  delta (quarterly) = %.6f\n', delta_q);
        fprintf('  g_A (annual, BGP) = %.4f (%.2f%%)\n', g_A_bgp_a, g_A_bgp_a*100);
    if isfield(params, 'terminal_growth') && isfield(params.terminal_growth, 'enabled') && params.terminal_growth.enabled
        fprintf('  rho_g (quarterly) = %.4f\n', params.terminal_growth.rho_quarterly);
    end
    fprintf('  g_c (quarterly)   = %.6f\n', g_c_q);
    fprintf('  K/Y (BGP)         = %.2f quarters\n', KY_bgp);
    fprintf('  K/Y (BGP annual)  = %.2f\n', KY_bgp / 4);
    fprintf('  r* (quarterly)    = %.4f\n', r_star_bgp);
    fprintf('  r* (annual)       = %.4f (%.2f%%)\n', r_star_annual, r_star_annual*100);

    %% 7. Prepare labor trend files
    %
    % These are PLACEHOLDERS. In production, you'd load from AMECO/Eurostat.
    % For now, create smooth trends from the observed data.

    fprintf('\n--- Labor trend files ---\n');

    % 7a. NAWRU: placeholder from HP-filtered unemployment rate
    % In production, download from AMECO (Variable: NAWRU)
    nawru_file = artifact_path('data', sprintf('nawru_%s.csv', lower(country)), 'create', true);
    if exist(nawru_file, 'file')
        old_nawru = readtable(nawru_file);
        if height(old_nawru) ~= T
            fprintf('  ⚠ Stale %s (%d rows vs %d data rows) — regenerating...\n', nawru_file, height(old_nawru), T);
            delete(nawru_file);
        end
    end
    if ~exist(nawru_file, 'file')
        fprintf('  Creating placeholder NAWRU from HP-filtered unemployment rate...\n');

        % Compute ILO unemployment rate from data
        if ismember('u_level', data.Properties.VariableNames)
            ur = data.u_level / 100;  % convert % to fraction
        else
            % Fallback: use constant NAWRU
            ur = 0.07 * ones(T, 1);  % 7% placeholder
            warning('No unemployment data found. Using constant NAWRU = 7%%.');
        end

        % Smooth with very high HP lambda to get structural NAWRU
        nawru = hp_filter_trend(ur, 160000);  % 100x standard lambda

        nawru_table = table(data.time, nawru, 'VariableNames', {'time', 'nawru'});
        writetable(nawru_table, nawru_file);
        fprintf('  Wrote %s (%d observations)\n', nawru_file, T);
        fprintf('  ⚠ PLACEHOLDER — replace with AMECO NAWRU data!\n');
    else
        fprintf('  NAWRU file exists: %s\n', nawru_file);
    end

    % 7b. Demographic labor force: HP-filtered placeholder if missing.
    % A genuine projection (Eurostat/IAB, aligned with the SimPaths population
    % projection) may extend beyond the historical sample, so accept files that
    % cover at least the in-sample period.
    demo_L_file = artifact_path('data', sprintf('demographic_L_%s.csv', lower(country)), 'create', true);
    if exist(demo_L_file, 'file')
        old_demo = readtable(demo_L_file);
        if height(old_demo) < T
            fprintf('  ⚠ Stale %s (%d rows < %d in-sample rows) — regenerating...\n', demo_L_file, height(old_demo), T);
            delete(demo_L_file);
        end
    end
    if ~exist(demo_L_file, 'file')
        fprintf('  Creating placeholder demographic L trend...\n');

        L_trend = hp_filter_trend(log(data.L_level), 160000);
        L_trend = exp(L_trend);  % back to levels

        demo_table = table(data.time, L_trend, 'VariableNames', {'time', 'L_demo'});
        writetable(demo_table, demo_L_file);
        fprintf('  Wrote %s (%d observations)\n', demo_L_file, T);
        fprintf('  ⚠ PLACEHOLDER — replace with Eurostat/IAB population projections!\n');
    else
        fprintf('  Demographic L file exists: %s\n', demo_L_file);
    end

    % 7c. Demographic population: the in-sample treatment of the resident
    % population (not a placeholder). The census-break splice happened in the
    % data stage, so the HP trend of log pop_level is smooth across the breaks.
    % A projection beyond the sample (SimPaths align_popProjections.xlsx, all
    % ages) may extend the file, so accept files that cover at least the
    % in-sample period.
    demo_P_file = artifact_path('data', sprintf('demographic_P_%s.csv', lower(country)), 'create', true);
    if exist(demo_P_file, 'file')
        old_demo_P = readtable(demo_P_file);
        if height(old_demo_P) < T
            fprintf('  ⚠ Stale %s (%d rows < %d in-sample rows) — regenerating...\n', demo_P_file, height(old_demo_P), T);
            delete(demo_P_file);
        end
    end
    if ~exist(demo_P_file, 'file')
        fprintf('  Creating in-sample demographic P trend...\n');

        P_trend = exp(hp_filter_trend(log(data.pop_level), 160000));

        demo_P_table = table(data.time, P_trend, 'VariableNames', {'time', 'P_demo'});
        writetable(demo_P_table, demo_P_file);
        fprintf('  Wrote %s (%d observations)\n', demo_P_file, T);
    else
        fprintf('  Demographic P file exists: %s\n', demo_P_file);
    end

    %% 8. Update JSON with calibrated values
    params.base_year_values.K = K_0;
    params.base_year_values.Y = Y_annual;
    params.base_year_values.N = N_0;
    params.base_year_values.h = h_0;
    params.base_year_values.A_implied = A_0;
    params.base_year_values.L = L_0;
    params.base_year_values.P = P_0;

    % Government and net export shares (for open-economy Ramsey model)
    params.base_year_values.G = G_annual;
    params.base_year_values.NX = NX_annual;
    params.expenditure_shares = struct();
    params.expenditure_shares.CY_base = CY;
    params.expenditure_shares.IY_base = IY_raw;
    params.expenditure_shares.GY_base = GY;
    params.expenditure_shares.NXY_base = NXY;

    % Also store derived quarterly values for convenience
    params.derived = struct();
    params.derived.beta_quarterly = beta_q;
    params.derived.delta_quarterly = delta_q;
    params.derived.tfp_growth_annual_terminal = g_A_bgp_a;
    params.derived.KY_bgp_quarterly = KY_bgp;
    params.derived.r_star_bgp_annual = r_star_annual;
    params.derived.Y_quarterly_base = Y_q;

    % Normalize solver metadata: the written JSON names only the stacked-Newton
    % solver and its fields. A template's values are read under those names or
    % under the forward-shooting ones (terminal_extension_quarters, tolerance,
    % max_iterations).
    solver_terminal_tail = 400;
    solver_tolerance = 1e-10;
    solver_max_iterations = 200;
    if isfield(params, 'solver')
        if isfield(params.solver, 'terminal_tail_quarters') && ...
                ~isempty(params.solver.terminal_tail_quarters) && ...
                params.solver.terminal_tail_quarters > 0
            solver_terminal_tail = params.solver.terminal_tail_quarters;
        elseif isfield(params.solver, 'terminal_extension_quarters') && ...
                ~isempty(params.solver.terminal_extension_quarters) && ...
                params.solver.terminal_extension_quarters > 0
            solver_terminal_tail = params.solver.terminal_extension_quarters;
        end
        if isfield(params.solver, 'newton_tolerance') && ...
                ~isempty(params.solver.newton_tolerance) && ...
                params.solver.newton_tolerance > 0
            solver_tolerance = params.solver.newton_tolerance;
        elseif isfield(params.solver, 'tolerance') && ...
                ~isempty(params.solver.tolerance) && ...
                params.solver.tolerance > 0
            solver_tolerance = params.solver.tolerance;
        end
        if isfield(params.solver, 'max_newton_iterations') && ...
                ~isempty(params.solver.max_newton_iterations) && ...
                params.solver.max_newton_iterations > 0
            solver_max_iterations = params.solver.max_newton_iterations;
        elseif isfield(params.solver, 'max_iterations') && ...
                ~isempty(params.solver.max_iterations) && ...
                params.solver.max_iterations > 0
            solver_max_iterations = params.solver.max_iterations;
        end
    end
    params.solver = struct(...
        'method', 'stacked_newton_lbj', ...
        'terminal_tail_quarters', solver_terminal_tail, ...
        'newton_tolerance', solver_tolerance, ...
        'max_newton_iterations', solver_max_iterations, ...
        'finite_difference_step_scale', 1e-7, ...
        'jacobian_half_bandwidth', 1, ...
        'line_search', 'armijo_backtracking', ...
        'x_comment', sprintf(['Stacked-time Newton (LBJ) perfect-foresight solver ', ...
            'with terminal K/Y closure. The solve runs over the observed sample plus ', ...
            'a terminal tail of %d quarters (~%d years); Newton convergence requires ', ...
            '||F||_inf <= %.1e within %d iterations.'], ...
            solver_terminal_tail, round(solver_terminal_tail / 4), ...
            solver_tolerance, solver_max_iterations));

    % Write updated JSON (safe pretty-print if supported)
    try
        json_text = jsonencode(params, 'PrettyPrint', true);
    catch
        json_text = jsonencode(params);
    end

    % The JSON lives in the bundle SimPaths reads, so rewriting it mixes this
    % calibration with the last export's files. The marker stays until the next
    % export's verify_export_bundle passes.
    mark_bundle_unverified(artifact_path('bundle', '', 'country', country, 'create', true), country, ...
        {sprintf(['calibrate_growth_model rewrote growth_params_%s.json; no export has ' ...
                  'verified the bundle since'], country)});

    % Atomic + verified: Java string-scans this file, so a truncated tail would
    % read as 0.0 parameters rather than raising anything.
    write_text_atomic(export_json_file, json_text, 'verify_json', true);

    fprintf('\n✓ Wrote calibrated growth parameters to %s\n', export_json_file);

    %% 8b. Export documentation summary (rendered by documentation_tables/refresh_documentation_tables)
    try
        ramsey_summary = build_ramsey_documentation_summary(params, country);
        summary_file = artifact_path('output', fullfile('documentation_data', 'ramsey', ...
                                     sprintf('ramsey_summary_%s.json', country)));
        validate_documentation_summary(ramsey_summary, 'ramsey');
        write_json_summary(ramsey_summary, summary_file, ...
            'country', country, ...
            'source_function', 'calibrate_growth_model');
        documentation_status = sprintf('summary exported to %s', summary_file);
    catch documentation_error
        documentation_status = sprintf('documentation export failed: %s', documentation_error.message);
        warning('calibrate_growth_model:DocumentationExportFailed', '%s', documentation_error.message);
    end

    %% 9. Summary
    fprintf('\n');
    fprintf('╔%s╗\n', repmat('═', 1, box_width));
    print_box_line('CALIBRATION COMPLETE', box_width, 'center');
    fprintf('╠%s╣\n', repmat('═', 1, box_width));
    print_box_line(sprintf('  K_0       = %12.1f million EUR', K_0), box_width);
    print_box_line(sprintf('  Y_0       = %12.1f million EUR (annual)', Y_annual), box_width);
    print_box_line(sprintf('  K/Y       = %12.2f', K_0 / Y_annual), box_width);
    print_box_line(sprintf('  A_0       = %12.6f (implied TFP)', A_0), box_width);
    print_box_line(sprintf('  G/Y       = %12.3f (government share)', GY), box_width);
    print_box_line(sprintf('  NX/Y      = %12.3f (net export share)', NXY), box_width);
    print_box_line(sprintf('  r* (BGP)  = %12.2f%%%% (annual)', r_star_annual*100), box_width);
    print_box_line(sprintf('  NAWRU     = %s', nawru_file), box_width);
    print_box_line(sprintf('  Demo L    = %s', demo_L_file), box_width);
    print_box_line(sprintf('  Demo P    = %s', demo_P_file), box_width);
    print_box_line(sprintf('  Docs      = %s', documentation_status), box_width);
    if isfield(params, 'terminal_growth') && isfield(params.terminal_growth, 'enabled') && params.terminal_growth.enabled
        print_box_line(sprintf('  g_A term. = %12.2f%%%% (annual)', 100*params.terminal_growth.g_A_terminal_annual), box_width);
        print_box_line(sprintf('  rho_g     = %12.4f (quarterly)', params.terminal_growth.rho_quarterly), box_width);
    end
    fprintf('╚%s╝\n', repmat('═', 1, box_width));
    fprintf('\n');
    fprintf('Next step: >> ramsey_trend(''%s'')\n', country);

    diary off;  % end logging

end


function tf = has_positive_override(p)
% True when the JSON supplies a usable K_override.
%
% jsondecode maps a JSON null to [], and `[] > 0` is an empty logical, which makes
% `isfield(...) && p.K_override > 0` throw "Operands to && must be convertible to
% logical scalar values" rather than simply falling through. Setting the field to
% null is the documented way to disable the override, so it must not crash.
    tf = isfield(p, 'K_override') && isscalar(p.K_override) ...
         && isnumeric(p.K_override) && isfinite(p.K_override) && p.K_override > 0;
end
