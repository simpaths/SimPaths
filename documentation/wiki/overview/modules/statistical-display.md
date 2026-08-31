---
title: Statistical display
---

<div class="module-detail" markdown>

# Statistical display

<p class="module-detail__lead">At the end of each simulated year, SimPaths produces year-specific summary statistics for post-simulation analysis. A selected subset is also displayed graphically while a simulation is running.</p>

## Output handling

[`SimPathsCollector`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/experiment/SimPathsCollector.java) collects and persists simulation outputs. When the graphical interface is enabled, [`SimPathsObserver`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/experiment/SimPathsObserver.java) updates the live charts and monitoring views.

The available outputs and their storage depend on the run configuration. See [Running your first simulation](../../getting-started/first-simulation.md) for the main run components and [Saving outputs](../../jasmine-reference/saving-outputs.md) for the underlying JAS-mine output facilities.

</div>
