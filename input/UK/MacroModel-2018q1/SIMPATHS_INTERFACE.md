# DSGE Policy Function Export for SimPaths

**Generated:** 09-Nov-2026 14:07:35
**Model:** Macromodel_UK_frozen

## 1. State-Space Representation

The linearized DSGE solution separates **state** (predetermined) and **jump** (forward-looking) variables:

```
s_t = A  * s_{t-1} + B_s * ε_t    (state transition)
y_t = C  * s_{t-1} + D   * ε_t    (jump variables)
```

Where:
- `s_t` = state variables (13×1): predetermined, depend on lagged values
- `y_t` = jump variables (9×1): forward-looking, determined by expectations
- `A`   = state transition matrix (13 × 13)
- `B_s` = shock impact on states (13 × 8)
- `C`   = jump response to lagged states (9 × 13)
- `D`   = jump response to shocks (9 × 8)
- `ε_t` = shock innovations (8×1)

**Key insight for SimPaths:**
- Wage `w` is a **state variable** → extract from `s_t` directly
- Inflation `π` is **also a state variable** (inflation
  indexation puts `pi(-1)` in the NKPC) → extract from `s_t`, NOT `y_t`
- Every shock, eps_L and eps_h included, is an innovation in **standard-deviation
  units**: 1.0 is a one-σ shock, multiplied by its std dev before the policy matrices
- The model is in 100 × log-deviation units (`deviation_scale`), so the wage
  deviation is a percent

## 2. Files Exported

| File | Dimension | Description |
|------|-----------|-------------|
| `policy_A.csv` | 13×13 | State transition matrix (A) |
| `policy_Bs.csv` | 13×8 | Shock impact on states (B_s) |
| `policy_C.csv` | 9×13 | Jump response to lagged states (C) |
| `policy_D.csv` | 9×8 | Jump response to shocks (D) |
| `policy_full_ghx.csv` | 22×13 | Full response matrix (reference only; nothing reads this) |
| `policy_full_ghu.csv` | 22×8 | Full shock matrix (reference only; nothing reads this) |
| `steady_state.csv` | 22×1 | Steady state values |
| `shock_params.csv` | 8×4 | Shock AR(1) coefficients and std devs |
| `model_info.json` | - | Variable names and indices |
| `irfs_eps_*.csv` | - | Dynare responses to one-σ shocks, one file per exported shock |

### Ramsey trend handoff

The files above are the DSGE half of the bundle. The Ramsey trend layer
consumes these, and **SimPaths must read them rather than re-derive them**.
Every path here is produced on the MATLAB side; a second implementation
downstream is how the two sides drift apart without any error surfacing.

| File | Description |
|------|-------------|
| `growth_model_reference_uk.csv` | In-sample Ramsey solution; its last row is the handoff quarter that every other file aligns to |
| `growth_params_UK.json` | Calibrated structural parameters and solver settings |
| `growth_terminal_state.json` | Terminal K and A, the handoff quarter, and `projection_quarters` (the forward horizon; a mismatch silently changes the terminal closure) |
| `population_projection_UK.csv` | SimPaths-consistent demographic path, `time,N,P`: persons in employment and resident population |
| `hours_projection_UK.csv` | Planner hours per worker, `time,h`, `projection_quarters + 1` rows from the handoff |

**Alignment.** Each projection is located by DATE, not by row position: find the
row whose label equals the handoff quarter and read forward from there. A
projection may legitimately begin earlier than the handoff.

**Hours.** SimPaths reads the forward hours path from `hours_projection_*.csv`
and fails the run if the file is missing. It does not extend the reference CSV's
`h` column itself: with a trailing-growth window of its own the two sides would
drift apart, which `hours_trend_method = log_linear` (a pure exponential `h`)
would hide.

**What is built on both sides.** The terminal tail appended past the
SimPaths horizon is not exported. Both sides construct it with the same rule
(trailing-growth window 20 for the demographic path, 1 for hours), enforced only
by convention and a downstream tripwire test. Beyond the projection's last row
the population follows employment at the last projected ratio.

## 3. Variable Classification

### State Variables (rows in A, B_s; columns in A, C)

| Index (s) | Variable | Description |
|-----------|----------|-------------|
| 0 | `c` | c |
| 1 | `R` | nominal interest rate |
| 2 | `pi` | pi |
| 3 | `w` | real wage |
| 4 | `v` | vacancies |
| 5 | `n` | employment |
| 6 | `h` | hours per worker |
| 7 | `x` | technology |
| 8 | `L` | labor force |
| 9 | `k` | capital stock |
| 10 | `inv` | investment |
| 11 | `sep_shock` | separation shock process |
| 12 | `d_shock` | demand/preference shock state |

### Jump Variables (rows in C, D)

| Index (y) | Variable | Description |
|-----------|----------|-------------|
| 0 | `y` | output |
| 1 | `d` | aggregate demand |
| 2 | `gap` | demand-supply gap |
| 3 | `w_star` | reset wage |
| 4 | `u` | unemployment level |
| 5 | `u_rate` | unemployment rate gap |
| 6 | `m` | matches |
| 7 | `mc` | marginal cost |
| 8 | `theta` | labor market tightness |

