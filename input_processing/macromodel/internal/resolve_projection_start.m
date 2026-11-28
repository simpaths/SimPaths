function projection_start = resolve_projection_start(projection_start)
% RESOLVE_PROJECTION_START  Normalize projection_start and apply the shared default.

    if nargin < 1 || isempty(projection_start)
        projection_start = '2023q1';
    else
        projection_start = char(lower(string(projection_start)));
    end
end
