"""Exercise event diffs against real Git histories, including fork-style PRs."""

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from docs_changes import affects_docs, needs_build


class DocumentationInputs(unittest.TestCase):
    def test_site_and_api_inputs(self):
        for path in (
            'documentation/wiki/research/index.md',
            'documentation/wiki/assets/images/model-structure-uk.svg',
            'documentation/overrides/main.html',
            'documentation/scripts/code_highlighting.py',
            'documentation/visual-tests/package-lock.json',
            'documentation/requirements.txt', 'mkdocs.yml', 'pom.xml',
            'src/main/java/simpaths/model/Person.java',
            'src/main/resources/META-INF/persistence.xml', '.mvn/maven.config',
            '.github/workflows/deploy-docs.yml', '.github/workflows/docs-checks.yml',
            '.github/scripts/docs_changes.py', '.gitattributes',
        ):
            with self.subTest(path=path):
                self.assertTrue(affects_docs(path))

    def test_unrelated_files(self):
        for path in ('README.md', 'input/reg_education.xlsx', 'config/default.yml',
                     'src/test/java/Test.java', 'documentation/SimPathsUK_Schedule.xlsx',
                     '.github/workflows/SimPathsBuild.yml'):
            with self.subTest(path=path):
                self.assertFalse(affects_docs(path))


class EventDiffs(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git('init', '-q', '-b', 'main')
        self.git('config', 'user.name', 'Workflow tests')
        self.git('config', 'user.email', 'tests@example.invalid')
        self.base = self.commit('README.md')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, stderr=subprocess.PIPE,
                                       text=True).strip()

    def commit(self, path):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(target.read_text() + '\nchange' if target.exists() else 'content')
        self.git('add', '--', path)
        self.git('commit', '-qm', 'Fixture')
        return self.git('rev-parse', 'HEAD')

    def push(self, base, head):
        return needs_build('push', {'before': base, 'after': head}, self.root)[0]

    def test_unrelated_push_and_unchanged_tip_skip(self):
        head = self.commit('input/example.xlsx')
        self.assertFalse(self.push(self.base, head))
        self.assertFalse(self.push(head, head))

    def test_multiple_commit_push_includes_earlier_docs_change(self):
        self.commit('documentation/wiki/index.md')
        head = self.commit('input/example.xlsx')
        self.assertTrue(self.push(self.base, head))

    def test_later_unrelated_push_does_not_supersede_docs_build(self):
        source = self.commit('documentation/wiki/index.md')
        tip = self.commit('input/example.xlsx')
        self.assertFalse(self.push(source, tip))
        newer = self.commit('src/main/java/Example.java')
        self.assertTrue(self.push(source, newer))

    def test_deletion_and_rename_out_of_docs_are_relevant(self):
        before = self.commit('documentation/wiki/index.md')
        self.git('mv', 'documentation/wiki/index.md', 'moved.md')
        self.git('commit', '-qm', 'Move outside docs')
        self.assertTrue(self.push(before, self.git('rev-parse', 'HEAD')))
        before = self.commit('documentation/wiki/old.md')
        self.git('rm', 'documentation/wiki/old.md')
        self.git('commit', '-qm', 'Delete')
        self.assertTrue(self.push(before, self.git('rev-parse', 'HEAD')))

    def test_more_than_300_changed_files_and_unusual_names(self):
        for i in range(310):
            (self.root / f'input-{i}.txt').write_text('data')
        self.git('add', '.')
        self.git('commit', '-qm', 'Many unrelated files')
        head = self.commit('documentation/wiki/a space\nand newline.md')
        self.assertTrue(self.push(self.base, head))

    def test_pr_uses_merge_base_and_checks_fork_changes(self):
        base_tip = self.commit('documentation/wiki/base-only.md')
        self.git('checkout', '-qb', 'contribution', self.base)
        head = self.commit('input/example.xlsx')
        event = {'pull_request': {'base': {'sha': base_tip}, 'head': {'sha': head}}}
        self.assertFalse(needs_build('pull_request', event, self.root)[0])
        event['pull_request']['head']['sha'] = self.commit('documentation/wiki/proposed.md')
        self.assertTrue(needs_build('pull_request', event, self.root)[0])

    def test_force_push_compares_old_and_new_tips(self):
        old = self.commit('documentation/wiki/index.md')
        self.git('checkout', '--detach', self.base)
        head = self.commit('input/example.xlsx')
        self.assertTrue(self.push(old, head))

    def test_manual_new_branch_and_missing_history_build(self):
        self.assertTrue(needs_build('workflow_dispatch', {}, self.root)[0])
        self.assertTrue(self.push('0' * 40, self.base))
        self.assertTrue(self.push('f' * 40, self.base))
        self.assertTrue(needs_build('push', {}, self.root)[0])
        self.assertTrue(needs_build('unknown', {}, self.root)[0])

    def test_command_writes_github_output_and_summary(self):
        head = self.commit('input/example.xlsx')
        event = self.root / 'event.json'
        event.write_text(json.dumps({'before': self.base, 'after': head}))
        output, summary = self.root / 'output', self.root / 'summary'
        script = Path(__file__).with_name('docs_changes.py')
        subprocess.run(['python3', str(script)], cwd=self.root, check=True, capture_output=True,
                       env={**os.environ, 'GITHUB_EVENT_NAME': 'push',
                            'GITHUB_EVENT_PATH': str(event), 'GITHUB_OUTPUT': str(output),
                            'GITHUB_STEP_SUMMARY': str(summary)})
        self.assertEqual(output.read_text(), 'changed=false\n')
        self.assertIn('skipped', summary.read_text())


if __name__ == '__main__':
    unittest.main()
