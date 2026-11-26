function plot_custom_irfs(nrows, ncols)
% PLOT_CUSTOM_IRFS - Custom IRF plotting with configurable subplot layout
%
% Usage:
%   plot_custom_irfs(3, 4)     % 3 rows x 4 columns = 12 variables per figure (default)
%   plot_custom_irfs(4, 3)     % 4 rows x 3 columns = 12 variables per figure
%   plot_custom_irfs()         % Default: 3 x 4 (better for 16:9 screens)
%
% This function creates IRF plots from Dynare's oo_.irfs structure with
% a user-specified subplot layout. Each figure shows all variables for
% one shock, arranged in the specified grid.
%
% Requirements:
%   - Must be called after stoch_simul has been run
%   - oo_.irfs structure must exist in the workspace
%
% Model note:
%   - L (labor force) and h (hours) are EXOGENOUS AR(1) processes
%   - L responds ONLY to eps_L; h responds ONLY to eps_h
%   - IRFs of L and h to other shocks will be zero (correct behavior)
%   - rho_L and rho_h are CALIBRATED per country in country_params/{C}_calibration.mod
%
% Author: Generated for WELLSIM_SVEC project
% =========================================================================

global oo_ M_ options_

% Default layout: 3 rows x 4 columns (better for 16:9 screens)
if nargin < 1, nrows = 3; end
if nargin < 2, ncols = 4; end

% Check that IRFs exist
if ~isfield(oo_, 'irfs') || isempty(fieldnames(oo_.irfs))
    error('No IRFs found in oo_.irfs. Run stoch_simul with irf > 0 first.');
end

% Get all IRF field names
irf_names = fieldnames(oo_.irfs);

% Parse IRF names to extract variable names and shock names
% Format: VARIABLE_SHOCK (e.g., y_eps_a)
vars_in_irfs = {};
shocks_in_irfs = {};
for i = 1:length(irf_names)
    name = irf_names{i};
    % Find position of shock name (starts with eps_ or e_)
    idx_eps = strfind(name, '_eps_');
    idx_e = strfind(name, '_e_');

    if ~isempty(idx_eps)
        var_name = name(1:idx_eps(end)-1);
        shock_name = name(idx_eps(end)+1:end);
    elseif ~isempty(idx_e)
        var_name = name(1:idx_e(end)-1);
        shock_name = name(idx_e(end)+1:end);
    else
        % Fallback: assume last underscore separates var from shock
        underscores = strfind(name, '_');
        if ~isempty(underscores)
            var_name = name(1:underscores(end)-1);
            shock_name = name(underscores(end)+1:end);
        else
            continue;
        end
    end

    if ~ismember(var_name, vars_in_irfs)
        vars_in_irfs{end+1} = var_name;
    end
    if ~ismember(shock_name, shocks_in_irfs)
        shocks_in_irfs{end+1} = shock_name;
    end
end

% Sort variables and shocks for consistent ordering
% Use custom variable order (same as variable-centric figure) - includes hours h
preferred_var_order = {'y', 'w', 'n', 'h', 'u', 'v', 'theta', 'pi', 'R', 'gap', 'd', 'c', 'x', 'L', 'u_rate', 'k', 'inv'};
% Keep only vars that exist, in preferred order
ordered_vars = {};
for i = 1:length(preferred_var_order)
    if ismember(preferred_var_order{i}, vars_in_irfs)
        ordered_vars{end+1} = preferred_var_order{i};
    end
end
% Add any remaining vars not in preferred order (alphabetically)
remaining_vars = setdiff(vars_in_irfs, preferred_var_order);
vars_in_irfs = [ordered_vars, sort(remaining_vars)];

shocks_in_irfs = sort(shocks_in_irfs);

nvars = length(vars_in_irfs);
nshocks = length(shocks_in_irfs);

fprintf('Found %d variables and %d shocks\n', nvars, nshocks);
fprintf('Variables: %s\n', strjoin(vars_in_irfs, ', '));
fprintf('Shocks: %s\n', strjoin(shocks_in_irfs, ', '));

% Ensure output directory exists
graphs_dir = fullfile(M_.fname, 'graphs');
if ~exist(graphs_dir, 'dir')
    mkdir(graphs_dir);
    fprintf('Created directory: %s\n', graphs_dir);
