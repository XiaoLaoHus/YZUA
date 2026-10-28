const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/cn/yzu/timetable/SsoLoginScript.kt'), 'utf8');
function script() {
  return source.match(/return """([\s\S]*?)"""/)[1]
    .replaceAll('$encodedAccount', Buffer.from('fixture-student').toString('base64'))
    .replaceAll('$encodedPassword', Buffer.from("fixture'password\\中文").toString('base64'));
}
function literalScript(name) {
  return source.match(new RegExp('val ' + name + ': String = """([\\s\\S]*?)"""'))[1];
}
function page({ disabled = false, title = '', url = 'https://sso.yzu.edu.cn/login?service=fixture', buttonPresent = true } = {}) {
  let clicks = 0, rawSubmits = 0;
  const queue = [];
  const user = { value: '', dispatchEvent() { queue.push(() => { button.disabled = false; }); } };
  const pass = { value: '', dispatchEvent() {} };
  const decoyUser = { value: 'other-form', dispatchEvent() {} };
  const button = { disabled, title, getAttribute(name) { return name === 'title' ? this.title : null; }, click() { if (!this.disabled) clicks++; } };
  const form = { querySelector(s) { return s.includes('username') ? user : s.includes('password') ? pass : buttonPresent ? button : null; }, checkValidity() { return true; }, submit() { rawSubmits++; } };
  user.form = pass.form = form;
  user.getClientRects = pass.getClientRects = () => [{ width: 320 }];
  const document = {
    querySelector(s) { return s.includes('username') ? decoyUser : s.includes('password') ? pass : null; },
    querySelectorAll(s) { return s.includes('password') ? [pass] : []; },
    getElementById() { return null; }
  };
  const context = vm.createContext({ document, location: new URL(url), window: { location: new URL(url) },
    atob: s => Buffer.from(s, 'base64').toString('binary'), escape, decodeURIComponent, Event: class { constructor(type) { this.type = type; } } });
  return { run: () => vm.runInContext(script(), context), click: () => vm.runInContext(literalScript('click'), context), user, pass, decoyUser, button, document, counts: () => [clicks, rawSubmits], settle: () => queue.splice(0).forEach(f => f()), context };
}
test('waits for framework updates before clicking the official submit button', () => {
  const p = page({ disabled: true });
  assert.equal(p.run(), 'FILLED');
  assert.deepEqual(p.counts(), [0, 0]);
  assert.equal(p.run(), 'WAIT');
  p.settle();
  assert.equal(p.run(), 'READY');
  assert.deepEqual(p.counts(), [0, 0]);
  assert.equal(p.click(), 'CLICKED');
  assert.deepEqual(p.counts(), [1, 0]);
  assert.equal(p.run(), 'CLICKED');
  assert.deepEqual(p.counts(), [1, 0]);
});
test('respects the schools Angular title gate even if HTML disabled is absent', () => {
  const p = page({ title: '请先填写完整信息' });
  p.run(); p.settle();
  assert.equal(p.run(), 'WAIT');
  assert.deepEqual(p.counts(), [0, 0]);
  p.button.title = '';
  assert.equal(p.run(), 'READY');
  assert.equal(p.click(), 'CLICKED');
  assert.equal(p.click(), 'CLICKED');
  assert.deepEqual(p.counts(), [1, 0]);
});
test('fills the username in the password form and preserves unicode and quotes', () => {
  const p = page(); p.run();
  assert.equal(p.user.value, 'fixture-student');
  assert.equal(p.pass.value, "fixture'password\\中文");
  assert.equal(p.decoyUser.value, 'other-form');
});
test('never bypasses the official password encryption with native form.submit', () => {
  const p = page({ buttonPresent: false }); p.run(); p.settle();
  assert.equal(p.run(), 'WAIT');
  assert.deepEqual(p.counts(), [0, 0]);
});
test('refuses to fill credentials after navigation outside the exact HTTPS SSO login', () => {
  for (const url of ['http://sso.yzu.edu.cn/login', 'https://sso.yzu.edu.cn.evil.example/login', 'https://sso.yzu.edu.cn:9000/login', 'https://sso.yzu.edu.cn/other']) {
    const p = page({ url });
    assert.equal(p.run(), 'UNTRUSTED');
    assert.equal(p.user.value, ''); assert.equal(p.pass.value, '');
    assert.deepEqual(p.counts(), [0, 0]);
  }
});
test('returns fixed challenge and rejection codes without exporting page text', () => {
  const p = page();
  const visible = textContent => ({ textContent, getClientRects: () => [{}] });
  p.document.querySelectorAll = s => s.startsWith('app-verification') ? [visible('fixture captcha')] : [];
  assert.equal(vm.runInContext(literalScript('inspect'), p.context), 'CAPTCHA');
  p.document.querySelectorAll = s => s.startsWith('.error-msg') ? [visible('fixture-student password rejected')] : [];
  assert.equal(vm.runInContext(literalScript('inspect'), p.context), 'ERROR');
  p.document.querySelectorAll = s => s.startsWith('.error-msg') ? [visible('请先完成验证后再登录')] : [];
  assert.equal(vm.runInContext(literalScript('inspect'), p.context), 'CAPTCHA');
  p.document.querySelectorAll = () => [];
  assert.equal(vm.runInContext(literalScript('inspect'), p.context), 'WAIT');
});
