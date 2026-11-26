function info = write_growth_terminal_state(growth_mat, output_dir)
% WRITE_GROWTH_TERMINAL_STATE  Write growth_terminal_state.json for SimPaths.
%
%   info = WRITE_GROWTH_TERMINAL_STATE(growth_mat, output_dir) reads the Ramsey
%   workspace at `growth_mat` and writes the handoff contract that the Java
%   runtime initialises from. Returns a struct describing what was written.
%
% CONTRACT: this is the only writer of growth_terminal_state.json. Both
% export_dynamic_for_simpaths.m (full workflow) and export_ramsey_for_simpaths.m
% (Ramsey-only refresh) call it.
%
% One writer keeps both export paths emitting `projection_quarters`. Java falls
% back to a 320-quarter horizon when that key is missing — with a log warning,
% but the run continues on a different forward horizon than the one the trend
% was solved over. Contract #9 in docs/Integration_Contracts.md keeps the horizon
% from being a constant on each side, including for the "Refresh Ramsey Files
% Only" recipe in input_processing/macromodel/README.md.

    arguments
        growth_mat (1,:) char
        output_dir (1,:) char
    end

    info = struct('written', false, 'file', '');

    if ~exist(growth_mat, 'file')
        return;
    end

    growth_data = load(growth_mat, 'K_terminal', 'A_terminal', 'params', ...
                       'simpaths_handoff_time', 'projection_quarters');

    if isfield(growth_data, 'simpaths_handoff_time')
        handoff_label = growth_data.simpaths_handoff_time;
    else
        handoff_label = 'sample end';
    end

    lines = {};
    lines{end+1} = '{';
    % %.16e, not %.10f: A is O(0.03), so ten decimals keep only ~9 significant
    % digits — worst-case ~1.4e-9 RELATIVE, above the 1e-9 Java parity tolerance.
    % The 4.4e-11 the anchor test measured at ten decimals was luck. Full precision
    % takes this file out of the parity budget.
    lines{end+1} = sprintf('  "K_terminal": %.16e,', growth_data.K_terminal);
    lines{end+1} = sprintf('  "A_terminal": %.16e,', growth_data.A_terminal);
    lines{end+1} = sprintf('  "handoff_time": "%s",', handoff_label);

    % Forward horizon of the handoff contract. Java rebuilds the projection over
    % the same number of quarters; a mismatch silently changes the terminal
    % closure, so the value is exported and validated there rather than
    % duplicated as a constant on each side.
    if isfield(growth_data, 'projection_quarters') && ~isempty(growth_data.projection_quarters)
        lines{end+1} = sprintf('  "projection_quarters": %d,', ...
                               round(growth_data.projection_quarters));
    else
        warning('write_growth_terminal_state:MissingHorizon', ...
                ['%s carries no projection_quarters, so the bundle cannot pin the ' ...
                 'forward horizon and Java will assume its default of 320 quarters. ' ...
                 'Re-run ramsey_trend to regenerate the workspace.'], growth_mat);
    end

    lines{end+1} = ['  "comment": "Capital stock and TFP level at the SimPaths handoff ' ...
                    'quarter. Use as initial values for SimPaths forward projection."'];
    lines{end+1} = '}';

    terminal_file = fullfile(output_dir, 'growth_terminal_state.json');
    write_text_atomic(terminal_file, strjoin(lines, newline), 'verify_json', true);

    fprintf('    ✓ growth_terminal_state.json (%s; K=%.1f, A=%.6f)\n', ...
            handoff_label, growth_data.K_terminal, growth_data.A_terminal);

    info.written = true;
    info.file = terminal_file;
    info.handoff_time = handoff_label;
end
