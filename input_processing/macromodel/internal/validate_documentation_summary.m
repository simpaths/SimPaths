function report = validate_documentation_summary(summary, kind, options)
% VALIDATE_DOCUMENTATION_SUMMARY  Validate Ramsey or DSGE documentation summaries.

    arguments
        summary (1,1) struct
        kind (1,:) char {mustBeMember(kind, {'ramsey', 'dsge'})}
        options.throw_on_error (1,1) logical = true
        options.vd_row_sum_tolerance (1,1) double = 1.0
        options.expected_posterior_names (1,:) cell = { ...
            'sigma_a', 'sigma_d', 'sigma_r', 'sigma_p', 'sigma_h', ...
            'sigma_L', 'sigma_inv', 'omega_h', 'phi_inv_r'}
        options.expected_moment_observables (1,:) cell = {'y', 'inv', 'c', 'pi', 'R', 'n', 'L', 'h'}
    end

    report = struct();
    report.kind = kind;
    report.ok = true;
    report.errors = {};
    report.warnings = {};

    validate_metadata(summary);

    switch kind
        case 'ramsey'
            validate_ramsey(summary);
        case 'dsge'
            validate_dsge(summary);
    end

    if ~isempty(report.errors)
        report.ok = false;
    end

    if options.throw_on_error && ~report.ok
        error('Documentation summary validation failed (%s):\n%s', ...
            kind, strjoin(report.errors, newline));
    end

    function validate_metadata(payload)
        if ~has_nonempty_field(payload, 'metadata') || ~isstruct(payload.metadata)
            add_error('Missing metadata struct.');
            return;
        end

        if ~has_nonempty_field(payload.metadata, 'country')
            add_error('Missing metadata.country.');
        end
    end

    function validate_ramsey(payload)
        require_fields(payload, 'ramsey summary', {'structural', 'base_year_values'});
        if ~isfield(payload, 'structural') || ~isstruct(payload.structural)
            return;
        end
        if ~isfield(payload, 'base_year_values') || ~isstruct(payload.base_year_values)
            return;
        end

        require_numeric_fields(payload.structural, 'structural', { ...
            'base_year', 'alpha_K', 'delta_annual', 'beta_annual', 'sigma', ...
            'tfp_growth_annual', 'beta_quarterly', 'delta_quarterly', ...
            'r_star_annual', 'ky_bgp_quarterly', 'solver_horizon_quarters'});

        require_numeric_fields(payload.base_year_values, 'base_year_values', { ...
            'K0', 'Y0_annual', 'N0', 'L0', 'h0', 'A0', 'P0', 'dependency_ratio0', 'CY', 'IY', 'GY', 'NXY'});

        share_names = {'CY', 'IY', 'GY', 'NXY'};
        if all_fields_exist(payload.base_year_values, share_names)
            share_sum = payload.base_year_values.CY + payload.base_year_values.IY + ...
                payload.base_year_values.GY + payload.base_year_values.NXY;
            if isfinite(share_sum) && (share_sum < 0.80 || share_sum > 1.20)
                add_warning(sprintf('Base-year expenditure shares sum to %.3f, outside the expected range.', share_sum));
            end
        end
    end

    function validate_dsge(payload)
        require_fields(payload, 'dsge summary', {'posterior_parameters'});
        if ~isfield(payload, 'posterior_parameters') || isempty(payload.posterior_parameters)
            add_error('posterior_parameters must be a non-empty struct array.');
            return;
        end

        posterior_rows = payload.posterior_parameters;
        if ~isstruct(posterior_rows)
            add_error('posterior_parameters must be a struct array.');
            return;
        end

        row_names = cell(1, numel(posterior_rows));
        for idx = 1:numel(posterior_rows)
            row = posterior_rows(idx);
            require_fields(row, sprintf('posterior_parameters(%d)', idx), ...
                {'name', 'label_latex', 'prior_mean', 'posterior_mean', 'hpd_low', 'hpd_high'});
            if isfield(row, 'name') && ~isempty(row.name)
                row_names{idx} = row.name;
            end
            check_numeric_scalar(row, 'prior_mean', sprintf('posterior_parameters(%d)', idx));
            check_numeric_scalar(row, 'posterior_mean', sprintf('posterior_parameters(%d)', idx));
            check_numeric_scalar(row, 'hpd_low', sprintf('posterior_parameters(%d)', idx));
            check_numeric_scalar(row, 'hpd_high', sprintf('posterior_parameters(%d)', idx));

            if all_fields_exist(row, {'posterior_mean', 'hpd_low', 'hpd_high'})
                if row.hpd_low > row.posterior_mean || row.posterior_mean > row.hpd_high
                    add_error(sprintf(['posterior_parameters(%d) violates HPD ordering: ' ...
                        'expected hpd_low <= posterior_mean <= hpd_high.'], idx));
                end
            end
        end

        missing_names = setdiff(options.expected_posterior_names, row_names);
        extra_names = setdiff(row_names, options.expected_posterior_names);
        if ~isempty(missing_names)
            add_error(sprintf('Missing expected posterior parameter rows: %s.', strjoin(missing_names, ', ')));
        end
        if ~isempty(extra_names)
            add_warning(sprintf('Unexpected posterior parameter rows present: %s.', strjoin(extra_names, ', ')));
        end

        if isfield(payload, 'variance_decomposition') && ~isempty(payload.variance_decomposition)
            validate_vd(payload.variance_decomposition);
        end

        if isfield(payload, 'moments') && ~isempty(payload.moments)
            validate_moments(payload.moments);
        end
    end

    function validate_vd(vd_payload)
        require_fields(vd_payload, 'variance_decomposition', {'rows'});
        if ~isfield(vd_payload, 'rows') || ~isstruct(vd_payload.rows)
            add_error('variance_decomposition.rows must be a struct array.');
            return;
        end

        for idx = 1:numel(vd_payload.rows)
            row = vd_payload.rows(idx);
            require_fields(row, sprintf('variance_decomposition.rows(%d)', idx), {'observable', 'shares_percent'});
            if isfield(row, 'shares_percent') && isstruct(row.shares_percent)
                share_fields = fieldnames(row.shares_percent);
                share_sum = 0;
                for jdx = 1:numel(share_fields)
                    value = row.shares_percent.(share_fields{jdx});
                    if ~isfinite_numeric_scalar(value)
                        add_error(sprintf('variance_decomposition row %d has non-scalar share for shock %s.', idx, share_fields{jdx}));
                    else
                        share_sum = share_sum + value;
                    end
                end
                if abs(share_sum - 100) > options.vd_row_sum_tolerance
                    add_warning(sprintf(['variance_decomposition row %d sums to %.3f, exceeding the %.2f tolerance.'], ...
                        idx, share_sum, options.vd_row_sum_tolerance));
                end
            end
        end
    end

    function validate_moments(moment_rows)
        if ~isstruct(moment_rows)
            add_error('moments must be a struct array.');
            return;
        end

        found_observables = cell(1, numel(moment_rows));
        for idx = 1:numel(moment_rows)
            row = moment_rows(idx);
            require_fields(row, sprintf('moments(%d)', idx), ...
                {'observable', 'data_std', 'model_std', 'data_ac1', 'model_ac1'});
            if isfield(row, 'observable') && ~isempty(row.observable)
                found_observables{idx} = row.observable;
            end
            check_numeric_scalar(row, 'data_std', sprintf('moments(%d)', idx));
            check_numeric_scalar(row, 'model_std', sprintf('moments(%d)', idx));
            check_numeric_scalar(row, 'data_ac1', sprintf('moments(%d)', idx));
            check_numeric_scalar(row, 'model_ac1', sprintf('moments(%d)', idx));
        end

        missing_observables = setdiff(options.expected_moment_observables, found_observables);
        if ~isempty(missing_observables)
            add_warning(sprintf('Moment rows missing documented observables: %s.', strjoin(missing_observables, ', ')));
        end
    end

    function require_fields(payload, scope_name, field_names)
        for idx = 1:numel(field_names)
            if ~isfield(payload, field_names{idx})
                add_error(sprintf('%s is missing field %s.', scope_name, field_names{idx}));
            end
        end
    end

    function require_numeric_fields(payload, scope_name, field_names)
        for idx = 1:numel(field_names)
            check_numeric_scalar(payload, field_names{idx}, scope_name);
        end
    end


    function check_numeric_scalar(payload, field_name, scope_name)
        if ~isfield(payload, field_name)
            add_error(sprintf('%s is missing field %s.', scope_name, field_name));
            return;
        end
        if ~isfinite_numeric_scalar(payload.(field_name))
            add_error(sprintf('%s.%s must be a finite numeric scalar.', scope_name, field_name));
        end
    end

    function tf = has_nonempty_field(payload, field_name)
        tf = isfield(payload, field_name) && ~isempty(payload.(field_name));
    end

    function tf = all_fields_exist(payload, field_names)
        tf = true;
        for idx = 1:numel(field_names)
            tf = tf && isfield(payload, field_names{idx}) && ~isempty(payload.(field_names{idx}));
        end
    end

    function tf = isfinite_numeric_scalar(value)
        tf = isnumeric(value) && isscalar(value) && isfinite(value);
    end

    function add_error(message)
        report.errors{end + 1} = message;
    end

    function add_warning(message)
        report.warnings{end + 1} = message;
    end
end
