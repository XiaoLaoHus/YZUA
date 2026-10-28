const { execFileSync } = require('node:child_process');
const { resolve } = require('node:path');

function isAllowedUiPath(file) {
  if (file.includes('\\') || file.split('/').some(p => p === '..' || p === '.')) return false;
  return file === 'README.md' || file.startsWith('docs/') ||
    file === 'app/src/main/java/cn/yzu/timetable/ui/AppRoot.kt' ||
    /^app\/src\/main\/java\/cn\/yzu\/timetable\/ui\/(screens|components|theme)\/.+\.kt$/.test(file) ||
    /^app\/src\/main\/res\/(drawable(?:-[^/]+)?|mipmap(?:-[^/]+)?|font)\/.+/.test(file) ||
    /^app\/src\/main\/res\/values(?:-[^/]+)?\/(strings|colors|dimens|typography)\.xml$/.test(file);
}

function main() {
  const base = process.argv[2] || 'v0.5';
  if (base.startsWith('-')) throw new Error('Base must be a Git revision');
  const cwd = resolve(__dirname, '..');
  const git = args => execFileSync('git', args, { cwd, encoding: 'utf8' });
  const commit = git(['rev-parse', '--verify', base + '^{commit}']).trim();
  // Collect both worktree and index diffs. `git diff <base>` alone misses a
  // protected file that is staged and then restored in the worktree.
  const tracked = git(['diff', '--name-only', '--no-renames', '-z', commit, '--']);
  const staged = git(['diff', '--cached', '--name-only', '--no-renames', '-z', commit, '--']);
  const untracked = git(['ls-files', '--others', '--exclude-standard', '-z']);
  const changed = [...new Set((tracked + staged + untracked).split('\0').filter(Boolean))];
  const blocked = changed.filter(file => !isAllowedUiPath(file));
  if (blocked.length) {
    process.stderr.write('UI scope check failed: protected files changed since ' + base + ':\n' + blocked.join('\n') + '\n');
    process.exitCode = 1;
  } else {
    process.stdout.write('UI scope check passed: ' + changed.length + ' changed paths stay within the presentation allowlist.\n');
  }
}

module.exports = { isAllowedUiPath };
if (require.main === module) {
  try { main(); } catch (error) { process.stderr.write(error.message + '\n'); process.exitCode = 1; }
}
