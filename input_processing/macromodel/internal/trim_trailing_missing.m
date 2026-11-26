function [tbl, n_trimmed] = trim_trailing_missing(tbl, value_vars)
% TRIM_TRAILING_MISSING  Drop trailing rows that carry no data in value_vars.
%
%   [tbl, n_trimmed] = TRIM_TRAILING_MISSING(tbl, value_vars)
%
% make_datasets.do exports a complete quarterly calendar, so the last rows of
% the level files routinely have no observables yet - the calendar runs ahead of
% published national accounts. Growth rates computed over a window that lands in
% that tail come out NaN, which propagates into g_Y, K_0 and the labour trend and
% ends as an infeasible Ramsey initial guess. A ragged edge is normal macro data,
% not an error, so trim it at load rather than defending against NaN downstream.
%
% Only the TAIL is trimmed. Interior gaps (e.g. COVID censoring) are preserved,
% because they are meaningful missingness the Kalman filter is expected to handle.
%
% value_vars names the columns that make a row useful; a row survives if ANY of
% them has data. Columns not present in the table are ignored, so callers may pass
% a superset. Pass the columns the CONSUMER actually needs, not every column in the
% file: Poland's L_level and u_level run two quarters past y_level/n_level/h_level,
% so trimming on the full set would keep rows the production function cannot use.
%
% Defaults to the Ramsey core production inputs, which is what both
% calibrate_growth_model.m and ramsey_trend.m require.

    if nargin < 2 || isempty(value_vars)
        value_vars = {'y_level', 'n_level', 'h_level'};
    end

    if ~istable(tbl)
        error('trim_trailing_missing:notATable', 'Expected a table.');
    end

    present = value_vars(ismember(value_vars, tbl.Properties.VariableNames));
    if isempty(present)
        error('trim_trailing_missing:noSuchColumns', ...
              'None of the requested value columns exist: %s', strjoin(value_vars, ', '));
    end

    has_data = false(height(tbl), 1);
    for i = 1:numel(present)
        col = tbl.(present{i});
        if ~isnumeric(col)
            continue;
        end
        has_data = has_data | ~isnan(col);
    end

    last_good = find(has_data, 1, 'last');
    if isempty(last_good)
        error('trim_trailing_missing:noData', ...
              ['No row carries data in any of: %s. The input file is empty or ' ...
               'its columns are misnamed.'], strjoin(present, ', '));
    end

    n_trimmed = height(tbl) - last_good;
    if n_trimmed > 0
        tbl = tbl(1:last_good, :);
    end
end
