function paths = get_growth_params_paths(country)
% GET_GROWTH_PARAMS_PATHS  Resolve source and export JSON paths for Ramsey parameters.

    country_config = get_country_config(country);
    country = country_config.name;

    paths = struct();
    paths.country = country;
    paths.source = fullfile('country_params', sprintf('%s_growth_params.json', country));
    paths.export_dir = artifact_path('bundle', '', 'country', country);
    paths.export = fullfile(paths.export_dir, sprintf('growth_params_%s.json', country));
end
