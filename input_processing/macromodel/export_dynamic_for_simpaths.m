%% =========================================================================
%% EXPORT DSGE POLICY FUNCTION FOR SIMPATHS
%% =========================================================================
%% This script exports the linearized policy function matrices from Dynare
%% to CSV/JSON format for import into SimPaths (Java).
%%
%% The model (Macromodel_template.mod):
%%   - Smets-Wouters consumption/investment decomposition, with habit in c
%%   - 8 shocks, including the cost-push shock eps_p
%%   - capital k and investment inv; sigma (inverse IES), s_inv (investment share)
%%   - hours and L are pure AR(1) at their calibrated persistence (rho_h, rho_L)
%%   - 9 estimated params: sigma_a, sigma_d, sigma_r, sigma_p, sigma_h, sigma_L, sigma_inv, omega_h, phi_inv_r
%%
%% The bundle is the estimated model for every shock. The DSGE takes no input from
%% SimPaths: every shock, eps_L and eps_h included, is an innovation in
%% standard-deviation units that a scenario may set, and SimPaths receives the
%% wage and inflation deviations. The model is in 100 x log-deviation units
%% (metadata.deviation_scale), which makes the cycle wage a percent.
%%
%% Run AFTER: dynare Macromodel_{COUNTRY}_frozen.mod
%%
%% State-Space Representation:
%% ---------------------------
%% The Dynare solution separates state (predetermined) and jump (forward-looking)
%% variables:
%%
%%   s_t = A * s_{t-1} + B_s * ε_t     (state transition)
%%   y_t = C * s_{t-1} + D   * ε_t     (jump variables)
%%
%% Where:
%%   s_t  = state variables, the predetermined ones in oo_.dr (for the current
%%          template: c, R, pi, w, v, n, h, x, L, k, inv, sep_shock, d_shock)
%%   y_t  = jump variables, the others (y, d, gap, w_star, u, u_rate, m, mc, theta)
%%   ε_t  = shock innovations (8×1), in the template's varexo declaration order:
%%          eps_a, eps_r, eps_d, eps_p, eps_L, eps_sep, eps_h, eps_inv
%%
%% The varexo block in Macromodel_template.mod is the single authority on this
%% order. Nothing here or downstream indexes shocks positionally — the exported
%% shock_indices_java map is what Java reads — but hand-building a shock vector
%% from a stale ordering in prose is exactly how a σ_p cost-push shock ends up
%% in the eps_L slot. If the varexo block changes, change this line with it.
%%
%% NOTE: Exact dimensions depend on Dynare's ordering after estimation.
%%       The script reads dynamically from oo_.dr — no hardcoded sizes.
%%
%% Exports:
%%   1. policy_A.csv        - State transition matrix A (n_state × n_state)
%%   2. policy_Bs.csv       - Shock impact on states B_s (n_state × n_shock)
%%   3. policy_C.csv        - Jump response to lagged states C (n_jump × n_state)
%%   4. policy_D.csv        - Jump response to shocks D (n_jump × n_shock)
%%   5. policy_full_ghx.csv - Full response matrix (reference only - no reader in SimPaths, production or tests)
%%   6. policy_full_ghu.csv - Full shock matrix (reference only - no reader in SimPaths, production or tests)
%%   7. steady_state.csv    - Steady state values
%%   8. shock_params.csv    - AR(1) coefficients and std devs
%%   9. model_info.json     - Variable names, ordering, metadata
%%  10. irfs_eps_*.csv      - IRFs for each shock
%%
%% Usage in Java:
%%   // Update states
%%   s_new = A * s_old + Bs * epsilon;
%%
%%   // Compute jumps (including π)
%%   y = C * s_old + D * epsilon;
%%
%%   // Extract outputs for microsim
%%   w  = s_new[IDX_W];    // wage is a state variable
%%   pi = s_new[IDX_PI];   // inflation is a state too (iota_p
%%                         // indexation puts pi(-1) in the NKPC)
%%
%% The Java implementation is simpaths.model.macro.DSGEModel.
%% =========================================================================

fprintf('\n');
box_width = 62;
fprintf('╔%s╗\n', repmat('═', 1, box_width));
print_box_line('EXPORT DSGE POLICY FUNCTION FOR SIMPATHS', box_width, 'center');
fprintf('╚%s╝\n', repmat('═', 1, box_width));
fprintf('\n');

%% =========================================================================
%% 0. CHECK PREREQUISITES
%% =========================================================================

% Check if Dynare results exist
if ~exist('oo_', 'var') || ~exist('M_', 'var')
    error(['Dynare results not found. Run this script AFTER:\n' ...
           '  >> dynare Macromodel_{COUNTRY}_frozen.mod\n']);
end

if ~isfield(oo_, 'dr') || ~isfield(oo_.dr, 'ghx')
    error('Decision rules not computed. Run stoch_simul first.');
end

% Infer country from Dynare model name (M_.fname = 'Macromodel_Germany_frozen')
country_from_fname = regexp(M_.fname, 'Macromodel_(\w+?)(?:_frozen)?$', 'tokens', 'once');
if isempty(country_from_fname)
    error('export_dynamic_for_simpaths:UnknownModel', ...
          ['Cannot infer the country from M_.fname = "%s" (expected Macromodel_<Country>[_frozen]). ' ...
           'Run dynare on the frozen model of the country to export first.'], M_.fname);
end
export_country = country_from_fname{1};

% The model is estimated on 100x-log-deviation data (the detrending scale in
% ramsey_trend.m), which is what makes its deviations, the cycle wage among them,
% percent. Declared in metadata.deviation_scale (section 7) so the Java side can
% refuse a bundle in other units. Checked here, before sections 4-6 write
% anything, so a trip is a clean refusal rather than an abort that leaves a
% mixed-vintage bundle behind.
est_data_file = artifact_path('data', sprintf('data_for_dynare_%s.csv', export_country));
if ~exist(est_data_file, 'file')
    error('Estimation datafile %s not found; cannot verify deviation_scale.', est_data_file);