end

% Calculate subplots per figure
plots_per_fig = nrows * ncols;

% Create one figure per shock
for s = 1:nshocks
    shock = shocks_in_irfs{s};

    % Calculate number of figures needed for this shock
    nfigs = ceil(nvars / plots_per_fig);

    for fig_idx = 1:nfigs
        fig = figure('Name', sprintf('IRF to %s (Page %d/%d)', shock, fig_idx, nfigs), ...
               'NumberTitle', 'off', ...
               'Position', [50, 50, 1920, 1080]);  % Full HD for 16:9

        % Set paper size for high-quality export
        set(fig, 'PaperPositionMode', 'auto');
        set(fig, 'PaperUnits', 'inches');
        set(fig, 'PaperSize', [19.2 10.8]);

        % Variables for this figure
        start_var = (fig_idx - 1) * plots_per_fig + 1;
        end_var = min(fig_idx * plots_per_fig, nvars);

        plot_idx = 0;
        for v = start_var:end_var
            var_name = vars_in_irfs{v};
            plot_idx = plot_idx + 1;

            % Construct IRF field name
            irf_field = sprintf('%s_%s', var_name, shock);

            if isfield(oo_.irfs, irf_field)
                irf_data = oo_.irfs.(irf_field);
                periods = 1:length(irf_data);

                subplot(nrows, ncols, plot_idx);
                plot(periods, irf_data, 'b-', 'LineWidth', 1.5);
                hold on;
                plot(periods, zeros(size(periods)), 'k--', 'LineWidth', 0.5);
                hold off;

                % Format title with proper variable name
                var_title = format_var_name(var_name);
                title(var_title, 'Interpreter', 'tex');
                xlabel('Quarters');
                ylabel('% dev.');
                grid on;

                % Tight axis
                xlim([1 length(irf_data)]);
            else
                % Variable doesn't respond to this shock (or not computed)
                subplot(nrows, ncols, plot_idx);
                text(0.5, 0.5, sprintf('%s\n(not available)', var_name), ...
                     'HorizontalAlignment', 'center', ...
                     'VerticalAlignment', 'middle');
                axis off;
            end
        end

        % Add figure title
        shock_title = format_shock_name(shock);
        sgtitle(sprintf('Impulse Responses to %s Shock', shock_title), ...
                'FontSize', 14, 'FontWeight', 'bold');

        % Save figure - use print for higher resolution PNG
        fig_filename = sprintf('IRF_%s_page%d', shock, fig_idx);
        % saveas(gcf, fullfile(M_.fname, 'graphs', [fig_filename '.fig']));
        % saveas(gcf, fullfile(M_.fname, 'graphs', [fig_filename '.eps']), 'epsc');
        print(gcf, fullfile(M_.fname, 'graphs', [fig_filename '.jpeg']), '-djpeg', '-r300');
        fprintf('Saved: %s\n', fig_filename);
    end
end

fprintf('\nCustom IRF plots completed. Figures saved to %s/graphs/\n', M_.fname);

% =========================================================================
% Create Combined Summary Figure (2x3 layout, one subplot per shock)
% =========================================================================
create_combined_irf_figure(oo_, M_, vars_in_irfs, shocks_in_irfs);

% =========================================================================
% Create Variable-centric Figure (4x3 layout, one subplot per variable)
% Shows each variable's response to ALL shocks - better for comparing
% parameter sensitivity (e.g., different lambda_w settings)
% =========================================================================
create_variable_irf_figure(oo_, M_, vars_in_irfs, shocks_in_irfs);

end

