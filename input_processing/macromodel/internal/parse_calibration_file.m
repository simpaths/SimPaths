function calibration = parse_calibration_file(calibration_file)
% PARSE_CALIBRATION_FILE  Read parameter assignments from a Dynare calibration .mod.
%
% Returns a struct mapping parameter name -> numeric value. Assignments may be
% plain literals (alpha_k = 0.33;) or additive expressions over parameters
% assigned earlier in the same file (chi_u = chi_L - chi_n;). Dynare evaluates
% the latter natively; this parser mirrors that so the documentation layer
% reports the same values the model actually uses.

    if ~exist(calibration_file, 'file')
        error('Calibration file not found: %s', calibration_file);
    end

    lines = splitlines(fileread(calibration_file));
    calibration = struct();
    literal_pattern = '^\s*([A-Za-z][A-Za-z0-9_]*)\s*=\s*([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)\s*;';
    expr_pattern = '^\s*([A-Za-z][A-Za-z0-9_]*)\s*=\s*([^;]+);';

    for idx = 1:numel(lines)
        tokens = regexp(lines{idx}, literal_pattern, 'tokens', 'once');
        if ~isempty(tokens)
            calibration.(tokens{1}) = str2double(tokens{2});
            continue;
        end

        tokens = regexp(lines{idx}, expr_pattern, 'tokens', 'once');
        if isempty(tokens)
            continue;
        end

        value = resolve_additive_expression(tokens{2}, calibration);
        if ~isnan(value)
            calibration.(tokens{1}) = value;
        end
    end
end

function value = resolve_additive_expression(expr, calibration)
% Evaluate a restricted additive expression over already-parsed parameters.
% Only + and - over literals and known parameter names are supported; anything
% else returns NaN so the caller skips the line rather than inventing a value.

    value = NaN;
    expr = strtrim(regexprep(expr, '//.*$', ''));
    if isempty(expr) || ~isempty(regexp(expr, '[*/^()]', 'once'))
        return;
    end

    terms = regexp(expr, '([+-]?)\s*([A-Za-z][A-Za-z0-9_]*|\d*\.?\d+(?:[eE][-+]?\d+)?)', 'tokens');
    if isempty(terms)
        return;
    end

    total = 0;
    for idx = 1:numel(terms)
        operand = terms{idx}{2};
        numeric = str2double(operand);
        if isnan(numeric)
            if ~isfield(calibration, operand)
                return;  % forward reference or unknown symbol - skip the line
            end
            numeric = calibration.(operand);
        end

        if strcmp(terms{idx}{1}, '-')
            total = total - numeric;
        else
            total = total + numeric;
        end
    end

    value = total;
end
