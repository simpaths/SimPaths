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

## Before pushing website changes

Run the complete local preflight from the checkout you intend to push:

```sh
bash documentation/scripts/preflight-docs.sh
```

It runs the workflow tests, CSS and content checks, Python tests, Java/API build,
strict MkDocs build, and both existing Playwright suites at desktop and mobile
sizes. It serves the fresh build on a separate port instead of using an existing
preview. A failure stops the command. Browser failure screenshots and traces are
saved in `documentation/visual-tests/test-results/`.

Install `documentation/requirements.txt` and provide Maven, JDK 25 and Node.js
before running it. The command uses `npm ci` and the pinned Playwright browser.
Local browsers run on the host OS; GitHub still checks Linux and the actual Pages
deployment, so a local pass cannot guarantee that external services will succeed.

The optional hook in `documentation/scripts/git-hooks/pre-push` uses the same
changed-file rules as CI. It blocks relevant pushes on a failed preflight and
requires the tested documentation/API inputs to be committed. Unrelated pushes,
tag pushes and branch deletions do not run the build. It tests the checked-out
commit, not another branch named in a push command.

For a repository with `extensions.worktreeConfig` already enabled, install it
for just the website checkout (check for an existing hook before replacing one):

```sh
git config --worktree core.hooksPath "$PWD/documentation/scripts/git-hooks"
```

When an approved design changes, update tests that intentionally specify its
appearance in the same commit. Keep navigation, accessibility, reduced-motion
and overflow checks intact. A visual review or a successful MkDocs build alone
does not replace the full preflight.