end
est_data = readtable(est_data_file);
for check_var = {'y', 'n'}
    col_vals = est_data.(check_var{1});
    med_abs = median(abs(col_vals(~isnan(col_vals))));
    if ~(med_abs > 0.05 && med_abs < 50)
        error(['%s: median |%s| = %.4g lies outside [0.05, 50], so the datafile does ' ...
               'not look like 100x-log-deviation units and deviation_scale = 100 ' ...
               'would be a lie the Java side trusts (it reads the cycle wage as a percent). ' ...
               'The detrending scale and this declaration must move together.'], ...
               est_data_file, check_var{1}, med_abs);
    end
end

% Create output directory
output_dir = artifact_path('bundle', '', 'country', export_country, 'create', true);

% Unverified until verify_export_bundle passes at the very end, so an export
% that dies partway cannot leave a clean-looking mixed bundle behind.
mark_bundle_unverified(output_dir, export_country, ...
    {'export started but did not reach verification'});

fprintf('>>> Country: %s\n', export_country);
fprintf('>>> Output directory: %s/\n\n', strrep(output_dir, '\', '/'));

%% =========================================================================
%% 1. EXTRACT DECISION RULE MATRICES
%% =========================================================================

fprintf('>>> Extracting decision rules...\n');

% Get decision rule matrices
ghx = oo_.dr.ghx;  % Response to state variables (n_endo × n_state)
ghu = oo_.dr.ghu;  % Response to shocks (n_endo × n_shocks)
ys  = oo_.dr.ys;   % Steady state (n_endo × 1)

% Variable ordering in decision rules
% Dynare reorders variables: state vars first, then jump vars
order_var = oo_.dr.order_var;  % Maps DR order → declaration order

% State variables (predetermined)
state_var_idx = oo_.dr.state_var;  % Indices of state variables in DR order
nspred = M_.nspred;  % Number of predetermined (state) variables

fprintf('    Endogenous variables: %d\n', M_.endo_nbr);
fprintf('    State variables: %d\n', nspred);
fprintf('    Shocks: %d\n', M_.exo_nbr);
fprintf('    ghx size: %d × %d\n', size(ghx, 1), size(ghx, 2));
fprintf('    ghu size: %d × %d\n', size(ghu, 1), size(ghu, 2));

%% =========================================================================
%% 2. GET VARIABLE AND SHOCK NAMES
%% =========================================================================

fprintf('\n>>> Extracting variable names...\n');

% Endogenous variable names (in declaration order)
endo_names = M_.endo_names;

% Exogenous shock names
exo_names = M_.exo_names;

% State variable names (in decision rule order)
state_names = cell(nspred, 1);
for i = 1:nspred
    % state_var_idx gives the index in declaration order
    state_names{i} = endo_names{state_var_idx(i)};
end

fprintf('    Endogenous: ');
fprintf('%s ', endo_names{:});
fprintf('\n');

fprintf('    States: ');
fprintf('%s ', state_names{:});
fprintf('\n');

fprintf('    Shocks: ');
fprintf('%s ', exo_names{:});
fprintf('\n');

%% =========================================================================
%% 3. REORDER MATRICES FOR SIMPATHS
%% =========================================================================
% Dynare's decision rules are in "DR order" (states first, jumps second).
% For SimPaths, we want matrices in DECLARATION order (as in .mod file).

fprintf('\n>>> Reordering matrices to declaration order...\n');

% Create inverse ordering (DR order → declaration order)
inv_order_var = zeros(M_.endo_nbr, 1);
for i = 1:M_.endo_nbr
    inv_order_var(order_var(i)) = i;
end

% Reorder ghx and ghu rows to declaration order
ghx_decl = zeros(M_.endo_nbr, nspred);
ghu_decl = zeros(M_.endo_nbr, M_.exo_nbr);

for i = 1:M_.endo_nbr
    dr_idx = inv_order_var(i);  % Position in DR order
    ghx_decl(i, :) = ghx(dr_idx, :);
    ghu_decl(i, :) = ghu(dr_idx, :);
end

% Also need to map state columns to variable names
% ghx columns correspond to LAGGED state variables
state_var_names_lagged = cell(nspred, 1);
for i = 1:nspred
    state_var_names_lagged{i} = [endo_names{state_var_idx(i)} '_lag'];
end

%% =========================================================================
%% 3b. IDENTIFY STATE AND JUMP VARIABLES
%% =========================================================================

fprintf('\n>>> Separating state and jump variables...\n');

% State variables: those in state_var_idx (in declaration order)
% Create logical index for state variables in declaration order
is_state = false(M_.endo_nbr, 1);
for i = 1:nspred
    is_state(state_var_idx(i)) = true;
end

% Get indices for states and jumps in declaration order
state_decl_idx = find(is_state);   % Indices of state vars in declaration order
jump_decl_idx = find(~is_state);   % Indices of jump vars in declaration order
n_state = length(state_decl_idx);
n_jump = length(jump_decl_idx);

% Get names
state_var_names_decl = endo_names(state_decl_idx);
jump_var_names_decl = endo_names(jump_decl_idx);

fprintf('    State variables (%d): ', n_state);
fprintf('%s ', state_var_names_decl{:});
fprintf('\n');

fprintf('    Jump variables (%d): ', n_jump);
fprintf('%s ', jump_var_names_decl{:});
fprintf('\n');

%% =========================================================================
%% 3c. CONSTRUCT A, B_s, C, D MATRICES
%% =========================================================================
% State-space representation:
%   s_t = A * s_{t-1} + B_s * ε_t    (state transition)
%   y_t = C * s_{t-1} + D   * ε_t    (jump variables)

fprintf('\n>>> Building state-space matrices A, B_s, C, D...\n');

% CRITICAL: ghx columns are in state_var_idx order (DR order)
% We need to reorder columns to match declaration order for consistency.

% Build mapping from DR state order to declaration state order
% state_var_idx(i) gives declaration index of i-th state in DR order
% We need: for each state in declaration order, which DR column is it?
dr_to_decl_state = zeros(nspred, 1);
for i = 1:nspred
    % state_var_idx(i) = declaration index of i-th DR state
    % Find where this declaration index appears in state_decl_idx
    decl_pos = find(state_decl_idx == state_var_idx(i));
    dr_to_decl_state(i) = decl_pos;
end

% Reorder ghx columns: from DR order to declaration order
ghx_decl_reordered = ghx_decl;
ghx_decl_reordered(:, dr_to_decl_state) = ghx_decl;

% Now extract A matrix - rows and columns both in declaration order
A_matrix = ghx_decl_reordered(state_decl_idx, :);
fprintf('    A matrix: %d × %d (state transition)\n', size(A_matrix, 1), size(A_matrix, 2));

% Update column names to match declaration order
state_var_names_lagged_decl = cell(n_state, 1);
for i = 1:n_state
    state_var_names_lagged_decl{i} = [state_var_names_decl{i} '_lag'];
end

% B_s matrix: state vars' response to shocks (n_state × n_shocks)
Bs_matrix = ghu_decl(state_decl_idx, :);
fprintf('    B_s matrix: %d × %d (shock → states)\n', size(Bs_matrix, 1), size(Bs_matrix, 2));

% C matrix: jump vars' response to lagged states - also need column reordering
C_matrix = ghx_decl_reordered(jump_decl_idx, :);
fprintf('    C matrix: %d × %d (lagged states → jumps)\n', size(C_matrix, 1), size(C_matrix, 2));

% D matrix: jump vars' response to shocks (n_jump × n_shocks)
D_matrix = ghu_decl(jump_decl_idx, :);
fprintf('    D matrix: %d × %d (shocks → jumps)\n', size(D_matrix, 1), size(D_matrix, 2));

%% =========================================================================
%% 4. EXPORT MATRICES TO CSV
%% =========================================================================

fprintf('\n>>> Exporting matrices...\n');

% 4a. State transition matrix A (n_state × n_state)
% Now both rows and columns are in declaration order
A_table = array2table(A_matrix, ...
    'VariableNames', state_var_names_lagged_decl, ...
    'RowNames', state_var_names_decl);
writetable(A_table, fullfile(output_dir, 'policy_A.csv'), 'WriteRowNames', true);
fprintf('    ✓ policy_A.csv (state transition matrix, %d × %d)\n', n_state, n_state);

% 4b. State shock impact matrix B_s (n_state × n_shocks)
Bs_table = array2table(Bs_matrix, ...
    'VariableNames', exo_names, ...
    'RowNames', state_var_names_decl);
writetable(Bs_table, fullfile(output_dir, 'policy_Bs.csv'), 'WriteRowNames', true);
fprintf('    ✓ policy_Bs.csv (shock → states, %d × %d)\n', n_state, M_.exo_nbr);

% 4c. Jump response to lagged states C (n_jump × n_state)
% Columns in declaration order to match A matrix
C_table = array2table(C_matrix, ...
    'VariableNames', state_var_names_lagged_decl, ...
    'RowNames', jump_var_names_decl);
writetable(C_table, fullfile(output_dir, 'policy_C.csv'), 'WriteRowNames', true);
fprintf('    ✓ policy_C.csv (lagged states → jumps, %d × %d)\n', n_jump, n_state);

% 4d. Jump response to shocks D (n_jump × n_shocks)
D_table = array2table(D_matrix, ...
    'VariableNames', exo_names, ...
    'RowNames', jump_var_names_decl);
writetable(D_table, fullfile(output_dir, 'policy_D.csv'), 'WriteRowNames', true);
fprintf('    ✓ policy_D.csv (shocks → jumps, %d × %d)\n', n_jump, M_.exo_nbr);

% 4e. Full matrices for reference (backward compatibility)
% Full state transition matrix (ghx in declaration order)
A_full_table = array2table(ghx_decl, ...
    'VariableNames', state_var_names_lagged, ...
    'RowNames', endo_names);
writetable(A_full_table, fullfile(output_dir, 'policy_full_ghx.csv'), 'WriteRowNames', true);
fprintf('    ✓ policy_full_ghx.csv (full, %d × %d)\n', M_.endo_nbr, nspred);

% Full shock impact matrix (ghu in declaration order)
B_full_table = array2table(ghu_decl, ...
    'VariableNames', exo_names, ...
    'RowNames', endo_names);
writetable(B_full_table, fullfile(output_dir, 'policy_full_ghu.csv'), 'WriteRowNames', true);
fprintf('    ✓ policy_full_ghu.csv (full, %d × %d)\n', M_.endo_nbr, M_.exo_nbr);

% 4f. Steady state values
ss_table = array2table(ys, 'VariableNames', {'steady_state'}, 'RowNames', endo_names);
writetable(ss_table, fullfile(output_dir, 'steady_state.csv'), 'WriteRowNames', true);
fprintf('    ✓ steady_state.csv\n');

%% =========================================================================
%% 5. EXPORT SHOCK PARAMETERS
%% =========================================================================

fprintf('\n>>> Extracting shock parameters...\n');

% Get shock standard deviations from Sigma_e.
% builtin() because this script runs in the BASE workspace (run_full_workflow
% invokes it through evalin), where any user variable named diag would shadow the
% function and turn this line into a struct index with a cryptic error.
shock_stderr = sqrt(builtin('diag', M_.Sigma_e));

% Get AR(1) coefficients from parameters
% These are model-specific - extract from M_.params
param_names = M_.param_names;
param_values = M_.params;

% Find rho parameters (rho_h and rho_L are calibrated, not estimated)
rho_params = {'rho_a', 'rho_d', 'rho_r', 'rho_L', 'rho_sep', 'rho_h'};
rho_values = zeros(length(rho_params), 1);
for i = 1:length(rho_params)
    idx = find(strcmp(param_names, rho_params{i}));
    if ~isempty(idx)
        rho_values(i) = param_values(idx);
    else
        rho_values(i) = NaN;
    end
end

% Build shock parameters table
% Map shocks to their AR coefficients
% eps_L and eps_h follow AR(1) at the country's calibrated rho_L and rho_h, in the
% estimation and the frozen model alike.
shock_rho_map = containers.Map(...
    {'eps_a', 'eps_d', 'eps_r', 'eps_L', 'eps_sep', 'eps_h', 'eps_inv'}, ...
    {'rho_a', 'rho_d', 'rho_r', 'rho_L', 'rho_sep', 'rho_h', 'rho_inv'});

shock_data = cell(M_.exo_nbr, 4);
for i = 1:M_.exo_nbr
    shock_name = exo_names{i};
    shock_data{i, 1} = shock_name;
    shock_data{i, 2} = shock_stderr(i);

    % Find corresponding rho (if shock follows AR(1) through state equation)
    % eps_d has an explicit AR(1) state: d_shock = rho_d*d_shock(-1) + eps_d
    if isKey(shock_rho_map, shock_name)
        rho_name = shock_rho_map(shock_name);
        idx = find(strcmp(param_names, rho_name));
        if ~isempty(idx)
            shock_data{i, 3} = param_values(idx);
            shock_data{i, 4} = rho_name;
        else
            shock_data{i, 3} = 0;  % No persistence (i.i.d.)
            shock_data{i, 4} = 'none';
        end
    else
        shock_data{i, 3} = 0;
        shock_data{i, 4} = 'none';
    end
end

shock_table = cell2table(shock_data, ...
    'VariableNames', {'shock_name', 'stderr', 'rho', 'rho_param_name'});
writetable(shock_table, fullfile(output_dir, 'shock_params.csv'));
fprintf('    ✓ shock_params.csv\n');

%% =========================================================================
%% 6. EXPORT IRFs FOR KEY SHOCKS
%% =========================================================================

fprintf('\n>>> Exporting impulse response functions...\n');

% Check if IRFs exist
if isfield(oo_, 'irfs') && ~isempty(fieldnames(oo_.irfs))

    % Key variables for SimPaths
    key_vars = {'w', 'n', 'pi', 'u_rate', 'y', 'R', 'L', 'h', 'k', 'inv'};

    % Key shocks
    key_shocks = {'eps_L', 'eps_a', 'eps_d', 'eps_r', 'eps_h', 'eps_inv'};

    for s = 1:length(key_shocks)
        shock = key_shocks{s};

        % Build IRF matrix for this shock
        irf_data = [];
        irf_vars = {};

        for v = 1:length(key_vars)
            var_name = key_vars{v};
            irf_field = [var_name '_' shock];

            if isfield(oo_.irfs, irf_field)
                irf_data = [irf_data; oo_.irfs.(irf_field)];
                irf_vars{end+1} = var_name;
            end
        end

        if ~isempty(irf_data)
            % Transpose: rows = periods, cols = variables
            irf_data = irf_data';

            % Add period column
            periods = (0:size(irf_data, 1)-1)';
            irf_table = array2table([periods, irf_data], ...
                'VariableNames', ['period', irf_vars]);

            filename = sprintf('irfs_%s.csv', shock);
            writetable(irf_table, fullfile(output_dir, filename));
            fprintf('    ✓ %s\n', filename);
        end
    end
else
    fprintf('    ⚠ No IRFs found. Run stoch_simul with irf>0 first.\n');
end

%% =========================================================================
%% 7. EXPORT MODEL METADATA AS JSON
%% =========================================================================

fprintf('\n>>> Creating model metadata...\n');

% Build metadata structure
metadata = struct();
metadata.model_name = M_.fname;
metadata.n_endo = M_.endo_nbr;
metadata.n_exo = M_.exo_nbr;
metadata.n_state = n_state;
metadata.n_jump = n_jump;
metadata.endo_names = endo_names;
metadata.exo_names = exo_names;
metadata.state_names = state_var_names_decl;
metadata.jump_names = jump_var_names_decl;
metadata.state_decl_idx = state_decl_idx;  % 1-based
metadata.jump_decl_idx = jump_decl_idx;    % 1-based
metadata.order_var = order_var;
metadata.state_var_idx = state_var_idx;

% Key variable indices (for SimPaths) - MATLAB 1-based
key_vars_idx = struct();
for i = 1:M_.endo_nbr
    var_name = endo_names{i};
    key_vars_idx.(var_name) = i;
end
metadata.var_indices = key_vars_idx;

% model_info.json carries only what Java reads: the split state/jump layout
% (state_idx_in_s and jump_idx_in_y below). There is deliberately no Java index
% map of the full declaration vector: an unread map invites edits that have no
% runtime effect, and the full-vector ordering is recoverable from endo_names.

% Key shock indices - Java 0-based
shock_idx_java = struct();
for i = 1:M_.exo_nbr
    shock_name = exo_names{i};
    shock_idx_java.(shock_name) = i - 1;  % 0-indexed for Java
end
metadata.shock_indices_java = shock_idx_java;

% The model is in 100 x log-deviation units (the detrending scale in
% ramsey_trend.m), so its deviations, the cycle wage among them, are percent;
% validated against the estimation datafile in section 0, before anything was
% written. The Java side refuses a bundle without this declaration.
metadata.deviation_scale = 100;

% State/jump indices within their respective vectors (0-based for Java)
state_idx_in_s = struct();
for i = 1:n_state
    state_idx_in_s.(state_var_names_decl{i}) = i - 1;
end
metadata.state_idx_in_s = state_idx_in_s;

jump_idx_in_y = struct();
for i = 1:n_jump
    jump_idx_in_y.(jump_var_names_decl{i}) = i - 1;
end
metadata.jump_idx_in_y = jump_idx_in_y;

% Matrix dimensions
metadata.matrix_dims = struct();
metadata.matrix_dims.A = [n_state, nspred];
metadata.matrix_dims.Bs = [n_state, M_.exo_nbr];
metadata.matrix_dims.C = [n_jump, nspred];
metadata.matrix_dims.D = [n_jump, M_.exo_nbr];

% Export as JSON
json_str = jsonencode(metadata);
% Pretty print (basic)
json_str = strrep(json_str, ',"', sprintf(',\n  "'));
json_str = strrep(json_str, '{', sprintf('{\n  '));
json_str = strrep(json_str, '}', sprintf('\n}'));

write_text_atomic(fullfile(output_dir, 'model_info.json'), json_str, 'verify_json', true);
fprintf('    ✓ model_info.json\n');

%% =========================================================================
%% 8. CREATE SIMPATHS INTERFACE DOCUMENTATION
%% =========================================================================

fprintf('\n>>> Creating SimPaths interface documentation...\n');

doc_file = fullfile(output_dir, 'SIMPATHS_INTERFACE.md');
fid = fopen(doc_file, 'w');

fprintf(fid, '# DSGE Policy Function Export for SimPaths\n\n');
fprintf(fid, '**Generated:** %s\n', datestr(now));
fprintf(fid, '**Model:** %s\n\n', M_.fname);

fprintf(fid, '## 1. State-Space Representation\n\n');
fprintf(fid, 'The linearized DSGE solution separates **state** (predetermined) and **jump** (forward-looking) variables:\n\n');
fprintf(fid, '```\n');
fprintf(fid, 's_t = A  * s_{t-1} + B_s * ε_t    (state transition)\n');
fprintf(fid, 'y_t = C  * s_{t-1} + D   * ε_t    (jump variables)\n');
fprintf(fid, '```\n\n');
fprintf(fid, 'Where:\n');
fprintf(fid, '- `s_t` = state variables (%d×1): predetermined, depend on lagged values\n', n_state);
fprintf(fid, '- `y_t` = jump variables (%d×1): forward-looking, determined by expectations\n', n_jump);
fprintf(fid, '- `A`   = state transition matrix (%d × %d)\n', n_state, nspred);
fprintf(fid, '- `B_s` = shock impact on states (%d × %d)\n', n_state, M_.exo_nbr);
fprintf(fid, '- `C`   = jump response to lagged states (%d × %d)\n', n_jump, nspred);
fprintf(fid, '- `D`   = jump response to shocks (%d × %d)\n', n_jump, M_.exo_nbr);
fprintf(fid, '- `ε_t` = shock innovations (%d×1)\n\n', M_.exo_nbr);

fprintf(fid, '**Key insight for SimPaths:**\n');
fprintf(fid, '- Wage `w` is a **state variable** → extract from `s_t` directly\n');
fprintf(fid, '- Inflation `π` is **also a state variable** (inflation\n');
fprintf(fid, '  indexation puts `pi(-1)` in the NKPC) → extract from `s_t`, NOT `y_t`\n');
fprintf(fid, '- Every shock, eps_L and eps_h included, is an innovation in **standard-deviation\n');
fprintf(fid, '  units**: 1.0 is a one-σ shock, multiplied by its std dev before the policy matrices\n');
fprintf(fid, '- The model is in 100 × log-deviation units (`deviation_scale`), so the wage\n');
fprintf(fid, '  deviation is a percent\n\n');

fprintf(fid, '## 2. Files Exported\n\n');
fprintf(fid, '| File | Dimension | Description |\n');
fprintf(fid, '|------|-----------|-------------|\n');
fprintf(fid, '| `policy_A.csv` | %d×%d | State transition matrix (A) |\n', n_state, nspred);
fprintf(fid, '| `policy_Bs.csv` | %d×%d | Shock impact on states (B_s) |\n', n_state, M_.exo_nbr);
fprintf(fid, '| `policy_C.csv` | %d×%d | Jump response to lagged states (C) |\n', n_jump, nspred);
fprintf(fid, '| `policy_D.csv` | %d×%d | Jump response to shocks (D) |\n', n_jump, M_.exo_nbr);
fprintf(fid, '| `policy_full_ghx.csv` | %d×%d | Full response matrix (reference only; nothing reads this) |\n', M_.endo_nbr, nspred);
fprintf(fid, '| `policy_full_ghu.csv` | %d×%d | Full shock matrix (reference only; nothing reads this) |\n', M_.endo_nbr, M_.exo_nbr);
fprintf(fid, '| `steady_state.csv` | %d×1 | Steady state values |\n', M_.endo_nbr);
fprintf(fid, '| `shock_params.csv` | %d×4 | Shock AR(1) coefficients and std devs |\n', M_.exo_nbr);
fprintf(fid, '| `model_info.json` | - | Variable names and indices |\n');
fprintf(fid, '| `irfs_eps_*.csv` | - | Dynare responses to one-σ shocks, one file per exported shock |\n\n');

fprintf(fid, '### Ramsey trend handoff\n\n');
fprintf(fid, 'The files above are the DSGE half of the bundle. The Ramsey trend layer\n');
fprintf(fid, 'consumes these, and **SimPaths must read them rather than re-derive them**.\n');
fprintf(fid, 'Every path here is produced on the MATLAB side; a second implementation\n');
fprintf(fid, 'downstream is how the two sides drift apart without any error surfacing.\n\n');
fprintf(fid, '| File | Description |\n');
fprintf(fid, '|------|-------------|\n');
fprintf(fid, '| `growth_model_reference_%s.csv` | In-sample Ramsey solution; its last row is the handoff quarter that every other file aligns to |\n', lower(export_country));
fprintf(fid, '| `growth_params_%s.json` | Calibrated structural parameters and solver settings |\n', export_country);
fprintf(fid, '| `growth_terminal_state.json` | Terminal K and A, the handoff quarter, and `projection_quarters` (the forward horizon; a mismatch silently changes the terminal closure) |\n');
fprintf(fid, '| `population_projection_%s.csv` | SimPaths-consistent demographic path, `time,N,P`: persons in employment and resident population |\n', export_country);
fprintf(fid, '| `hours_projection_%s.csv` | Planner hours per worker, `time,h`, `projection_quarters + 1` rows from the handoff |\n\n', export_country);
fprintf(fid, '**Alignment.** Each projection is located by DATE, not by row position: find the\n');
fprintf(fid, 'row whose label equals the handoff quarter and read forward from there. A\n');
fprintf(fid, 'projection may legitimately begin earlier than the handoff.\n\n');
fprintf(fid, '**Hours.** SimPaths reads the forward hours path from `hours_projection_*.csv`\n');
fprintf(fid, 'and fails the run if the file is missing. It does not extend the reference CSV''s\n');
fprintf(fid, '`h` column itself: with a trailing-growth window of its own the two sides would\n');
fprintf(fid, 'drift apart, which `hours_trend_method = log_linear` (a pure exponential `h`)\n');
fprintf(fid, 'would hide.\n\n');
fprintf(fid, '**What is built on both sides.** The terminal tail appended past the\n');
fprintf(fid, 'SimPaths horizon is not exported. Both sides construct it with the same rule\n');
fprintf(fid, '(trailing-growth window 20 for the demographic path, 1 for hours), enforced only\n');
fprintf(fid, 'by convention and a downstream tripwire test. Beyond the projection''s last row\n');
fprintf(fid, 'the population follows employment at the last projected ratio.\n\n');

fprintf(fid, '## 3. Variable Classification\n\n');
fprintf(fid, '### State Variables (rows in A, B_s; columns in A, C)\n\n');
fprintf(fid, '| Index (s) | Variable | Description |\n');
fprintf(fid, '|-----------|----------|-------------|\n');
state_descriptions = containers.Map(...
    {'R', 'v', 'n', 'h', 'x', 'L', 'sep_shock', 'w', 'k', 'inv', 'd_shock'}, ...
    {'nominal interest rate', 'vacancies', 'employment', 'hours per worker', ...
    'technology', 'labor force', 'separation shock process', ...
     'real wage', 'capital stock', 'investment', 'demand/preference shock state'});
for i = 1:n_state
    var_name = state_var_names_decl{i};
    if isKey(state_descriptions, var_name)
        desc = state_descriptions(var_name);
    else
        desc = var_name;
    end
    fprintf(fid, '| %d | `%s` | %s |\n', i-1, var_name, desc);
end
fprintf(fid, '\n');

fprintf(fid, '### Jump Variables (rows in C, D)\n\n');
fprintf(fid, '| Index (y) | Variable | Description |\n');
fprintf(fid, '|-----------|----------|-------------|\n');
jump_descriptions = containers.Map(...
    {'y', 'd', 'gap', 'pi', 'w_star', 'u', 'u_rate', 'm', 'mc', 'theta'}, ...
    {'output', 'aggregate demand', 'demand-supply gap', 'inflation', ...
     'reset wage', 'unemployment level', 'unemployment rate gap', 'matches', 'marginal cost', 'labor market tightness'});
for i = 1:n_jump
    var_name = jump_var_names_decl{i};
    if isKey(jump_descriptions, var_name)
        desc = jump_descriptions(var_name);
    else
        desc = var_name;
    end
    fprintf(fid, '| %d | `%s` | %s |\n', i-1, var_name, desc);
end
fprintf(fid, '\n');

fprintf(fid, '### Shocks (columns in B_s, D)\n\n');
fprintf(fid, '| Index | Shock | Description | ρ | σ |\n');
fprintf(fid, '|-------|-------|-------------|---|---|\n');
shock_descriptions = {'technology', 'monetary policy', 'demand/preference', ...
    'price-setting / cost-push', 'labor force', ...
    'separation', 'hours worked', 'investment-specific'};
for i = 1:M_.exo_nbr
    fprintf(fid, '| %d | `%s` | %s | %.3f | %.4f |\n', ...
        i-1, exo_names{i}, shock_descriptions{min(i, length(shock_descriptions))}, ...
        shock_data{i, 3}, shock_data{i, 2});
end
fprintf(fid, '\n');

fprintf(fid, '## 4. Java Sketch\n\n');
fprintf(fid, 'A sketch of the state-space step. The implementation is\n');
fprintf(fid, '`simpaths.model.macro.DSGEModel`, which multiplies each σ-unit shock by its σ\n');
fprintf(fid, 'before `eps` enters the matrices.\n\n');
fprintf(fid, '**IMPORTANT:** All indices in this documentation are **0-based** (Java convention).\n\n');
fprintf(fid, '```java\n');
fprintf(fid, 'public class DSGEPolicyFunction {\n');
fprintf(fid, '    // Matrix dimensions\n');
fprintf(fid, '    private static final int N_STATE = %d;\n', n_state);
fprintf(fid, '    private static final int N_JUMP = %d;\n', n_jump);
fprintf(fid, '    private static final int N_SHOCK = %d;\n', M_.exo_nbr);
fprintf(fid, '    \n');
fprintf(fid, '    // State-space matrices\n');
fprintf(fid, '    private double[][] A;   // %d × %d (state transition)\n', n_state, nspred);
fprintf(fid, '    private double[][] Bs;  // %d × %d (shock → states)\n', n_state, M_.exo_nbr);
fprintf(fid, '    private double[][] C;   // %d × %d (lagged states → jumps)\n', n_jump, nspred);
fprintf(fid, '    private double[][] D;   // %d × %d (shocks → jumps)\n', n_jump, M_.exo_nbr);
fprintf(fid, '    \n');
fprintf(fid, '    // State variable indices (within s vector)\n');
for i = 1:n_state
    fprintf(fid, '    public static final int S_%s = %d;\n', upper(state_var_names_decl{i}), i-1);
end
fprintf(fid, '    \n');
fprintf(fid, '    // Jump variable indices (within y vector)\n');
for i = 1:n_jump
    fprintf(fid, '    public static final int Y_%s = %d;\n', upper(jump_var_names_decl{i}), i-1);
end
fprintf(fid, '    \n');
fprintf(fid, '    // Shock indices\n');
for i = 1:M_.exo_nbr
    fprintf(fid, '    public static final int EPS_%s = %d;\n', upper(strrep(exo_names{i}, 'eps_', '')), i-1);
end
fprintf(fid, '    \n');
fprintf(fid, '    /**\n');
fprintf(fid, '     * Update state variables: s_new = A * s_old + Bs * eps\n');
fprintf(fid, '     */\n');
fprintf(fid, '    public double[] updateStates(double[] sOld, double[] eps) {\n');
fprintf(fid, '        double[] sNew = new double[N_STATE];\n');
fprintf(fid, '        for (int i = 0; i < N_STATE; i++) {\n');
fprintf(fid, '            sNew[i] = 0.0;\n');
fprintf(fid, '            for (int j = 0; j < N_STATE; j++) {\n');
fprintf(fid, '                sNew[i] += A[i][j] * sOld[j];\n');
fprintf(fid, '            }\n');
fprintf(fid, '            for (int j = 0; j < N_SHOCK; j++) {\n');
fprintf(fid, '                sNew[i] += Bs[i][j] * eps[j];\n');
fprintf(fid, '            }\n');
fprintf(fid, '        }\n');
fprintf(fid, '        return sNew;\n');
fprintf(fid, '    }\n');
fprintf(fid, '    \n');
fprintf(fid, '    /**\n');
fprintf(fid, '     * Compute jump variables: y = C * s_old + D * eps\n');
fprintf(fid, '     */\n');
fprintf(fid, '    public double[] computeJumps(double[] sOld, double[] eps) {\n');
fprintf(fid, '        double[] y = new double[N_JUMP];\n');
fprintf(fid, '        for (int i = 0; i < N_JUMP; i++) {\n');
fprintf(fid, '            y[i] = 0.0;\n');
fprintf(fid, '            for (int j = 0; j < N_STATE; j++) {\n');
fprintf(fid, '                y[i] += C[i][j] * sOld[j];\n');
fprintf(fid, '            }\n');
fprintf(fid, '            for (int j = 0; j < N_SHOCK; j++) {\n');
fprintf(fid, '                y[i] += D[i][j] * eps[j];\n');
fprintf(fid, '            }\n');
fprintf(fid, '        }\n');
fprintf(fid, '        return y;\n');
fprintf(fid, '    }\n');
fprintf(fid, '    \n');
fprintf(fid, '    /**\n');
fprintf(fid, '     * Get wage and inflation, both state variables, for the\n');
fprintf(fid, '     * microsimulation.\n');
fprintf(fid, '     */\n');
fprintf(fid, '    public double[] getMicrosimFeedback(double[] sOld, double[] eps) {\n');
fprintf(fid, '        double[] sNew = updateStates(sOld, eps);\n');
fprintf(fid, '        \n');
fprintf(fid, '        double w = sNew[S_W];    // wage is a state\n');
fprintf(fid, '        double pi = sNew[S_PI];  // inflation is a state too (indexation)\n');
fprintf(fid, '        \n');
fprintf(fid, '        return new double[]{w, pi};  // to the microsimulation\n');
fprintf(fid, '    }\n');
fprintf(fid, '}\n');
fprintf(fid, '```\n\n');

fprintf(fid, '## 5. Interface with SimPaths\n\n');
fprintf(fid, '- SimPaths sends nothing to the DSGE; its shocks come from a scenario only\n');
fprintf(fid, '- SimPaths receives the wage deviation (Δw) and the inflation deviation (Δπ)\n');
fprintf(fid, '- `irfs_eps_L.csv` and `irfs_eps_h.csv` hold the responses to one-σ labour-force\n');
fprintf(fid, '  and hours innovations, which decay at the calibrated ρ of the table above\n\n');

fprintf(fid, '## 6. Usage Notes\n\n');
fprintf(fid, '1. **Units:** All variables are in %% deviations from steady state\n');
fprintf(fid, '2. **Frequency:** Quarterly (DSGEModel.java runs 4 quarters and averages for annual)\n');
fprintf(fid, '3. **Causality:** One-way coupling:\n');
fprintf(fid, '   - Scenario → DSGE: shocks in standard-deviation units (none from SimPaths)\n');
fprintf(fid, '   - DSGE → SimPaths: Wage (Δw) and inflation (Δπ)\n');
fprintf(fid, '4. **Steady state:** All zeros (log-linearized model)\n');
fprintf(fid, '5. **Indexing:** JSON uses 1-based (MATLAB), Java code uses 0-based\n\n');

fclose(fid);
fprintf('    ✓ SIMPATHS_INTERFACE.md\n');

%% =========================================================================
%% 9. COPY RAMSEY GROWTH MODEL FILES (NEW)
%% =========================================================================
%
% The Ramsey growth model provides secular trend paths alongside the DSGE
% business cycle model. SimPaths needs these files for:
%   - Computing forward wage/output trends (RamseyTrendModel.java)
%   - Cross-validation of Java vs MATLAB implementations
%   - Initializing the capital stock at the terminal estimation sample value

fprintf('\n>>> Copying Ramsey growth model files...\n');

% Use export_country already inferred from M_.fname at script start
country = export_country;
growth_json = sprintf('growth_params_%s.json', country);
growth_ref  = artifact_path('output', sprintf('growth_model_reference_%s.csv', lower(country)));
growth_mat  = artifact_path('output', sprintf('ramsey_trend_%s.mat', lower(country)));

% growth_params JSON should already exist in the bundle folder as
% the calibrated runtime copy written by calibrate_growth_model.m.
growth_json_path = fullfile(output_dir, growth_json);
if exist(growth_json_path, 'file')
    fprintf('    ✓ %s (already in export folder)\n', growth_json);
else
    fprintf('    ⚠ %s not found in %s (run calibrate_growth_model first)\n', growth_json, output_dir);
end

if exist(growth_ref, 'file')
    copyfile(growth_ref, fullfile(output_dir, basename_of(growth_ref)));
    fprintf('    ✓ %s\n', growth_ref);
else
    fprintf('    ⚠ %s not found (run ramsey_trend first)\n', growth_ref);
end

% Extract and export terminal K and A values for SimPaths initialization.
% The writer is shared with export_ramsey_for_simpaths.m so the Ramsey-only
% refresh path cannot emit a different contract (see write_growth_terminal_state.m).
if exist(growth_mat, 'file')
    write_growth_terminal_state(growth_mat, output_dir);
else
    fprintf('    ⚠ %s not found (run ramsey_trend first)\n', growth_mat);
end

% Copy population projection file (if built by build_population_projection.m):
% time,N, located by date on the Java side.
pop_proj_file = artifact_path('output', sprintf('population_projection_%s.csv', export_country));
if exist(pop_proj_file, 'file')
    copyfile(pop_proj_file, fullfile(output_dir, basename_of(pop_proj_file)));
    fprintf('    ✓ %s (SimPaths-consistent demographic path)\n', pop_proj_file);
else
    fprintf('    ℹ %s not found (using default N extrapolation)\n', pop_proj_file);
end

% Hours projection over the SimPaths horizon. Shared writer, so the Ramsey-only
% refresh path cannot emit a different contract (see write_hours_projection.m).
hours_info = write_hours_projection(growth_mat, output_dir, export_country);
if hours_info.written
    fprintf('    ✓ %s (planner hours path, %d quarters from %s)\n', ...
            basename_of(hours_info.file), hours_info.quarters, hours_info.handoff_time);
else
    fprintf('    ⚠ %s not found; cannot emit hours projection (run ramsey_trend first)\n', growth_mat);
end

% Contract #18: name the tracked inputs this bundle was built from, so the
% Java test can tell a stale bundle from a current one without MATLAB.
prov_paths = get_growth_params_paths(export_country);
write_bundle_provenance(output_dir, export_country, 'export_dynamic_for_simpaths', { ...
    sprintf('Macromodel_%s_frozen.mod', export_country), 't'; ...
    artifact_path('data', sprintf('data_for_dynare_%s.csv', export_country)), 't'; ...
    artifact_path('data', sprintf('data_for_dynare_levels_%s.csv', export_country)), 't'; ...
    prov_paths.source, 't'; ...
    'Macromodel_template.mod', 't'; ...
    fullfile('country_params', sprintf('%s_calibration.mod', export_country)), 't'; ...
    model_location().population_workbook, 'b'});

% A partial or mixed bundle must not reach SimPaths. Last thing the export
% does, so a failure here means nothing downstream ran on it.
verify_export_bundle(output_dir, export_country, 'dsge', true);

%% =========================================================================
%% 10. SUMMARY
%% =========================================================================

fprintf('\n');
fprintf('╔%s╗\n', repmat('═', 1, box_width));
print_box_line('EXPORT COMPLETE', box_width, 'center');
fprintf('╚%s╝\n', repmat('═', 1, box_width));
fprintf('\n');
fprintf('Output directory: %s/\n', strrep(output_dir, '\', '/'));
fprintf('\n');
fprintf('State-space representation:\n');
fprintf('  s_t = A * s_{t-1} + B_s * ε_t    (state transition)\n');
fprintf('  y_t = C * s_{t-1} + D   * ε_t    (jump variables)\n');
fprintf('\n');
fprintf('Files created:\n');
fprintf('  📄 policy_A.csv         - State transition A (%d × %d)\n', n_state, nspred);
fprintf('  📄 policy_Bs.csv        - Shock → states B_s (%d × %d)\n', n_state, M_.exo_nbr);
fprintf('  📄 policy_C.csv         - Lagged states → jumps C (%d × %d)\n', n_jump, nspred);
fprintf('  📄 policy_D.csv         - Shocks → jumps D (%d × %d)\n', n_jump, M_.exo_nbr);
fprintf('  📄 policy_full_ghx.csv  - Full response matrix (reference only - no reader in SimPaths)\n');
fprintf('  📄 policy_full_ghu.csv  - Full shock matrix (reference only - no reader in SimPaths)\n');
fprintf('  📄 steady_state.csv     - Steady state values\n');
fprintf('  📄 shock_params.csv     - Shock AR(1) params and std devs\n');
fprintf('  📄 model_info.json      - Variable names and indices\n');
fprintf('  📄 irfs_eps_*.csv       - Impulse response functions\n');
fprintf('  📄 SIMPATHS_INTERFACE.md - Integration documentation\n');
fprintf('  📄 growth_params_%s.json - Ramsey growth model parameters\n', country);
fprintf('  📄 growth_model_reference_%s.csv - Reference output for Java cross-validation\n', lower(country));
fprintf('  📄 growth_terminal_state.json - Terminal K, A for SimPaths init\n');
fprintf('\n');
fprintf('Variable classification:\n');
fprintf('  State variables (%d): ', n_state);
fprintf('%s ', state_var_names_decl{:});
fprintf('\n');
fprintf('  Jump variables (%d): ', n_jump);
fprintf('%s ', jump_var_names_decl{:});
fprintf('\n');
fprintf('\n');
fprintf('Key for SimPaths:\n');
fprintf('  - Wage (w) is a STATE → extract from s_t directly\n');
fprintf('  - Inflation (π) is a STATE too → extract from s_t\n');
fprintf('\n');
fprintf('Next steps:\n');
fprintf('  1. Bundle written to %s/, which SimPaths reads directly\n', strrep(output_dir, '\', '/'));
fprintf('  2. Run the macro tests: mvn -q test -Dtest=''simpaths.model.macro.*Test''\n');
fprintf('  3. Commit the bundle with the inputs it was built from (BundleProvenanceTest)\n');
fprintf('\n');
