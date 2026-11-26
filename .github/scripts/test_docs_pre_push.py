"""Verify that the local guard checks the pushed revision and fails closed."""

import io
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from docs_pre_push import main, requires_preflight


ZERO = '0' * 40


class PrePushChecks(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.repo = Path(temp.name)
        self.git('init', '-q', '-b', 'develop')
        self.git('config', 'user.name', 'Preflight tests')
        self.git('config', 'user.email', 'tests@example.invalid')
        self.base = self.commit('README.md')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, stderr=subprocess.PIPE,
                                       text=True).strip()

    def write(self, path):
        target = self.repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text('changed\n' + (target.read_text() if target.exists() else ''))

    def commit(self, path):
        self.write(path)
        self.git('add', '--', path)
        self.git('commit', '-qm', 'Fixture')
        return self.git('rev-parse', 'HEAD')

    def update(self, head, base=None, ref='refs/heads/develop'):
        return f'refs/heads/develop {head} {ref} {base or self.base}\n'

    def test_docs_and_api_changes_require_checks_even_before_unrelated_commit(self):
        for path in ('documentation/wiki/index.md', 'src/main/java/Example.java'):
            self.commit(path)
            head = self.commit('input/example.txt')
            self.assertTrue(requires_preflight(self.repo, [self.update(head)]))

    def test_unrelated_push_deletion_and_tags_skip(self):
        head = self.commit('input/example.txt')
        for update in (self.update(head), self.update(ZERO),
                       self.update(head, ZERO, 'refs/tags/test-release')):
            self.assertFalse(requires_preflight(self.repo, [update]))

    def test_new_branch_or_missing_history_checks_conservatively(self):
        head = self.commit('documentation/wiki/index.md')
        for base in (ZERO, '1' * 40):
            self.assertTrue(requires_preflight(self.repo, [self.update(head, base)]))

    def test_different_checked_out_revision_stops_push(self):
        head = self.commit('documentation/wiki/index.md')
        self.commit('input/example.txt')
        with self.assertRaisesRegex(ValueError, 'Check out'):
            requires_preflight(self.repo, [self.update(head)])

    def test_dirty_tracked_and_staged_inputs_stop_push(self):
        head = self.commit('documentation/wiki/index.md')
        self.write('documentation/wiki/index.md')
        for staged in (False, True):
            if staged:
                self.git('add', 'documentation/wiki/index.md')
            with self.assertRaisesRegex(ValueError, 'Commit the documentation/API inputs'):
                requires_preflight(self.repo, [self.update(head)])

    def test_untracked_input_stops_but_unrelated_work_is_allowed(self):
        head = self.commit('documentation/wiki/index.md')
        self.write('input/local-experiment.txt')
        self.assertTrue(requires_preflight(self.repo, [self.update(head)]))
        self.write('documentation/wiki/new page.md')
        with self.assertRaisesRegex(ValueError, 'new page.md'):
            requires_preflight(self.repo, [self.update(head)])

    @patch('docs_pre_push.git', return_value='GIT_DIR\nGIT_WORK_TREE\n')
    @patch('docs_pre_push.requires_preflight', return_value=True)
    @patch('docs_pre_push.subprocess.check_output', return_value='/tmp/fixture\n')
    @patch('docs_pre_push.subprocess.run')
    def test_failing_preflight_blocks_and_clears_hook_environment(self, run, *_):
        run.return_value.returncode = 7
        with patch('docs_pre_push.sys.stdin', io.StringIO(self.update(self.base))), \
             patch.dict('os.environ', {'GIT_DIR': '/wrong/repo', 'GIT_WORK_TREE': '/wrong/tree'}):
            self.assertEqual(main(), 7)
        self.assertNotIn('GIT_DIR', run.call_args.kwargs['env'])
        self.assertNotIn('GIT_WORK_TREE', run.call_args.kwargs['env'])


if __name__ == '__main__':
    unittest.main()
