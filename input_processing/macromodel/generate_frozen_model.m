% =========================================================================
% GENERATE FROZEN MODEL FROM ESTIMATION RESULTS
% =========================================================================
% This script reads posterior modes from Dynare estimation output and
% creates Macromodel_[COUNTRY]_frozen.mod with hardcoded parameters.
%
% THE MODEL (Macromodel_template.mod with the country's calibration and
% estimation files):
%   - Smets-Wouters-style C/I decomposition: consumption Euler equation (c) and
%     a demand aggregation identity with the investment share s_inv
%   - Measurement errors for c and inv
%   - Capital k and investment inv (alpha_k, delta_k, rho_inv, phi_inv_r, s_inv),
%     with the investment-specific shock eps_inv
%   - Hours is a pure AR(1)
%   - rho_h and rho_L are CALIBRATED (not estimated)
%   - The estimated parameters are those of country_params/{C}_estimation.mod;
%     their names come from oo_.posterior_mode, so this script lists none
%
% Usage:
%   1. Run estimation: dynare Macromodel_{COUNTRY}.mod
%   2. Run this script: generate_frozen_model('{COUNTRY}')
%   3. Verify/export: dynare Macromodel_{COUNTRY}_frozen.mod noclearall
%                     followed by export_dynamic_for_simpaths
%
% The frozen model can then be used for:
%   - Simulation without re-estimation
%   - Dynamic policy-function export for SimPaths
%   - Comparison across estimation runs
%
% Author: WELLSIM Project
% Date: February 2026
% =========================================================================

