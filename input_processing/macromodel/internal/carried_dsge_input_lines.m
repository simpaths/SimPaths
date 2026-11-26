function lines = carried_dsge_input_lines(output_dir, country)
% CARRIED_DSGE_INPUT_LINES  Provenance lines of the DSGE half a Ramsey-only export leaves in place.
%
% The frozen model, Macromodel_template.mod and {C}_calibration.mod are inputs
% of the policy matrices, not of the Ramsey files; export_ramsey_for_simpaths
% does not rebuild them, so their lines are copied from the previous
% BUNDLE_PROVENANCE.txt (in file order) when the bundle still holds policy_A.csv.
% Nothing is carried otherwise, and the Java test then reports the missing
% inputs with the re-export recipe.
    lines = {};
    prov_file = fullfile(output_dir, 'BUNDLE_PROVENANCE.txt');
    if ~exist(fullfile(output_dir, 'policy_A.csv'), 'file') || ~exist(prov_file, 'file')
        return;
    end
    old = regexp(fileread(prov_file), '\r?\n', 'split');
    c = regexptranslate('escape', country);
    root = regexptranslate('escape', model_location().repo_rel);
    pattern = ['^sha256 \S+ \S+ ' root '/(Macromodel_' c '_frozen\.mod|' ...
               'Macromodel_template\.mod|country_params/' c '_calibration\.mod)$'];
    lines = old(~cellfun(@isempty, regexp(old, pattern, 'once')));
end
