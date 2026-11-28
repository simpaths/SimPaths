function loc = model_location()
% MODEL_LOCATION  Repository paths shared by the UK macro pipeline and its exporters.
% Paths are relative to the model root and also apply inside a rebuild workspace.
    loc.repo_rel = 'input_processing/macromodel';
    loc.input_tree = fullfile('..', '..', 'input');
    loc.population_workbook = fullfile(loc.input_tree, 'alignment_files', ...
                                       'align_popProjections.xlsx');
end
