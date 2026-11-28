function ramsey_projection_plot(country, options)
% RAMSEY_PROJECTION_PLOT  Ex-ante annual diagnostic plot of the Ramsey forward projection.
%
% Plots the out-of-sample trajectories of the Ramsey wage, output,
% consumption, investment and capital trends as % deviation from the first
% projection year. Mirrors the SimPaths-side Ramsey-vs-no-macro-model
% diagnostic but reads the MATLAB Ramsey workspace directly, so no SimPaths
% simulation is needed.
%
% Usage:
%   >> ramsey_projection_plot('Poland')
%   >> ramsey_projection_plot('Poland', 'projection_start', '2023q1', ...
%                                       'projection_end',   '2060q4')
%
% Reads:
%   ramsey_trend_{country}.mat   (must contain the forward-projection bundle
%                                 saved by ramsey_trend.m)
%
% Writes:
%   ramsey_projection_{country}.png   (raster)
%   ramsey_projection_{country}.pdf   (vector, for slides/manuscript)

    arguments
        country (1,:) char
        options.projection_start (1,:) char = ''
        options.projection_end   (1,:) char = '2060q4'
        options.output_file      (1,:) char = ''
        options.simpaths_saving_rate (1,1) double = 0.056
    end

    country_config = get_country_config(country);
    country = country_config.name;

    projection_start = resolve_projection_start(options.projection_start);
    projection_end   = char(lower(string(options.projection_end)));

    mat_file = artifact_path('output', sprintf('ramsey_trend_%s.mat', lower(country)));
    if ~exist(mat_file, 'file')
        error('ramsey_projection_plot:WorkspaceMissing', ...
              ['Ramsey workspace not found: %s\n' ...
               'Run ramsey_trend(''%s'', ...) first to refresh the workspace.'], ...
              mat_file, country);
    end

    S = load(mat_file);

    required = {'handoff_K_path', 'handoff_Y_path', 'handoff_c_path', ...
                'handoff_N_path', 'handoff_h_ext', 'handoff_gY_ext', ...
                'handoff_nxY_ext', 'handoff_P_ext', 'simpaths_handoff_time', 'params'};
    missing = required(~isfield(S, required));
    if ~isempty(missing)
        error('ramsey_projection_plot:BundleIncomplete', ...
              ['Forward projection arrays missing from %s: %s\n' ...
               'Re-run ramsey_trend(''%s'', ...) to refresh the workspace ' ...
               'with the post-handoff bundle.'], ...
              mat_file, strjoin(missing, ', '), country);
    end

    alpha = S.params.parameters.alpha_K;

    % Quarterly forward paths (length = handoff_total_len)
    Kq = S.handoff_K_path(:);
    Yq = S.handoff_Y_path(:);
    cq = S.handoff_c_path(:);
    Nq = S.handoff_N_path(:);
    hq = S.handoff_h_ext(1:length(Kq));
    gYq = S.handoff_gY_ext(1:length(Kq));
    nxYq = S.handoff_nxY_ext(1:length(Kq));

    Pq = S.handoff_P_ext(1:length(Kq));
    Pq = Pq(:);

    % handoff_c_path is consumption per head of the resident population, so
    % aggregate consumption scales with P. Output per worker and hours stay on N.
    Cq = Pq .* cq;
    Iq = Yq - Cq - gYq .* Yq - nxYq .* Yq;
    wq = (1 - alpha) * Yq ./ (Nq .* hq);

    % Build quarterly time labels starting from simpaths_handoff_time
    handoff_label = char(S.simpaths_handoff_time);
    [start_year, start_q] = parse_quarter_label(handoff_label);
    n_q = length(Kq);
    quarter_year = zeros(n_q, 1);
    quarter_qid  = zeros(n_q, 1);
    y = start_year; q = start_q;
    for t = 1:n_q
        quarter_year(t) = y;
        quarter_qid(t)  = q;
        q = q + 1;
        if q > 4
            q = 1;
            y = y + 1;
        end
    end

    % Aggregate to annual averages (only keep complete 4-quarter years)
    [start_year_proj, ~] = parse_quarter_label(projection_start);
    [end_year_proj,   ~] = parse_quarter_label(projection_end);

    years = start_year_proj:end_year_proj;
    n_years = numel(years);
    depratio_q = Pq ./ Nq;                 % residents per person employed
    hph_q      = Nq .* hq ./ Pq;           % hours worked per resident
    ann = annualize_complete_years([wq, Yq, Cq, Iq, Kq, depratio_q, hph_q], quarter_year, years);
    Wann = ann(:,1); Yann = ann(:,2); Cann = ann(:,3); Iann = ann(:,4); Kann = ann(:,5);
    DRann = ann(:,6); HPHann = ann(:,7);

    % --- Investment-rate vs capital-widening diagnostic (6th panel) ---
    % Capital accumulates as K_{t+1} = (1-delta)K_t + I_t, so the capital-
    % output ratio rises iff the gross investment rate I/Y clears the
    % capital-widening requirement (g_Y + delta) * K/Y. The signed gap
    % between the two lines is exactly the direction of the K/Y transition.
    % This is the structural resolution of the "investment up, capital down"
    % paradox: a permanent rise in trend TFP growth lifts g_Y (hence the
    % break-even line) and lowers the BGP K/Y target, so capital accumulates
    % more slowly relative to output even while the investment flow is high.
    % Computed quarterly (where the accumulation identity is exact) then
    % aggregated to annual.
    delta_q = 1 - (1 - S.params.parameters.delta_annual)^(1/4);
    sI_q = Iq ./ Yq;
    KY_q = Kq ./ Yq;
    % Trailing-4Q trend output growth: the capital-widening requirement is a
    % trend object, and the raw quarterly series carries a sharp near-term
    % step from the demographic (employment) input path.
    gY_trend = movmean([NaN; Yq(2:end) ./ Yq(1:end-1) - 1], [3 0], 'omitnan');
    be_q = (gY_trend + delta_q) .* KY_q;

    ann = annualize_complete_years([sI_q, be_q], quarter_year, years);
    sIann = ann(:,1); beann = ann(:,2);

    % --- Implied Ramsey saving rate diagnostic (7th panel) ---
    % SimPaths applies a single constant household saving rate s via
    % c = yd*(1-s). The Ramsey projection implies a time-varying rate, but
    % which number to pass depends on what counts as "saved" out of output:
    %   I/Y       gross investment rate (closed-economy Ramsey, S=I)
    %   (I+NX)/Y  national saving (adds the current account to investment)
    %   1-C/Y     all non-consumed output; closest to SimPaths' 1-s share
    sNonC_q = 1 - Cq ./ Yq;
    sNat_q  = (Yq - Cq - gYq .* Yq) ./ Yq;

    % Disposable-income basis. SimPaths sets c = yd*(1-s), so the comparable
    % Ramsey rate measures saving as a share of disposable income, not output.
    % With a balanced government budget, household disposable income is output
    % net of the government claim (yd = Y - G), and household saving then
    % finances investment plus the current account: s_yd = (I+NX)/(Y-G) =
    % 1 - C/(Y-G). Used as the basis for the channel-ready transform below.
    yd_q  = (1 - gYq) .* Yq;
    sYd_q = 1 - Cq ./ yd_q;

    ann = annualize_complete_years([sNonC_q, sNat_q, sYd_q], quarter_year, years);
    sNonCann = ann(:,1); sNatann = ann(:,2); sYdann = ann(:,3);

    valid = ~isnan(Yann);
    if nnz(valid) < 2
        error('ramsey_projection_plot:RangeOutOfBounds', ...
              ['No complete annual aggregates in [%d, %d]. ' ...
               'Forward projection covers %s to %s.'], ...
              start_year_proj, end_year_proj, ...
              format_quarter(quarter_year(1), quarter_qid(1)), ...
              format_quarter(quarter_year(end), quarter_qid(end)));
    end

    % Index annual averages against the handoff quarter, matching SimPaths'
    % MacroPaths convention for the displayed "first year" trend columns.
    first_idx = find(valid, 1, 'first');
    base_W = wq(1);
    base_Y = Yq(1);
    base_C = Cq(1);
    base_I = Iq(1);
    base_K = Kq(1);

    pct_W = 100 * (Wann ./ base_W - 1);
    pct_Y = 100 * (Yann ./ base_Y - 1);
    pct_C = 100 * (Cann ./ base_C - 1);
    pct_I = 100 * (Iann ./ base_I - 1);
    pct_K = 100 * (Kann ./ base_K - 1);
    pct_DR  = 100 * (DRann  ./ depratio_q(1) - 1);
    pct_HPH = 100 * (HPHann ./ hph_q(1) - 1);

    fig = figure('Name', sprintf('Ramsey Projection - %s', country), ...
                 'Color', 'w', 'Position', [100 100 1500 850]);
    layout = tiledlayout(fig, 4, 3, 'TileSpacing', 'compact', 'Padding', 'compact');

    plot_panel(years, pct_W, 'Wage trend');
    plot_panel(years, pct_Y, 'Output trend');
    plot_panel(years, pct_C, 'Consumption trend');
    plot_panel(years, pct_I, 'Investment trend');
    plot_panel(years, pct_K, 'Capital trend');
    plot_breakeven_panel(years, sIann, beann);
    plot_saving_panel(years, sIann, sNatann, sNonCann, options.simpaths_saving_rate);
    plot_saving_deviation_panel(years, sIann, sNatann, sNonCann, sYdann);
    plot_household_rate_panel(years, sYdann, options.simpaths_saving_rate);
    plot_dependency_panel(years, pct_DR, pct_HPH);

    layout_title = sprintf('Ramsey ex-ante projection -- %s (%d-%d)', ...
                           country, years(first_idx), years(find(valid,1,'last')));
    title(layout, {layout_title; 'Annual structural trend index (% from first year)'}, ...
          'FontWeight', 'bold');

    if isempty(options.output_file)
        png_file = artifact_path('figures', sprintf('ramsey_projection_%s.png', lower(country)), 'create', true);
        [fig_dir, fig_name] = fileparts(png_file);
        out_stem = fullfile(fig_dir, fig_name);
    else
        png_file = options.output_file;
        [out_dir, out_name] = fileparts(options.output_file);
        out_stem = fullfile(out_dir, out_name);
    end
    pdf_file = [out_stem '.pdf'];
    exportgraphics(fig, png_file, 'BackgroundColor', 'white');
    exportgraphics(fig, pdf_file, 'BackgroundColor', 'white', 'ContentType', 'vector');
    fprintf('  Saved: %s\n', png_file);
    fprintf('  Saved: %s\n', pdf_file);
