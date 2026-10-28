const { test } = require('node:test');
const assert = require('node:assert/strict');
const { isAllowedUiPath } = require('./check-ui-scope.cjs');
const { mkdtempSync, mkdirSync, writeFileSync, copyFileSync, rmSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join, dirname } = require('node:path');
const { execFileSync, spawnSync } = require('node:child_process');

test('allows Compose presentation and safe display resources', () => {
  for (const file of [
    'app/src/main/java/cn/yzu/timetable/ui/AppRoot.kt',
    'app/src/main/java/cn/yzu/timetable/ui/screens/LoginScreen.kt',
    'app/src/main/java/cn/yzu/timetable/ui/components/Header.kt',
    'app/src/main/java/cn/yzu/timetable/ui/theme/Theme.kt',
    'app/src/main/res/drawable/ic_new.xml', 'app/src/main/res/font/example.ttf',
    'app/src/main/res/values/strings.xml', 'app/src/main/res/values-night/colors.xml',
    'docs/OPUS-UI-HANDOFF.md', 'README.md',
  ]) assert.equal(isAllowedUiPath(file), true, file);
});
test('checks real Git worktree, index, commits, untracked files and protected renames', () => {
  const cwd = mkdtempSync(join(tmpdir(), 'yzua-ui-scope-'));
  const git = args => execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  const backend = 'app/src/main/java/cn/yzu/timetable/data/Fixture.kt';
  const ui = 'app/src/main/java/cn/yzu/timetable/ui/components/Fixture.kt';
  const commit = () => git(['-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture']);
  const put = (file, content) => { mkdirSync(dirname(join(cwd, file)), { recursive: true }); writeFileSync(join(cwd, file), content); };
  try {
    git(['init', '-q']);
    put(backend, 'protected fixture');
    mkdirSync(join(cwd, 'scripts'));
    copyFileSync(join(__dirname, 'check-ui-scope.cjs'), join(cwd, 'scripts/check-ui-scope.cjs'));
    git(['add', '.']); commit();
    const base = git(['rev-parse', 'HEAD']).trim();
    const check = () => spawnSync(process.execPath, ['scripts/check-ui-scope.cjs', base], { cwd, encoding: 'utf8' });
    assert.equal(check().status, 0);
    put(ui, 'presentation fixture');
    assert.equal(check().status, 0);
    const protectedNew = 'app/src/main/java/cn/yzu/timetable/data/New.kt';
    put(protectedNew, 'untracked fixture');
    let result = check(); assert.equal(result.status, 1); assert.ok(result.stderr.includes(protectedNew));
    rmSync(join(cwd, protectedNew));
    put(backend, 'modified fixture');
    assert.equal(check().status, 1);
    git(['add', backend]);
    assert.equal(check().status, 1);
    // The worktree can be restored after staging; the index must still fail.
    git(['restore', '--worktree', '--source', base, '--', backend]);
    result = check(); assert.equal(result.status, 1); assert.ok(result.stderr.includes(backend));
    git(['reset', '-q', '--', backend]);
    put(backend, 'modified fixture');
    git(['add', backend]);
    commit();
    assert.equal(check().status, 1);
    git(['checkout', base, '--', backend]);
    git(['add', backend]); commit();
    assert.equal(check().status, 0);
    git(['mv', backend, 'app/src/main/java/cn/yzu/timetable/ui/components/Moved.kt']);
    result = check(); assert.equal(result.status, 1); assert.ok(result.stderr.includes(backend));
  } finally { rmSync(cwd, { recursive: true, force: true }); }
});
test('rejects backend, authentication, state, manifests, build and test changes', () => {
  for (const file of [
    'app/src/main/java/cn/yzu/timetable/data/urp/UrpHttpDataSource.kt',
    'app/src/main/java/cn/yzu/timetable/data/AcademicCache.kt',
    'app/src/main/java/cn/yzu/timetable/SsoLoginActivity.kt',
    'app/src/main/java/cn/yzu/timetable/SsoLoginScript.kt',
    'app/src/main/java/cn/yzu/timetable/SsoLoginWaitPolicy.kt',
    'app/src/main/java/cn/yzu/timetable/ui/AppViewModel.kt',
    'app/src/main/java/cn/yzu/timetable/ui/AppViewModelFactory.kt',
    'app/src/main/java/cn/yzu/timetable/MainActivity.kt',
    'app/src/main/AndroidManifest.xml', 'app/build.gradle.kts',
    '.github/workflows/android.yml', 'app/src/test/java/Example.kt',
    'app/src/main/res/xml/network_security_config.xml',
    'app/src/main/res/values/config.xml', 'app/src/main/res/values/themes.xml',
    'app/src/main/java/cn/yzu/timetable/ui/screens/../../data/New.kt',
  ]) assert.equal(isAllowedUiPath(file), false, file);
});
