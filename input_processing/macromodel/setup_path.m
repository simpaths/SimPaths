function added = setup_path(options)
% SETUP_PATH  Put the macro model's code folders on the MATLAB path.
%
%   setup_path            adds the folders if they are not already there
%   setup_path('force',true)   re-adds them even if present
%   added = setup_path(...)    returns the folders actually added
%
% Run once per MATLAB session before using the model, or let any entry point
% call it for you -- run_full_workflow, calibrate_growth_model, ramsey_trend,
% build_population_projection, generate_frozen_model, postestimation_analysis
% and the exporters all call it defensively at their top, so the usual workflow
% needs no manual setup.
%
% WHY THIS IS NEEDED
%   MATLAB's addpath is NOT recursive, and the model's code lives in
%   subfolders (internal/, diagnostics/, documentation_tables/, tests/). Only the current folder is
%   searched automatically, so without this call a function in one of those
%   subfolders is simply "undefined".
%
%   Folders are resolved from this file's own location, not from pwd, so the
%   call works from anywhere. The DATA paths inside the model are a different
%   matter: they are pwd-relative by design (fullfile('country_params', ...),
%   'data_for_dynare_%s.csv', ...), and Dynare writes its own output into pwd
%   unconditionally. The working directory therefore still has to be this
%   folder when running the pipeline. setup_path only fixes code resolution.
%
% NOTE ON genpath
%   genpath deliberately skips +package, @class and private folders. That is
%   what we want: Dynare's generated +Macromodel_* packages must be reached
%   through their parent, not added directly.

    arguments
        options.force (1,1) logical = false
        options.verbose (1,1) logical = false
    end

    root = fileparts(mfilename('fullpath'));

    % Code folders only. Data folders (data, country_params) are addressed by
    % path from the code and must NOT be on the MATLAB path -- a .m file
    % appearing in one of them would then shadow or be shadowed unpredictably.
    code_dirs = {'internal', 'diagnostics', 'documentation_tables', 'tests'};

    added = {};
    current = path;
    for i = 1:numel(code_dirs)
        d = fullfile(root, code_dirs{i});
        if ~exist(d, 'dir')
            continue;
        end
        if options.force || ~contains(current, [d pathsep]) && ~endsWith(current, d)
            addpath(d);
            added{end+1} = d; %#ok<AGROW>
        end
    end

    % The root itself, so entry points resolve when pwd is elsewhere.
    if options.force || (~contains(current, [root pathsep]) && ~endsWith(current, root))
        addpath(root);
        added{end+1} = root; %#ok<AGROW>
    end

    if options.verbose
        if isempty(added)
            fprintf('setup_path: already configured.\n');
        else
            fprintf('setup_path: added %d folder(s):\n', numel(added));
            fprintf('  %s\n', added{:});
        end
    end
end
