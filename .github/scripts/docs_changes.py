"""Select documentation builds from the complete Git diff, not a truncated API list.

The check job still runs on every event so required checks never stay pending.
Unknown/missing history and manual runs build conservatively.
"""

import json
import os
import re
import subprocess
from pathlib import Path


PREFIXES = (
    "documentation/wiki/",
    "documentation/overrides/",
    "documentation/scripts/",
    "documentation/visual-tests/",
    "src/main/",  # Javadoc and the source-aware syntax highlighting hook.
    ".mvn/",
    ".github/scripts/",
)
FILES = {
    "mkdocs.yml",
    "documentation/requirements.txt",
    "pom.xml",
    "mvnw",
    "mvnw.cmd",
    ".gitattributes",
    ".gitmodules",
    ".github/workflows/deploy-docs.yml",
    ".github/workflows/docs-checks.yml",
}


def affects_docs(path):
    return path in FILES or path.startswith(PREFIXES)


def needs_build(event_name, event, cwd=None):
    if event_name == "workflow_dispatch":
        return True, "Manual run: build requested."
    try:
        if event_name == "push":
            base, head = event["before"], event["after"]
            merge_base = False
        elif event_name == "pull_request":
            pr = event["pull_request"]
            base, head = pr["base"]["sha"], pr["head"]["sha"]
            merge_base = True
        else:
            return True, "Unrecognised event: build conservatively."
        for revision in (base, head):
            if not re.fullmatch(r"[0-9a-fA-F]{40}", revision) or set(revision) == {"0"}:
                return True, "New branch or unavailable revision: build conservatively."
        # PRs compare against their merge base, so unrelated base-branch changes
        # do not trigger builds. Pushes include every commit since the last tip.
        revisions = [f"{base}...{head}"] if merge_base else [base, head]
        result = subprocess.run(
            ["git", "diff", "--name-only", "--no-renames", "-z", *revisions, "--"],
            cwd=cwd, check=True, capture_output=True,
        )
        paths = result.stdout.decode("utf-8", errors="surrogateescape").split("\0")
    except (KeyError, TypeError, OSError, subprocess.CalledProcessError):
        return True, "Cannot establish the full diff: build conservatively."
    if any(affects_docs(path) for path in paths):
        return True, "Website, API or documentation build inputs changed."
    return False, "No documentation build inputs changed; expensive checks skipped."


def main():
    try:
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        changed, reason = needs_build(os.environ.get("GITHUB_EVENT_NAME", ""), event)
    except (KeyError, OSError, ValueError):
        changed, reason = True, "Cannot read the event: build conservatively."
    print(reason)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"changed={str(changed).lower()}\n")
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a") as output:
            output.write(f"Documentation: {reason}\n")


if __name__ == "__main__":
    main()