end


function plot_panel(years, values, title_str)
    nexttile;
    plot(years, zeros(size(years)), '--', 'Color', [0.5 0.5 0.5], 'LineWidth', 1.0);
    hold on;
    plot(years, values, '-', 'Color', [0.10 0.25 0.55], 'LineWidth', 1.6);
    hold off;
    xlim([years(1) years(end)]);
    grid on;
    title(title_str);
    ylabel('% from first year');
    legend('Baseline', 'Scenario', 'Location', 'best', 'FontSize', 7, 'Box', 'on');
    tick_step = max(1, round((years(end) - years(1)) / 8));
    xticks(years(1):tick_step:years(end));
    xtickangle(0);
end


function plot_dependency_panel(years, pct_dr, pct_hph)
% PLOT_DEPENDENCY_PANEL  Dependency ratio P/N and hours worked per resident
% N*h/P, annual averages indexed against the handoff quarter like the other
% tiles.
    nexttile;
    plot(years, zeros(size(years)), '--', 'Color', [0.5 0.5 0.5], 'LineWidth', 1.0);
    hold on;
    h1 = plot(years, pct_dr,  '-',  'Color', [0.10 0.25 0.55], 'LineWidth', 1.6);
    h2 = plot(years, pct_hph, '--', 'Color', [0.75 0.20 0.20], 'LineWidth', 1.6);
    hold off;
    xlim([years(1) years(end)]);
    grid on;
    title('Dependency ratio and hours worked per head');
    ylabel('% from first year');
    legend([h1 h2], {'P/N', 'N\cdot h/P'}, 'Location', 'best', 'FontSize', 7, 'Box', 'on');
    tick_step = max(1, round((years(end) - years(1)) / 8));
    xticks(years(1):tick_step:years(end));
    xtickangle(0);