function generate_frozen_model(country)
    % Resolve the model's code folders (internal/, diagnostics/, ...).
    % Idempotent; see setup_path.m for why MATLAB cannot do this itself.
    setup_path();
    if nargin < 1
        country = 'UK';
    end

    country_config = get_country_config(country);
    country = country_config.name;

    fprintf('\n');
    fprintf('=========================================================================\n');
    fprintf('  GENERATING FROZEN MODEL FOR %s\n', upper(country));
    fprintf('=========================================================================\n\n');

    % -------------------------------------------------------------------------
    % 1. Load estimation results
    % -------------------------------------------------------------------------
    model_name = sprintf('Macromodel_%s', country);
    mode_file = fullfile(model_name, 'Output', [model_name '_mode.mat']);

    if ~exist(mode_file, 'file')
        % Try alternative location (Dynare sometimes puts it in current dir)
        mode_file = [model_name '_mode.mat'];
    end

    if ~exist(mode_file, 'file')
        error('Mode file not found: %s\nRun estimation first: dynare %s.mod', ...
              mode_file, model_name);
    end

    % Load from results file (contains oo_ with properly named posterior modes)
    results_file = fullfile(model_name, 'Output', [model_name '_results.mat']);
    if ~exist(results_file, 'file')
        error('Results file not found: %s\nRun estimation first: dynare %s.mod', ...
              results_file, model_name);
    end

    fprintf('Loading estimation results from: %s\n', results_file);
    load(results_file, 'oo_');

    % -------------------------------------------------------------------------
    % 2. Extract parameter names and estimated values from oo_.posterior_mode
    % -------------------------------------------------------------------------
    % Using oo_.posterior_mode.parameters and oo_.posterior_mode.shocks_std
    % which have named fields - much safer than parsing xparam1 vector

    % Get estimated parameter names and values
    if isfield(oo_, 'posterior_mode') && isfield(oo_.posterior_mode, 'parameters')
        est_param_names = fieldnames(oo_.posterior_mode.parameters);
        est_param_values = zeros(length(est_param_names), 1);
        for i = 1:length(est_param_names)
            est_param_values(i) = oo_.posterior_mode.parameters.(est_param_names{i});
        end
    else
        error('No posterior mode found in results. Did estimation complete successfully?');
    end

    % Get estimated shock std devs
    if isfield(oo_.posterior_mode, 'shocks_std')
        est_shock_names = fieldnames(oo_.posterior_mode.shocks_std);
        est_shock_values = zeros(length(est_shock_names), 1);
        for i = 1:length(est_shock_names)
            est_shock_values(i) = oo_.posterior_mode.shocks_std.(est_shock_names{i});
        end
    else
        est_shock_names = {};
        est_shock_values = [];
    end

    fprintf('\nEstimated parameters:\n');
    fprintf('---------------------\n');
    for i = 1:length(est_param_names)
        fprintf('  %-12s = %.6f\n', est_param_names{i}, est_param_values(i));
    end

    fprintf('\nEstimated shock std devs:\n');
    fprintf('-------------------------\n');
    for i = 1:length(est_shock_names)
        fprintf('  %-12s = %.6f\n', est_shock_names{i}, est_shock_values(i));
    end

    % -------------------------------------------------------------------------
    % 3. Read model source and expand wrapper/template includes
    % -------------------------------------------------------------------------
    template_file = [model_name '.mod'];
    frozen_file = [model_name '_frozen.mod'];

    fprintf('\nReading source model: %s\n', template_file);
    template = expand_dynare_includes(template_file, struct('country', country));

    % -------------------------------------------------------------------------
    % 4. Modify template for frozen model
    % -------------------------------------------------------------------------
    frozen = template;

    % Numeric literal as written in a .mod calibration line: optional sign, digits with
    % optional decimal point, optional exponent.
    VALUE_PATTERN = '[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?';

    % Every substitution below goes through subst_verified, which fails if the pattern
    % does not match exactly the expected number of times. A plain regexprep that
    % misses after a reformatting upstream silently leaves the original text in a
    % file whose header claims "FROZEN (estimated parameters)". The
    % most dangerous case is 4b - a missed substitution there leaves
    % do_parameter_estimation = true, so the "frozen" model re-runs estimation on export.

    % 4a. Change header
    frozen = subst_verified(frozen, ...
        '// DSGE Model for Microsimulation Coupling', ...
        '// DSGE Model for Microsimulation Coupling - FROZEN (estimated parameters)', ...
        'model header', 1);

    % 4b. Disable estimation
    frozen = subst_verified(frozen, ...
        '@#define do_parameter_estimation = true', ...
        '@#define do_parameter_estimation = false  // FROZEN: estimation disabled', ...
        'do_parameter_estimation switch', 1);

    % 4b2. Disable custom IRFs (frozen model is for export, not interactive plotting)
    % The pattern accepts either value (the template sets false)
    frozen = subst_verified(frozen, ...
        '@#define use_custom_irfs = (true|false)[^\n]*', ...
        '@#define use_custom_irfs = false   // FROZEN: use standard IRFs for export', ...
        'use_custom_irfs switch', 1);

    % rho_L, rho_h and rho_inv are calibrated, not estimated, so the calibration's
    % lines pass through unchanged: the frozen model is the estimated model for
    % every shock. Zeroing rho_inv would destroy the long-run investment
    % multiplier phi_inv_r/(1 - rho_inv) that estimation identifies.

    % 4c. Update estimated parameters in calibration section
    for i = 1:length(est_param_names)
        pname = est_param_names{i};
        pvalue = est_param_values(i);

        % Pattern: parameter = value; with possible comment
        % Replace value while preserving comment
        % Use (?<![a-zA-Z_]) negative lookbehind to avoid matching 'eta' in 'beta'
        % VALUE_PATTERN accepts negatives and scientific notation: psi_wL is -0.08
        % (Poland) / -0.05 (Germany), and an unsigned [0-9.]+ would silently fail
        % to substitute it the moment such a parameter is estimated.
        pattern = sprintf('((?<![a-zA-Z_])%s\\s*=\\s*)%s\\s*;', pname, VALUE_PATTERN);
        replacement = sprintf('$1%.6f;  // ESTIMATED (posterior mode)', pvalue);
        frozen = subst_verified(frozen, pattern, replacement, ...
            sprintf('estimated parameter %s', pname), 1);
    end

    % 4d. Update shock std devs in shocks block
    for i = 1:length(est_shock_names)
        sname = est_shock_names{i};
        svalue = est_shock_values(i);

        % Pattern: var shock_name; stderr value;
        % Shock names start with 'eps_' so no word boundary issue, but add for safety
        pattern = sprintf('(var\\s+%s\\s*;\\s*stderr\\s*)%s\\s*;', sname, VALUE_PATTERN);
        replacement = sprintf('$1%.4f;  // ESTIMATED', svalue);
        frozen = subst_verified(frozen, pattern, replacement, ...
            sprintf('shock stderr %s', sname), 1);
    end

    % 4e. Add generation timestamp
    timestamp = datestr(now, 'yyyy-mm-dd HH:MM:SS');
    header_addition = sprintf([...
        '// =========================================================================\n' ...
        '// FROZEN MODEL - AUTO-GENERATED\n' ...
        '// Generated: %s\n' ...
        '// Source: %s (estimation results)\n' ...
        '// DO NOT EDIT MANUALLY - regenerate with generate_frozen_model.m\n' ...
        '// =========================================================================\n\n'], ...
        timestamp, model_name);
    frozen = [header_addition, frozen];

    % -------------------------------------------------------------------------
    % 5. Write frozen model
    % -------------------------------------------------------------------------
    fprintf('Writing frozen model: %s\n', frozen_file);
    fid = fopen(frozen_file, 'w');
    if fid == -1
        error('Cannot write to: %s', frozen_file);
    end
    fwrite(fid, frozen);
    fclose(fid);

    % -------------------------------------------------------------------------
    % 6. Summary
    % -------------------------------------------------------------------------
    fprintf('\n');
    fprintf('=========================================================================\n');
    fprintf('  FROZEN MODEL GENERATED SUCCESSFULLY\n');
    fprintf('=========================================================================\n');
    fprintf('\n');
    fprintf('Next steps:\n');
    fprintf('  1. Run frozen model:      dynare %s noclearall nograph\n', frozen_file);
    fprintf('  2. Export for SimPaths:   export_dynamic_for_simpaths\n');
    fprintf('  3. Or run full pipeline:  run_full_workflow(''%s'')\n', country);
    fprintf('\n');

    % -------------------------------------------------------------------------
    % 7. Create parameter summary file (for documentation)
    % -------------------------------------------------------------------------
    summary_file = sprintf('params_%s_estimated.txt', country);
    fid = fopen(summary_file, 'w');
    fprintf(fid, 'ESTIMATED PARAMETERS FOR %s\n', upper(country));
    fprintf(fid, 'Generated: %s\n', timestamp);
    fprintf(fid, '==========================================\n\n');

    fprintf(fid, 'STRUCTURAL PARAMETERS:\n');
    fprintf(fid, '----------------------\n');
    for i = 1:length(est_param_names)
        fprintf(fid, '%-15s = %.6f\n', est_param_names{i}, est_param_values(i));
    end

    fprintf(fid, '\nSHOCK STANDARD DEVIATIONS:\n');
    fprintf(fid, '--------------------------\n');
    for i = 1:length(est_shock_names)
        fprintf(fid, '%-15s = %.6f\n', est_shock_names{i}, est_shock_values(i));
    end

    fclose(fid);
    fprintf('Parameter summary written to: %s\n\n', summary_file);
