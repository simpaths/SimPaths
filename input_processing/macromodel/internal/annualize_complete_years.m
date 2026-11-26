function ann = annualize_complete_years(quarterly, quarter_year, years)
% ANNUALIZE_COMPLETE_YEARS  Mean of each complete calendar year, one column per input series.
%
% A year is kept only when all four of its quarters are present; NaN values
% inside a present quarter are skipped ('omitnan'), which matters for the
% break-even series whose first entry is NaN by construction (its trailing-4Q
% growth term has no history at the handoff).
%
% Every annual panel of ramsey_projection_plot aggregates through this one
% function, so all of them treat incomplete years and NaN values the same way.
    n_years = numel(years);
    ann = nan(n_years, size(quarterly, 2));
    for k = 1:n_years
        mask = quarter_year == years(k);
        if nnz(mask) == 4
            ann(k, :) = mean(quarterly(mask, :), 1, 'omitnan');
        end
    end
end