% =========================================================================
% Combined IRF Figure (all shocks in one 2x3 figure)
% =========================================================================
function create_combined_irf_figure(oo_, M_, vars_in_irfs, shocks_in_irfs)
    % Key variables to show in combined plot
    key_vars = {'y', 'w', 'n', 'u', 'pi'};

    % Filter to variables that exist
    key_vars = key_vars(ismember(key_vars, vars_in_irfs));

    % Colors for each variable
    colors = lines(length(key_vars));

    % Create 2x3 figure
    figure('Name', 'Combined IRFs - All Shocks', ...
           'NumberTitle', 'off', ...
           'Position', [50, 50, 1400, 700]);

    nshocks = length(shocks_in_irfs);

    for s = 1:min(nshocks, 6)  % Max 6 subplots in 2x3
        shock = shocks_in_irfs{s};

        subplot(2, 3, s);
        hold on;

        legend_entries = {};
        for v = 1:length(key_vars)
            var_name = key_vars{v};
            irf_field = sprintf('%s_%s', var_name, shock);

            if isfield(oo_.irfs, irf_field)
                irf_data = oo_.irfs.(irf_field);
                periods = 1:length(irf_data);

                plot(periods, irf_data, '-', 'LineWidth', 1.5, 'Color', colors(v,:));
                legend_entries{end+1} = format_var_name_short(var_name);
            end
        end

        % Zero line
        plot(periods, zeros(size(periods)), 'k--', 'LineWidth', 0.5);
        hold off;

        % Format
        shock_title = format_shock_name(shock);
        title(shock_title, 'FontSize', 11, 'FontWeight', 'bold');
        xlabel('Quarters');
        ylabel('% dev.');
        grid on;
        xlim([1 length(irf_data)]);

        % Legend only in first subplot to avoid clutter
        if s == 1
            legend(legend_entries, 'Location', 'best', 'FontSize', 8);
        end
    end

    sgtitle('Impulse Response Functions by Shock', 'FontSize', 14, 'FontWeight', 'bold');

    % Save combined figure - use print for higher resolution PNG
    % saveas(gcf, fullfile(M_.fname, 'graphs', 'IRF_combined.fig'));
    % saveas(gcf, fullfile(M_.fname, 'graphs', 'IRF_combined.eps'), 'epsc');
    print(gcf, fullfile(M_.fname, 'graphs', 'IRF_combined.jpeg'), '-djpeg', '-r300');
    fprintf('Saved: IRF_combined\n');
end

function formatted = format_var_name_short(var_name)
    % Short format for legend
    switch var_name
        case 'y',     formatted = 'Output';
        case 'w',     formatted = 'Wage';
        case 'n',     formatted = 'Employment';
        case 'u',     formatted = 'Unemployment';
        case 'pi',    formatted = 'Inflation';
        case 'R',     formatted = 'Interest Rate';
        case 'theta', formatted = 'Tightness';
        case 'v',     formatted = 'Vacancies';
        otherwise,    formatted = var_name;
    end
end

% =========================================================================
% Variable-centric IRF Figure (one subplot per variable, all shocks overlaid)
% =========================================================================
function create_variable_irf_figure(oo_, M_, vars_in_irfs, shocks_in_irfs)
    % Variables to show (in desired order) - includes hours h
    display_vars = {'y', 'w', 'n', 'h', 'u', 'v', 'theta', 'pi', 'R', 'gap', 'd', 'x', 'L', 'u_rate', 'k', 'inv'};

    % Filter to variables that exist
    display_vars = display_vars(ismember(display_vars, vars_in_irfs));
    nvars = length(display_vars);

    % Colors for each shock
    nshocks = length(shocks_in_irfs);
    colors = lines(nshocks);

    % Line styles for better distinction
    line_styles = {'-', '--', ':', '-.', '-'};

    % Create 3x4 figure - better for 16:9 screens
    fig = figure('Name', 'IRFs by Variable - All Shocks', ...
           'NumberTitle', 'off', ...
           'Position', [50, 50, 1920, 1080]);  % Full HD resolution

    % Set paper size for high-quality export
    set(fig, 'PaperPositionMode', 'auto');
    set(fig, 'PaperUnits', 'inches');
    set(fig, 'PaperSize', [19.2 10.8]);  % Matches 1920x1080 at 100 dpi

    for v = 1:min(nvars, 16)  % Max 16 subplots in 4x4
        var_name = display_vars{v};

        subplot(4, 4, v);
        hold on;

        legend_entries = {};
        for s = 1:nshocks
            shock = shocks_in_irfs{s};
            irf_field = sprintf('%s_%s', var_name, shock);

            if isfield(oo_.irfs, irf_field)
                irf_data = oo_.irfs.(irf_field);
                periods = 1:length(irf_data);

                style_idx = mod(s-1, length(line_styles)) + 1;
                plot(periods, irf_data, line_styles{style_idx}, ...
                     'LineWidth', 1.5, 'Color', colors(s,:));
                legend_entries{end+1} = format_shock_name_short(shock);
            end
        end

        % Zero line
        if exist('periods', 'var')
            plot(periods, zeros(size(periods)), 'k--', 'LineWidth', 0.5);
        end
        hold off;

        % Format
        var_title = format_var_name(var_name);
        title(var_title, 'FontSize', 10, 'FontWeight', 'bold');
        xlabel('Quarters');
        ylabel('% dev.');
        grid on;
        if exist('irf_data', 'var')
            xlim([1 length(irf_data)]);
        end

        % Legend only in first subplot
        if v == 1
            legend(legend_entries, 'Location', 'best', 'FontSize', 7);
        end
    end

    sgtitle('IRF of Each Variable to All Shocks', 'FontSize', 14, 'FontWeight', 'bold');

    % Save figure - use print for higher resolution PNG
    % saveas(gcf, fullfile(M_.fname, 'graphs', 'IRF_by_variable.fig'));
    % saveas(gcf, fullfile(M_.fname, 'graphs', 'IRF_by_variable.eps'), 'epsc');
    print(gcf, fullfile(M_.fname, 'graphs', 'IRF_by_variable.jpeg'), '-djpeg', '-r300');
    fprintf('Saved: IRF_by_variable\n');