end

function expanded = expand_dynare_includes(file_path, macros)
    if nargin < 2
        macros = struct();
    end

    fid = fopen(file_path, 'r');
    if fid == -1
        error('Cannot open source file: %s', file_path);
    end
    cleanup = onCleanup(@() fclose(fid));
    raw_text = fread(fid, '*char')';

    lines = regexp(raw_text, '\r\n|\n|\r', 'split');
    base_dir = fileparts(file_path);
    expanded_parts = {};

    for i = 1:length(lines)
        line = lines{i};

        define_tokens = regexp(line, '^\s*@#define\s+(\w+)\s*=\s*"([^"]*)"\s*$', 'tokens', 'once');
        if ~isempty(define_tokens)
            macros.(define_tokens{1}) = define_tokens{2};
            continue;
        end

        include_path = resolve_dynare_include(line, macros);
        if ~isempty(include_path)
            include_file = fullfile(base_dir, include_path);
            expanded_parts{end+1} = expand_dynare_includes(include_file, macros); %#ok<AGROW>
            continue;
        end

        expanded_parts{end+1} = line; %#ok<AGROW>
    end

    expanded = strjoin(expanded_parts, newline);
end

function include_path = resolve_dynare_include(line, macros)
    include_path = '';

    direct_tokens = regexp(line, '^\s*@#include\s+"([^"]+)"\s*$', 'tokens', 'once');
    if ~isempty(direct_tokens)
        include_path = direct_tokens{1};
        return;
    end

    concat_tokens = regexp(line, '^\s*@#include\s+"([^"]*)"\s*\+\s*(\w+)\s*\+\s*"([^"]*)"\s*$', 'tokens', 'once');
    if isempty(concat_tokens)
        return;
    end

    macro_name = concat_tokens{2};
    if ~isfield(macros, macro_name)
        error('Undefined Dynare macro "%s" while resolving include: %s', macro_name, line);
    end

    include_path = [concat_tokens{1}, macros.(macro_name), concat_tokens{3}];
end
