function problems = check_demographic_pair_coherence(pop_proj_file, proj_column, demo_file, demo_column, handoff_label)
% CHECK_DEMOGRAPHIC_PAIR_COHERENCE  One demographic story on both sides, for
% workers and for consumers.
%
% build_population_projection.m writes the SimPaths-facing projection
% (population_projection_*.csv, absolute levels in the columns N and P) AND the
% post-handoff tails of demographic_L_*.csv (labor-force units, column L_demo)
% and demographic_P_*.csv (column P_demo) from one computation. Java consumes
% the former; ramsey_trend.m consumes the latter, chained on relative growth at
% the handoff. Nothing else ties them together: regenerate one without the
% other, or hand-edit a demographic file, and MATLAB detrends on one
% demographic future while Java projects on another -- silently. The files live
% in different units, so the invariant is GROWTH, not level: over every
% post-handoff quarter the projection column proj_column and the demographic
% column demo_column must imply the same log growth. The caller invokes this
% once per pair: (N, L_demo) and (P, P_demo).
%
% Returns a cell row of problem strings; empty means coherent.

    problems = {};
    tol = 1e-6;   % levels written at %.6f on O(1e4) leave ~1e-10; 1e-6 is generous

    if ~exist(demo_file, 'file')
        problems{end+1} = sprintf(['missing %s: ramsey_trend would extrapolate its own ' ...
            'demographic tail while SimPaths uses the exported projection.'], demo_file);
        return;
    end

    pop = readtable(pop_proj_file);
    demo = readtable(demo_file);

    if ~ismember(proj_column, pop.Properties.VariableNames)
        problems{end+1} = sprintf(['%s lacks column %s; re-run build_population_projection ' ...
            '(then ramsey_trend).'], pop_proj_file, proj_column);
        return;
    end
    if ~ismember(demo_column, demo.Properties.VariableNames)
        problems{end+1} = sprintf(['%s lacks column %s; re-run build_population_projection ' ...
            '(then ramsey_trend).'], demo_file, demo_column);
        return;
    end

    pop_times = lower(string(pop.time));
    demo_times = lower(string(demo.time));
    handoff = lower(string(handoff_label));

    pop_h = find(pop_times == handoff, 1);
    demo_h = find(demo_times == handoff, 1);
    if isempty(pop_h) || isempty(demo_h)
        problems{end+1} = sprintf('handoff %s missing from %s or %s.', ...
            handoff, pop_proj_file, demo_file);
        return;
    end

    n_overlap = min(height(pop) - pop_h, height(demo) - demo_h);
    if n_overlap < 1
        problems{end+1} = sprintf(['%s has no rows beyond the handoff %s; the MATLAB ' ...
            'solve and the SimPaths projection diverge from the first forward quarter. ' ...
            'Re-run build_population_projection (then ramsey_trend).'], demo_file, handoff);
        return;
    end

    proj_vals = pop.(proj_column);
    demo_vals = demo.(demo_column);
    for k = 1:n_overlap
        if pop_times(pop_h + k) ~= demo_times(demo_h + k)
            problems{end+1} = sprintf('demographic pair time axes diverge at offset %d: %s vs %s.', ...
                k, pop_times(pop_h + k), demo_times(demo_h + k)); %#ok<AGROW>
            return;
        end
        g_pop = log(proj_vals(pop_h + k) / proj_vals(pop_h + k - 1));
        g_demo = log(demo_vals(demo_h + k) / demo_vals(demo_h + k - 1));
        if abs(g_pop - g_demo) > tol
            problems{end+1} = sprintf(['demographic growth diverges at %s: population ' ...
                'projection column %s implies %.8f, %s column %s implies %.8f. The pair is ' ...
                'not one vintage; re-run build_population_projection (then ramsey_trend) ' ...
                'rather than editing either file.'], pop_times(pop_h + k), proj_column, ...
                g_pop, demo_file, demo_column, g_demo); %#ok<AGROW>
            return;
        end
    end
end