### Shocks (columns in B_s, D)

| Index | Shock | Description | ρ | σ |
|-------|-------|-------------|---|---|
| 0 | `eps_a` | technology | 0.950 | 0.5701 |
| 1 | `eps_r` | monetary policy | 0.850 | 0.5076 |
| 2 | `eps_d` | demand/preference | 0.800 | 0.4135 |
| 3 | `eps_p` | price-setting / cost-push | 0.000 | 1.4866 |
| 4 | `eps_L` | labor force | 0.950 | 0.2517 |
| 5 | `eps_sep` | separation | 0.800 | 0.1000 |
| 6 | `eps_h` | hours worked | 0.800 | 0.3894 |
| 7 | `eps_inv` | investment-specific | 0.850 | 2.3953 |

## 4. Java Sketch

A sketch of the state-space step. The implementation is
`simpaths.model.macro.DSGEModel`, which multiplies each σ-unit shock by its σ
before `eps` enters the matrices.

**IMPORTANT:** All indices in this documentation are **0-based** (Java convention).

```java
public class DSGEPolicyFunction {
    // Matrix dimensions
    private static final int N_STATE = 13;
    private static final int N_JUMP = 9;
    private static final int N_SHOCK = 8;

    // State-space matrices
    private double[][] A;   // 13 × 13 (state transition)
    private double[][] Bs;  // 13 × 8 (shock → states)
    private double[][] C;   // 9 × 13 (lagged states → jumps)
    private double[][] D;   // 9 × 8 (shocks → jumps)

    // State variable indices (within s vector)
    public static final int S_C = 0;
    public static final int S_R = 1;
    public static final int S_PI = 2;
    public static final int S_W = 3;
    public static final int S_V = 4;
    public static final int S_N = 5;
    public static final int S_H = 6;
    public static final int S_X = 7;
    public static final int S_L = 8;
    public static final int S_K = 9;
    public static final int S_INV = 10;
    public static final int S_SEP_SHOCK = 11;
    public static final int S_D_SHOCK = 12;

    // Jump variable indices (within y vector)
    public static final int Y_Y = 0;
    public static final int Y_D = 1;
    public static final int Y_GAP = 2;
    public static final int Y_W_STAR = 3;
    public static final int Y_U = 4;
    public static final int Y_U_RATE = 5;
    public static final int Y_M = 6;
    public static final int Y_MC = 7;
    public static final int Y_THETA = 8;

    // Shock indices
    public static final int EPS_A = 0;
    public static final int EPS_R = 1;
    public static final int EPS_D = 2;
    public static final int EPS_P = 3;
    public static final int EPS_L = 4;
    public static final int EPS_SEP = 5;
    public static final int EPS_H = 6;
    public static final int EPS_INV = 7;

    /**
     * Update state variables: s_new = A * s_old + Bs * eps
     */
    public double[] updateStates(double[] sOld, double[] eps) {
        double[] sNew = new double[N_STATE];
        for (int i = 0; i < N_STATE; i++) {
            sNew[i] = 0.0;
            for (int j = 0; j < N_STATE; j++) {
                sNew[i] += A[i][j] * sOld[j];
            }
            for (int j = 0; j < N_SHOCK; j++) {
                sNew[i] += Bs[i][j] * eps[j];
            }
        }
        return sNew;
    }

    /**
     * Compute jump variables: y = C * s_old + D * eps
     */
    public double[] computeJumps(double[] sOld, double[] eps) {
        double[] y = new double[N_JUMP];
        for (int i = 0; i < N_JUMP; i++) {
            y[i] = 0.0;
            for (int j = 0; j < N_STATE; j++) {
                y[i] += C[i][j] * sOld[j];
            }
            for (int j = 0; j < N_SHOCK; j++) {
                y[i] += D[i][j] * eps[j];
            }
        }
        return y;
    }

    /**
     * Get wage and inflation, both state variables, for the
     * microsimulation.
     */
    public double[] getMicrosimFeedback(double[] sOld, double[] eps) {
        double[] sNew = updateStates(sOld, eps);

        double w = sNew[S_W];    // wage is a state
        double pi = sNew[S_PI];  // inflation is a state too (indexation)

        return new double[]{w, pi};  // to the microsimulation
    }
}
```

## 5. Interface with SimPaths

- SimPaths sends nothing to the DSGE; its shocks come from a scenario only
- SimPaths receives the wage deviation (Δw) and the inflation deviation (Δπ)
- `irfs_eps_L.csv` and `irfs_eps_h.csv` hold the responses to one-σ labour-force
  and hours innovations, which decay at the calibrated ρ of the table above

## 6. Usage Notes

1. **Units:** All variables are in % deviations from steady state
2. **Frequency:** Quarterly (DSGEModel.java runs 4 quarters and averages for annual)
3. **Causality:** One-way coupling:
   - Scenario → DSGE: shocks in standard-deviation units (none from SimPaths)
   - DSGE → SimPaths: Wage (Δw) and inflation (Δπ)
4. **Steady state:** All zeros (log-linearized model)
5. **Indexing:** JSON uses 1-based (MATLAB), Java code uses 0-based
