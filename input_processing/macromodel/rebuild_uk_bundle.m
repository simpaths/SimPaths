function bundle = rebuild_uk_bundle(start_year, work_root)
% REBUILD_UK_BUNDLE  Re-solve a UK handoff while retaining the fitted DSGE.
%   bundle = rebuild_uk_bundle(2019)
%   bundle = rebuild_uk_bundle(2019, 'path/to/a/new/work/directory')
% Use the returned absolute bundle directory as SimPaths' mm_macroBundleDir.

    arguments
        start_year (1,1) double {mustBeInteger, mustBePositive}
        work_root (1,:) char = ''
    end

    check_dependencies('strict', true, 'require_dynare', false);
    model_root = prepare_uk_workspace(work_root);
    original_dir = pwd;
    original_path = path;
    restore_dir = onCleanup(@() cd(original_dir));
    restore_path = onCleanup(@() path(original_path));
    cd(model_root);
    setup_path();

    % ramsey_trend rewrites the detrended data for the new handoff.
    % Keep the data used to estimate the retained policy matrices separately.
    fitted_data = artifact_path('data', 'data_for_dynare_fitted_UK.csv');
    copyfile(artifact_path('data', 'data_for_dynare_UK.csv'), fitted_data);

    run_full_workflow('UK', 'projection_start', sprintf('%dq1', start_year), ...
        'skip_growth_calibration', true, 'skip_estimation', true, 'skip_frozen', true, ...
        'skip_dynamic_export', true, 'skip_postestimation', true, 'skip_projection_plot', true);
    export_ramsey_for_simpaths('UK', 'dsge_estimation_data', fitted_data);
    bundle = fullfile(fileparts(fileparts(model_root)), 'input', 'UK', 'MacroModel');
    fprintf('SimPaths macro bundle directory: %s\n', bundle);
end
