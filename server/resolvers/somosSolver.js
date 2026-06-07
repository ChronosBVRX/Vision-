const axios = require('axios');

const CAPSOLVER_API_URL = 'https://api.capsolver.com/v1';
const SITE_KEY = '0x4AAAAAACxq6ifgnDM7TOQE';
const PAGE_URL = null; // set per-call
const API_TOKEN = '248b6f1fbcbcbccd683fe6ae7030211a';

function getCapsolverKey() {
  return process.env.CAPSOLVER_API_KEY || '';
}

async function solveTurnstileToken(pageUrl) {
  const apiKey = getCapsolverKey();
  if (!apiKey) {
    console.log('[SomosSolver] ⚠️ No CAPSOLVER_API_KEY configurada. No se puede resolver Turnstile.');
    return null;
  }
  try {
    console.log('[SomosSolver] Solicitando token Turnstile via CapSolver...');
    const taskResp = await axios.post(`${CAPSOLVER_API_URL}/createTask`, {
      clientKey: apiKey,
      task: {
        type: 'AntiTurnstileTaskProxyLess',
        websiteURL: pageUrl,
        websiteKey: SITE_KEY
      }
    }, { timeout: 15000 });
    const taskId = taskResp.data.taskId;
    if (!taskId) {
      console.log('[SomosSolver] ❌ No se obtuvo taskId de CapSolver');
      return null;
    }
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000));
      const resultResp = await axios.post(`${CAPSOLVER_API_URL}/getTaskResult`, {
        clientKey: apiKey,
        taskId
      }, { timeout: 10000 });
      const data = resultResp.data;
      if (data.status === 'ready') {
        console.log('[SomosSolver] ✅ Token Turnstile obtenido');
        return data.solution.token;
      }
      if (data.status === 'failed') {
        console.log('[SomosSolver] ❌ CapSolver falló:', data.errorDescription || 'desconocido');
        return null;
      }
    }
    console.log('[SomosSolver] ⏰ Timeout esperando solución de CapSolver');
    return null;
  } catch (e) {
    console.log('[SomosSolver] ❌ Error CapSolver:', e.message.slice(0, 80));
    return null;
  }
}

async function getVideoLinksFromApi(turnstileToken, movieId) {
  if (!turnstileToken) return [];
  try {
    const resp = await axios.post(
      `https://somosmovies.org/api/services/recaptcha/?token=${API_TOKEN}`,
      { response: turnstileToken },
      {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          'Referer': 'https://somosmovies.org/',
          'X-Requested-With': 'XMLHttpRequest'
        },
        timeout: 15000
      }
    );
    if (resp.data && resp.data.data) {
      return extractIframes(resp.data.data);
    }
    if (resp.data && resp.data.success && resp.data.links) {
      return extractIframes(resp.data.links);
    }
    return [];
  } catch (e) {
    console.log(`[SomosSolver] Error llamando API recaptcha: ${e.message.slice(0, 80)}`);
    return [];
  }
}

function extractIframes(data) {
  if (!data) return [];
  const results = [];
  const str = typeof data === 'string' ? data : JSON.stringify(data);
  const iframeRegex = /<iframe[^>]*src=["']([^"']+)["'][^>]*>/gi;
  let match;
  while ((match = iframeRegex.exec(str)) !== null) {
    if (match[1].startsWith('http')) results.push(match[1]);
  }
  const urlRegex = /https?:\/\/[^\s"']+\.(?:m3u8|mp4)[^\s"']*/gi;
  while ((match = urlRegex.exec(str)) !== null) {
    results.push(match[0]);
  }
  return results;
}

async function extractStreamsWithBrowser(pageUrl) {
  const { fetchHtmlWithBrowser } = require('./browserFetcher');
  console.log(`[SomosSolver] 🔍 Abriendo ${pageUrl} con Playwright para extraer streams...`);
  try {
    const { getPage, releasePage } = require('./browserPool');
    const page = await getPage();
    const streams = [];
    const foundUrls = new Set();
    await page.route('**/*', (route) => {
      const reqUrl = route.request().url();
      if (reqUrl.includes('.m3u8') || reqUrl.includes('.mp4')) {
        if (!foundUrls.has(reqUrl)) {
          foundUrls.add(reqUrl);
          streams.push(reqUrl);
        }
      }
      if (reqUrl.includes('acscdn') || reqUrl.includes('doubleclick') || reqUrl.includes('google-analytics') || reqUrl.includes('popads')) {
        return route.abort();
      }
      route.continue();
    });
    await page.goto(pageUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(3000);
    const btn = await page.$('button[data-target="#links"]');
    if (btn) {
      await btn.click();
      await page.waitForTimeout(2000);
    }
    const turnstileFrame = await page.$('iframe[src*="challenges.cloudflare.com/turnstile"]');
    if (turnstileFrame) {
      console.log('[SomosSolver] ⛔ Turnstile detectado en la página. Se requiere CapSolver para resolver.');
      const token = await solveTurnstileToken(pageUrl);
      if (token) {
        const apiLinks = await getVideoLinksFromApi(token, '');
        apiLinks.forEach(u => {
          if (!foundUrls.has(u)) { foundUrls.add(u); streams.push(u); }
        });
      }
    }
    await page.waitForTimeout(3000);
    const links = await page.evaluate(() => {
      const res = [];
      document.querySelectorAll('iframe').forEach(f => {
        const src = f.src || f.getAttribute('data-src');
        if (src && src.startsWith('http')) res.push(src);
      });
      document.querySelectorAll('a[href*="http"]').forEach(a => {
        const href = a.href;
        if (href.includes('.m3u8') || href.includes('.mp4')) res.push(href);
      });
      document.querySelectorAll('[data-url]').forEach(el => {
        const url = el.getAttribute('data-url');
        if (url && url.startsWith('http')) res.push(url);
      });
      return res;
    });
    links.forEach(u => {
      if (!foundUrls.has(u)) { foundUrls.add(u); streams.push(u); }
    });
    releasePage(page);
    return streams;
  } catch (e) {
    console.log(`[SomosSolver] Error browser: ${e.message.slice(0, 80)}`);
    return [];
  }
}

module.exports = { solveTurnstileToken, getVideoLinksFromApi, extractStreamsWithBrowser, extractIframes };
