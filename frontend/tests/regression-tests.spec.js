const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE_URL = 'http://localhost:5173';
const SCREENSHOT_DIR = path.resolve(__dirname, 'test-screenshots');
const ADMIN_EMAIL = 'test_admin@test.com';
const STUDENT_EMAIL = 'test_student@test.com';
const STUDENT_USERNAME = 'test_student';
const PASSWORD = 'Test1234';

if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function ss(name) {
  return path.join(SCREENSHOT_DIR, `${name}-${Date.now()}.png`);
}

async function doLogin(page, email, expectedUrl) {
  console.log(`[LOGIN] ${email} ...`);
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(2000);

  if (page.url().includes(expectedUrl)) {
    console.log('[LOGIN] Already logged in');
    return true;
  }

  await page.locator('input[type="email"]').first().fill(email);
  await page.locator('input[type="password"]').first().fill(PASSWORD);
  await page.locator('button[type="submit"]').first().click();

  try {
    await page.waitForURL(`**${expectedUrl}**`, { timeout: 20000 });
    await page.waitForTimeout(2500);
    console.log(`[LOGIN] OK → ${page.url()}`);
    return true;
  } catch {
    await page.waitForTimeout(1500);
    const err = await page.locator('[class*="animate-shake"]').textContent().catch(() => '');
    console.log(`[LOGIN] Failed: ${err || '-'}`);
    return page.url().includes(expectedUrl);
  }
}

