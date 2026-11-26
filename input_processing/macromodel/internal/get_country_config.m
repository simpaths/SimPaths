function config = get_country_config(country)
% GET_COUNTRY_CONFIG  Select the UK macro inputs and SimPaths bundle path.
    if ~strcmpi(country, 'UK')
        error('get_country_config:UnsupportedCountry', 'This checkout ships the UK macro model. Requested: %s.', country);
    end
    config = struct('name', 'UK', 'simpaths_code', 'UK');
end
