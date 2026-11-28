function verify_export_bundle(output_dir, country, options)
% VERIFY_EXPORT_BUNDLE  The exported bundle must be complete and one vintage.
%
%   verify_export_bundle(dir, 'Poland')                  Ramsey files only
%   verify_export_bundle(dir, 'Poland', 'dsge', true)    also the DSGE files
%
% WHY THIS EXISTS
%   An exporter copies some artefacts into the bundle only when their source
%   exists (the population projection, for one), so a missing source leaves an
%   incomplete bundle. An exporter that dies partway through leaves a bundle
%   whose halves come from different runs: DSGE matrices from one estimation
%   next to Ramsey files from an earlier one. Each file individually looks fine,
%   so nothing else announces either inconsistency.
%
%   Two things are checked, and both are failures rather than warnings:
%     completeness  every file the bundle is supposed to contain is there
%     freshness     each copied Ramsey artefact is byte-identical to the
%                   output/ file it was copied from, so the bundle cannot hold
%                   an older trend than the one the model was just solved for
%
% This runs at the END of an export. Failing here marks the bundle so that
% nothing downstream -- a commit, a SimPaths run -- proceeds on a mixed one.
%
% The verdict is also written into the bundle: BUNDLE_NOT_VERIFIED.txt is
% present whenever the last verification failed and absent when it passed
% (see mark_bundle_unverified). Never commit a bundle that contains it.

    arguments
        output_dir (1,:) char
        country (1,:) char
        options.dsge (1,1) logical = false
    end

    problems = {};

    ramsey_files = { ...
        sprintf('growth_params_%s.json', country), ''; ...
        'growth_terminal_state.json', ''; ...
        sprintf('growth_model_reference_%s.csv', lower(country)), ...
            artifact_path('output', sprintf('growth_model_reference_%s.csv', lower(country))); ...
        sprintf('population_projection_%s.csv', country), ...
            artifact_path('output', sprintf('population_projection_%s.csv', country)); ...
        sprintf('hours_projection_%s.csv', country), ''; ...
        'BUNDLE_PROVENANCE.txt', ''};

    dsge_files = {'policy_A.csv'; 'policy_Bs.csv'; 'policy_C.csv'; 'policy_D.csv'; ...
                  'policy_full_ghx.csv'; 'policy_full_ghu.csv'; 'steady_state.csv'; ...
                  'shock_params.csv'; 'model_info.json'; 'SIMPATHS_INTERFACE.md'};

    % --- completeness -----------------------------------------------------
    for i = 1:size(ramsey_files, 1)
        target = fullfile(output_dir, ramsey_files{i, 1});
        if ~exist(target, 'file')
            problems{end+1} = sprintf('missing: %s', ramsey_files{i, 1}); %#ok<AGROW>
        end
    end
    if options.dsge
        for i = 1:numel(dsge_files)
            target = fullfile(output_dir, dsge_files{i});
            if ~exist(target, 'file')
                problems{end+1} = sprintf('missing: %s', dsge_files{i}); %#ok<AGROW>
            end
        end
    end

    % --- freshness --------------------------------------------------------
    for i = 1:size(ramsey_files, 1)
        source = ramsey_files{i, 2};
        if isempty(source) || ~exist(source, 'file')
            continue;
        end
        target = fullfile(output_dir, ramsey_files{i, 1});
        if ~exist(target, 'file')
            continue;   % already reported as missing
        end
        if ~files_identical(source, target)
            problems{end+1} = sprintf( ...
                ['stale: %s in the bundle differs from %s.\n' ...
                 '           The bundle holds an older Ramsey run than the one just solved.'], ...
                ramsey_files{i, 1}, source); %#ok<AGROW>
        end
    end

    % --- demographic pair coherence ----------------------------------------
    % population_projection_*.csv (Java) and the tails of demographic_L_*.csv and
    % demographic_P_*.csv (this side's solve) must tell one demographic story on
    % both sides, for workers (N vs L_demo) and for consumers (P vs P_demo); see
    % check_demographic_pair_coherence for why nothing else enforces it. A
    % projection without a P column, or a missing demographic_P file, is a problem.
    pop_in_bundle = fullfile(output_dir, sprintf('population_projection_%s.csv', country));
    terminal_json = fullfile(output_dir, 'growth_terminal_state.json');
    if exist(pop_in_bundle, 'file') && exist(terminal_json, 'file')
        tok = regexp(fileread(terminal_json), '"handoff_time"\s*:\s*"([^"]+)"', 'tokens', 'once');
        if ~isempty(tok)
            demo_L_file = artifact_path('data', sprintf('demographic_L_%s.csv', lower(country)));
            demo_P_file = artifact_path('data', sprintf('demographic_P_%s.csv', lower(country)));
            pair_problems = [ ...
                check_demographic_pair_coherence(pop_in_bundle, 'N', demo_L_file, 'L_demo', tok{1}), ...
                check_demographic_pair_coherence(pop_in_bundle, 'P', demo_P_file, 'P_demo', tok{1})];
            problems = [problems, pair_problems];
        end
    end

    % The verdict goes into the bundle itself, not only into this run's output:
    % a refused bundle stays where SimPaths reads it and must not look shippable.
    mark_bundle_unverified(output_dir, country, problems);

    if ~isempty(problems)
        error('verify_export_bundle:Inconsistent', ...
              ['The export bundle in %s is not a single consistent vintage:\n' ...
               '  %s\n' ...
               'The bundle is REFUSED: do not commit it, and SimPaths must not run on it.\n' ...
               'Re-run the export. Without re-estimating, from input_processing/macromodel/:\n' ...
               '  run_full_workflow(''%s'', ''skip_estimation'', true, ''skip_frozen'', true, ' ...
               '''skip_postestimation'', true)\n' ...
               'rebuilds both halves from the tracked frozen model (MATLAB + Dynare, about\n' ...
               'five minutes); export_ramsey_for_simpaths(''%s'') refreshes only the Ramsey half.'], ...
              output_dir, strjoin(problems, sprintf('\n  ')), country, country);
    end

    fprintf('    ✓ bundle verified: complete and one vintage\n');
end

function tf = files_identical(a, b)
    da = dir(a); db = dir(b);
    if isempty(da) || isempty(db) || da(1).bytes ~= db(1).bytes
        tf = false;
        return;
    end
    fa = fopen(a, 'r'); ba = fread(fa, Inf, '*uint8'); fclose(fa);
    fb = fopen(b, 'r'); bb = fread(fb, Inf, '*uint8'); fclose(fb);
    tf = isequal(ba, bb);
end
