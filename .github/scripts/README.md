# Documentation workflow inputs

The public site is published only by `simpaths/SimPaths`, from `develop`.
Keep the deployment workflow identical on `main` and `develop`: pushes to
`main`, fork pushes and pull requests must never deploy a site.

`docs_changes.py` gates the expensive build steps using the event's Git diff.
Documentation checks retain the `check` job on every push/PR so a required check
can succeed even when there is nothing to build. Fork contributions still receive
validation when they change website or API inputs. Manual runs force a build.

The input list covers the MkDocs content, theme, scripts, tests and dependencies,
plus Java sources/resources and Maven configuration for the generated API.
Update it if a new build input is added. Simulation inputs, experiment outputs
and Java tests alone do not require rebuilding the documentation.

Pushes compare their before/after revisions; PRs compare their merge base with
the head revision. Deleted/renamed files are included. Missing history causes a
build rather than an unsafe skip. This avoids the changed-file limits of workflow
path filters and API listings.

Builds use the triggering revision. Only deployment jobs share the Pages queue,
so an unrelated push cannot displace a pending documentation build. The queue
uses `queue: max` to retain pending deployments when builds finish out of order:
an older build must not replace a newer deployment waiting to run. GitHub allows
up to 100 pending jobs in this mode and cancels additional jobs when full; a
manual run can retry publication if that limit is reached. See
[GitHub's concurrency documentation](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency).
Before publishing, a build is rejected if newer website/API changes exist on `develop`;
unrelated commits do not prevent publication. A manual deployment from `develop`
can retry a failed publication without requiring another content edit.

Run the change-detection tests with:

```sh
python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
```
