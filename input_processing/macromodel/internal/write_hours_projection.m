function info = write_hours_projection(growth_mat, output_dir, country)
% WRITE_HOURS_PROJECTION  Write hours_projection_<COUNTRY>.csv for SimPaths.
%
%   info = WRITE_HOURS_PROJECTION(growth_mat, output_dir, country) reads the
%   Ramsey workspace at `growth_mat` and writes the planner's hours-per-worker
%   path over the SimPaths horizon. Returns a struct describing what was written.
%
% CONTRACT: this is the only writer of hours_projection_*.csv. Both
% export_dynamic_for_simpaths.m (full workflow) and export_ramsey_for_simpaths.m
% (Ramsey-only refresh) call it, following write_growth_terminal_state.m.
%
% WHY THE FILE EXISTS. SimPaths reads the hours path from here instead of
% re-deriving it by extending the `h` column of growth_model_reference_*.csv, so
% the trailing growth window is not a convention to keep in step across the
% MATLAB/Java boundary. This side extends hours with window 1
% (extend_level_path(handoff_h_hist, ..., 1) in ramsey_trend.m). A Java-side
% extension with another window would agree only while hours_trend_method is
% log_linear, which makes the exported h column a pure exponential, so every
% window recovers the same rate; after a change to that method the two sides
% would project different hours from the same bundle with no error anywhere.
% Emitting the path leaves one computation instead of two.
%
% The demographic side works the same way: population_projection_*.csv
% (time,N,P) is written here and read there.
%
% FORMAT: `time,h`, one row per quarter, starting at the handoff quarter so the
% first row coincides with the last row of growth_model_reference_*.csv. Length is
% projection_quarters + 1. The terminal tail is NOT included: RamseyTrendModel
% rebuilds it with the same window-1 rule.

    arguments
        growth_mat (1,:) char
        output_dir (1,:) char
        country (1,:) char
    end

    info = struct('written', false, 'file', '', 'quarters', 0, 'handoff_time', '');

    if ~exist(growth_mat, 'file')
        return;
    end

    mat_vars = who('-file', growth_mat);
    required = {'handoff_h_ext', 'projection_quarters', 'simpaths_handoff_time'};
    missing = required(~ismember(required, mat_vars));
    if ~isempty(missing)
        error('wellsim:hoursProjection:missingVars', ...
              ['%s lacks %s. The hours projection cannot be emitted; re-run ' ...
               'ramsey_trend to refresh the workspace.'], ...
              growth_mat, strjoin(missing, ', '));
    end

    G = load(growth_mat, required{:});
    proj_len = G.projection_quarters + 1;

    if numel(G.handoff_h_ext) < proj_len
        error('wellsim:hoursProjection:tooShort', ...
              ['handoff_h_ext has %d quarters but the SimPaths horizon needs %d. ' ...
               'Re-run ramsey_trend before exporting.'], ...
              numel(G.handoff_h_ext), proj_len);
    end

    % handoff_h_ext = [projection over the SimPaths horizon, terminal tail].
    hours_proj = G.handoff_h_ext(1:proj_len);
    if any(~isfinite(hours_proj)) || any(hours_proj <= 0)
        error('wellsim:hoursProjection:nonPositive', ...
              'Hours projection contains non-finite or non-positive values.');
    end

    handoff_label = char(string(G.simpaths_handoff_time));
    tokens = regexp(handoff_label, '(\d+)q(\d)', 'tokens', 'once');
    if isempty(tokens)
        error('wellsim:hoursProjection:badHandoffTime', ...
              'Cannot parse simpaths_handoff_time "%s" as YYYYqQ.', handoff_label);
    end
    start_year = str2double(tokens{1});
    start_quarter = str2double(tokens{2});

    out_file = fullfile(output_dir, sprintf('hours_projection_%s.csv', country));
    fid = fopen(out_file, 'w');
    if fid < 0
        error('wellsim:hoursProjection:cannotWrite', ...
              'Cannot open %s for writing.', out_file);
    end
    closer = onCleanup(@() fclose(fid));

    fprintf(fid, 'time,h\n');
    for i = 1:proj_len
        absolute_quarter = (start_quarter - 1) + (i - 1);
        label_year = start_year + floor(absolute_quarter / 4);
        label_quarter = mod(absolute_quarter, 4) + 1;
        % %.10f, not the %.6f used for population_projection_*.csv: hours are
        % O(500) where N is O(14000), so six decimals would cap the handoff at
        % ~1e-9 relative, the size of the Java parity tolerance, and make this
        % file, rather than the solve, the precision bottleneck.
        fprintf(fid, '%dq%d,%.10f\n', label_year, label_quarter, hours_proj(i));
    end

    clear closer;

    info.written = true;
    info.file = out_file;
    info.quarters = proj_len;
    info.handoff_time = handoff_label;
end
