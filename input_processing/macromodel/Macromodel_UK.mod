// =========================================================================
// DSGE Model - UNITED KINGDOM
// =========================================================================
// Thin wrapper: defines country and includes shared template.
// All structural equations are in Macromodel_template.mod.
// Country-specific parameters are in country_params/UK_*.mod.
//
// Usage:  >> dynare Macromodel_UK use_dll
// =========================================================================

@#define country = "UK"
@#include "Macromodel_template.mod"
