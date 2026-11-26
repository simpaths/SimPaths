"""Run documentation preflight for relevant pushes from this checkout."""

import os
import subprocess
import sys
from pathlib import Path

from docs_changes import affects_docs, needs_build


def git(repo, *args):
    return subprocess.check_output(['git', *args], cwd=repo).decode(
        'utf-8', errors='surrogateescape')


def requires_preflight(repo, updates):
    relevant_heads = set()
    for line in updates:
        _local_ref, local_sha, remote_ref, remote_sha = line.split()
        if set(local_sha) == {'0'} or not remote_ref.startswith('refs/heads/'):
            continue
        changed, _ = needs_build('push', {'before': remote_sha, 'after': local_sha}, repo)
        if changed:
            relevant_heads.add(local_sha)
    if not relevant_heads:
        return False
    if relevant_heads != {git(repo, 'rev-parse', 'HEAD').strip()}:
        raise ValueError('Check out the documentation revision being pushed before running its preflight.')
    # The test must describe the pushed commit, including staged changes and new files.
    dirty = git(repo, 'diff', '--name-only', '-z', 'HEAD', '--').split('\0')
    dirty += git(repo, 'ls-files', '--others', '--exclude-standard', '-z').split('\0')
    inputs = sorted(path for path in dirty if affects_docs(path))
    if inputs:
        raise ValueError('Commit the documentation/API inputs before pushing so preflight tests the exact commit:\n'
                         + '\n'.join(inputs))
    return True


def main():
    repo = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
    updates = list(sys.stdin)
    try:
        run = requires_preflight(repo, updates)
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f'Push stopped: {error}', file=sys.stderr)
        return 1
    if not run:
        print('Documentation preflight: no website/API inputs changed.')
        return 0
    print('Checking documentation before push...', flush=True)
    # Git sets repository variables for hooks. Do not leak them into tests that
    # create their own temporary repositories.
    git_vars = git(repo, 'rev-parse', '--local-env-vars').splitlines()
    environment = {key: value for key, value in os.environ.items() if key not in git_vars}
    result = subprocess.run(['bash', str(repo / 'documentation/scripts/preflight-docs.sh')],
                            cwd=repo, env=environment)
    if result.returncode:
        print('Push stopped: documentation preflight failed. Fix the failure and retry.', file=sys.stderr)
        return result.returncode
    try:
        requires_preflight(repo, updates)
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f'Push stopped: checkout changed during preflight. {error}', file=sys.stderr)
        return 1
    return result.returncode


if __name__ == '__main__':
    sys.exit(main())
