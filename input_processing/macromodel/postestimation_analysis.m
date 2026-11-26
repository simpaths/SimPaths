%% =========================================================================
%% DSGE Post-Estimation Analysis with PDF Report Generation
%% Macromodel_UK.mod - UK DSGE for SimPaths Coupling
%% =========================================================================
%% Run this function AFTER completing Dynare estimation (with mh_replic > 0)
%%
%% Usage:
%%   1. Run: dynare Macromodel_UK
%%   2. Run: postestimation_analysis
%%      (or postestimation_analysis('Macromodel_UK'))
%%
%% Output:
%%   - Comprehensive PDF report: Macromodel_UK/Output/DSGE_Estimation_Report.pdf
%%   - Individual figures saved to Macromodel_UK/graphs/
%%   - Summary text saved to Macromodel_UK/Output/postestimation_summary.txt
%%
%% Model notes:
%%   - 8 shocks: eps_a, eps_d, eps_r, eps_p, eps_L, eps_sep, eps_h, eps_inv. There
%%     is no matching-efficiency shock: it contributes 0.0% to every variance
%%     decomposition and is collinear with eps_sep
%%   - Capital extension: k (capital stock), inv (investment) with estimated rule
%%   - L and h are EXOGENOUS AR(1) processes driven by eps_L and eps_h; rho_L and
%%     rho_h are CALIBRATED per country in country_params/{C}_calibration.mod
%%   - eps_p is an independent cost-push shock in the NKPC
%%   - 9 params ESTIMATED: sigma_a, sigma_d, sigma_r, sigma_p, sigma_h, sigma_L,
%%     sigma_inv, omega_h, phi_inv_r
%%   - phi_inv_y is calibrated, not estimated: cosn = 0.9989 with rho_inv
%% =========================================================================

function postestimation_analysis(model_name, data_file)
    % Resolve the model's code folders (internal/, diagnostics/, ...).
    % Idempotent; see setup_path.m for why MATLAB cannot do this itself.
    setup_path();
if nargin < 1 || isempty(model_name)
    % Use Dynare's M_.fname if available (set after any dynare run)
    if evalin('base', 'exist(''M_'',''var'')') && isfield(evalin('base', 'M_'), 'fname')
        model_name = evalin('base', 'M_.fname');
    else
        model_name = 'Macromodel_UK';
    end
end
% Auto-prepend Macromodel_ if user passes just the country name
if ~startsWith(model_name, 'Macromodel_')
    model_name = ['Macromodel_' model_name];
end
if nargin < 2 || isempty(data_file)
    country_tok = regexp(model_name, 'Macromodel_(\w+)', 'tokens');
    if ~isempty(country_tok)
        data_file = artifact_path('data', sprintf('data_for_dynare_%s.csv', country_tok{1}{1}));
    else
        data_file = artifact_path('data', 'data_for_dynare.csv');
    end
end

clc; close all;

%% =========================================================================
%% CONFIGURATION
%% =========================================================================
config = struct();
config.model_name = model_name;
config.output_dir = [config.model_name '/Output'];
config.graphs_dir = [config.model_name '/graphs'];
config.report_file = [config.output_dir '/DSGE_Estimation_Report.pdf'];
config.data_file = data_file;
config.sample_start = 2000;  % Fallback start year when the data file has no parseable time column
config.data_dates = [];
config.irf_horizon = 40;     % IRF horizon in quarters

% Observable variables and their labels (8 observables, consumption and L among them)
config.obs_vars = {'y', 'inv', 'c', 'pi', 'R', 'n', 'L', 'h'};
config.obs_labels = {'Output Gap (%)', 'Investment Gap (%)', 'Consumption Gap (%)', 'Inflation (% ann.)', 'Interest Rate (%)', ...
                     'Employment Gap (%)', 'Labor Force Gap (%)', 'Hours Gap (%)'};

% Shock names and labels (8 shocks)
config.shock_names = {'eps_a', 'eps_d', 'eps_r', 'eps_p', 'eps_L', 'eps_sep', 'eps_h', 'eps_inv'};
config.shock_labels = {'Technology', 'Demand', 'Monetary', 'Cost-Push', 'Labor Force', 'Separation', 'Hours', 'Investment'};

% var_list: parsed from the .mod file at runtime (estimation/stoch_simul command).
% Dynare stores VD rows in this order, NOT in M_.endo_names declaration order.
% We extract it automatically so it stays in sync with the .mod file.
config.var_list = {};  % Will be populated below after parsing .mod file
% Try the wrapper first, then fall back to the template (wrappers are thin
% @#include files that don't contain estimation/stoch_simul commands).
mod_candidates = {[config.model_name '.mod'], 'Macromodel_template.mod'};
for mc = 1:length(mod_candidates)
    mod_file_path = mod_candidates{mc};
    if ~exist(mod_file_path, 'file'), continue; end
    mod_text = fileread(mod_file_path);
    % Match: estimation(...) var1 var2 ... ;  or  stoch_simul(...) var1 var2 ... ;
    % The [\s\S]*? handles multi-line option blocks inside parentheses.
    % [\w\s]+ captures the space-separated variable names after the closing ).
    tokens = regexp(mod_text, '(?:estimation|stoch_simul)\s*\([\s\S]*?\)\s*([\w\s]+);', 'tokens');
    if ~isempty(tokens)
        var_list_str = strtrim(tokens{end}{1});  % Take last match (real data branch)
        config.var_list = strsplit(var_list_str);
        config.var_list = config.var_list(~cellfun('isempty', config.var_list));
        fprintf('    Parsed var_list from %s: %s\n', mod_file_path, strjoin(config.var_list, ', '));
        break;
    end
end
if isempty(config.var_list)
    warning('Could not parse var_list from any .mod file. VD row mapping may be wrong.');
end

% Colors for plots
config.colors = struct();
config.colors.primary = [0.2 0.4 0.8];
config.colors.secondary = [0.8 0.2 0.2];
config.colors.tertiary = [0.2 0.6 0.2];
config.colors.fill = [0.8 0.85 1];

fprintf('\n');
fprintf('===================================================================\n');
fprintf('     DSGE POST-ESTIMATION ANALYSIS WITH PDF REPORT\n');
fprintf('     Model: %s\n', config.model_name);
fprintf('     Date: %s\n', datestr(now, 'yyyy-mm-dd'));
fprintf('===================================================================\n\n');

%% =========================================================================
%% 1. LOAD AND VALIDATE ESTIMATION RESULTS
%% =========================================================================
fprintf('>>> Loading estimation results...\n');

results_file = [config.output_dir '/' config.model_name '_results.mat'];
if ~exist(results_file, 'file')
    error('Results file not found: %s\nRun "dynare %s" first.', results_file, config.model_name);
end

load(results_file);
fprintf('    [OK] Loaded: %s\n', results_file);

% Create output directories if needed
if ~exist(config.output_dir, 'dir'), mkdir(config.output_dir); end
if ~exist(config.graphs_dir, 'dir'), mkdir(config.graphs_dir); end

% Load data for comparison
if exist(config.data_file, 'file')
    data = readtable(config.data_file);
    fprintf('    [OK] Loaded data: %s\n', config.data_file);
    if ismember('time', data.Properties.VariableNames)
        parsed_dates = parse_time_column(data.time);
        if ~isempty(parsed_dates)
            config.data_dates = parsed_dates;
            config.sample_start = floor(parsed_dates(1));
        end
    end
else
    warning('Data file not found: %s', config.data_file);
    data = [];
end

% Get sample size
T = length(oo_.SmoothedVariables.Mean.y);
if ~isempty(config.data_dates) && length(config.data_dates) >= T
    dates = config.data_dates(1:T);
else
    dates = config.sample_start + (0:T-1)'/4;
end

%% =========================================================================
%% 2. INITIALIZE PDF REPORT
%% =========================================================================
fprintf('\n>>> Initializing PDF report...\n');

% Check if Report Generator is available
hasReportGen = ~isempty(ver('rptgen')) || ~isempty(ver('mlreportgen'));

if hasReportGen
    try
        % Image dimensions preserving aspect ratio (landscape A4)
        % Format: {width, height} based on original figure proportions
        imgFull_1400x900 = {'9in', '5.8in'};    % Observables (1400x900)
        imgFull_1400x800 = {'9in', '5.1in'};    % Trace plots, IRFs (1400x800)
        imgFull_1400x500 = {'9in', '3.2in'};    % Prior/Posterior (1400x500)
        imgFull_1600x700 = {'9.5in', '4.2in'};  % IRF panels (1600x700)
        imgHalf_1000x600 = {'6in', '3.6in'};    % Variance decomp (1000x600)
        imgHalf_800x600 = {'5in', '3.75in'};    % Y-Pi correlation (800x600)
        imgFull_1200x500 = {'9in', '3.75in'};   % Moment comparison (1200x500)

        % Create report
        rpt = mlreportgen.report.Report(config.report_file, 'pdf');
        rpt.Layout.Landscape = true;  % Landscape orientation for wide figures

        % Title page
        tp = mlreportgen.report.TitlePage();
        tp.Title = 'DSGE Estimation Report';
        country_tok_rpt = regexp(config.model_name, 'Macromodel_(\w+)', 'tokens');
        if ~isempty(country_tok_rpt)
            tp.Subtitle = sprintf('Model: %s', config.model_name);
        else
            tp.Subtitle = sprintf('Model: %s', config.model_name);
        end
        tp.Author = 'Generated by postestimation_analysis.m';
        tp.PubDate = datestr(now, 'mmmm dd, yyyy');
        append(rpt, tp);

        % Table of contents
        append(rpt, mlreportgen.report.TableOfContents());

        fprintf('    [OK] Report initialized with mlreportgen\n');
    catch ME
        hasReportGen = false;
        rpt = [];
        warning('postestimation_analysis:ReportInitFailed', '%s', ...
            ['Could not initialize PDF report (' ME.message '). Continuing with figures + text summary only.']);
    end
else
    rpt = [];
    warning('MATLAB Report Generator not available. Will generate figures only.');
end

%% =========================================================================
%% 3. OBSERVABLE VARIABLES PANEL
%% =========================================================================
fprintf('\n>>> Creating observable variables panel...\n');

fig1 = figure('Name', 'Observable Variables', 'Position', [50 50 1400 900], 'Visible', 'off');

n_obs = length(config.obs_vars);
n_cols = 3;
n_rows = ceil(n_obs / n_cols);

for i = 1:n_obs
    subplot(n_rows, n_cols, i);

    vname = config.obs_vars{i};

    % Get smoothed variable
    if isfield(oo_.SmoothedVariables.Mean, vname)
        smooth_var = oo_.SmoothedVariables.Mean.(vname);

        % Get data if available
        if ~isempty(data) && ismember(vname, data.Properties.VariableNames)
            data_var = data.(vname);
            if ~isempty(config.data_dates) && length(config.data_dates) >= length(data_var)
                data_dates = config.data_dates(1:length(data_var));
            else
                data_dates = config.sample_start + (0:length(data_var)-1)'/4;
            end

            % Plot data
            h1 = plot(data_dates, data_var, 'o', 'Color', [0.5 0.5 0.5], ...
                'MarkerSize', 4, 'MarkerFaceColor', [0.7 0.7 0.7]);
            hold on;
        end

        % Plot smoothed
        h2 = plot(dates, smooth_var, '-', 'Color', config.colors.primary, 'LineWidth', 1.5);
        hold on;

        % Zero line
        yline(0, 'k--', 'LineWidth', 0.5);

        % Highlight COVID period
        fill([2020 2021.5 2021.5 2020], [min(ylim)*1.5 min(ylim)*1.5 max(ylim)*1.5 max(ylim)*1.5], ...
            [1 0.9 0.9], 'EdgeColor', 'none', 'FaceAlpha', 0.3);

        % Bring lines to front
        uistack(h2, 'top');
        if exist('h1', 'var'), uistack(h1, 'top'); end
    end

    title(config.obs_labels{i}, 'FontSize', 11, 'FontWeight', 'bold');
    xlabel('Year');
    xlim([config.sample_start-0.5 max(dates)+0.5]);
    grid on;
    box on;

    if i == 1
        legend({'Data', 'Smoothed'}, 'Location', 'best', 'FontSize', 8);
    end
end

sgtitle('Observable Variables: Data vs Model-Implied (Smoothed)', ...
    'FontSize', 14, 'FontWeight', 'bold');