end

function formatted = format_shock_name_short(shock_name)
    % Short format for legend
    switch shock_name
        case 'eps_a',     formatted = 'Technology';
        case 'eps_r',     formatted = 'Monetary';
        case 'eps_L',     formatted = 'Labor Force';
        case 'eps_h',     formatted = 'Hours';
        case 'eps_d',     formatted = 'Demand';
        case 'eps_sep',   formatted = 'Separation';
        case 'eps_inv',   formatted = 'Investment';
        otherwise
            if startsWith(shock_name, 'eps_')
                formatted = shock_name(5:end);
            else
                formatted = shock_name;
            end
    end
end

% =========================================================================
% Helper Functions
% =========================================================================

function formatted = format_var_name(var_name)
    % Format variable names for display
    switch var_name
        case 'y'
            formatted = 'Output (y)';
        case 'd'
            formatted = 'Demand (d)';
        case 'gap'
            formatted = 'Output Gap';
        case 'pi'
            formatted = 'Inflation (\pi)';
        case 'R'
            formatted = 'Interest Rate (R)';
        case 'n'
            formatted = 'Employment (n)';
        case 'h'
            formatted = 'Hours (h)';
        case 'u'
            formatted = 'Unemployment (u)';
        case 'u_rate'
            formatted = 'Unemp. Rate (u\_rate)';
        case 'v'
            formatted = 'Vacancies (v)';
        case 'w'
            formatted = 'Real Wage (w)';
        case 'theta'
            formatted = 'Labor Mkt Tightness (\theta)';
        case 'x'
            formatted = 'Technology (x)';
        case 'L'
            formatted = 'Labor Force (L)';
        case 'k'
            formatted = 'Capital (k)';
        case 'inv'
            formatted = 'Investment (inv)';
        case 'mc'
            formatted = 'Marginal Cost (mc)';
        otherwise
            formatted = var_name;
    end
end

function formatted = format_shock_name(shock_name)
    % Format shock names for display
    switch shock_name
        case 'eps_a'
            formatted = 'Technology';
        case 'eps_r'
            formatted = 'Monetary Policy';
        case 'eps_L'
            formatted = 'Labor Force';
        case 'eps_h'
            formatted = 'Hours';
        case 'eps_sep'
            formatted = 'Separation';

        case 'eps_d'
            formatted = 'Demand';
        case 'eps_inv'
            formatted = 'Investment';
        otherwise
            % Remove 'eps_' prefix if present
            if startsWith(shock_name, 'eps_')
                formatted = shock_name(5:end);
            elseif startsWith(shock_name, 'e_')
                formatted = shock_name(3:end);
            else
                formatted = shock_name;
            end
            % Capitalize first letter
            formatted(1) = upper(formatted(1));
    end
end
