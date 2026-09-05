# Uncertainty Analysis

Repeated simulations can describe variation in model results, but the interpretation depends on what changes between runs. A spread across seeds is not automatically a confidence interval covering every source of uncertainty.

## Decide what to vary

| Source | Practical approach | What it does not establish |
| --- | --- | --- |
| Simulation randomness | Repeat the same scenario with different seeds, holding coefficients fixed if isolating this component | Robustness to different model structures or input datasets |
| Estimated coefficients | Draw alternative coefficients using their estimated covariance matrices and repeat the simulation | Uncertainty in omitted processes or future structural change |
| Model specification and scenario assumptions | Compare explicitly defined alternative equations, settings or policy scenarios | Probabilities for those alternatives unless separately justified |
| Input data | Evaluate alternative samples, weights or data constructions where feasible | That weighting alone removes sampling or measurement error |

Population size, replication count and uncertainty design should be chosen for the outcomes and comparisons of interest. Check whether results are stable as these choices change rather than assuming one universal number of runs is sufficient.

## Check coefficient resampling in your version

In the [development revision checked for this guide](https://github.com/simpaths/SimPaths/blob/b223738b9cdf1d814cc3c6f09b04bc4930d3c667/src/main/java/simpaths/data/Parameters.java), `Parameters.bootstrapAll` is a `static final boolean` set to `true`. The parameter-loading routine resamples the coefficient maps covered by its bootstrap block before constructing the regression objects.

This is a source-level setting in that revision, not a supported run-time YAML or command-line toggle. Adding `bootstrapAll: false` to a configuration file does not provide a reliable way to disable it. A fixed-coefficient comparison requires a separately built and checked version with the setting changed, or a later version that explicitly exposes this control.

Not every input parameter is necessarily covered by that bootstrap block. Inspect the loader for the processes used in your analysis. Resampling from individual coefficient covariance matrices also does not by itself establish that dependence between separately estimated equations is represented.

## Set up repeated runs

1. Follow [Multiple Runs](multiple-runs.md) and copy its configuration into a named file for the experiment.
2. Record the code revision, parameter workbooks, input population, donor data and policy schedule.
3. Set `maxNumberOfRuns` and the initial `randomSeed`. Set `innovation_args.randomSeedInnov` to `true` if the seed should increase between runs.
4. Hold other scenario and innovation settings fixed unless varying them is part of the design.
5. Check the effective settings and output files for each run, then summarise the distribution of the outcomes and policy differences you actually need.

In the checked implementation, the model seeds its random generator before loading parameters. With coefficient resampling enabled, changing the seed can change both coefficients and simulation draws. Such runs should not be labelled as measuring only Monte Carlo variation.

For scenario comparisons, using matched seeds can help comparisons, but does not guarantee identical random events if scenarios change the order or number of draws. Verify the comparison design and document what is paired.

## Report the scope

State which uncertainties were varied, which were held fixed, the replication count, population size, seeds and summary method. Distinguish variation in simulated outcomes from uncertainty in an estimated mean or policy effect. Do not present percentile ranges as covering data, parameter and structural uncertainty unless the experiment actually represents those sources.

The [Development Roadmap](../overview/roadmap.md) describes planned improvements to uncertainty controls. A planned control should not be treated as available in the executable being used.

## Further reading

- Bilcke J, Beutels P, Brisson M, Jit M (2011). Accounting for Methodological, Structural, and Parameter Uncertainty in Decision-Analytic Models: A Practical Guide. Medical Decision Making 31(4): 675-692.
- Creedy J, Kalb G, Kew H (2007). Confidence intervals for policy reforms in behavioural tax microsimulation modelling. Bulletin of Economic Research 59(1): 37-65.
- Goedemé T, Van den Bosch K, Salanauskaite L, Verbist G (2013). Testing the Statistical Significance of Microsimulation Results: A Plea. International Journal of Microsimulation 6(3): 50-77.
- Mitton L, Sutherland H, Weeks M (2000). Microsimulation Modelling for Policy Analysis. Challenges and Innovations. Cambridge University Press.