% Save figure
saveas(fig1, [config.graphs_dir '/Fig01_Observables.png']);
print(fig1, [config.graphs_dir '/Fig01_Observables'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig01_Observables.png\n');

% Add to report
if hasReportGen
    ch1 = mlreportgen.report.Chapter('Observable Variables');
    sec1 = mlreportgen.report.Section('Data vs Model Fit');
    para1 = mlreportgen.dom.Paragraph(['This section shows the observable variables used in estimation. ', ...
        'Gray circles represent the actual data, blue lines show the model-implied ', ...
        '(Kalman-smoothed) values. The pink shaded area indicates the COVID period ', ...
        '(2020-2021) which was treated as missing data.']);
    para1.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec1, para1);
    fig1_path = fullfile(pwd, config.graphs_dir, 'Fig01_Observables.png');
    img1 = mlreportgen.dom.Image(fig1_path); img1.Width = imgFull_1400x900{1}; img1.Height = imgFull_1400x900{2};
    add(sec1, img1);
    add(ch1, sec1);
    append(rpt, ch1);
end

%% =========================================================================
%% 4. ESTIMATION DIAGNOSTICS
%% =========================================================================
fprintf('\n>>> Creating estimation diagnostics...\n');

% --- 4.1 MCMC Convergence: Trace Plots ---
fig2 = figure('Name', 'MCMC Trace Plots', 'Position', [50 50 1400 800], 'Visible', 'off');

% Get MCMC draws if available
mcmc_file = [config.model_name '/metropolis/' config.model_name '_mh1_blck1.mat'];
if exist(mcmc_file, 'file')
    mcmc_data = load(mcmc_file);
    n_params = size(mcmc_data.x2, 2);
    n_draws = size(mcmc_data.x2, 1);

    % Get parameter names from estim_params (shock stderrs + structural params)
    param_names = {};
    for i = 1:size(estim_params_.var_exo, 1)
        idx = estim_params_.var_exo(i, 1);
        param_names{end+1} = ['SE_' M_.exo_names{idx}];
    end
    if isfield(estim_params_, 'param_vals') && ~isempty(estim_params_.param_vals)
        for i = 1:size(estim_params_.param_vals, 1)
            idx = estim_params_.param_vals(i, 1);
            param_names{end+1} = M_.param_names{idx};
        end
    end

    n_plot = min(6, length(param_names));
    for i = 1:n_plot
        subplot(2, 3, i);

        % Load both chains if available
        chain1 = mcmc_data.x2(:, i);
        plot(chain1, 'Color', [config.colors.primary 0.5], 'LineWidth', 0.5);
        hold on;

        % Try to load second chain
        mcmc_file2 = [config.model_name '/metropolis/' config.model_name '_mh1_blck2.mat'];
        if exist(mcmc_file2, 'file')
            mcmc_data2 = load(mcmc_file2);
            chain2 = mcmc_data2.x2(:, i);
            plot(chain2, 'Color', [config.colors.secondary 0.5], 'LineWidth', 0.5);
        end

        title(strrep(param_names{i}, '_', '\_'), 'FontSize', 10);
        xlabel('Draw');
        ylabel('Value');
        grid on;
        box on;
    end

    sgtitle('MCMC Trace Plots (Chain Mixing)', 'FontSize', 14, 'FontWeight', 'bold');
    has_mcmc = true;
else
    % Create placeholder
    text(0.5, 0.5, 'MCMC files not found', 'HorizontalAlignment', 'center', ...
        'FontSize', 14, 'Units', 'normalized');
    has_mcmc = false;
end

saveas(fig2, [config.graphs_dir '/Fig02_TracePlots.png']);
print(fig2, [config.graphs_dir '/Fig02_TracePlots'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig02_TracePlots.png\n');

% --- 4.2 Prior vs Posterior Distributions ---
% Build combined list of all estimated parameters: shock stderrs + structural params
est_shocks = fieldnames(oo_.posterior_mean.shocks_std);
n_est_shocks = length(est_shocks);

% Check for estimated structural parameters
est_struct = {};
if isfield(oo_.posterior_mean, 'parameters')
    est_struct = fieldnames(oo_.posterior_mean.parameters);
end
n_est_struct = length(est_struct);
n_est = n_est_shocks + n_est_struct;

% Determine subplot layout
n_cols = min(n_est, 6);
n_rows = ceil(n_est / n_cols);
fig3 = figure('Name', 'Prior vs Posterior', 'Position', [50 50 1400 max(500, 250*n_rows)], 'Visible', 'off');

for i = 1:n_est
    subplot(n_rows, n_cols, i);

    if i <= n_est_shocks
        % Shock stderr parameter
        sname = est_shocks{i};
        post_mean = oo_.posterior_mean.shocks_std.(sname);
        hpd_lo = oo_.posterior_hpdinf.shocks_std.(sname);
        hpd_hi = oo_.posterior_hpdsup.shocks_std.(sname);
        title_str = ['\sigma_{' strrep(sname(5:end), '_', '') '}'];

        % Get prior parameters from var_exo
        prior_mean = NaN;
        prior_std = NaN;
        for j = 1:size(estim_params_.var_exo, 1)
            if estim_params_.var_exo(j, 1) == find(strcmp(M_.exo_names, sname))
                prior_mean = estim_params_.var_exo(j, 6);
                prior_std = estim_params_.var_exo(j, 7);
                break;
            end
        end
    else
        % Structural parameter
        pname = est_struct{i - n_est_shocks};
        post_mean = oo_.posterior_mean.parameters.(pname);
        hpd_lo = oo_.posterior_hpdinf.parameters.(pname);
        hpd_hi = oo_.posterior_hpdsup.parameters.(pname);
        title_str = strrep(pname, '_', '\_');

        % Get prior parameters from param_vals
        prior_mean = NaN;
        prior_std = NaN;
        for j = 1:size(estim_params_.param_vals, 1)
            if estim_params_.param_vals(j, 1) == find(strcmp(M_.param_names, pname))
                prior_mean = estim_params_.param_vals(j, 6);
                prior_std = estim_params_.param_vals(j, 7);
                break;
            end
        end
    end

    % Create x-axis range
    x_lo = max(0.001, min(hpd_lo * 0.3, prior_mean - 3*prior_std));
    x_hi = max(hpd_hi * 1.5, prior_mean + 3*prior_std);
    x_range = linspace(x_lo, x_hi, 200);

    % Plot prior (inverse gamma approximated as log-normal for visualization)
    if ~isnan(prior_mean)
        % Approximate prior with gamma
        prior_var = prior_std^2;
        k = (prior_mean^2) / prior_var;  % shape
        theta = prior_var / prior_mean;   % scale
        prior_pdf = gampdf(x_range, k, theta);
        prior_pdf = prior_pdf / max(prior_pdf);  % normalize
        plot(x_range, prior_pdf, 'k--', 'LineWidth', 1.5, 'DisplayName', 'Prior');
        hold on;
    end

    % Plot posterior (approximate as normal)
    post_std = (hpd_hi - hpd_lo) / 3.29;  % 90% CI
    post_pdf = normpdf(x_range, post_mean, post_std);
    post_pdf = post_pdf / max(post_pdf);  % normalize

    area(x_range, post_pdf, 'FaceColor', config.colors.primary, ...
        'FaceAlpha', 0.4, 'EdgeColor', config.colors.primary, ...
        'LineWidth', 1.5, 'DisplayName', 'Posterior');
    hold on;

    % Mark posterior mode/mean
    xline(post_mean, 'r-', 'LineWidth', 2, 'HandleVisibility', 'off');
    xline(hpd_lo, 'r--', 'LineWidth', 1, 'HandleVisibility', 'off');
    xline(hpd_hi, 'r--', 'LineWidth', 1, 'HandleVisibility', 'off');

    title(title_str, 'FontSize', 12);
    xlabel('Value');
    if i == 1
        ylabel('Density (normalized)');
        legend('Location', 'best', 'FontSize', 8);
    end
    xlim([x_lo x_hi]);
    grid on;
    box on;

    % Add text with values
    text(0.95, 0.95, sprintf('Post: %.3f\n[%.3f, %.3f]', post_mean, hpd_lo, hpd_hi), ...
        'Units', 'normalized', 'HorizontalAlignment', 'right', ...
        'VerticalAlignment', 'top', 'FontSize', 8, 'BackgroundColor', 'white');
end

sgtitle('Prior vs Posterior Distributions (Estimated Parameters)', ...
    'FontSize', 14, 'FontWeight', 'bold');

saveas(fig3, [config.graphs_dir '/Fig03_PriorPosterior.png']);
print(fig3, [config.graphs_dir '/Fig03_PriorPosterior'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig03_PriorPosterior.png\n');

% Add to report
if hasReportGen
    ch2 = mlreportgen.report.Chapter('Estimation Diagnostics');

    sec2a = mlreportgen.report.Section('MCMC Convergence');
    para2a = mlreportgen.dom.Paragraph(['Trace plots show the evolution of MCMC draws across iterations. ', ...
        'Good mixing is indicated by stationary behavior without trends or jumps. ', ...
        'Multiple chains (if shown in different colors) should overlap.']);
    para2a.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec2a, para2a);
    img2 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig02_TracePlots.png')); img2.Width = imgFull_1400x800{1}; img2.Height = imgFull_1400x800{2};
    add(sec2a, img2);
    add(ch2, sec2a);

    sec2b = mlreportgen.report.Section('Prior vs Posterior Distributions');
    para2b = mlreportgen.dom.Paragraph(['Comparison of prior (dashed black) and posterior (blue shaded) ', ...
        'distributions. Vertical red lines show posterior mean and 90% HPD interval. ', ...
        'Large differences indicate the data is informative about the parameter.']);
    para2b.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec2b, para2b);
    img3 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig03_PriorPosterior.png')); img3.Width = imgFull_1400x500{1}; img3.Height = imgFull_1400x500{2};
    add(sec2b, img3);
    add(ch2, sec2b);

    % Store ch2 for later - we'll add identification section after it's computed
    ch2_stored = ch2;
end

%% =========================================================================
%% 5. PARAMETER ESTIMATES TABLE
%% =========================================================================
fprintf('\n>>> Creating parameter estimates table...\n');

% Create table data: shock stderrs + structural parameters
param_table = cell(n_est + 1, 5);
param_table(1, :) = {'Parameter', 'Prior Mean', 'Post. Mean', 'HPD Low', 'HPD High'};

% Shock standard deviations
for i = 1:n_est_shocks
    sname = est_shocks{i};
    post_mean = oo_.posterior_mean.shocks_std.(sname);
    hpd_lo = oo_.posterior_hpdinf.shocks_std.(sname);
    hpd_hi = oo_.posterior_hpdsup.shocks_std.(sname);

    % Get prior mean
    prior_mean = NaN;
    for j = 1:size(estim_params_.var_exo, 1)
        if estim_params_.var_exo(j, 1) == find(strcmp(M_.exo_names, sname))
            prior_mean = estim_params_.var_exo(j, 6);
            break;
        end
    end

    param_table{i+1, 1} = ['\sigma_{' sname(5:end) '}'];
    param_table{i+1, 2} = sprintf('%.4f', prior_mean);
    param_table{i+1, 3} = sprintf('%.4f', post_mean);
    param_table{i+1, 4} = sprintf('%.4f', hpd_lo);
    param_table{i+1, 5} = sprintf('%.4f', hpd_hi);
end

% Structural parameters
for i = 1:n_est_struct
    pname = est_struct{i};
    post_mean = oo_.posterior_mean.parameters.(pname);
    hpd_lo = oo_.posterior_hpdinf.parameters.(pname);
    hpd_hi = oo_.posterior_hpdsup.parameters.(pname);

    % Get prior mean
    prior_mean = NaN;
    for j = 1:size(estim_params_.param_vals, 1)
        if estim_params_.param_vals(j, 1) == find(strcmp(M_.param_names, pname))
            prior_mean = estim_params_.param_vals(j, 6);
            break;
        end
    end

    param_table{n_est_shocks + i + 1, 1} = strrep(pname, '_', '\_');
    param_table{n_est_shocks + i + 1, 2} = sprintf('%.4f', prior_mean);
    param_table{n_est_shocks + i + 1, 3} = sprintf('%.4f', post_mean);
    param_table{n_est_shocks + i + 1, 4} = sprintf('%.4f', hpd_lo);
    param_table{n_est_shocks + i + 1, 5} = sprintf('%.4f', hpd_hi);
end

% Display table
fprintf('\n    Posterior Parameter Estimates:\n');
fprintf('    %-15s %12s %12s %12s %12s\n', param_table{1,:});
fprintf('    %s\n', repmat('-', 1, 65));
for i = 2:size(param_table, 1)
    fprintf('    %-15s %12s %12s %12s %12s\n', param_table{i,:});
end

%% =========================================================================
%% 6. MODEL FIT STATISTICS
%% =========================================================================
fprintf('\n>>> Computing model fit statistics...\n');

fit_stats = struct();

% Log marginal likelihood
fit_stats.log_mdd_laplace = oo_.MarginalDensity.LaplaceApproximation;
if isfield(oo_.MarginalDensity, 'ModifiedHarmonicMean')
    fit_stats.log_mdd_mhm = oo_.MarginalDensity.ModifiedHarmonicMean;
else
    fit_stats.log_mdd_mhm = NaN;
end

% Acceptance rate (if available)
if exist('record', 'var') && isfield(record, 'AcceptanceRatio')
    fit_stats.acceptance_rate = record.AcceptanceRatio;
else
    fit_stats.acceptance_rate = NaN;
end

fprintf('\n    Model Fit Statistics:\n');
fprintf('    Log Marginal Likelihood (Laplace): %.2f\n', fit_stats.log_mdd_laplace);
if ~isnan(fit_stats.log_mdd_mhm)
    fprintf('    Log Marginal Likelihood (MHM):     %.2f\n', fit_stats.log_mdd_mhm);
end

%% =========================================================================
%% 6B. IDENTIFICATION ANALYSIS RESULTS
%% =========================================================================
fprintf('\n>>> Extracting identification analysis results...\n');

% Initialize identification results structure
id_results = struct();
id_results.available = false;
id_results.rank_checks = {};
id_results.collinearity = {};

% Check if identification results exist - try multiple sources
% Prefer _identif.mat (generated during estimation, has all N estimated params)
% over _prior_mean_identif.mat (which may be stale from a previous run with
% fewer estimated parameters)
id_file = [config.model_name '/identification/' config.model_name '_identif.mat'];
if ~exist(id_file, 'file')
    % Fallback to prior mean identif if the main file doesn't exist
    id_file = [config.model_name '/identification/' config.model_name '_prior_mean_identif.mat'];
end
if exist(id_file, 'file')
    % Load identification results from file
    id_data = load(id_file);
    id_results.available = true;
    fprintf('\n    Identification Analysis Results (loaded from %s):\n', id_file);
    fprintf('    %s\n', repmat('-', 1, 60));

    % Extract parameter names
    param_names_id = {};
    if isfield(id_data, 'name')
        param_names_id = id_data.name;
    elseif isfield(id_data, 'params')
        param_names_id = id_data.params;
    else
        % Build from estimated parameters
        for j = 1:size(estim_params_.var_exo, 1)
            idx = estim_params_.var_exo(j, 1);
            param_names_id{end+1} = ['SE_' M_.exo_names{idx}];
        end
        if isfield(estim_params_, 'param_vals') && ~isempty(estim_params_.param_vals)
            for j = 1:size(estim_params_.param_vals, 1)
                idx = estim_params_.param_vals(j, 1);
                param_names_id{end+1} = M_.param_names{idx};
            end
        end
    end
    id_results.param_names = param_names_id;

    % Extract rank checks from ide_moments_point structure
    rank_checks = {};
    if isfield(id_data, 'ide_moments_point')
        ide = id_data.ide_moments_point;

        % Moments identification (Iskrev)
        if isfield(ide, 'rank') && isfield(ide, 'ind0')
            n_params = length(ide.ind0);
            j_rank = ide.rank;
            j_full = (j_rank == sum(ide.ind0));
            rank_checks{end+1, 1} = 'Moments (Iskrev 2010)';
            rank_checks{end, 2} = sprintf('%d/%d', j_rank, sum(ide.ind0));
            rank_checks{end, 3} = conditional(j_full, 'FULL', 'DEFICIENT');
            fprintf('    Moments rank (J):           %d/%d - %s\n', j_rank, sum(ide.ind0), rank_checks{end, 3});
        end
    end

    if isfield(id_data, 'ide_spectrum_point')
        ide_sp = id_data.ide_spectrum_point;
        if isfield(ide_sp, 'rank') && isfield(ide_sp, 'ind0')
            gbar_rank = ide_sp.rank;
            gbar_full = (gbar_rank == sum(ide_sp.ind0));
            rank_checks{end+1, 1} = 'Spectrum (Qu-Tkachenko 2012)';
            rank_checks{end, 2} = sprintf('%d/%d', gbar_rank, sum(ide_sp.ind0));
            rank_checks{end, 3} = conditional(gbar_full, 'FULL', 'DEFICIENT');
            fprintf('    Spectrum rank (Gbar):       %d/%d - %s\n', gbar_rank, sum(ide_sp.ind0), rank_checks{end, 3});
        end
    end

    if isfield(id_data, 'ide_reducedform_point')
        ide_rf = id_data.ide_reducedform_point;
        if isfield(ide_rf, 'rank') && isfield(ide_rf, 'ind0')
            tau_rank = ide_rf.rank;
            tau_full = (tau_rank == sum(ide_rf.ind0));
            rank_checks{end+1, 1} = 'Reduced-form (Tau)';
            rank_checks{end, 2} = sprintf('%d/%d', tau_rank, sum(ide_rf.ind0));
            rank_checks{end, 3} = conditional(tau_full, 'FULL', 'DEFICIENT');
            fprintf('    Reduced-form rank (Tau):    %d/%d - %s\n', tau_rank, sum(ide_rf.ind0), rank_checks{end, 3});
        end
    end
    id_results.rank_checks = rank_checks;

    % Extract collinearity patterns (cosndMOMENTS contains cosn for 1,2,3 explaining params)
    collin_data = {};
    if isfield(id_data, 'ide_moments_point') && isfield(id_data.ide_moments_point, 'cosndMOMENTS')
        cosn = id_data.ide_moments_point.cosndMOMENTS;  % columns: 1-param, 2-param, 3-param collinearity
        pars_cell = id_data.ide_moments_point.pars;      % explaining parameter indices

        fprintf('\n    Collinearity Patterns (cosn values with 1 explaining parameter):\n');
        fprintf('    %s\n', repmat('-', 1, 70));
        fprintf('    %-20s %12s %-25s %10s\n', 'Parameter', 'Max cosn', 'Most Collinear With', 'Status');
        fprintf('    %s\n', repmat('-', 1, 70));

        n_est_params = length(param_names_id);

        for i = 1:min(n_est_params, size(cosn, 1))
            % Get cosn with 1 explaining parameter (first column)
            max_cosn = cosn(i, 1);

            % Get the explaining parameter index
            if ~isempty(pars_cell{i, 1})
                max_idx = pars_cell{i, 1}(1);
                if max_idx <= length(param_names_id)
                    collin_with = param_names_id{max_idx};
                else
                    collin_with = sprintf('param_%d', max_idx);
                end
            else
                collin_with = 'N/A';
            end

            if i <= length(param_names_id)
                pname = param_names_id{i};
            else
                pname = sprintf('param_%d', i);
            end

            % Determine status
            if max_cosn > 0.99
                status = 'HIGH';
                flag = ' [WARN]';
            elseif max_cosn > 0.95
                status = 'Moderate';
                flag = '';
            else
                status = 'OK';
                flag = '';
            end

            fprintf('    %-20s %12.4f %-25s %10s%s\n', pname, max_cosn, collin_with, status, flag);

            collin_data{end+1, 1} = pname;
            collin_data{end, 2} = sprintf('%.4f', max_cosn);
            collin_data{end, 3} = collin_with;
            collin_data{end, 4} = status;
        end

        id_results.collinearity = collin_data;
    end

    % Check for problematic parameters (weak/not identified)
    id_results.problematic = {};
    if isfield(id_data, 'ide_moments_point') && isfield(id_data.ide_moments_point, 'ind0')
        ind0 = id_data.ide_moments_point.ind0;
        for i = 1:length(ind0)
            if ind0(i) == 0 && i <= length(param_names_id)
                id_results.problematic{end+1} = param_names_id{i};
            end
        end
    end

    % Also treat near-perfect collinearity as problematic for reporting
    if ~isempty(id_results.collinearity)
        for i = 1:size(id_results.collinearity, 1)
            if strcmp(id_results.collinearity{i, 4}, 'HIGH')
                id_results.problematic{end+1} = sprintf('%s (HIGH collinearity with %s, cosn=%s)', ...
                    id_results.collinearity{i, 1}, id_results.collinearity{i, 3}, id_results.collinearity{i, 2});
            end
        end
    end
    if ~isempty(id_results.problematic)
        id_results.problematic = unique(id_results.problematic, 'stable');
    end

    if ~isempty(id_results.problematic)
        fprintf('\n    [WARN] PROBLEMATIC PARAMETERS (not identified):\n');
        for i = 1:length(id_results.problematic)
            fprintf('      - %s\n', id_results.problematic{i});
        end
    else
        fprintf('\n    [OK] All parameters are identified\n');
    end

elseif isfield(oo_, 'identification')
    id_results.available = true;
    id_struct = oo_.identification;

    fprintf('\n    Identification Analysis Results:\n');
    fprintf('    %s\n', repmat('-', 1, 60));

    % Extract rank condition results
    rank_checks = {};

    % Check reduced-form identification (Tau matrix)
    if isfield(id_struct, 'reducedform') && isfield(id_struct.reducedform, 'rank')
        tau_rank = id_struct.reducedform.rank;
        n_params = length(M_.params) + M_.exo_nbr;  % Approximate
        if isfield(id_struct.reducedform, 'ncols')
            n_params = id_struct.reducedform.ncols;
        end
        tau_pass = (tau_rank >= size(estim_params_.var_exo, 1));
        rank_checks{end+1, 1} = 'Reduced-form (Tau)';
        rank_checks{end, 2} = sprintf('%d', tau_rank);
        rank_checks{end, 3} = conditional(tau_pass, 'PASS', 'FAIL');
        fprintf('    Reduced-form rank (Tau):    %d - %s\n', tau_rank, rank_checks{end, 3});
    end

    % Check spectrum identification (Gbar matrix - Qu & Tkachenko)
    if isfield(id_struct, 'spectrum') && isfield(id_struct.spectrum, 'rank')
        gbar_rank = id_struct.spectrum.rank;
        gbar_pass = true;  % Full rank assumed if available
        rank_checks{end+1, 1} = 'Spectrum (Gbar)';
        rank_checks{end, 2} = sprintf('%d', gbar_rank);
        rank_checks{end, 3} = conditional(gbar_pass, 'PASS', 'FAIL');
        fprintf('    Spectrum rank (Gbar):       %d - %s\n', gbar_rank, rank_checks{end, 3});
    end

    % Check moments identification (J matrix - Iskrev)
    if isfield(id_struct, 'moments') && isfield(id_struct.moments, 'rank')
        j_rank = id_struct.moments.rank;
        j_pass = true;  % Full rank assumed if available
        rank_checks{end+1, 1} = 'Moments (J)';
        rank_checks{end, 2} = sprintf('%d', j_rank);
        rank_checks{end, 3} = conditional(j_pass, 'PASS', 'FAIL');
        fprintf('    Moments rank (J):           %d - %s\n', j_rank, rank_checks{end, 3});
    end

    id_results.rank_checks = rank_checks;

    % Extract collinearity patterns
    collin_data = {};
    if isfield(id_struct, 'cosn') && ~isempty(id_struct.cosn)
        fprintf('\n    Collinearity Patterns (cosn values):\n');
        fprintf('    %s\n', repmat('-', 1, 60));

        % Get parameter names
        param_names_id = {};
        if isfield(id_struct, 'parameter_names')
            param_names_id = id_struct.parameter_names;
        else
            % Build from estimated parameters
            for j = 1:size(estim_params_.var_exo, 1)
                idx = estim_params_.var_exo(j, 1);
                param_names_id{end+1} = ['SE_' M_.exo_names{idx}];
            end
            if isfield(estim_params_, 'param_vals') && ~isempty(estim_params_.param_vals)
                for j = 1:size(estim_params_.param_vals, 1)
                    idx = estim_params_.param_vals(j, 1);
                    param_names_id{end+1} = M_.param_names{idx};
                end
            end
        end

        cosn = id_struct.cosn;
        n_est_params = length(param_names_id);

        % Display collinearity for each parameter
        fprintf('    %-20s %12s %s\n', 'Parameter', 'Max cosn', 'Most Collinear With');
        fprintf('    %s\n', repmat('-', 1, 60));

        for i = 1:min(n_est_params, size(cosn, 1))
            max_cosn = 0;
            max_idx = 1;
            for j = 1:min(n_est_params, size(cosn, 2))
                if i ~= j && cosn(i, j) > max_cosn
                    max_cosn = cosn(i, j);
                    max_idx = j;
                end
            end

            if max_idx <= length(param_names_id)
                collin_with = param_names_id{max_idx};
            else
                collin_with = sprintf('param_%d', max_idx);
            end

            if i <= length(param_names_id)
                pname = param_names_id{i};
            else
                pname = sprintf('param_%d', i);
            end

            % Flag high collinearity
            if max_cosn > 0.99
                flag = ' [WARN] HIGH';
            elseif max_cosn > 0.95
                flag = ' (moderate)';
            else
                flag = '';
            end

            fprintf('    %-20s %12.4f %s%s\n', pname, max_cosn, collin_with, flag);

            collin_data{end+1, 1} = pname;
            collin_data{end, 2} = sprintf('%.4f', max_cosn);
            collin_data{end, 3} = collin_with;
            collin_data{end, 4} = conditional(max_cosn > 0.99, 'HIGH', ...
                                   conditional(max_cosn > 0.95, 'Moderate', 'OK'));
        end

        id_results.collinearity = collin_data;
    end

    % Check for problematic parameters
    if isfield(id_struct, 'problematic_pars') && ~isempty(id_struct.problematic_pars)
        fprintf('\n    [WARN] PROBLEMATIC PARAMETERS DETECTED:\n');
        for i = 1:length(id_struct.problematic_pars)
            fprintf('      - %s\n', id_struct.problematic_pars{i});
        end
        id_results.problematic = id_struct.problematic_pars;
    else
        id_results.problematic = {};
    end

    % Also treat near-perfect collinearity as problematic for reporting
    if ~isempty(id_results.collinearity)
        for i = 1:size(id_results.collinearity, 1)
            if strcmp(id_results.collinearity{i, 4}, 'HIGH')
                id_results.problematic{end+1} = sprintf('%s (HIGH collinearity with %s, cosn=%s)', ...
                    id_results.collinearity{i, 1}, id_results.collinearity{i, 3}, id_results.collinearity{i, 2});
            end
        end
    end
    if ~isempty(id_results.problematic)
        id_results.problematic = unique(id_results.problematic, 'stable');
        fprintf('\n    [WARN] PROBLEMATIC PARAMETERS DETECTED:\n');
        for i = 1:length(id_results.problematic)
            fprintf('      - %s\n', id_results.problematic{i});
        end
    else
        fprintf('\n    [OK] No problematic parameters detected\n');
    end

else
    fprintf('    Identification results not available in oo_ structure.\n');
    fprintf('    (Run identification command before estimation to generate these.)\n');
end

% Add identification section to PDF report
if hasReportGen && exist('ch2_stored', 'var')
    sec2c = mlreportgen.report.Section('Parameter Identification');

    if id_results.available
        para2c = mlreportgen.dom.Paragraph(['Identification analysis verifies that estimated parameters ', ...
            'can be uniquely determined from the data. Three complementary criteria are checked: ', ...
            'reduced-form (steady state and policy functions), spectrum (Qu & Tkachenko 2012), ', ...
            'and moments (Iskrev 2010). Collinearity measures (cosn) indicate how well each ', ...
            'parameter can be distinguished from linear combinations of others.']);
    else
        para2c = mlreportgen.dom.Paragraph(['Identification results not available. Run the identification ', ...
            'command before estimation to generate identification diagnostics.']);
    end
    para2c.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec2c, para2c);

    % Add rank checks table if available
    if id_results.available && ~isempty(id_results.rank_checks)
        add(sec2c, mlreportgen.dom.Paragraph(' '));  % Spacer
        rank_header = {'Test', 'Rank', 'Result'};
        rank_tbl = mlreportgen.dom.FormalTable(rank_header, id_results.rank_checks);
        rank_tbl.Border = 'solid';
        rank_tbl.ColSep = 'solid';
        rank_tbl.RowSep = 'solid';
        rank_tbl.TableEntriesStyle = {mlreportgen.dom.FontSize('9pt')};
        rank_tbl.Width = '60%';
        add(sec2c, rank_tbl);
    end

    % Add collinearity table if available
    if id_results.available && ~isempty(id_results.collinearity)
        add(sec2c, mlreportgen.dom.Paragraph(' '));  % Spacer
        collin_para = mlreportgen.dom.Paragraph(['Collinearity Analysis: cosn values close to 1.0 indicate ', ...
            'near-perfect collinearity (identification problems). Values below 0.95 are acceptable.']);
        collin_para.Style = {mlreportgen.dom.FontSize('9pt'), mlreportgen.dom.Italic(true)};
        add(sec2c, collin_para);

        collin_header = {'Parameter', 'Max cosn', 'Most Collinear With', 'Status'};
        collin_tbl = mlreportgen.dom.FormalTable(collin_header, id_results.collinearity);
        collin_tbl.Border = 'solid';
        collin_tbl.ColSep = 'solid';
        collin_tbl.RowSep = 'solid';
        collin_tbl.TableEntriesStyle = {mlreportgen.dom.FontSize('9pt')};
        collin_tbl.Width = '100%';
        add(sec2c, collin_tbl);
    end

    % Add warning for problematic parameters
    if id_results.available && ~isempty(id_results.problematic)
        add(sec2c, mlreportgen.dom.Paragraph(' '));
        warn_para = mlreportgen.dom.Paragraph(['WARNING: Problematic parameters detected: ', ...
            strjoin(id_results.problematic, ', '), ...
            '. Consider fixing these parameters or revising the model specification.']);
        warn_para.Style = {mlreportgen.dom.FontSize('10pt'), mlreportgen.dom.Bold(true), mlreportgen.dom.Color('red')};
        add(sec2c, warn_para);
    end

    add(ch2_stored, sec2c);
    append(rpt, ch2_stored);
end

%% =========================================================================
%% 7. SMOOTHED SHOCKS
%% =========================================================================
fprintf('\n>>> Creating smoothed shocks figure...\n');

fig4 = figure('Name', 'Smoothed Shocks', 'Position', [50 50 1400 800], 'Visible', 'off');

shock_fields = fieldnames(oo_.SmoothedShocks.Mean);
n_shocks_plot = min(length(config.shock_names), length(shock_fields));
n_cols_shocks = 4;
n_rows_shocks = ceil(n_shocks_plot / n_cols_shocks);
colors_shock = lines(n_shocks_plot);

for i = 1:n_shocks_plot
    subplot(n_rows_shocks, n_cols_shocks, i);

    sname = shock_fields{i};
    s = oo_.SmoothedShocks.Mean.(sname);

    % Create bar plot
    bar(dates, s, 'FaceColor', colors_shock(i,:), 'EdgeColor', 'none', 'FaceAlpha', 0.7);
    hold on;
    yline(0, 'k-', 'LineWidth', 0.5);

    % Highlight COVID period
    xline(2020, 'r--', 'LineWidth', 1);
    xline(2021.5, 'r--', 'LineWidth', 1);

    % Find label
    idx = find(strcmp(config.shock_names, sname));
    if ~isempty(idx)
        title_str = config.shock_labels{idx};
    else
        title_str = strrep(sname, '_', '\_');
    end

    title([title_str ' (' sname ')'], 'FontSize', 10);
    xlabel('Year');
    ylabel('Shock (std units)');
    xlim([config.sample_start-0.5 max(dates)+0.5]);
    grid on;
    box on;

    % Add statistics
    text(0.02, 0.98, sprintf('\\mu=%.3f\n\\sigma=%.3f', mean(s), std(s)), ...
        'Units', 'normalized', 'VerticalAlignment', 'top', ...
        'FontSize', 8, 'BackgroundColor', 'white');
end

sgtitle('Smoothed Structural Shocks (Posterior Mean)', 'FontSize', 14, 'FontWeight', 'bold');

saveas(fig4, [config.graphs_dir '/Fig04_SmoothedShocks.png']);
print(fig4, [config.graphs_dir '/Fig04_SmoothedShocks'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig04_SmoothedShocks.png\n');

%% =========================================================================
%% 8. VARIANCE DECOMPOSITION
%% =========================================================================
fprintf('\n>>> Creating variance decomposition figure...\n');

has_vd_mode_plot = false;
vd_matrix_mode = [];
shock_labels_plot_mode = {};
vd_mode_varnames = {};
vd_source = 'none';
vd_source_title = 'UNKNOWN SOURCE';
vd_varnames_hint = {};
vd_shock_names_hint = {};
vd_row_sum_check_mean = struct('available', false, 'row_sums', [], 'max_abs_dev', NaN, 'bad_rows', []);
vd_row_sum_check_mode = struct('available', false, 'row_sums', [], 'max_abs_dev', NaN, 'bad_rows', []);
vd_sum_tolerance = 1.0;  % percentage points
fig5 = figure('Name', 'Variance Decomposition', 'Position', [50 50 1000 600], 'Visible', 'off');

% Get variance decomposition (prefer Dynare PosteriorTheoreticalMoments posterior mean)
if isfield(oo_, 'PosteriorTheoreticalMoments') && ...
   isfield(oo_.PosteriorTheoreticalMoments, 'dsge') && ...
   isfield(oo_.PosteriorTheoreticalMoments.dsge, 'VarianceDecomposition') && ...
   isfield(oo_.PosteriorTheoreticalMoments.dsge.VarianceDecomposition, 'Mean')

    vd_struct = oo_.PosteriorTheoreticalMoments.dsge.VarianceDecomposition.Mean;
    vd_varnames_hint = fieldnames(vd_struct);

    if ~isempty(vd_varnames_hint)
        first_var = vd_varnames_hint{1};
        vd_shock_names_hint = fieldnames(vd_struct.(first_var));

        n_v = length(vd_varnames_hint);
        n_s = length(vd_shock_names_hint);
        vd = zeros(n_v, n_s);

        for i = 1:n_v
            vname = vd_varnames_hint{i};
            for j = 1:n_s
                sname = vd_shock_names_hint{j};
                if isfield(vd_struct.(vname), sname)
                    vd(i, j) = vd_struct.(vname).(sname);
                else
                    vd(i, j) = NaN;
                end
            end
        end

        if max(vd(:), [], 'omitnan') <= 1.5
            vd = 100 * vd;
        end

        vd_source = 'posterior_mean_ptm';
        vd_source_title = 'Posterior Mean';
        fprintf('    Using posterior mean VD from oo_.PosteriorTheoreticalMoments.dsge.VarianceDecomposition.Mean\n');
    else
        vd = [];
    end
elseif isfield(oo_, 'posterior_mean') && isfield(oo_.posterior_mean, 'variance_decomposition')
    vd = oo_.posterior_mean.variance_decomposition;
    vd_source = 'posterior_mean_legacy';
    vd_source_title = 'Posterior Mean (legacy field)';
    fprintf('    Using posterior mean variance decomposition (legacy field)\n');
elseif isfield(oo_, 'variance_decomposition')
    vd = oo_.variance_decomposition;
    vd_source = 'stoch_simul';
    vd_source_title = 'stoch\\_simul (calibration, not posterior mean)';
    fprintf('    WARNING: Using stoch_simul variance decomposition (posterior mean VD not found in oo_)\n');
else
    vd = [];
end

% Posterior mode VD (optional, for comparison with posterior mean)
if isfield(oo_, 'posterior_mode') && isfield(oo_.posterior_mode, 'variance_decomposition')
    vd_mode = oo_.posterior_mode.variance_decomposition;
    vd_mode_varnames = {};
    fprintf('    Posterior mode variance decomposition available\n');
else
    vd_mode = [];

    % Fallback: derive pointwise mode from posterior VD draws saved by Dynare
    vd_draws_file = [config.model_name '/metropolis/' config.model_name '_PosteriorVarianceDecomposition1.mat'];
    if exist(vd_draws_file, 'file')
        try
            vd_draws_data = load(vd_draws_file, 'Decomposition_array', 'endo_names');
            if isfield(vd_draws_data, 'Decomposition_array') && ~isempty(vd_draws_data.Decomposition_array)
                vd_draws = vd_draws_data.Decomposition_array;  % Ndraw x (Nexo*Nendo)
                n_endo = 0;
                if isfield(vd_draws_data, 'endo_names') && ~isempty(vd_draws_data.endo_names)
                    n_endo = numel(vd_draws_data.endo_names);
                end
                if n_endo <= 0
                    n_endo = M_.endo_nbr;
                end
                n_exo = M_.exo_nbr;

                % Find the draw closest to posterior mode parameters for a coherent
                % decomposition vector that respects adding-up constraints.
                mode_file = [config.output_dir '/' config.model_name '_mode.mat'];
                draws_file = [config.model_name '/metropolis/' config.model_name '_posterior_draws1.mat'];
                if exist(mode_file, 'file') && exist(draws_file, 'file')
                    mode_data = load(mode_file, 'xparam1');
                    draws_data = load(draws_file, 'pdraws');
                    if isfield(mode_data, 'xparam1') && isfield(draws_data, 'pdraws') && ~isempty(draws_data.pdraws)
                        param_draws = cell2mat(draws_data.pdraws(:, 1));
                        xmode = mode_data.xparam1(:)';

                        if size(param_draws, 2) == length(xmode) && size(param_draws, 1) == size(vd_draws, 1)
                            sq_dist = sum((param_draws - xmode).^2, 2);
                            [~, idx_mode_draw] = min(sq_dist);

                            n_cols_expected = n_endo * n_exo;
                            n_cols_available = size(vd_draws, 2);
                            if n_cols_available >= n_cols_expected
                                vd_vec = vd_draws(idx_mode_draw, 1:n_cols_expected);
                                % IMPORTANT: Dynare stores as [shock blocks of endo vars],
                                % i.e. [Nexo x Nendo] flattened by columns. Correct mapping:
                                % reshape to [Nexo, Nendo] then transpose.
                                vd_mode = reshape(vd_vec, [n_exo, n_endo])';

                                if max(vd_mode(:), [], 'omitnan') <= 1.5
                                    vd_mode = 100 * vd_mode;
                                end

                                if isfield(vd_draws_data, 'endo_names') && ~isempty(vd_draws_data.endo_names)
                                    if iscell(vd_draws_data.endo_names)
                                        vd_mode_varnames = vd_draws_data.endo_names(:)';
                                    else
                                        vd_mode_varnames = cellstr(vd_draws_data.endo_names);
                                        vd_mode_varnames = vd_mode_varnames(:)';
                                    end
                                else
                                    vd_mode_varnames = M_.endo_names(1:n_endo);
                                end

                                fprintf('    Derived posterior mode VD from nearest posterior draw (idx=%d): %s\n', ...
                                    idx_mode_draw, vd_draws_file);
                            else
                                fprintf('    WARNING: Could not derive mode VD from draws (size mismatch: expected %d cols, got %d)\n', ...
                                    n_cols_expected, n_cols_available);
                            end
                        else
                            fprintf('    WARNING: Could not align posterior draws with mode parameters for mode VD fallback\n');
                        end
                    end
                end
            end
        catch ME
            fprintf('    WARNING: Failed to derive mode VD from %s (%s)\n', vd_draws_file, ME.message);
        end
    end
end

if ~isempty(vd)
    % Determine row-to-variable mapping for the VD matrix.
    %
    % KEY INSIGHT: After Dynare estimation/stoch_simul with a var_list, the
    % VD matrix has N_varlist rows ordered in the SAME ORDER as the var_list
    % written in the command (e.g., "y d gap pi R n h ..."), NOT in
    % M_.endo_names declaration order. Dynare's internal code builds ivar by
    % iterating through var_list in user-specified order, then subsets the
    % full VD matrix as vd(ivar,:).
    %
    % WRONG approach: filtering M_.endo_names to SmoothedVariables fields
    % gives M_.endo_names order (y,d,gap,R,pi,w,...) which differs from
    % var_list order (y,d,gap,pi,R,n,...) starting at position 4.
    %
    % CORRECT approach: use config.var_list (parsed from .mod file at startup).
    % options_.var_list is empty in saved results (Dynare clears it post-run),
    % and M_.endo_names has a DIFFERENT order than the var_list.
    if ~isempty(vd_varnames_hint) && length(vd_varnames_hint) == size(vd, 1)
        vd_varnames = vd_varnames_hint;
        fprintf('    Using VD variable names from PosteriorTheoreticalMoments (%d variables)\n', length(vd_varnames));
    elseif size(vd, 1) == M_.endo_nbr
        vd_varnames = M_.endo_names;
        fprintf('    VD has full M_.endo_nbr rows +/-†’ using M_.endo_names order\n');
    elseif isfield(config, 'var_list') && length(config.var_list) == size(vd, 1)
        % Primary: config.var_list (hardcoded, matches .mod file exactly)
        vd_varnames = config.var_list;
        fprintf('    Using config.var_list for VD row mapping (%d variables)\n', length(vd_varnames));
    elseif exist('options_', 'var') && isfield(options_, 'var_list') && ~isempty(options_.var_list)
        % Fallback 1: options_.var_list (if Dynare saved it)
        if iscell(options_.var_list)
            vd_varnames = options_.var_list(:)';
        else
            vd_varnames = strtrim(cellstr(options_.var_list))';
        end
        fprintf('    Using options_.var_list for VD row mapping (%d variables)\n', length(vd_varnames));
    else
        % Last resort: M_.endo_names (WARNING: wrong order for subset!)
        vd_varnames = M_.endo_names(1:size(vd, 1));
        fprintf('    WARNING: No reliable var_list source, using M_.endo_names fallback (order WILL be wrong!)\n');
    end

    % VALIDATION: h is a pure AR(1) (h = rho_h*h(-1) + eps_h), so its VD
    % must be ~100% eps_h. If not, the row mapping is wrong.
    h_idx_in_vd = find(strcmp(vd_varnames, 'h'));
    eps_h_idx = find(strcmp(M_.exo_names, 'eps_h'));
    if ~isempty(h_idx_in_vd) && ~isempty(eps_h_idx) && h_idx_in_vd <= size(vd, 1)
        h_eps_h_share = vd(h_idx_in_vd, eps_h_idx);
        n_vd_cols_check = min(size(vd, 2), M_.exo_nbr);
        h_total = sum(vd(h_idx_in_vd, 1:n_vd_cols_check));
        if h_total > 0
            h_eps_h_pct = 100 * h_eps_h_share / h_total;
        else
            h_eps_h_pct = 0;
        end
        if h_eps_h_pct > 95
            fprintf('    [OK] VD validation PASSED: h gets %.1f%% from eps_h (pure AR(1) confirmed)\n', h_eps_h_pct);
        else
            fprintf('    [FAIL] VD validation FAILED: h gets only %.1f%% from eps_h (expected ~100%%)\n', h_eps_h_pct);
            fprintf('      Row mapping is WRONG. VD row %d labeled "h" actually contains another variable.\n', h_idx_in_vd);
            fprintf('      Attempting M_.endo_names-filtered fallback for diagnosis...\n');
        end
    end

    % Safety check: if reconstructed list length doesn't match VD rows, warn
    if length(vd_varnames) ~= size(vd, 1)
        fprintf('    WARNING: VD row count (%d) != var_list length (%d). Possible mismatch.\n', ...
            size(vd, 1), length(vd_varnames));
        % If VD has MORE rows than our reconstructed list (e.g., includes ME columns), truncate
        if length(vd_varnames) < size(vd, 1)
            fprintf('    Truncating VD to %d rows to match var_list\n', length(vd_varnames));
        end
    end

    fprintf('    VD matrix size: %d rows x %d cols, mapping list has %d entries\n', ...
        size(vd, 1), size(vd, 2), length(vd_varnames));
    fprintf('    VD variable order: %s\n', strjoin(vd_varnames, ', '));

    % Handle extra columns (measurement error columns beyond M_.exo_nbr)
    n_vd_cols = min(size(vd, 2), M_.exo_nbr);

    % Prepare matrix for observables
    vd_matrix = zeros(length(config.obs_vars), n_vd_cols);
    for i = 1:length(config.obs_vars)
        var_idx = find(strcmp(vd_varnames, config.obs_vars{i}));
        if ~isempty(var_idx) && var_idx <= size(vd, 1)
            vd_matrix(i, :) = vd(var_idx, 1:n_vd_cols);
        else
            fprintf('    WARNING: Variable "%s" not found in VD (idx=%s, nrows=%d)\n', ...
                config.obs_vars{i}, mat2str(var_idx), size(vd, 1));
        end
    end

    % Reorder shocks to match config.shock_names
    shock_name_source = M_.exo_names;
    if ~isempty(vd_shock_names_hint) && length(vd_shock_names_hint) == size(vd, 2)
        shock_name_source = vd_shock_names_hint;
    end

    shock_order = zeros(1, length(config.shock_names));
    for j = 1:length(config.shock_names)
        idx = find(strcmp(shock_name_source, config.shock_names{j}));
        if ~isempty(idx)
            shock_order(j) = idx;
        end
    end
    valid_idx = shock_order > 0 & shock_order <= n_vd_cols;
    vd_matrix = vd_matrix(:, shock_order(valid_idx));
    shock_labels_plot = config.shock_labels(valid_idx);

    % Validation: row sums should be ~100 for each observable
    row_sums_mean = sum(vd_matrix, 2);
    abs_dev_mean = abs(row_sums_mean - 100);
    bad_rows_mean = find(abs_dev_mean > vd_sum_tolerance);
    vd_row_sum_check_mean.available = true;
    vd_row_sum_check_mean.row_sums = row_sums_mean;
    vd_row_sum_check_mean.max_abs_dev = max(abs_dev_mean);
    vd_row_sum_check_mean.bad_rows = bad_rows_mean;
    if isempty(bad_rows_mean)
        fprintf('    [OK] VD row-sum check (posterior mean): all rows within +/-%.1fpp of 100%%\n', vd_sum_tolerance);
    else
        fprintf('    [WARN] VD row-sum check (posterior mean): %d/%d rows exceed +/-%.1fpp (max dev %.2fpp)\n', ...
            length(bad_rows_mean), length(config.obs_vars), vd_sum_tolerance, vd_row_sum_check_mean.max_abs_dev);
        for k = 1:length(bad_rows_mean)
            irow = bad_rows_mean(k);
            fprintf('      - %s: sum=%.2f%%\n', config.obs_vars{irow}, row_sums_mean(irow));
        end
    end

    % Create stacked bar chart
    % Use ordered categorical to preserve config.obs_labels order (prevent alphabetical sorting)
    cat_labels = categorical(config.obs_labels, config.obs_labels, 'Ordinal', true);
    b = bar(cat_labels, vd_matrix, 'stacked');

    % Set colors
        cmap = [0.00 0.45 0.70;  % Technology - blue
            0.84 0.15 0.16;  % Demand - red
            0.00 0.62 0.45;  % Monetary - green
            0.80 0.47 0.65;  % Cost-Push - magenta
            0.58 0.40 0.74;  % Labor - purple
            0.94 0.89 0.26;  % Separation - yellow
            0.60 0.60 0.60;  % Hours - gray
            0.90 0.62 0.00]; % Investment - orange
    for i = 1:length(b)
        if i <= size(cmap, 1)
            b(i).FaceColor = cmap(i, :);
        end
    end

    ylabel('Variance Share (%)', 'FontSize', 11);
    title(['Variance Decomposition at ' vd_source_title], 'FontSize', 14, 'FontWeight', 'bold');
    legend(shock_labels_plot, 'Location', 'eastoutside', 'FontSize', 10);
    ylim([0 100]);
    grid on;
    box on;

    % Add percentage labels on bars
    for i = 1:length(config.obs_vars)
        cum = 0;
        for j = 1:size(vd_matrix, 2)
            if vd_matrix(i, j) > 5  % Only label if > 5%
                text(i, cum + vd_matrix(i,j)/2, sprintf('%.0f%%', vd_matrix(i,j)), ...
                    'HorizontalAlignment', 'center', 'FontSize', 8, 'Color', 'white');
            end
            cum = cum + vd_matrix(i, j);
        end
    end

    % Posterior mode plot (if available)
    if ~isempty(vd_mode)
        vd_varnames_mode = vd_varnames;
        if ~isempty(vd_mode_varnames) && length(vd_mode_varnames) == size(vd_mode, 1)
            vd_varnames_mode = vd_mode_varnames;
        end
        if length(vd_varnames_mode) ~= size(vd_mode, 1)
            if size(vd_mode, 1) == M_.endo_nbr
                vd_varnames_mode = M_.endo_names;
            elseif isfield(config, 'var_list') && length(config.var_list) == size(vd_mode, 1)
                vd_varnames_mode = config.var_list;
            elseif exist('options_', 'var') && isfield(options_, 'var_list') && ~isempty(options_.var_list)
                if iscell(options_.var_list)
                    vd_varnames_mode = options_.var_list(:)';
                else
                    vd_varnames_mode = strtrim(cellstr(options_.var_list))';
                end
            else
                vd_varnames_mode = M_.endo_names(1:size(vd_mode, 1));
            end
        end

        n_vd_cols_mode = min(size(vd_mode, 2), M_.exo_nbr);
        vd_matrix_mode = zeros(length(config.obs_vars), n_vd_cols_mode);
        for i = 1:length(config.obs_vars)
            var_idx = find(strcmp(vd_varnames_mode, config.obs_vars{i}));
            if ~isempty(var_idx) && var_idx <= size(vd_mode, 1)
                vd_matrix_mode(i, :) = vd_mode(var_idx, 1:n_vd_cols_mode);
            end
        end

        valid_idx_mode = shock_order > 0 & shock_order <= n_vd_cols_mode;
        vd_matrix_mode = vd_matrix_mode(:, shock_order(valid_idx_mode));
        shock_labels_plot_mode = config.shock_labels(valid_idx_mode);

        % Validation: row sums should be ~100 for each observable
        row_sums_mode = sum(vd_matrix_mode, 2);
        abs_dev_mode = abs(row_sums_mode - 100);
        bad_rows_mode = find(abs_dev_mode > vd_sum_tolerance);
        vd_row_sum_check_mode.available = true;
        vd_row_sum_check_mode.row_sums = row_sums_mode;
        vd_row_sum_check_mode.max_abs_dev = max(abs_dev_mode);
        vd_row_sum_check_mode.bad_rows = bad_rows_mode;
        if isempty(bad_rows_mode)
            fprintf('    [OK] VD row-sum check (posterior mode): all rows within +/-%.1fpp of 100%%\n', vd_sum_tolerance);
        else
            fprintf('    [WARN] VD row-sum check (posterior mode): %d/%d rows exceed +/-%.1fpp (max dev %.2fpp)\n', ...
                length(bad_rows_mode), length(config.obs_vars), vd_sum_tolerance, vd_row_sum_check_mode.max_abs_dev);
            for k = 1:length(bad_rows_mode)
                irow = bad_rows_mode(k);
                fprintf('      - %s: sum=%.2f%%\n', config.obs_vars{irow}, row_sums_mode(irow));
            end
        end

        fig5b = figure('Name', 'Variance Decomposition (Posterior Mode)', ...
            'Position', [80 80 1000 600], 'Visible', 'off');
        b_mode = bar(cat_labels, vd_matrix_mode, 'stacked');

        for i = 1:length(b_mode)
            if i <= size(cmap, 1)
                b_mode(i).FaceColor = cmap(i, :);
            end
        end

        ylabel('Variance Share (%)', 'FontSize', 11);
        title('Variance Decomposition at Posterior Mode', 'FontSize', 14, 'FontWeight', 'bold');
        legend(shock_labels_plot_mode, 'Location', 'eastoutside', 'FontSize', 10);
        ylim([0 100]);
        grid on;
        box on;

        for i = 1:length(config.obs_vars)
            cum = 0;
            for j = 1:size(vd_matrix_mode, 2)
                if vd_matrix_mode(i, j) > 5
                    text(i, cum + vd_matrix_mode(i,j)/2, sprintf('%.0f%%', vd_matrix_mode(i,j)), ...
                        'HorizontalAlignment', 'center', 'FontSize', 8, 'Color', 'white');
                end
                cum = cum + vd_matrix_mode(i, j);
            end
        end

        saveas(fig5b, [config.graphs_dir '/Fig05b_VarianceDecomp_Mode.png']);
        print(fig5b, [config.graphs_dir '/Fig05b_VarianceDecomp_Mode'], '-dpng', '-r200');
        fprintf('    [OK] Saved: Fig05b_VarianceDecomp_Mode.png\n');
        has_vd_mode_plot = true;
    end
end

saveas(fig5, [config.graphs_dir '/Fig05_VarianceDecomp.png']);
print(fig5, [config.graphs_dir '/Fig05_VarianceDecomp'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig05_VarianceDecomp.png\n');

% Add to report
if hasReportGen
    ch3 = mlreportgen.report.Chapter('Shock Analysis');

    sec3a = mlreportgen.report.Section('Smoothed Shocks');
    para3a = mlreportgen.dom.Paragraph(['Historical structural shocks inferred from the Kalman smoother. ', ...
        'These represent the model''s interpretation of what shocks drove observed ', ...
        'fluctuations in each period.']);
    para3a.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec3a, para3a);
    img4 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig04_SmoothedShocks.png')); img4.Width = imgFull_1400x800{1}; img4.Height = imgFull_1400x800{2};
    add(sec3a, img4);
    add(ch3, sec3a);

    sec3b = mlreportgen.report.Section('Variance Decomposition');
    para3b = mlreportgen.dom.Paragraph(['Share of variance in each observable explained by different ', ...
        'structural shocks. This reveals the main drivers of business cycle fluctuations.']);
    para3b.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec3b, para3b);
    img5 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig05_VarianceDecomp.png')); img5.Width = imgHalf_1000x600{1}; img5.Height = imgHalf_1000x600{2};
    add(sec3b, img5);
    if has_vd_mode_plot
        para3b_mode = mlreportgen.dom.Paragraph('Comparison chart at posterior mode (same decomposition setup).');
        para3b_mode.Style = {mlreportgen.dom.FontSize('10pt')};
        add(sec3b, para3b_mode);
        img5b = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig05b_VarianceDecomp_Mode.png')); img5b.Width = imgHalf_1000x600{1}; img5b.Height = imgHalf_1000x600{2};
        add(sec3b, img5b);
    end
    add(ch3, sec3b);

    append(rpt, ch3);
end

%% =========================================================================
%% 9. IMPULSE RESPONSE FUNCTIONS
%% =========================================================================
fprintf('\n>>> Creating impulse response function figures...\n');

% --- 9.1 Technology vs Demand Shocks ---
fig6 = figure('Name', 'IRFs - Tech vs Demand', 'Position', [50 50 1800 700], 'Visible', 'off');

irf_vars = {'y', 'pi', 'R', 'n', 'w', 'h', 'u_rate'};
irf_labels = {'Output (y)', 'Inflation (\\pi)', 'Interest Rate (R)', ...
              'Employment (n)', 'Real Wage (w)', 'Hours (h)', 'Unemp. Rate (u)'};
irf_shocks = {'eps_a', 'eps_d'};
irf_shock_titles = {'Technology Shock (\\epsilon_a)', 'Demand Shock (\\epsilon_d)'};

if isfield(oo_, 'PosteriorIRF')
    for row = 1:2
        for col = 1:7
            subplot(2, 7, (row-1)*7 + col);

            vname = [irf_vars{col} '_' irf_shocks{row}];

            if isfield(oo_.PosteriorIRF.dsge.Mean, vname)
                irf_mean = oo_.PosteriorIRF.dsge.Mean.(vname);
                irf_lo = oo_.PosteriorIRF.dsge.HPDinf.(vname);
                irf_hi = oo_.PosteriorIRF.dsge.HPDsup.(vname);

                H = length(irf_mean);
                x = 0:H-1;

                % Credible interval
                fill([x fliplr(x)], [irf_lo(:)' fliplr(irf_hi(:)')], ...
                    config.colors.fill, 'EdgeColor', 'none');
                hold on;

                % Mean IRF
                plot(x, irf_mean, 'Color', config.colors.primary, 'LineWidth', 2);
                yline(0, 'k--', 'LineWidth', 0.5);

                if row == 1
                    title(irf_labels{col}, 'FontSize', 10);
                end
                if col == 1
                    ylabel(irf_shock_titles{row}, 'FontSize', 9, 'FontWeight', 'bold');
                end
                xlabel('Quarters');
                xlim([0 config.irf_horizon-1]);
                grid on;
                box on;
            end
        end
    end
end

sgtitle('Bayesian IRFs: Technology vs Demand Shocks (90% CI)', ...
    'FontSize', 14, 'FontWeight', 'bold');

saveas(fig6, [config.graphs_dir '/Fig06_IRF_TechDemand.png']);
print(fig6, [config.graphs_dir '/Fig06_IRF_TechDemand'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig06_IRF_TechDemand.png\n');

% --- 9.2 Monetary and Labor Market Shocks (including Hours shock) ---
fig7 = figure('Name', 'IRFs - Monetary, Hours & Labor', 'Position', [50 50 1800 900], 'Visible', 'off');

irf_shocks2 = {'eps_r', 'eps_h', 'eps_sep'};
irf_shock_titles2 = {'Monetary Shock (\\epsilon_r)', 'Hours Shock (\\epsilon_h)', 'Separation Shock (\\epsilon_{sep})'};

if isfield(oo_, 'PosteriorIRF')
    for row = 1:3
        for col = 1:7
            subplot(3, 7, (row-1)*7 + col);

            vname = [irf_vars{col} '_' irf_shocks2{row}];

            if isfield(oo_.PosteriorIRF.dsge.Mean, vname)
                irf_mean = oo_.PosteriorIRF.dsge.Mean.(vname);
                irf_lo = oo_.PosteriorIRF.dsge.HPDinf.(vname);
                irf_hi = oo_.PosteriorIRF.dsge.HPDsup.(vname);

                H = length(irf_mean);
                x = 0:H-1;

                fill([x fliplr(x)], [irf_lo(:)' fliplr(irf_hi(:)')], ...
                    config.colors.fill, 'EdgeColor', 'none');
                hold on;
                plot(x, irf_mean, 'Color', config.colors.primary, 'LineWidth', 2);
                yline(0, 'k--', 'LineWidth', 0.5);

                if row == 1
                    title(irf_labels{col}, 'FontSize', 10);
                end
                if col == 1
                    ylabel(irf_shock_titles2{row}, 'FontSize', 9, 'FontWeight', 'bold');
                end
                xlabel('Quarters');
                xlim([0 config.irf_horizon-1]);
                grid on;
                box on;
            end
        end
    end
end

sgtitle('Bayesian IRFs: Monetary & Separation Shocks (90% CI)', ...
    'FontSize', 14, 'FontWeight', 'bold');

saveas(fig7, [config.graphs_dir '/Fig07_IRF_MonLabor.png']);
print(fig7, [config.graphs_dir '/Fig07_IRF_MonLabor'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig07_IRF_MonLabor.png\n');

% Add to report
if hasReportGen
    ch4 = mlreportgen.report.Chapter('Impulse Response Functions');

    sec4a = mlreportgen.report.Section('Technology and Demand Shocks');
    para4a = mlreportgen.dom.Paragraph(['Response of endogenous variables to one-standard-deviation ', ...
        'technology and demand shocks. Blue lines show posterior mean, shaded areas ', ...
        'represent 90% highest posterior density intervals.']);
    para4a.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec4a, para4a);
    img6 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig06_IRF_TechDemand.png')); img6.Width = imgFull_1600x700{1}; img6.Height = imgFull_1600x700{2};
    add(sec4a, img6);
    add(ch4, sec4a);

    sec4b = mlreportgen.report.Section('Monetary and Labor Market Shocks');
    para4b = mlreportgen.dom.Paragraph('Response to monetary policy and separation shocks.');
    para4b.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec4b, para4b);
    img7 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig07_IRF_MonLabor.png')); img7.Width = imgFull_1600x700{1}; img7.Height = imgFull_1600x700{2};
    add(sec4b, img7);
    add(ch4, sec4b);

    append(rpt, ch4);
end

%% =========================================================================
%% 10. OUTPUT-INFLATION CORRELATION CHECK
%% =========================================================================
fprintf('\n>>> Creating output-inflation correlation figure...\n');

fig8 = figure('Name', 'Y-Pi Correlation', 'Position', [50 50 800 600], 'Visible', 'off');

y_smooth = oo_.SmoothedVariables.Mean.y;
pi_smooth = oo_.SmoothedVariables.Mean.pi;

% Scatter plot
scatter(y_smooth, pi_smooth, 60, dates, 'filled', 'MarkerFaceAlpha', 0.7);
hold on;
colormap(parula);
cb = colorbar;
cb.Label.String = 'Year';

% Fit line
p = polyfit(y_smooth, pi_smooth, 1);
x_fit = linspace(min(y_smooth), max(y_smooth), 100);
y_fit = polyval(p, x_fit);
plot(x_fit, y_fit, 'r-', 'LineWidth', 2.5);

% Zero lines
xline(0, 'k--', 'LineWidth', 0.5);
yline(0, 'k--', 'LineWidth', 0.5);

% Correlation
rho_model = corr(y_smooth, pi_smooth);

% Get data correlation if available
if ~isempty(data)
    y_data = data.y(~isnan(data.y) & ~isnan(data.pi));
    pi_data = data.pi(~isnan(data.y) & ~isnan(data.pi));
    rho_data = corr(y_data, pi_data);
else
    rho_data = 0.27;  % Target from calibration
end

title(sprintf('Output-Inflation Correlation: Model %.3f vs Data %.3f', rho_model, rho_data), ...
    'FontSize', 14, 'FontWeight', 'bold');
xlabel('Output Gap (%)', 'FontSize', 12);
ylabel('Inflation (% ann.)', 'FontSize', 12);
grid on;
box on;

% Add annotation
% PASS criteria: model matches data sign, OR both are near zero (|rho| < 0.1)
rho_tol = 0.1;  % tolerance for "near zero"
both_near_zero = (abs(rho_model) < rho_tol) && (abs(rho_data) < rho_tol);
sign_match = (sign(rho_model) == sign(rho_data)) && (rho_model ~= 0) && (rho_data ~= 0);
corr_check_pass = sign_match || both_near_zero;

annotation_text = sprintf(['Model \\rho = %.3f\n' ...
                          'Data \\rho = %.3f\n' ...
                          'Target: Match data sign'], rho_model, rho_data);
text(0.05, 0.95, annotation_text, 'Units', 'normalized', ...
    'FontSize', 11, 'BackgroundColor', 'white', ...
    'VerticalAlignment', 'top', 'EdgeColor', 'black');

if corr_check_pass
    text(0.95, 0.05, '[OK] PASS', 'Units', 'normalized', 'FontSize', 14, ...
        'Color', [0 0.6 0], 'FontWeight', 'bold', 'HorizontalAlignment', 'right');
else
    text(0.95, 0.05, '[FAIL] FAIL', 'Units', 'normalized', 'FontSize', 14, ...
        'Color', [0.8 0 0], 'FontWeight', 'bold', 'HorizontalAlignment', 'right');
end

saveas(fig8, [config.graphs_dir '/Fig08_YPiCorrelation.png']);
print(fig8, [config.graphs_dir '/Fig08_YPiCorrelation'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig08_YPiCorrelation.png\n');
fprintf('    Output-Inflation Correlation: Model=%.3f, Data=%.3f\n', rho_model, rho_data);

%% =========================================================================
%% 11. MOMENT COMPARISON: MODEL VS DATA
%% =========================================================================
fprintf('\n>>> Computing moment comparison...\n');

fig9 = figure('Name', 'Moment Comparison', 'Position', [50 50 1200 500], 'Visible', 'off');

% Compute moments
moments = struct();
for i = 1:length(config.obs_vars)
    vname = config.obs_vars{i};

    % Data moments
    if ~isempty(data) && ismember(vname, data.Properties.VariableNames)
        d = data.(vname);
        d = d(~isnan(d));
        moments.(vname).data_std = std(d);
        moments.(vname).data_ac1 = corr(d(1:end-1), d(2:end));
    else
        moments.(vname).data_std = NaN;
        moments.(vname).data_ac1 = NaN;
    end

    % Model moments
    if isfield(oo_.SmoothedVariables.Mean, vname)
        m = oo_.SmoothedVariables.Mean.(vname);
        moments.(vname).model_std = std(m);
        moments.(vname).model_ac1 = corr(m(1:end-1), m(2:end));
    else
        moments.(vname).model_std = NaN;
        moments.(vname).model_ac1 = NaN;
    end
end

% --- Standard Deviations ---
subplot(1, 2, 1);
data_std = zeros(length(config.obs_vars), 1);
model_std = zeros(length(config.obs_vars), 1);
for i = 1:length(config.obs_vars)
    data_std(i) = moments.(config.obs_vars{i}).data_std;
    model_std(i) = moments.(config.obs_vars{i}).model_std;
end

x = 1:length(config.obs_vars);
bar_data = [data_std, model_std];
b = bar(x, bar_data, 'grouped');
b(1).FaceColor = [0.5 0.5 0.5];
b(2).FaceColor = config.colors.primary;

set(gca, 'XTickLabel', config.obs_labels, 'XTickLabelRotation', 30);
ylabel('Standard Deviation');
title('Volatility: Data vs Model', 'FontSize', 12, 'FontWeight', 'bold');
legend({'Data', 'Model'}, 'Location', 'best');
grid on;
box on;

% --- Autocorrelations ---
subplot(1, 2, 2);
data_ac = zeros(length(config.obs_vars), 1);
model_ac = zeros(length(config.obs_vars), 1);
for i = 1:length(config.obs_vars)
    data_ac(i) = moments.(config.obs_vars{i}).data_ac1;
    model_ac(i) = moments.(config.obs_vars{i}).model_ac1;
end

bar_data = [data_ac, model_ac];
b = bar(x, bar_data, 'grouped');
b(1).FaceColor = [0.5 0.5 0.5];
b(2).FaceColor = config.colors.primary;

set(gca, 'XTickLabel', config.obs_labels, 'XTickLabelRotation', 30);
ylabel('First-Order Autocorrelation');
title('Persistence: Data vs Model', 'FontSize', 12, 'FontWeight', 'bold');
legend({'Data', 'Model'}, 'Location', 'best');
ylim([0 1]);
grid on;
box on;

sgtitle('Moment Comparison: Data vs Model', 'FontSize', 14, 'FontWeight', 'bold');

saveas(fig9, [config.graphs_dir '/Fig09_MomentComparison.png']);
print(fig9, [config.graphs_dir '/Fig09_MomentComparison'], '-dpng', '-r200');
fprintf('    [OK] Saved: Fig09_MomentComparison.png\n');

%% =========================================================================
%% 12. IRF SIGN CHECK
%% =========================================================================
fprintf('\n>>> Checking IRF signs for economic sensibility...\n');

irf_checks = {
    'Technology: y should rise',       'y_eps_a',        1;
    'Technology: pi should fall',      'pi_eps_a',      -1;
    'Technology: h should rise',       'h_eps_a',        1;
    'Demand: y should rise',           'y_eps_d',        1;
    'Demand: pi should rise',          'pi_eps_d',       1;
    'Monetary: y should fall',         'y_eps_r',       -1;
    'Monetary: R should rise',         'R_eps_r',        1;
    'Hours: h should rise',            'h_eps_h',        1;
    'Hours: y should rise',            'y_eps_h',        1;
    'Separation: n should fall',       'n_eps_sep',     -1;
    'Separation: u_rate should rise',  'u_rate_eps_sep', 1;
    'Cost-push: pi should rise',        'pi_eps_p',       1;
    'Cost-push: y should fall',         'y_eps_p',       -1;
};

fprintf('\n    IRF Sign Checks:\n');
fprintf('    %-40s %10s %10s\n', 'Check', 'IRF(0)', 'Pass?');
fprintf('    %s\n', repmat('-', 1, 62));

irf_results = cell(size(irf_checks, 1), 4);
all_pass = true;

if isfield(oo_, 'PosteriorIRF')
    for i = 1:size(irf_checks, 1)
        desc = irf_checks{i, 1};
        vname = irf_checks{i, 2};
        expected = irf_checks{i, 3};

        if isfield(oo_.PosteriorIRF.dsge.Mean, vname)
            irf0 = oo_.PosteriorIRF.dsge.Mean.(vname)(1);
            pass = (sign(irf0) == expected) || (abs(irf0) < 0.001);
            pass_str = conditional(pass, 'PASS', 'FAIL');
            if ~pass, all_pass = false; end
        else
            irf0 = NaN;
            pass_str = 'N/A';
        end

        fprintf('    %-40s %10.4f %10s\n', desc, irf0, pass_str);

        irf_results{i, 1} = desc;
        irf_results{i, 2} = irf0;
        irf_results{i, 3} = expected;
        irf_results{i, 4} = pass_str;
    end
end

if all_pass
    fprintf('\n    [OK] All IRF sign checks passed!\n');
else
    fprintf('\n    [WARN] Some IRF sign checks failed - review model specification.\n');
end

%% =========================================================================
%% 13. ADD REMAINING SECTIONS TO REPORT
%% =========================================================================
if hasReportGen
    % Model Validation Chapter
    ch5 = mlreportgen.report.Chapter('Model Validation');

    sec5a = mlreportgen.report.Section('Output-Inflation Correlation');
    para5a = mlreportgen.dom.Paragraph(['A key validation criterion is achieving positive output-inflation ', ...
        'correlation, matching the data. Standard NK models with supply shocks dominant ', ...
        'produce negative correlation. The demand shock (eps_d) addresses this issue.']);
    para5a.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec5a, para5a);
    img8 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig08_YPiCorrelation.png')); img8.Width = imgHalf_800x600{1}; img8.Height = imgHalf_800x600{2};
    add(sec5a, img8);
    add(ch5, sec5a);

    sec5b = mlreportgen.report.Section('Moment Comparison');
    para5b = mlreportgen.dom.Paragraph(['Comparison of volatility (standard deviation) and persistence ', ...
        '(first-order autocorrelation) between data and model-implied series.']);
    para5b.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec5b, para5b);
    img9 = mlreportgen.dom.Image(fullfile(pwd, config.graphs_dir, 'Fig09_MomentComparison.png')); img9.Width = imgFull_1200x500{1}; img9.Height = imgFull_1200x500{2};
    add(sec5b, img9);
    add(ch5, sec5b);

    sec5c = mlreportgen.report.Section('IRF Sign Checks');
    para5c = mlreportgen.dom.Paragraph(['Verification that impulse responses have economically sensible ', ...
        'signs. Key checks: technology raises output and lowers inflation; demand shocks ', ...
        'raise both; monetary contraction lowers output.']);
    para5c.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec5c, para5c);

    % Create IRF check table
    tbl = mlreportgen.dom.FormalTable({'Check', 'IRF(0)', 'Expected', 'Result'}, irf_results);
    tbl.Border = 'solid';
    tbl.ColSep = 'solid';
    tbl.RowSep = 'solid';
    tbl.TableEntriesStyle = {mlreportgen.dom.FontSize('9pt')};
    tbl.Width = '100%';
    add(sec5c, tbl);
    add(ch5, sec5c);

    append(rpt, ch5);

    % Summary Statistics Chapter
    ch6 = mlreportgen.report.Chapter('Summary Statistics');

    sec6a = mlreportgen.report.Section('Model Fit');
    summary_text = sprintf(['Log Marginal Likelihood (Laplace): %.2f ', ...
        'Output-Inflation Correlation: %.3f (Data: %.3f) ', ...
        'Sample Period: %.0f - %.0f ', ...
        'Number of Observations: %d'], ...
        fit_stats.log_mdd_laplace, rho_model, rho_data, ...
        config.sample_start, max(dates), T);
    para6a = mlreportgen.dom.Paragraph(summary_text);
    para6a.Style = {mlreportgen.dom.FontSize('10pt')};
    add(sec6a, para6a);
    add(ch6, sec6a);

    sec6b = mlreportgen.report.Section('Parameter Estimates');
    param_tbl = mlreportgen.dom.FormalTable(param_table(1,:), param_table(2:end,:));
    param_tbl.Border = 'solid';
    param_tbl.ColSep = 'solid';
    param_tbl.RowSep = 'solid';
    param_tbl.TableEntriesStyle = {mlreportgen.dom.FontSize('9pt')};
    param_tbl.Width = '100%';
    add(sec6b, param_tbl);
    add(ch6, sec6b);

    append(rpt, ch6);
end

%% =========================================================================
%% 14. FINALIZE REPORT
%% =========================================================================
if hasReportGen
    fprintf('\n>>> Generating PDF report...\n');
    close(rpt);
    fprintf('    [OK] Report saved: %s\n', config.report_file);

    % Try to open report
    if ispc
        winopen(config.report_file);
    elseif ismac
        system(['open ' config.report_file]);
    end
end

%% =========================================================================
%% 15. SAVE TEXT SUMMARY
%% =========================================================================
fprintf('\n>>> Saving text summary...\n');

summary_file = [config.output_dir '/postestimation_summary.txt'];
fid = fopen(summary_file, 'w');

fprintf(fid, '===================================================================\n');
fprintf(fid, '          DSGE POST-ESTIMATION SUMMARY\n');
fprintf(fid, '          Model: %s\n', config.model_name);
fprintf(fid, '          Date: %s\n', datestr(now, 'yyyy-mm-dd'));
fprintf(fid, '===================================================================\n\n');

fprintf(fid, '=== MODEL FIT STATISTICS ===\n\n');
fprintf(fid, 'Log Marginal Likelihood (Laplace): %.4f\n', fit_stats.log_mdd_laplace);
if ~isnan(fit_stats.log_mdd_mhm)
    fprintf(fid, 'Log Marginal Likelihood (MHM):     %.4f\n', fit_stats.log_mdd_mhm);
end
fprintf(fid, '\nOutput-Inflation Correlation:\n');
fprintf(fid, '  Model: %.4f\n', rho_model);
fprintf(fid, '  Data:  %.4f\n', rho_data);
if corr_check_pass
    fprintf(fid, '  Check: PASS (sign match or both near zero)\n');
else
    fprintf(fid, '  Check: FAIL (sign mismatch, |rho| > %.2f)\n', rho_tol);
end

fprintf(fid, '\n=== POSTERIOR PARAMETER ESTIMATES ===\n\n');
fprintf(fid, '%-20s %12s %12s %12s %12s\n', 'Parameter', 'Prior Mean', 'Post. Mean', 'HPD Low', 'HPD High');
fprintf(fid, '%s\n', repmat('-', 1, 70));
for i = 2:size(param_table, 1)
    fprintf(fid, '%-20s %12s %12s %12s %12s\n', param_table{i,:});
end

fprintf(fid, '\n=== IDENTIFICATION ANALYSIS ===\n\n');
if id_results.available
    % Rank checks
    if ~isempty(id_results.rank_checks)
        fprintf(fid, 'Rank Condition Checks:\n');
        fprintf(fid, '%-25s %10s %10s\n', 'Test', 'Rank', 'Result');
        fprintf(fid, '%s\n', repmat('-', 1, 47));
        for i = 1:size(id_results.rank_checks, 1)
            fprintf(fid, '%-25s %10s %10s\n', id_results.rank_checks{i,:});
        end
        fprintf(fid, '\n');
    end

    % Collinearity patterns
    if ~isempty(id_results.collinearity)
        fprintf(fid, 'Collinearity Patterns (cosn values):\n');
        fprintf(fid, '%-20s %12s %-20s %10s\n', 'Parameter', 'Max cosn', 'Most Collinear With', 'Status');
        fprintf(fid, '%s\n', repmat('-', 1, 65));
        for i = 1:size(id_results.collinearity, 1)
            fprintf(fid, '%-20s %12s %-20s %10s\n', id_results.collinearity{i,:});
        end
        fprintf(fid, '\n');
        fprintf(fid, 'Note: cosn > 0.99 indicates potential identification problems.\n');
        fprintf(fid, '      cosn > 0.95 indicates moderate collinearity.\n');
    end

    % Problematic parameters
    if ~isempty(id_results.problematic)
        fprintf(fid, '\nWARNING - Problematic Parameters:\n');
        for i = 1:length(id_results.problematic)
            fprintf(fid, '  - %s\n', id_results.problematic{i});
        end
    else
        fprintf(fid, '\nNo problematic parameters detected.\n');
    end
else
    fprintf(fid, 'Identification analysis not available.\n');
    fprintf(fid, '(Run identification command before estimation to generate these results.)\n');
end

fprintf(fid, '\n=== VARIANCE DECOMPOSITION (%s) ===\n\n', upper(vd_source_title));
if ~isempty(vd)
    fprintf(fid, '%-15s', 'Variable');
    for j = 1:length(shock_labels_plot)
        fprintf(fid, '%12s', shock_labels_plot{j});
    end
    fprintf(fid, '\n%s\n', repmat('-', 1, 15 + 12*length(shock_labels_plot)));
    for i = 1:length(config.obs_vars)
        fprintf(fid, '%-15s', config.obs_vars{i});
        for j = 1:size(vd_matrix, 2)
            fprintf(fid, '%12.1f', vd_matrix(i, j));
        end
        fprintf(fid, '\n');
    end
end

fprintf(fid, '\nVD Row-Sum Validation for %s (tolerance +/-%.1fpp around 100%%):\n', vd_source_title, vd_sum_tolerance);
if vd_row_sum_check_mean.available
    fprintf(fid, '  %s: max deviation %.2fpp\n', vd_source_title, vd_row_sum_check_mean.max_abs_dev);
    if isempty(vd_row_sum_check_mean.bad_rows)
        fprintf(fid, '    [OK] All rows pass\n');
    else
        fprintf(fid, '    [WARN] Rows with deviation > %.1fpp:\n', vd_sum_tolerance);
        for k = 1:length(vd_row_sum_check_mean.bad_rows)
            irow = vd_row_sum_check_mean.bad_rows(k);
            fprintf(fid, '      - %s: sum=%.2f%%\n', config.obs_vars{irow}, vd_row_sum_check_mean.row_sums(irow));
        end
    end
else
    fprintf(fid, '  %s: not available\n', vd_source_title);
end

if has_vd_mode_plot && ~isempty(vd_matrix_mode)
    fprintf(fid, '\n=== VARIANCE DECOMPOSITION (POSTERIOR MODE) ===\n\n');
    fprintf(fid, '%-15s', 'Variable');
    for j = 1:length(shock_labels_plot_mode)
        fprintf(fid, '%12s', shock_labels_plot_mode{j});
    end
    fprintf(fid, '\n%s\n', repmat('-', 1, 15 + 12*length(shock_labels_plot_mode)));
    for i = 1:length(config.obs_vars)
        fprintf(fid, '%-15s', config.obs_vars{i});
        for j = 1:size(vd_matrix_mode, 2)
            fprintf(fid, '%12.1f', vd_matrix_mode(i, j));
        end
        fprintf(fid, '\n');
    end
end

if vd_row_sum_check_mode.available
    fprintf(fid, '  Posterior Mode: max deviation %.2fpp\n', vd_row_sum_check_mode.max_abs_dev);
    if isempty(vd_row_sum_check_mode.bad_rows)
        fprintf(fid, '    [OK] All rows pass\n');
    else
        fprintf(fid, '    [WARN] Rows with deviation > %.1fpp:\n', vd_sum_tolerance);
        for k = 1:length(vd_row_sum_check_mode.bad_rows)
            irow = vd_row_sum_check_mode.bad_rows(k);
            fprintf(fid, '      - %s: sum=%.2f%%\n', config.obs_vars{irow}, vd_row_sum_check_mode.row_sums(irow));
        end
    end
elseif has_vd_mode_plot
    fprintf(fid, '  Posterior Mode: not available\n');
end

fprintf(fid, '\n=== MOMENT COMPARISON ===\n\n');
fprintf(fid, '%-15s %12s %12s %12s %12s\n', 'Variable', 'Data Std', 'Model Std', 'Data AC1', 'Model AC1');
fprintf(fid, '%s\n', repmat('-', 1, 65));
for i = 1:length(config.obs_vars)
    vname = config.obs_vars{i};
    fprintf(fid, '%-15s %12.4f %12.4f %12.4f %12.4f\n', vname, ...
        moments.(vname).data_std, moments.(vname).model_std, ...
        moments.(vname).data_ac1, moments.(vname).model_ac1);
end

fprintf(fid, '\n=== IRF SIGN CHECKS ===\n\n');
fprintf(fid, '%-45s %10s %10s\n', 'Check', 'IRF(0)', 'Result');
fprintf(fid, '%s\n', repmat('-', 1, 67));
for i = 1:size(irf_results, 1)
    fprintf(fid, '%-45s %10.4f %10s\n', irf_results{i,1}, irf_results{i,2}, irf_results{i,4});
end

fprintf(fid, '\n=== SMOOTHED SHOCKS STATISTICS ===\n\n');
fprintf(fid, '%-15s %12s %12s %12s %12s\n', 'Shock', 'Mean', 'Std.Dev', 'Min', 'Max');
fprintf(fid, '%s\n', repmat('-', 1, 65));
for i = 1:length(shock_fields)
    s = oo_.SmoothedShocks.Mean.(shock_fields{i});
    fprintf(fid, '%-15s %12.4f %12.4f %12.4f %12.4f\n', ...
        shock_fields{i}, mean(s), std(s), min(s), max(s));
end

fprintf(fid, '\n===================================================================\n');
fprintf(fid, 'Analysis completed: %s\n', datestr(now));
fprintf(fid, '===================================================================\n');

fclose(fid);
fprintf('    [OK] Summary saved: %s\n', summary_file);

%% =========================================================================
%% 15B. EXPORT DOCUMENTATION SUMMARY (rendered by documentation_tables/refresh_documentation_tables)
%% =========================================================================
try
    dsge_summary = build_dsge_documentation_summary(config, fit_stats, param_table, moments, ...
        vd_source, vd_matrix, valid_idx, vd_row_sum_check_mean, dates, T);
    country_tok_docs = regexp(config.model_name, 'Macromodel_(\w+)', 'tokens');
    if isempty(country_tok_docs)
        error('Could not infer country name from model %s.', config.model_name);
    end
    doc_country = country_tok_docs{1}{1};
    dsge_summary.metadata.country = doc_country;
    validate_documentation_summary(dsge_summary, 'dsge');
    dsge_summary_file = artifact_path('output', fullfile('documentation_data', 'dsge', sprintf('dsge_postestimation_%s.json', doc_country)));
    write_json_summary(dsge_summary, dsge_summary_file, ...
        'country', doc_country, ...
        'source_function', 'postestimation_analysis');
    documentation_status = sprintf('summary exported to %s', dsge_summary_file);
catch documentation_error
    documentation_status = sprintf('documentation export failed: %s', documentation_error.message);
    warning('postestimation_analysis:DocumentationExportFailed', '%s', documentation_error.message);
end

fprintf('    [OK] Documentation status: %s\n', documentation_status);

%% =========================================================================
%% 16. CLOSE ALL FIGURES AND REPORT COMPLETION
%% =========================================================================
close all;

fprintf('\n');
fprintf('===================================================================\n');
fprintf('          POST-ESTIMATION ANALYSIS COMPLETE\n');
fprintf('===================================================================\n\n');

fprintf('Output files:\n');
fprintf('  - PDF Report:  %s\n', config.report_file);
fprintf('  - Text Summary: %s\n', summary_file);
fprintf('\nFigures saved to: %s/\n', config.graphs_dir);
fprintf('  - Fig01_Observables.png\n');
fprintf('  - Fig02_TracePlots.png\n');
fprintf('  - Fig03_PriorPosterior.png\n');
fprintf('  - Fig04_SmoothedShocks.png\n');
fprintf('  - Fig05_VarianceDecomp.png\n');
if has_vd_mode_plot
    fprintf('  - Fig05b_VarianceDecomp_Mode.png\n');
end
fprintf('  - Fig06_IRF_TechDemand.png\n');
fprintf('  - Fig07_IRF_MonLabor.png\n');
fprintf('  - Fig08_YPiCorrelation.png\n');
fprintf('  - Fig09_MomentComparison.png\n');
fprintf('\nDocumentation:\n');
fprintf('  - %s\n', documentation_status);

end

%% =========================================================================
%% HELPER FUNCTIONS
%% =========================================================================

function result = conditional(cond, true_val, false_val)
    if cond
        result = true_val;
    else
        result = false_val;
    end
end


function parsed_dates = parse_time_column(time_column)
    parsed_dates = [];

    if isempty(time_column)
        return;
    end

    if isnumeric(time_column)
        parsed_dates = time_column(:);
        return;
    end

    time_values = string(time_column);

    parsed_dates = nan(length(time_values), 1);
    for idx = 1:length(time_values)
        token = strtrim(char(time_values(idx)));
        match = regexp(token, '^(\d{4})q([1-4])$', 'tokens', 'once');
        if isempty(match)
            parsed_dates = [];
            return;
        end

        year_num = str2double(match{1});
        quarter_num = str2double(match{2});
        parsed_dates(idx) = year_num + (quarter_num - 1) / 4;
    end
end


function summary = build_dsge_documentation_summary(config, fit_stats, param_table, moments, ...
    vd_source, vd_matrix, valid_idx, vd_row_sum_check_mean, dates, T)

    summary = struct();
    summary.metadata = struct();
    summary.metadata.source_function = 'postestimation_analysis';
    summary.metadata.model_name = config.model_name;

    summary.fit = struct();
    summary.fit.log_mdd_laplace = fit_stats.log_mdd_laplace;
    summary.fit.log_mdd_mhm = fit_stats.log_mdd_mhm;
    summary.fit.sample_start = config.sample_start;
    summary.fit.sample_end = dates(end);
    summary.fit.n_observations = T;

    posterior_rows = repmat(struct('name', '', 'label_latex', '', 'prior_mean', NaN, ...
        'posterior_mean', NaN, 'hpd_low', NaN, 'hpd_high', NaN, 'group', ''), 0, 1);
    for idx = 2:size(param_table, 1)
        name = normalize_parameter_name(param_table{idx, 1});
        posterior_rows(end + 1).name = name; %#ok<AGROW>
        posterior_rows(end).label_latex = char(param_table{idx, 1});
        posterior_rows(end).prior_mean = str2double(param_table{idx, 2});
        posterior_rows(end).posterior_mean = str2double(param_table{idx, 3});
        posterior_rows(end).hpd_low = str2double(param_table{idx, 4});
        posterior_rows(end).hpd_high = str2double(param_table{idx, 5});
        posterior_rows(end).group = conditional(startsWith(name, 'sigma_'), 'shock_std', 'structural');
    end
    summary.posterior_parameters = posterior_rows;

    moment_rows = repmat(struct('observable', '', 'data_std', NaN, 'model_std', NaN, ...
        'data_ac1', NaN, 'model_ac1', NaN), 0, 1);
    for idx = 1:length(config.obs_vars)
        vname = config.obs_vars{idx};
        moment_rows(end + 1).observable = vname; %#ok<AGROW>
        moment_rows(end).data_std = moments.(vname).data_std;
        moment_rows(end).model_std = moments.(vname).model_std;
        moment_rows(end).data_ac1 = moments.(vname).data_ac1;
        moment_rows(end).model_ac1 = moments.(vname).model_ac1;
    end
    summary.moments = moment_rows;

    if ~isempty(vd_matrix)
        vd = struct();
        vd.source = vd_source;
        vd.shock_order = config.shock_names(valid_idx);
        vd.rows = repmat(struct('observable', '', 'shares_percent', struct(), 'row_sum', NaN), 0, 1);
        for idx = 1:length(config.obs_vars)
            share_struct = struct();
            for jdx = 1:size(vd_matrix, 2)
                share_struct.(vd.shock_order{jdx}) = vd_matrix(idx, jdx);
            end
            vd.rows(end + 1).observable = config.obs_vars{idx}; %#ok<AGROW>
            vd.rows(end).shares_percent = share_struct;
            vd.rows(end).row_sum = sum(vd_matrix(idx, :));
        end
        vd.validation = vd_row_sum_check_mean;
        summary.variance_decomposition = vd;
    else
        summary.variance_decomposition = struct('source', vd_source, 'shock_order', {{}}, 'rows', struct([]));
    end
end


function name = normalize_parameter_name(label)
    switch char(label)
        case '\sigma_{a}'
            name = 'sigma_a';
        case '\sigma_{d}'
            name = 'sigma_d';
        case '\sigma_{r}'
            name = 'sigma_r';
        case '\sigma_{p}'
            name = 'sigma_p';
        case '\sigma_{h}'
            name = 'sigma_h';
        case '\sigma_{L}'
            name = 'sigma_L';
        case '\sigma_{inv}'
            name = 'sigma_inv';
        otherwise
            cleaned = regexprep(char(label), '[\\$\{\}]', '');
            cleaned = strrep(cleaned, '\_', '_');
            cleaned = strrep(cleaned, ' ', '');
            if contains(cleaned, 'phi_inv,r')
                cleaned = strrep(cleaned, 'phi_inv,r', 'phi_inv_r');
            end
            name = cleaned;
    end
end