end


function plot_breakeven_panel(years, sI, breakeven)
% PLOT_BREAKEVEN_PANEL  Investment rate vs the capital-widening requirement.
%
% I/Y above (g_Y+delta)*K/Y => K/Y rising (capital deepening); below =>
% K/Y falling. The two lines meet on the balanced growth path.
    nexttile;
    h1 = plot(years, 100*sI, '-', 'Color', [0.10 0.25 0.55], 'LineWidth', 1.6);
    hold on;
    h2 = plot(years, 100*breakeven, ':', 'Color', [0.75 0.20 0.20], 'LineWidth', 1.6);
    hold off;
    xlim([years(1) years(end)]);
    grid on;
    title('Investment rate vs break-even');
    ylabel('% of GDP');
    legend([h1 h2], {'I/Y', '(g_Y+\delta)\cdot K/Y'}, ...
           'Location', 'best', 'FontSize', 7, 'Box', 'on');
    tick_step = max(1, round((years(end) - years(1)) / 8));
    xticks(years(1):tick_step:years(end));
    xtickangle(0);
end


function plot_saving_panel(years, sInv, sNat, sNonC, simpaths_rate)
% PLOT_SAVING_PANEL  Implied Ramsey saving rate under three accounting choices.
%
% SimPaths currently hard-codes a single constant household saving rate
% (the reference line) applied as c = yd*(1-s). The Ramsey projection
% implies a time-varying rate; the three lines bracket the candidate
% channels by what counts as saving out of output: gross investment (S=I),
% national saving (adds net exports), and all non-consumed output (closest
% to SimPaths' own 1-s share). Levels sit far above the reference because
% these are economy-wide output shares, not a personal net saving rate; the
% disposable-income panel makes the comparison level-comparable.
    nexttile;
    h1 = plot(years, 100*sInv,  '-',  'Color', [0.10 0.25 0.55], 'LineWidth', 1.6);
    hold on;
    h2 = plot(years, 100*sNat,  '--', 'Color', [0.20 0.55 0.30], 'LineWidth', 1.6);
    h3 = plot(years, 100*sNonC, ':',  'Color', [0.75 0.20 0.20], 'LineWidth', 1.6);
    href = yline(100*simpaths_rate, '-', 'Color', [0.6 0.6 0.6], 'LineWidth', 1.0);
    hold off;
    xlim([years(1) years(end)]);
    grid on;
    title('Implied saving rate');
    ylabel('% of GDP');
    legend([h1 h2 h3 href], {'I/Y', '(I+NX)/Y', '1-C/Y', ...
           sprintf('SimPaths %.1f%%', 100*simpaths_rate)}, ...
           'Location', 'best', 'FontSize', 7, 'Box', 'on');
    tick_step = max(1, round((years(end) - years(1)) / 8));
    xticks(years(1):tick_step:years(end));
    xtickangle(0);
end


function plot_household_rate_panel(years, sYd, simpaths_rate)
% PLOT_HOUSEHOLD_RATE_PANEL  Channel-ready household saving rate: the
% calibrated SimPaths level scaled by the Ramsey proportional change.
%
% The representative-agent Ramsey model has no household/corporate split, so
% it cannot pin the household saving *level* -- but it does pin the relative
% path. Keeping the calibrated anchor s0 and applying the Ramsey proportional
% change, s_h(t) = s0 * sYd(t)/sYd(t0), turns the hard-coded constant into a
% breathing series without claiming the macro level. The disposable-income
% basis is used for the ratio as the closest concept to a household rate.
    base = sYd(find(~isnan(sYd), 1, 'first'));
    s_h = simpaths_rate * (sYd / base);
    nexttile;
    h1 = plot(years, 100*s_h, '-', 'Color', [0.10 0.25 0.55], 'LineWidth', 1.6);
    hold on;
    href = yline(100*simpaths_rate, '-', 'Color', [0.6 0.6 0.6], 'LineWidth', 1.0);
    hold off;
    xlim([years(1) years(end)]);
    grid on;
    title('Channel-ready household saving rate');
    ylabel('% of disposable income');
    legend([h1 href], {'s_0\cdot s_{Ram}(t)/s_{Ram}(t_0)', ...
           sprintf('SimPaths %.1f%%', 100*simpaths_rate)}, ...
           'Location', 'best', 'FontSize', 7, 'Box', 'on');
    tick_step = max(1, round((years(end) - years(1)) / 8));
    xticks(years(1):tick_step:years(end));
    xtickangle(0);
end


function plot_saving_deviation_panel(years, sInv, sNat, sNonC, sYd)
% PLOT_SAVING_DEVIATION_PANEL  Saving rates as percentage-point deviation
% from their first projection year.
%
% Levels differ across definitions and sit well above the SimPaths constant,
% which obscures the only thing a channel needs: how much the rate moves
% over the projection. Re-basing each series to its own first year strips
% the level and shows the common transition shape -- the candidate
% time-variation to pass to SimPaths.
    series = {sInv, sNat, sNonC, sYd};
    labels = {'I/Y', '(I+NX)/Y', '1-C/Y', '1-C/(Y-G)'};
    colors = [0.10 0.25 0.55; 0.20 0.55 0.30; 0.75 0.20 0.20; 0.50 0.20 0.60];
    styles = {'-', '--', ':', '-.'};

    nexttile;
    plot(years, zeros(size(years)), '-', 'Color', [0.6 0.6 0.6], 'LineWidth', 1.0);
    hold on;
    h = gobjects(1, numel(series));
    for j = 1:numel(series)
        s = series{j};
        base = s(find(~isnan(s), 1, 'first'));
        h(j) = plot(years, 100*(s - base), styles{j}, ...
                    'Color', colors(j, :), 'LineWidth', 1.6);
    end
    hold off;
    xlim([years(1) years(end)]);
    grid on;
    title('Saving rate change from first year');
    ylabel('pp deviation');
    legend(h, labels, 'Location', 'best', 'FontSize', 7, 'Box', 'on');
    tick_step = max(1, round((years(end) - years(1)) / 8));
    xticks(years(1):tick_step:years(end));
    xtickangle(0);
end


function [year_num, quarter_num] = parse_quarter_label(label)
    label = char(lower(string(label)));
    tokens = regexp(label, '^(\d{4})q([1-4])$', 'tokens', 'once');
    if isempty(tokens)
        error('ramsey_projection_plot:BadQuarterLabel', ...
              'Expected quarter label like ''2023q1''; got ''%s''.', label);
    end
    year_num    = str2double(tokens{1});
    quarter_num = str2double(tokens{2});
end


function label = format_quarter(year_num, quarter_num)
    label = sprintf('%dq%d', year_num, quarter_num);
end
