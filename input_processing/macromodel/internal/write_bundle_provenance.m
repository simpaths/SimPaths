function prov_file = write_bundle_provenance(output_dir, country, exporter, inputs, options)
% WRITE_BUNDLE_PROVENANCE  Record which tracked inputs a bundle was built from.
%
%   write_bundle_provenance(dir, 'Poland', 'export_dynamic_for_simpaths', ...
%       {'Macromodel_Poland_frozen.mod', 't'; ...
%        'data/data_for_dynare_Poland.csv', 't'; ...
%        'country_params/Poland_growth_params.json', 't'; ...
%        '../../input/PL/align_popProjections.xlsx', 'b'})
%   write_bundle_provenance(..., 'extra_lines', {line, ...})
%
% After the inputs, every regular file in OUTPUT_DIR is hashed too (text mode,
% sorted by name), so the Java test also fails on a bundle file edited or
% rewritten after the export, not only on a changed input. Excluded:
% BUNDLE_PROVENANCE.txt itself, BUNDLE_NOT_VERIFIED.txt, names starting with
% _macroScenario_ (scenario files Java reads and no exporter writes), names
% starting with '.' (Finder's .DS_Store) and names ending in .tmp (a
% write_text_atomic temp file a killed MATLAB left; git ignores *.tmp). A line
% for a file git never sees would fail CI with "input no longer exists".
%
% 'extra_lines' (cell of char, default {}) are appended verbatim after the
% hashed lines. export_ramsey_for_simpaths passes the previous file's
% DSGE-half input lines this way: a Ramsey-only refresh leaves the DSGE half of
% the bundle in place, and that half was built from the frozen model, the
% template and the country calibration.
%
% INPUTS is an n-by-2 cell of {path, kind}: the path is relative to pwd
% (input_processing/macromodel/, see model_location), the kind is 't' (text, CR bytes ignored, see sha256_file) or
% 'b' (binary). Paths are written relative to the SimPathsEU root, which is
% where BundleProvenanceTest.java resolves them. A missing input is recorded
% as "missing <path>", never hashed, never fatal: whether the bundle may ship
% is verify_export_bundle's decision.
%
% The dynare and matlab lines record the toolchain of this export. They are a
% record, not a requirement: compare_bundles ignores them, and the Java test
% reads only the country and sha256 lines.
%
% Contract #18: the committed bundle must be rebuilt whenever one of these
% inputs changes. The Java test recomputes the hashes in CI, so a PR that
% moves an input without re-exporting goes red without MATLAB on the runner.

    arguments
        output_dir (1,:) char
        country (1,:) char
        exporter (1,:) char
        inputs cell
        options.extra_lines cell = {}
    end
    assert(size(inputs, 2) == 2, 'write_bundle_provenance:Shape', ...
           'inputs must be an n-by-2 cell {path, kind}');
    assert(all(cellfun(@(s) ischar(s) && isrow(s) && ~any(s == newline | s == char(13)), ...
                       options.extra_lines)), 'write_bundle_provenance:ExtraLines', ...
           'extra_lines must be single-line char rows');

    lines = {'BUNDLE_PROVENANCE', ...
             sprintf('country      %s', country), ...
             sprintf('exporter     %s', exporter), ...
             sprintf('exported_at  %s', char(datetime('now', 'Format', 'yyyy-MM-dd HH:mm:ss'))), ...
             sprintf('dynare       %s', dynare_release()), ...
             sprintf('matlab       R%s', version('-release')), ...
             '# sha256 <t|b> <hex> <path relative to the SimPaths root>; t = CR bytes dropped before hashing'};

    for i = 1:size(inputs, 1)
        rel = inputs{i, 1};
        kind = inputs{i, 2};
        assert(ismember(kind, {'t', 'b'}), 'write_bundle_provenance:Kind', ...
               'kind must be ''t'' or ''b'', got ''%s''', kind);
        root_rel = repo_relative(rel);
        if ~exist(rel, 'file')
            lines{end+1} = sprintf('missing %s', root_rel); %#ok<AGROW>
            continue;
        end
        hex = sha256_file(rel, 'text', strcmp(kind, 't'));
        lines{end+1} = sprintf('sha256 %s %s %s', kind, hex, root_rel); %#ok<AGROW>
    end
    lines = [lines, bundle_file_lines(output_dir), reshape(options.extra_lines, 1, [])];

    prov_file = fullfile(output_dir, 'BUNDLE_PROVENANCE.txt');
    write_text_atomic(prov_file, strjoin(lines, newline));   % appends the final newline
end

function v = dynare_release()
% 'unknown' when Dynare is not on the path: a Ramsey-only export does not need it.
    try
        v = char(dynare_version());
    catch
        v = 'unknown';
    end
end

function lines = bundle_file_lines(output_dir)
    L = dir(output_dir);
    L = L(~[L.isdir]);
    names = sort({L.name});
    skip = strcmp(names, 'BUNDLE_PROVENANCE.txt') | strcmp(names, 'BUNDLE_NOT_VERIFIED.txt') | ...
           startsWith(names, '_macroScenario_') | startsWith(names, '.') | endsWith(names, '.tmp');
    names = names(~skip);
    lines = cell(1, numel(names));
    for i = 1:numel(names)
        rel = fullfile(output_dir, names{i});
        lines{i} = sprintf('sha256 t %s %s', sha256_file(rel), repo_relative(rel));
    end
end

function root_rel = repo_relative(rel)
% Paths are recorded relative to the SimPathsEU root. pwd is the model root,
% model_location().repo_rel; each leading '../' drops one of its segments, so
% 'data/x' maps to <repo_rel>/data/x and '../../input/PL/x' to input/PL/x.
% Absolute paths are left alone (tests use temp folders).
    rel = strrep(rel, '\', '/');
    if ~isempty(regexp(rel, '^([A-Za-z]:)?/', 'once'))
        root_rel = rel;
        return;
    end
    base = strsplit(model_location().repo_rel, '/');
    while startsWith(rel, '../')
        if ~isempty(base)
            base(end) = [];
        end
        rel = rel(4:end);
    end
    root_rel = strjoin([base, {rel}], '/');
end
