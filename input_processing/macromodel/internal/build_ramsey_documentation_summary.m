function summary = build_ramsey_documentation_summary(params, country, diagnostics)
% BUILD_RAMSEY_DOCUMENTATION_SUMMARY  Build Ramsey documentation summary payload.

    arguments
        params (1,1) struct
        country (1,:) char
        diagnostics struct = struct()
    end

    p = params.parameters;
    base = params.base_year_values;
    derived = params.derived;

    summary = struct();
    summary.metadata = struct();
    summary.metadata.country = country;

    summary.structural = struct();
    summary.structural.base_year = params.base_year;
    summary.structural.alpha_K = p.alpha_K;
    summary.structural.delta_annual = p.delta_annual;
    summary.structural.beta_annual = p.beta_annual;
    summary.structural.sigma = p.sigma;
    summary.structural.tfp_growth_annual = p.tfp_growth_annual;
    summary.structural.tfp_growth_terminal_annual = derived.tfp_growth_annual_terminal;
    summary.structural.rho_g = get_optional_nested_value(params, {'terminal_growth', 'rho_quarterly'});
    summary.structural.inflation_target_annual = get_optional_nested_value(params, {'inflation_target', 'pi_star_annual'});
    summary.structural.beta_quarterly = derived.beta_quarterly;
    summary.structural.delta_quarterly = derived.delta_quarterly;
    summary.structural.r_star_annual = derived.r_star_bgp_annual;
    summary.structural.ky_bgp_quarterly = derived.KY_bgp_quarterly;
    if isfield(params.solver, 'terminal_tail_quarters')
        summary.structural.solver_horizon_quarters = params.solver.terminal_tail_quarters;
    else
        summary.structural.solver_horizon_quarters = params.solver.terminal_extension_quarters;
    end
    summary.structural.K_override = get_optional_value(p, 'K_override');

    summary.base_year_values = struct();
    summary.base_year_values.K0 = base.K;
    summary.base_year_values.Y0_annual = base.Y;
    summary.base_year_values.N0 = base.N;
    summary.base_year_values.L0 = base.L;
    summary.base_year_values.h0 = base.h;
    summary.base_year_values.A0 = base.A_implied;
    summary.base_year_values.P0 = base.P;
    summary.base_year_values.dependency_ratio0 = base.P / base.N;
    summary.base_year_values.CY = params.expenditure_shares.CY_base;
    summary.base_year_values.IY = params.expenditure_shares.IY_base;
    summary.base_year_values.GY = params.expenditure_shares.GY_base;
    summary.base_year_values.NXY = params.expenditure_shares.NXY_base;

    if ~isempty(fieldnames(diagnostics))
        summary.validation = diagnostics;
    end
end


function value = get_optional_value(payload, field_name)
    if isfield(payload, field_name) && ~isempty(payload.(field_name))
        value = payload.(field_name);
    else
        value = [];
    end
end


function value = get_optional_nested_value(payload, field_path)
    value = [];
    current = payload;
    for idx = 1:numel(field_path)
        field_name = field_path{idx};
        if ~isstruct(current) || ~isfield(current, field_name) || isempty(current.(field_name))
            return;
        end
        current = current.(field_name);
    end
    value = current;
end
