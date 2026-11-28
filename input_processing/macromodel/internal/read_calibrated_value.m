function value_text = read_calibrated_value(model_text, pname)
% READ_CALIBRATED_VALUE  The literal assigned to a calibrated parameter, as written.
%
%   txt = read_calibrated_value(fileread('country_params/Spain_calibration.mod'), 'rho_h')
%
% Returns the text of the value in `pname = <value>;` (so 0.80 stays '0.80'),
% and fails unless there is exactly one such assignment. A mention without a
% terminating semicolon - "rho_h=0.85 capture the observed persistence" in a
% comment - is not an assignment and is ignored.
%
% Used by generate_frozen_model to carry each country's own rho_L / rho_h into
% the estimation-mode branch of the frozen model.

    pattern = sprintf('(?<![a-zA-Z_])%s\\s*=\\s*([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s*;', pname);
    tokens = regexp(model_text, pattern, 'tokens');

    if numel(tokens) ~= 1
        error('generate_frozen_model:calibratedValueNotFound', ...
              ['Expected exactly one calibration line "%s = <value>;", found %d. ' ...
               'The frozen model records this value as the one the model was ' ...
               'estimated with, so it cannot be guessed.'], pname, numel(tokens));
    end

    value_text = tokens{1}{1};
end
