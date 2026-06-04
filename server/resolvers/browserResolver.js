const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

/**
 * Reads whitelisted domains from environment variable or manually from .env file.
 * @returns {Array<string>} Whitelisted domains.
 */
function getAuthorizedDomains() {
  let domainsStr = process.env.AUTHORIZED_VIDEO_DOMAINS;
  if (!domainsStr) {
    try {
      const envPath = path.join(__dirname, '../../.env');
      if (fs.existsSync(envPath)) {
        const envContent = fs.readFileSync(envPath, 'utf8');
        const match = envContent.match(/^AUTHORIZED_VIDEO_DOMAINS=(.+)$/m);
        if (match && match[1]) {
          domainsStr = match[1].trim();
        }
      }
    } catch (e) {}
  }
  
  if (!domainsStr) return [];
  return domainsStr.split(',').map(d => d.trim().toLowerCase()).filter(Boolean);
}

/**
 * Checks if the hostname of a URL is whitelisted.
 * @param {string} url - The URL to check.
 * @returns {boolean} True if whitelisted.
 */
function isAuthorized(url) {
  try {
    const hostname = new URL(url).hostname.toLowerCase();
    const authorized = getAuthorizedDomains();
    
    // Check if hostname matches any authorized domain exactly or as a subdomain
    return authorized.some(domain => hostname === domain || hostname.endsWith('.' + domain));
  } catch (e) {
    return false;
  }
}

/**
 * Sniffs video stream URLs by navigating to an authorized embed page in Playwright.
 * @param {string} targetUrl - The embed URL to sniff.
 * @returns {Promise<Object>} Object containing resolution results: { success: boolean, url: string, headers: Object, reason: string }
 */
