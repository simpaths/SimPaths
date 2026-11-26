function x_trend = hp_filter_trend(x, lambda)
% HP_FILTER_TREND  Hodrick-Prescott filter, trend component only.
%
%   x_trend = HP_FILTER_TREND(x, lambda) solves
%       min_tau  sum_t (x_t - tau_t)^2 + lambda * sum_t (Delta^2 tau_t)^2
%   and returns tau.
%
% Self-contained (no Econometrics Toolbox). NaN entries are linearly
% interpolated (with linear extrapolation at the ends) before filtering and
% restored as NaN in the output, so a censored sample does not propagate the
% gap through the smoother.
%
% CONTRACT: this is the single HP implementation in the repository. It is used
% both by calibrate_growth_model.m (NAWRU, demographic L trend) and by
% ramsey_trend.m (demographic, NAWRU, N, expenditure shares, r*). Those two
% must smooth the same series the same way: the calibration fixes A0/K0 and
% the terminal closure, while the detrending produces the series the DSGE is
% estimated on. Two copies that drifted apart would mis-anchor the trend
% against the calibration with nothing failing. Do not re-inline a local copy.

    x = x(:);
    T = numel(x);

    nan_mask = isnan(x);
    if any(nan_mask)
        valid_idx = find(~nan_mask);
        if numel(valid_idx) < 2
            error('hp_filter_trend:InsufficientData', ...
                  'Series has %d non-NaN observations; at least 2 are required.', ...
                  numel(valid_idx));
        end
        x = interp1(valid_idx, x(valid_idx), (1:T)', 'linear', 'extrap');
    end

    % Second-difference matrix D (T-2 x T)
    e = ones(T, 1);
    D = spdiags([e -2*e e], 0:2, T-2, T);

    % tau = (I + lambda * D'D)^{-1} * x
    x_trend = (speye(T) + lambda * (D' * D)) \ x;

    if any(nan_mask)
        x_trend(nan_mask) = NaN;
    end
end
