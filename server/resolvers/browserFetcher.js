const { chromium } = require('playwright');

/**
 * Fetches the HTML content of a URL using a headless Playwright browser to bypass bot/Cloudflare protection.
 * @param {string} url - The URL to fetch.
 * @param {Object} options - Custom options (timeout, wait for selector, etc.)
 * @returns {Promise<string>} The HTML content of the page.
 */
async function fetchHtmlWithBrowser(url, options = {}) {
  const timeout = options.timeout || 20000;
  console.log(`[BrowserFetcher] 🌐 Cargando página con Playwright para evadir antibot: ${url}`);
  
  let browser = null;
  let context = null;
  let page = null;
  
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
    
    context = await browser.newContext({
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
      locale: 'es-MX,es;q=0.9,en;q=0.8',
      viewport: { width: 1280, height: 800 }
    });
    
    // Evasion init script: hide navigator.webdriver
    await context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', {
        get: () => undefined
      });
    });
    
    page = await context.newPage();
    
    // Add ad blocker to speed up loading
    await page.route('**/*', async (route) => {
      const reqUrl = route.request().url().toLowerCase();
      const resourceType = route.request().resourceType();
      
      const isAdOrResource = 
        reqUrl.includes('popads') || 
        reqUrl.includes('adsterra') || 
        reqUrl.includes('onclick') || 
        reqUrl.includes('propeller') || 
        reqUrl.includes('google-analytics') || 
        reqUrl.includes('histats') || 
        reqUrl.includes('doubleclick') ||
        resourceType === 'image' && !reqUrl.includes('logo') && !reqUrl.includes('poster') ||
        resourceType === 'font' ||
        resourceType === 'media';
        
      if (isAdOrResource) {
        try { await route.abort(); } catch (e) {}
      } else {
        try { await route.continue(); } catch (e) {}
      }
    });

    const response = await page.goto(url, {
      waitUntil: 'domcontentloaded',
      timeout: timeout
    });

    if (!response) {
      throw new Error('No se recibió respuesta del servidor.');
    }

    // Wait a brief moment for the page scripts to run (e.g. to solve Cloudflare challenge)
    await new Promise(r => setTimeout(r, 2000));

    // Cloudflare check: wait if title indicates challenge
    let title = await page.title();
    let attempts = 0;
    while ((title.includes('Just a moment') || title.includes('Please wait') || title.toLowerCase().includes('cloudflare') || title.includes('Atención')) && attempts < 5) {
      console.log(`[BrowserFetcher] ⏳ Reto detectado ("${title}"). Esperando solución... (intento ${attempts + 1}/5)`);
      await new Promise(r => setTimeout(r, 2000));
      title = await page.title();
      attempts++;
    }

    const html = await page.content();
    console.log(`[BrowserFetcher] ✅ HTML cargado exitosamente. Longitud: ${html.length} bytes.`);
    return html;
    
  } catch (err) {
    console.error(`[BrowserFetcher] ❌ Error cargando URL con Playwright (${url}):`, err.message);
    throw err;
  } finally {
    if (page) { try { await page.close(); } catch (_) {} }
    if (context) { try { await context.close(); } catch (_) {} }
    if (browser) { try { await browser.close(); } catch (_) {} }
  }
}

module.exports = {
  fetchHtmlWithBrowser
};
