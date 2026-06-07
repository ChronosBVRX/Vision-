const { chromium } = require('playwright');

let browser = null;
let pagePool = [];
const POOL_MAX = 5;

async function getBrowser() {
  if (!browser || !browser.isConnected()) {
    browser = await chromium.launch({
      headless: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-blink-features=AutomationControlled',
        '--disable-web-security',
        '--disable-gpu',
        '--no-first-run',
        '--no-default-browser-check'
      ]
    });
  }
  return browser;
}

async function acquirePage() {
  if (pagePool.length > 0) {
    const entry = pagePool.pop();
    try {
      await entry.page.evaluate('1');
      return entry;
    } catch (e) {
      try { await entry.page.close(); } catch (e2) {}
      try { await entry.context.close(); } catch (e2) {}
    }
  }

  const b = await getBrowser();
  const context = await b.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 720 }
  });

  await context.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
  });

  const page = await context.newPage();

  const entry = { page, context, release: () => {
    if (pagePool.length < POOL_MAX) {
      pagePool.push(entry);
    } else {
      try { page.close(); } catch (e) {}
      try { context.close(); } catch (e) {}
    }
  }};

  return entry;
}

async function drainPool() {
  for (const entry of pagePool) {
    try { await entry.page.close(); } catch (e) {}
    try { await entry.context.close(); } catch (e) {}
  }
  pagePool = [];
  if (browser) {
    try { await browser.close(); } catch (e) {}
    browser = null;
  }
}

process.on('exit', () => { drainPool(); });
process.on('SIGINT', () => { drainPool(); process.exit(); });
process.on('SIGTERM', () => { drainPool(); process.exit(); });

module.exports = { acquirePage, drainPool };