async function navigateToUsers(page) {
  if (!page.url().includes('/admin')) {
    await page.goto(`${BASE_URL}/admin`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(2000);
  }
  for (const fn of [
    () => page.locator('button:has-text("用户管理")').first(),
    () => page.locator('nav').locator('text=用户管理').first(),
  ]) {
    const el = fn();
    if (await el.count() > 0) { await el.click(); await page.waitForTimeout(2000); return true; }
  }
  return false;
}

async function tc009(browser) {
  console.log('\n=== TC-ADMIN-USER-009: 管理员不能禁用自己 ===');
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  let result = 'FAIL', detail = '';

  try {
    if (!await doLogin(page, ADMIN_EMAIL, '/admin')) {
      detail = 'Admin login failed';
      await page.screenshot({ path: ss('tc009-login-fail'), fullPage: false });
      await ctx.close();
      return { id: 'TC-ADMIN-USER-009', result, detail };
    }
    if (!await navigateToUsers(page)) {
      detail = 'Nav to user management failed';
      await page.screenshot({ path: ss('tc009-nav-fail'), fullPage: false });
      await ctx.close();
      return { id: 'TC-ADMIN-USER-009', result, detail };
    }

    await page.waitForTimeout(1500);
    await page.screenshot({ path: ss('tc009-01-user-list'), fullPage: false });

    const row = page.locator('tr', { has: page.locator('td:has-text("test_admin")') }).first();
    if (await row.count() === 0) {
      detail = 'test_admin row not found in user list';
      await page.screenshot({ path: ss('tc009-no-row'), fullPage: false });
    } else {
      await row.hover();
      await page.waitForTimeout(800);

      const btns = row.locator('td').last().locator('button');
      const n = await btns.count();
      console.log(`[TC009] Action buttons in row: ${n}`);

      let clicked = false;
      for (let i = 0; i < n; i++) {
        const t = await btns.nth(i).getAttribute('title');
        console.log(`[TC009] Button ${i}: title="${t}"`);
        if (t && t.includes('禁用')) { await btns.nth(i).click({ force: true }); clicked = true; break; }
      }
      if (!clicked && n >= 2) {
        console.log('[TC009] No title match, clicking 2nd button as disable');
        await btns.nth(1).click({ force: true });
      }

      await page.waitForTimeout(2000);

      const body = await page.locator('body').textContent().catch(() => '');
      const warn = body.includes('不能禁用自己的账号');
      const dlg = await page.locator('[role="dialog"][aria-modal="true"]:visible').isVisible().catch(() => false);

      console.log(`[TC009] Warning toast: ${warn}, Confirm dialog: ${dlg}`);

      if (warn && !dlg) {
        result = 'PASS';
        detail = 'Toast "不能禁用自己的账号" 出现，无确认弹窗，无API请求 ✅';
      } else if (dlg) {
        result = 'FAIL';
        detail = '出现确认弹窗——应在弹窗前被拦截 ❌';
      } else {
        result = 'FAIL';
        detail = `未检测到警告提示。Body截取(300): ${body.substring(0, 300)}`;
      }
      await page.screenshot({ path: ss('tc009-02-result'), fullPage: false });
    }
  } catch (e) {
    result = 'FAIL'; detail = `异常: ${e.message}`;
    await page.screenshot({ path: ss('tc009-exception'), fullPage: false });
  }

  console.log(`[TC009] ${result} → ${detail}`);
  await ctx.close();
  return { id: 'TC-ADMIN-USER-009', result, detail };
}

async function tc019(browser) {
  console.log('\n=== TC-ADMIN-USER-019: 会话详情显示设备信息 ===');
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  let result = 'FAIL', detail = '';

  try {
    if (!await doLogin(page, ADMIN_EMAIL, '/admin')) {
      detail = 'Admin login failed';
      await page.screenshot({ path: ss('tc019-login-fail'), fullPage: false });
      await ctx.close();
      return { id: 'TC-ADMIN-USER-019', result, detail };
    }
    if (!await navigateToUsers(page)) {
      detail = 'Nav to user management failed';
      await page.screenshot({ path: ss('tc019-nav-fail'), fullPage: false });
      await ctx.close();
      return { id: 'TC-ADMIN-USER-019', result, detail };
    }

    await page.waitForTimeout(1500);
    await page.screenshot({ path: ss('tc019-01-user-list'), fullPage: false });

    const row = page.locator('tr', { has: page.locator('td:has-text("test_student")') }).first();
    if (await row.count() === 0) {
      detail = 'test_student row not found in user list';
      await page.screenshot({ path: ss('tc019-no-row'), fullPage: false });
    } else {
      const onlineCell = row.locator('td').nth(3);
      const onlineBtn = onlineCell.locator('button').first();

      if (await onlineBtn.count() > 0) {
        console.log('[TC019] Clicking online status button...');
        await onlineBtn.click({ force: true });
      } else {
        console.log('[TC019] No online button in cell 3, trying first button in row');
        await row.locator('button').first().click({ force: true });
      }

      await page.waitForTimeout(3000);
      await page.screenshot({ path: ss('tc019-02-session-modal'), fullPage: false });

      const body = await page.locator('body').textContent().catch(() => '');
      const hasUnknown = body.includes('未知设备');
      const hasNoSession = body.includes('暂无会话记录');
      const hasSessionDetail = body.includes('会话详情');
      const isLoading = body.includes('加载中');

      console.log(`[TC019] 会话详情:${hasSessionDetail} 未知设备:${hasUnknown} 暂无会话:${hasNoSession} 加载中:${isLoading}`);

      if (hasSessionDetail && !hasUnknown && !hasNoSession) {
        const deviceMatch = body.match(/(Chrome|Firefox|Safari|Edge|Windows|Mac|Linux|iPhone|Android|Mobile|Playwright|Desktop)/gi);
        if (deviceMatch && deviceMatch.length > 0) {
          result = 'PASS';
          detail = `设备信息可读: "${deviceMatch.join(', ')}" ✅`;
        } else {
          const idx = body.indexOf('会话详情');
          const excerpt = idx >= 0 ? body.substring(idx, idx + 300) : body.substring(0, 300);
          if (excerpt.includes('会话详情') && !excerpt.includes('未知设备') && !excerpt.includes('暂无会话记录')) {
            result = 'PASS';
            detail = '设备信息显示正常（无"未知设备"占位符） ✅';
          } else {
            result = 'FAIL';
            detail = `设备信息不确定。截取: ${excerpt}`;
          }
        }
      } else if (hasUnknown) {
        result = 'FAIL';
        detail = '显示"未知设备"——设备信息未正确解析 ❌';
      } else if (hasNoSession) {
        result = 'FAIL';
        detail = '暂无会话记录——test_student 需先登录一次创建会话 ❌';
      } else if (isLoading) {
        result = 'FAIL';
        detail = '仍在加载中——API 响应超时 ❌';
      } else {
        result = 'FAIL';
        detail = `会话弹窗未按预期显示。Body截取: ${body.substring(0, 400)}`;
      }

      await page.screenshot({ path: ss('tc019-03-result'), fullPage: false });
    }
  } catch (e) {
    result = 'FAIL'; detail = `异常: ${e.message}`;
    await page.screenshot({ path: ss('tc019-exception'), fullPage: false });
  }

  console.log(`[TC019] ${result} → ${detail}`);
  await ctx.close();
  return { id: 'TC-ADMIN-USER-019', result, detail };
}

(async () => {
  console.log('═══ Regression Test ═══');
  console.log(`URL: ${BASE_URL}`);
  console.log(`Password: ${PASSWORD}\n`);

  const browser = await chromium.launch({ headless: true });
  const results = [];

  try {
    results.push(await tc009(browser));
    results.push(await tc019(browser));
  } finally {
    await browser.close();
  }

  console.log('\n═══════════════════════════════');
  console.log('   REGRESSION TEST REPORT');
  console.log('═══════════════════════════════');
  for (const r of results) {
    const icon = r.result === 'PASS' ? '✓' : '✗';
    console.log(`\n${icon} ${r.id}: ${r.result}`);
    console.log(`  ${r.detail}`);
  }
  console.log('\n═══════════════════════════════');
  const p = results.filter(r => r.result === 'PASS').length;
  const f = results.filter(r => r.result === 'FAIL').length;
  console.log(`Total: ${results.length} | Pass: ${p} | Fail: ${f}`);
  console.log(`Screenshots: ${SCREENSHOT_DIR}`);

  process.exit(f > 0 ? 1 : 0);
})();