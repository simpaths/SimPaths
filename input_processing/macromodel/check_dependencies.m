function report = check_dependencies(options)
% CHECK_DEPENDENCIES  Report what the macro model needs; with 'strict' enforce it.
%
%   check_dependencies                   print a report, return it
%   check_dependencies('strict', true)   error check_dependencies:Missing on a hard failure
%
% Hard requirements (README, "Dependencies"): MATLAB R2020a or newer (readtable
% 'VariableNamingRule', exportgraphics; the arguments blocks need R2019b; tested
% on R2025b),
% Statistics and Machine Learning Toolbox (the only
% toolbox matlab.codetools.requiredFilesAndProducts reports for the entry
% points; re-run it if the code changes), Dynare on the path (6.5 produced the
% committed bundles), a JVM (sha256_file hashes through java.security).
% Soft: a C compiler for Dynare's use_dll. The Windows Dynare installer ships
% MinGW under <dynare>/mingw64, so a missing mex compiler is only a note there.

    arguments
        options.strict (1,1) logical = false
        options.require_dynare (1,1) logical = true
    end

    report = struct('ok', true, 'problems', {{}}, 'notes', {{}}, ...
                    'matlab', '', 'dynare_path', '', 'dynare_version', '');

    % version('-release') exists on every release; ver('MATLAB').Release carries
    % parentheses, and matlabRelease (what checkcode suggests) needs R2020b.
    report.matlab = ['R' version('-release')];
    % The arguments block above needs R2019b: an older MATLAB cannot parse this file
    % and fails before this line. R2019b parses it and reaches this check.
    if verLessThan('matlab', '9.8')
        report = fail(report, sprintf(['MATLAB %s is older than R2020a; readtable ''VariableNamingRule'' ' ...
                                       'and exportgraphics need 9.8+'], report.matlab));
    end

    if isempty(ver('stats')) || ~license('test', 'Statistics_Toolbox')
        report = fail(report, 'Statistics and Machine Learning Toolbox is not installed or not licensed');
    end

    dyn = which('dynare');
    if ~options.require_dynare
        report.dynare_version = 'not required for a Ramsey handoff rebuild';
    elseif isempty(dyn)
        report = fail(report, 'Dynare is not on the MATLAB path: addpath(''<dynare>/matlab'')');
    else
        report.dynare_path = dyn;
        try
            report.dynare_version = char(dynare_version());
        catch
            report.dynare_version = 'unknown';
        end
        if ~strcmp(report.dynare_version, '6.5')
            report.notes{end+1} = sprintf(['Dynare %s found; the committed bundles were produced with 6.5. ' ...
                                           'Check the resulting fit and bundle when using another version.'], report.dynare_version);
        end
        mingw = fullfile(fileparts(fileparts(dyn)), 'mingw64');
        cc = mex.getCompilerConfigurations('C', 'Selected');
        if isempty(cc) && ~exist(mingw, 'dir')
            report.notes{end+1} = ['no C compiler selected (mex -setup C) and no <dynare>/mingw64: ' ...
                                   'estimation with use_dll will fail; run dynare without use_dll'];
        end
    end

    if ~usejava('jvm')
        report = fail(report, 'MATLAB runs without a JVM (-nojvm); sha256_file needs java.security');
    end

    fprintf('\nMacro model dependencies\n');
    fprintf('  MATLAB   : %s\n', report.matlab);
    fprintf('  Dynare   : %s (%s)\n', report.dynare_version, report.dynare_path);
    for i = 1:numel(report.problems), fprintf('  MISSING  : %s\n', report.problems{i}); end
    for i = 1:numel(report.notes),    fprintf('  note     : %s\n', report.notes{i}); end
    fprintf('  verdict  : %s\n\n', ternary(report.ok, 'ok', 'NOT OK'));

    if options.strict && ~report.ok
        error('check_dependencies:Missing', '%s', strjoin(report.problems, newline));
    end
end

function report = fail(report, msg)
    report.ok = false;
    report.problems{end+1} = msg;
end

function out = ternary(tf, a, b)
    if tf, out = a; else, out = b; end
end
