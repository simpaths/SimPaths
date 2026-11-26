function marker_file = mark_bundle_unverified(output_dir, country, problems)
% MARK_BUNDLE_UNVERIFIED  Record inside an export bundle that it must not ship.
%
%   mark_bundle_unverified(dir, 'Sweden', {'missing: ...'})   write the marker
%   mark_bundle_unverified(dir, 'Sweden', {})                 remove it
%
% WHY THIS EXISTS
%   verify_export_bundle refuses a bundle by raising an error, which is seen by
%   whoever ran that export and by nobody afterwards. The files stay in
%   input/{CC}/MacroModel/, the folder SimPaths reads, and without a marker a
%   refused bundle can be committed looking exactly like a shippable one.
%
%   The marker makes the refusal a property of the bundle. Both exporters write
%   it before their first file, so an export that dies partway is marked too,
%   and verify_export_bundle removes it only when the bundle passes.
%   calibrate_growth_model and ramsey_trend write it before they rewrite the
%   bundle's growth_params_{C}.json. MacroModelManager refuses a marked bundle.
%
% The content is deterministic (no timestamp), and the marker is never tracked
% (test_artifact_layout refuses it), so a re-run that writes the same bytes is
% still invisible to git.

    arguments
        output_dir (1,:) char
        country (1,:) char
        problems cell
    end

    marker_file = fullfile(output_dir, 'BUNDLE_NOT_VERIFIED.txt');

    if isempty(problems)
        if exist(marker_file, 'file')
            delete(marker_file);
        end
        return;
    end

    lines = [{ ...
        'THIS BUNDLE IS NOT VERIFIED - DO NOT COMMIT IT, SimPaths MUST NOT RUN ON IT', ...
        '===========================================================================', ...
        sprintf('Country: %s', country), ...
        '', ...
        'Reason:'}, ...
        cellfun(@(p) ['  ' strrep(p, newline, [newline '  '])], problems(:)', ...
                'UniformOutput', false), ...
        {'', ...
        'Written by the export whenever verification fails or is never reached,', ...
        'and removed by the next export that verifies. While this file is here', ...
        'the bundle is incomplete or mixes vintages.', ...
        'See input_processing/macromodel/internal/verify_export_bundle.m.'}];

    write_text_atomic(marker_file, strjoin(lines, newline));
end
