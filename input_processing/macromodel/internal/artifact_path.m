function p = artifact_path(kind, name, options)
% ARTIFACT_PATH  Path of a pipeline artefact, by role.
%
%   artifact_path('data',   'data_for_dynare_Poland.csv')  -> 'data/data_for_dynare_Poland.csv'
%   artifact_path('output', 'ramsey_trend_poland.mat')     -> 'output/ramsey_trend_poland.mat'
%   artifact_path('logs',   'ramsey_trend_poland.log')     -> 'logs/ramsey_trend_poland.log'
%   artifact_path('figures','ramsey_diagnostic_poland.png')-> 'figures/ramsey_diagnostic_poland.png'
%   artifact_path(..., 'create', true)   also creates the folder
%
% Roles:
%   data      inputs the pipeline consumes (Stata exports, NAWRU, demographics)
%   output    series and state the model produces (trends, reference CSV, .mat)
%   logs      diaries and text reports
%   figures   figures written by our own plotting code
%   bundle    the SimPaths-facing export, <input tree>/{CC}/MacroModel (needs 'country'; see model_location)
%
% NAME: deliberately not 'model_file'. run_full_workflow.m and
% run_detrending_benchmark.m hold a .mod filename in local variables of that
% name. MATLAB classifies an identifier as a VARIABLE for the whole function
% scope if it is assigned anywhere in the body, so every call in those two files
% would parse as indexing an unassigned variable -- regardless of the function
% existing on the path. checkcode does not flag it, and the unit tests would
% pass because none of them calls an entry point. tests/test_name_collisions.m
% guards the class.
%
% Returns a path RELATIVE to the working directory, deliberately. The working
% directory has to be the model root anyway -- Dynare writes its own output
% into pwd unconditionally and resolves its datafile from there -- so anchoring
% these paths anywhere else would split the artefacts of one run across two
% places. That requirement is checked here rather than surfacing later as a
% file-not-found from whichever reader happened to run first.

    arguments
        kind (1,:) char
        name (1,:) char = ''
        options.create (1,1) logical = false
        options.country (1,:) char = ''
    end

    folders = struct( ...
        'data',      'data', ...
        'output',    'output', ...
        'logs',      'logs', ...
        'figures',   'figures');

    assert_in_model_root();

    if strcmp(kind, 'bundle')
        % The export bundle lives where the Java runtime reads it. The exporter
        % writes there directly, so no copy step can leave producer and consumer
        % apart (contract #18). The folder is created only on request. The model
        % root's place in the tree comes from model_location, so a move changes one file.
        if isempty(options.country)
            error('artifact_path:CountryRequired', ...
                  'artifact_path(''bundle'', ...) needs ''country'', <Name>.');
        end
        cfg = get_country_config(options.country);
        folder = fullfile(model_location().input_tree, cfg.simpaths_code, 'MacroModel');
    elseif isfield(folders, kind)
        folder = folders.(kind);
    else
        error('artifact_path:UnknownKind', ...
              'Unknown artefact role "%s". Valid roles: %s, bundle.', ...
              kind, strjoin(fieldnames(folders)', ', '));
    end

    % isfolder: exist(folder, 'dir') also answers for a same-named folder on the MATLAB path (connector2/figures did)
    if options.create && ~isfolder(folder)
        mkdir(folder);
    end

    if isempty(name)
        p = folder;
    else
        p = fullfile(folder, name);
    end
end

function assert_in_model_root()
% The model root is identifiable by its entry points. Checking costs nothing and
% turns "the CSV is missing" into "you are in the wrong folder", which names the
% actual cause.
    persistent checked_dir
    if ~isempty(checked_dir) && strcmp(checked_dir, pwd)
        return;
    end
    if ~exist(fullfile(pwd, 'run_full_workflow.m'), 'file')
        error('artifact_path:WrongFolder', ...
              ['The macro model must be run from its root folder (the one holding ' ...
               'run_full_workflow.m). Current folder is:\n  %s\n' ...
               'Dynare writes its output into the working directory and resolves ' ...
               'its datafile from there, so this is not something the code can ' ...
               'paper over.'], pwd);
    end
    checked_dir = pwd;
end