async function sniffAuthorizedEmbed(targetUrl) {
  if (!isAuthorized(targetUrl)) {
    console.log(`[BrowserResolver] ❌ Dominio no autorizado para: ${targetUrl}`);
    return { success: false, reason: 'NOT_AUTHORIZED_DOMAIN' };
  }

  console.log(`[BrowserResolver] 🌐 Iniciando Playwright para: ${targetUrl}`);
  let browser = null;
  let detectedUrl = null;
  let detectedHeaders = {};

  try {
    browser = await chromium.launch({
      headless: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-blink-features=AutomationControlled',
        '--disable-web-security'
      ]
    });
    
    const context = await browser.newContext({
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    });
    
    // Evasion init script: hide navigator.webdriver
    await context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', {
        get: () => undefined
      });
    });
    
    const page = await context.newPage();

    // Intercept requests to capture the stream URL
    const detectedStreams = []; // Collect multiple candidates, pick best later

    await page.route('**/*', async (route) => {
      const request = route.request();
      const url = request.url();
      const lowerUrl = url.toLowerCase();
      const resourceType = request.resourceType();

      // Check if it's a video file or stream playlist
      if (
        lowerUrl.includes('.m3u8') || 
        lowerUrl.includes('.mpd') ||
        (lowerUrl.includes('.mp4') && !lowerUrl.includes('ads') && !lowerUrl.includes('loader'))
      ) {
        // --- Ad/Preroll filter: block known ad CDN and brand/betting ads ---
        const isAd =
          lowerUrl.includes('preroll') ||
          lowerUrl.includes('postroll') ||
          lowerUrl.includes('midroll') ||
          lowerUrl.includes('pinup') ||
          lowerUrl.includes('kaiserscene') ||
          lowerUrl.includes('1xbet') ||
          lowerUrl.includes('ads.') ||
          lowerUrl.includes('/ad/') ||
          lowerUrl.includes('/ads/') ||
          lowerUrl.includes('advert') ||
          lowerUrl.includes('doubleclick') ||
          lowerUrl.includes('googlesyndication') ||
          lowerUrl.includes('imasdk') ||
          lowerUrl.includes('googleads') ||
          lowerUrl.includes('advertisement') ||
          url.includes('amd-cdn-') ||
          /ffb7df5a878b59e42e257c042f54bed2/.test(url) ||
          /\bpreroll\b|\bpostroll\b|\bmidroll\b/.test(lowerUrl);

        if (!isAd) {
          console.log(`[BrowserResolver] 🎥 Stream detectado en red: ${url}`);
          detectedStreams.push({ url, headers: request.headers(), isHls: lowerUrl.includes('.m3u8') });
          if (!detectedUrl || (!detectedUrl.toLowerCase().includes('.m3u8') && lowerUrl.includes('.m3u8'))) {
            detectedUrl = url;
            detectedHeaders = request.headers();
          } else if (!detectedUrl) {
            detectedUrl = url;
            detectedHeaders = request.headers();
          }
        } else {
          console.log(`[BrowserResolver] 🚫 Anuncio ignorado: ${url}`);
        }
      }

      // Ad blocker to speed up page load — also block known ad CDN
      const isAdRequest = 
        lowerUrl.includes('popads') || 
        lowerUrl.includes('onclick') || 
        lowerUrl.includes('adsterra') || 
        lowerUrl.includes('acscdn') || 
        lowerUrl.includes('propeller') || 
        lowerUrl.includes('doubleclick') || 
        lowerUrl.includes('google-analytics') || 
        lowerUrl.includes('histats') || 
        lowerUrl.includes('pagead') || 
        lowerUrl.includes('adsense') ||
        url.includes('amd-cdn-') ||
        /ffb7df5a878b59e42e257c042f54bed2/.test(url) ||
        resourceType === 'image' && !lowerUrl.includes('logo') ||
        resourceType === 'font';

      if (isAdRequest) {
        try { await route.abort(); } catch (e) {}
      } else {
        try { await route.continue(); } catch (e) {}
      }
    });

    // Load page, wait for initial commit
    try {
      await page.goto(targetUrl, { waitUntil: 'commit', timeout: 10000 });
    } catch (e) {
      console.log(`[BrowserResolver] Navegación rápida finalizada para ${targetUrl}`);
    }

    // Wait 2s for player JS to initialize, then click multiple times to bypass transparent ad overlays
    await new Promise(r => setTimeout(r, 2000));
    try {
      const viewport = page.viewportSize() || { width: 640, height: 480 };
      for (let i = 0; i < 3; i++) {
        await page.mouse.click(viewport.width / 2, viewport.height / 2);
        console.log(`[BrowserResolver] 🖱️ Clic en el player (intento ${i+1})`);
        await new Promise(r => setTimeout(r, 800));
      }
    } catch (e) {}

    // Wait up to 25s: prefer HLS, fallback to last mp4 after 12s
    const start = Date.now();
    while (Date.now() - start < 25000) {
      const hlsStream = detectedStreams.find(s => s.isHls);
      if (hlsStream) {
        detectedUrl = hlsStream.url;
        detectedHeaders = hlsStream.headers;
        console.log(`[BrowserResolver] ✅ HLS seleccionado: ${detectedUrl}`);
        break;
      }
      if (detectedStreams.length > 0 && Date.now() - start > 12000) {
        // Pick the LAST detected mp4 (ads come first, movie comes later)
        const best = detectedStreams[detectedStreams.length - 1];
        detectedUrl = best.url;
        detectedHeaders = best.headers;
        break;
      }
      await new Promise(r => setTimeout(r, 500));
    }

  } catch (err) {
    console.error(`[BrowserResolver] Error en proceso de Playwright:`, err.message);
  } finally {
    if (browser) {
      try {
        await browser.close();
      } catch (e) {}
    }
  }

  if (detectedUrl) {
    return {
      success: true,
      url: detectedUrl,
      headers: detectedHeaders
    };
  }
  
  return { success: false, reason: 'TIMEOUT' };
}

module.exports = {
  sniffAuthorizedEmbed,
  isAuthorized
};
