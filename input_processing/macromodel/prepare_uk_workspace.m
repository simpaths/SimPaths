function model_root = prepare_uk_workspace(work_root)
% PREPARE_UK_WORKSPACE  Copy the UK build inputs into an isolated work directory.
% Run Stata in the returned folder's stata/ directory and MATLAB/Dynare from
% the returned folder. Generated data and bundles stay in the workspace.

    arguments
        work_root (1,:) char = ''
    end

    source_model = fileparts(mfilename('fullpath'));
    source_repo = fileparts(fileparts(source_model));
    if isempty(work_root)
        work_parent = fullfile(source_repo, 'output', 'macro-rebuilds');
        if ~isfolder(work_parent)
            mkdir(work_parent);
        end
        work_root = tempname(work_parent);
    end
    if isfolder(work_root) || isfile(work_root)
        error('prepare_uk_workspace:WorkspaceExists', 'Choose a new work directory: %s', work_root);
    end
    mkdir(work_root);
    [~, info] = fileattrib(work_root);
    work_root = info.Name;

    model_root = fullfile(work_root, 'input_processing', 'macromodel');
    mkdir(model_root);
    copyfile(fullfile(source_model, '*.m'), model_root);
    copyfile(fullfile(source_model, '*.mod'), model_root);
    for name = {'country_params', 'data', 'diagnostics', 'internal', 'tools'}
        copyfile(fullfile(source_model, name{1}), fullfile(model_root, name{1}));
    end

    work_stata = fullfile(model_root, 'stata');
    mkdir(work_stata);
    copyfile(fullfile(source_model, 'stata', '*.ado'), work_stata);
    copyfile(fullfile(source_model, 'stata', '*.do'), work_stata);
    copyfile(fullfile(source_model, 'stata', 'hicp_q_sa_uk.dta'), work_stata);

    workbook_folder = fullfile('input', 'alignment_files');
    mkdir(fullfile(work_root, workbook_folder));
    copyfile(fullfile(source_repo, workbook_folder, 'align_popProjections.xlsx'), ...
             fullfile(work_root, workbook_folder));
    bundle_folder = fullfile('input', 'UK', 'MacroModel');
    mkdir(fullfile(work_root, 'input', 'UK'));
    copyfile(fullfile(source_repo, bundle_folder), fullfile(work_root, bundle_folder));
end
