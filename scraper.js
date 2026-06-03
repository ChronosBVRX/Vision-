const axios = require('axios');
const cheerio = require('cheerio');
const puppeteer = require('puppeteer');

// Predefined fallback mirrors in case search engine discovery fails
const CANDIDATE_DOMAINS = [
  "https://www.rojadirectatv.me",
  "https://www.rojadirecta.watch",
  "https://www.rojadirectatv.top",
  "https://www.rojadirecta.me",
  "https://www.tarjetarojatv.org",
  "https://www.tarjetarojatv.net",
  "https://www.rojadirecta.click"
];

/**
 * Returns a user-friendly name for the streaming server based on URL hostname or filename.
 * @param {string} url The streaming channel URL
 * @param {number} index Fallback option number
 */
function getStreamName(url, index) {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.replace('www.', '').toLowerCase();
    if (host.includes('pirlotv')) return 'Pirlo TV';
    if (host.includes('verfutbol')) return 'Ver Fútbol';
    if (host.includes('tarjetaroja')) return 'Tarjeta Roja';
    if (host.includes('sportsonline')) return 'Sports Online';
    if (host.includes('futbollibre')) return 'Fútbol Libre';
    if (host.includes('pelotalibre')) return 'Pelota Libre';
    return host.split('.')[0].toUpperCase();
  } catch (e) {
    const pathLower = url.toLowerCase();
    if (pathLower.includes('directv')) return 'DirecTV';
    if (pathLower.includes('bein')) return 'Bein Sports';
    if (pathLower.includes('tnt')) return 'TNT Sports';
    if (pathLower.includes('gol-tv') || pathLower.includes('goltv')) return 'Gol TV';
    if (pathLower.includes('espn-deportes')) return 'ESPN Deportes';
    if (pathLower.includes('espn')) return 'ESPN';
    if (pathLower.includes('fox')) return 'Fox Sports';
    if (pathLower.includes('sky')) return 'Sky Sports';
    if (pathLower.includes('tudn')) return 'TUDN';
    if (pathLower.includes('dazn')) return 'DAZN';
    return `Opción ${index}`;
  }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CONFIGURABLE ALLOW-LIST of sports streaming domains.
 * Each entry is the base pattern used to build the site: operator.
 * To update mirrors, just edit this list — no other logic needs to change.
 * ─────────────────────────────────────────────────────────────────────────────
 */
const SPORTS_ALLOWED_DOMAINS = [
  'rojadirectatv.me',
  'rojadirectatv.top',
  'rojadirecta.watch',
  'rojadirecta.me',
  'rojadirecta.click',
  'tarjetarojatv.org',
  'tarjetarojatv.net',
  'tarjetaroja.tv',
  'pirlotv.uk',
  'pirlotv.cc',
  'pirlotv.me',
  'pirlotv.us',
  'verfutbol.at',
  'sportsonline.to',
  'sportsonline.si',
  'futbollibre.net',
  'pelotalibre.net',
];

/**
 * Builds a Brave Search URL that restricts results to the allowed sports domains
 * using "site:domain1 OR site:domain2 ..." operators appended to the query term.
 *
 * @param {string} queryTerm   The base search keyword (e.g. "rojadirecta" / "pirlo tv")
 * @param {string[]} domains   The allow-listed domains to restrict to
 * @returns {string}           Full Brave Search URL
 */
function buildSiteRestrictedQuery(queryTerm, domains) {
  const siteOps = domains.map(d => `site:${d}`).join(' OR ');
  const fullQuery = `${queryTerm} (${siteOps})`;
  return `https://search.brave.com/search?q=${encodeURIComponent(fullQuery)}`;
}

/**
 * Performs a Brave Search restricted to the SPORTS_ALLOWED_DOMAINS allow-list,
 * returning only domains from that list. Falls back to CANDIDATE_DOMAINS.
 */
async function searchMirrorsOnWeb() {
  console.log(`[SportsScraper] Buscando espejos activos con búsqueda restringida a dominios permitidos...`);
  const discoveredDomains = new Set();

  // Keywords that help find the right type of page on these sites
  const searchTerms = ['rojadirecta', 'pirlo tv', 'tarjeta roja', 'ver futbol'];

  const searchUrls = searchTerms.map(term => ({
    term,
    url: buildSiteRestrictedQuery(term, SPORTS_ALLOWED_DOMAINS)
  }));

  console.log(`[SportsScraper] Queries generadas con site: restriction a ${SPORTS_ALLOWED_DOMAINS.length} dominios:`);
  searchUrls.forEach(s => console.log(`  → ${s.url}`));

  for (const item of searchUrls) {
    try {
      const response = await axios.get(item.url, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
          'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8'
        },
        timeout: 8000
      });

      const $ = cheerio.load(response.data);

      $('a').each((i, el) => {
        let href = $(el).attr('href');
        if (!href || !href.startsWith('http')) return;

        try {
          const domainUrl = new URL(href);
          const hostname = domainUrl.hostname.replace(/^www\./, '').toLowerCase();
          const fullOrigin = `${domainUrl.protocol}//${domainUrl.hostname}`;

          // Only add if it's in our allow-list (direct match or subdomain match)
          const isAllowed = SPORTS_ALLOWED_DOMAINS.some(allowed =>
            hostname === allowed || hostname.endsWith(`.${allowed}`)
          );

          if (isAllowed) {
            discoveredDomains.add(fullOrigin);
            console.log(`[SportsScraper] Dominio permitido encontrado: ${fullOrigin} (query: "${item.term}")`);
          }
        } catch (e) {
          // Invalid URL
        }
      });
    } catch (error) {
      console.error(`[SportsScraper] Error en búsqueda site-restricted para "${item.term}":`, error.message);
    }
  }

  const results = Array.from(discoveredDomains);
  console.log(`[SportsScraper] Búsqueda site-restricted finalizada. Dominios de la lista encontrados: ${results.length}`);
  return results;
}


/**
 * Scrapes the list of live/upcoming sports matches from a specific Rojadirecta domain,
 * grouping duplicate event listings into single match objects with multiple stream options.
 */
async function scrapeRojadirectaMatches(baseUrl) {
  try {
    const response = await axios.get(baseUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8'
      },
      timeout: 5000
    });

    const $ = cheerio.load(response.data);
    const rawMatches = [];

    // Find all <a> tags to locate matches and stream events
    $('a').each((idx, el) => {
      const link = $(el);
      const href = link.attr('href');
      if (!href) return;

      let url = href;
      if (url.startsWith('/')) {
        url = new URL(url, baseUrl).toString();
      } else if (!url.startsWith('http')) {
        url = baseUrl.endsWith('/') ? `${baseUrl}${url}` : `${baseUrl}/${url}`;
      }

      if (
        url === baseUrl || 
        url === `${baseUrl}/` || 
        url.includes('/contacto') || 
        url.includes('/dmca') || 
        url.includes('/anuncios') ||
        url.includes('/publicidad') ||
        url.includes('/soporte') ||
        url.includes('/politica')
      ) {
        return;
      }

      const text = link.text().replace(/\s+/g, ' ').trim();
      if (!text || text.length < 5) return;

      let time = '';
      const closestRow = link.closest('tr').length > 0 
        ? link.closest('tr') 
        : (link.closest('div.agenda-item').length > 0 ? link.closest('div.agenda-item') : link.parent());
      
      const containerText = closestRow.text().replace(/\s+/g, ' ').trim();
      
      const timeMatch = containerText.match(/(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?)/i);
      if (timeMatch) {
        time = timeMatch[1].trim();
      }

      const title = text.replace(time, '').replace(/^[|\s•\-\+]+/g, '').trim();
      
      const isMatchUrl = href.includes('/ver') || href.includes('/watch') || href.includes('/stream') || href.includes('/ir/') || href.includes('/canal') || href.includes('/evento') || href.length > 22 || url.includes('.php');
      const isMatchTitle = title.includes('vs') || title.includes(' - ') || title.includes(':') || title.includes(' v ');

      if (title && isMatchUrl && isMatchTitle && title.length > 5) {
        let sport = 'Fútbol';
        
        const imgAlt = closestRow.find('img').attr('alt');
        if (imgAlt) {
          sport = imgAlt.trim();
        } else {
          const lowerTitle = title.toLowerCase();
          if (lowerTitle.includes('nba') || lowerTitle.includes('basket') || lowerTitle.includes('baloncesto')) sport = 'Baloncesto';
          else if (lowerTitle.includes('f1') || lowerTitle.includes('formula') || lowerTitle.includes('carrera')) sport = 'Fórmula 1';
          else if (lowerTitle.includes('gp') || lowerTitle.includes('moto')) sport = 'MotoGP';
          else if (lowerTitle.includes('tenis') || lowerTitle.includes('tennis')) sport = 'Tenis';
          else if (lowerTitle.includes('ufc') || lowerTitle.includes('box') || lowerTitle.includes('combate')) sport = 'Combate';
        }

        rawMatches.push({
          title,
          time: time || '--:--',
          url,
          sport
        });
      }
    });

    // Group channels of the exact same matches together
    const groupedMap = new Map();
    rawMatches.forEach((item) => {
      const key = `${item.title.toLowerCase()}@${item.time}`;
      
      if (groupedMap.has(key)) {
        const existing = groupedMap.get(key);
        if (!existing.streams.some(s => s.url === item.url)) {
          const streamIdx = existing.streams.length + 1;
          const name = getStreamName(item.url, streamIdx);
          existing.streams.push({
            name,
            url: item.url,
            resolver: 'direct' // Direct resolver now routes through Puppeteer sniffer
          });
        }
      } else {
        const name = getStreamName(item.url, 1);
        groupedMap.set(key, {
          id: 'match_' + Math.random().toString(36).substr(2, 5),
          title: item.title,
          time: item.time,
          sport: item.sport,
          streams: [{
            name,
            url: item.url,
            resolver: 'direct'
          }]
        });
      }
    });

    const groupedMatches = Array.from(groupedMap.values());
    return groupedMatches.sort((a, b) => a.time.localeCompare(b.time));
  } catch (error) {
    throw new Error(`Failed to scrape matches from ${baseUrl}: ${error.message}`);
  }
}

/**
 * Searches across both discovered Yahoo mirrors and candidate domains 
 * in parallel to find the fastest active mirror with scheduled matches.
 */
async function discoverActiveMirror() {
  let candidates = [];
  try {
    candidates = await searchMirrorsOnWeb();
  } catch (e) {
    console.error("[SportsScraper] Fallo al buscar espejos en la web, usando candidatos predeterminados:", e.message);
  }

  const allUrls = [...new Set([...candidates, ...CANDIDATE_DOMAINS])];
  console.log(`[SportsScraper] Escaneando un total de ${allUrls.length} espejos en paralelo...`);

  return new Promise((resolve, reject) => {
    let completed = 0;
    let resolved = false;
    
    if (allUrls.length === 0) {
      return reject(new Error("No hay candidatos de Rojadirecta configurados."));
    }

    allUrls.forEach((url) => {
      scrapeRojadirectaMatches(url)
        .then((matches) => {
          if (matches && matches.length >= 3 && !resolved) {
            resolved = true;
            console.log(`[SportsScraper] ¡ESPEJO SELECCIONADO! -> ${url} (${matches.length} partidos encontrados)`);
            resolve({ activeUrl: url, matches });
          } else {
            console.log(`[SportsScraper] Candidato rechazado: ${url} (Solo ${matches ? matches.length : 0} partidos)`);
          }
        })
        .catch((err) => {
          console.log(`[SportsScraper] Candidato caido/invalido: ${url} (${err.message})`);
        })
        .finally(() => {
          completed++;
          if (completed === allUrls.length && !resolved) {
            reject(new Error("No se encontro ningun espejo de Rojadirecta activo o con partidos validos online."));
          }
        });
    });
  });
}

/**
 * Sniffs network requests on a target stream page using a headless Puppeteer browser,
 * extracting direct video URLs (.m3u8 / .mp4) like WebVideoCaster.
 * Includes ad-blocking to load pages fast.
 * @param {string} targetUrl The stream page URL (e.g. pirlotv / verfutbol)
 * @returns {Promise<string|null>} Direct video URL or null if not found
 */
async function sniffVideoUrl(targetUrl) {
  console.log(`[Sniffer] Iniciando analisis de trafico para: ${targetUrl}`);
  let browser = null;
  let detectedVideoUrl = null;
  let detectedReferer = null;

  try {
    browser = await puppeteer.launch({
      headless: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-accelerated-2d-canvas',
        '--disable-gpu',
        '--mute-audio'
      ]
    });

    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.setViewport({ width: 1280, height: 720 });
    
    // Enable request interception for Ad-blocking and media sniffing
    await page.setRequestInterception(true);

    page.on('request', (req) => {
      const url = req.url();
      const lowerUrl = url.toLowerCase();
      const resourceType = req.resourceType();

      // Ad-blocker: Abort tracker, popup scripts, and generic ad domains
      const isAd = 
        lowerUrl.includes('popads') || 
        lowerUrl.includes('onclick') || 
        lowerUrl.includes('adsterra') || 
        lowerUrl.includes('acscdn') || 
        lowerUrl.includes('propeller') || 
        lowerUrl.includes('doubleclick') || 
        lowerUrl.includes('google-analytics') || 
        lowerUrl.includes('histats') || 
        lowerUrl.includes('whos.amung.us') || 
        lowerUrl.includes('pagead') || 
        lowerUrl.includes('adsense') || 
        resourceType === 'image' && !lowerUrl.includes('logo') || // Block ads loading images
        resourceType === 'font';

      if (isAd) {
        req.abort();
        return;
      }

      // Sniff outgoing media request URLs (.m3u8, .mp4)
      if (
        lowerUrl.includes('.m3u8') || 
        (lowerUrl.includes('.mp4') && !lowerUrl.includes('ads') && !lowerUrl.includes('loader'))
      ) {
        console.log(`[Sniffer] ¡Video detectado en peticion de red! -> ${url}`);
        const headers = req.headers();
        console.log(`[Sniffer] Headers detectados:`, headers);
        detectedVideoUrl = url;
        detectedReferer = headers['referer'] || headers['Referer'] || '';
      }

      req.continue();
    });

    // Sniff response headers for video content-types
    page.on('response', (res) => {
      const url = res.url();
      const headers = res.headers();
      const contentType = headers['content-type'] || '';
      
      if (
        contentType.includes('application/x-mpegurl') || 
        contentType.includes('application/vnd.apple.mpegurl') || 
        (contentType.includes('video/mp4') && !url.includes('ads'))
      ) {
        console.log(`[Sniffer] ¡Video detectado en respuesta Content-Type! -> ${url} (${contentType})`);
        detectedVideoUrl = url;
        try {
          const reqHeaders = res.request().headers();
          detectedReferer = reqHeaders['referer'] || reqHeaders['Referer'] || '';
        } catch (e) {}
      }
    });

    // Navigate and wait for media scripts to trigger
    try {
      await page.goto(targetUrl, { 
        waitUntil: 'domcontentloaded', 
        timeout: 10000 
      });
    } catch (e) {
      // Continue even if page timeout was hit, scripts might have already loaded
      console.log(`[Sniffer] Nota: Goto completo o excedio tiempo en ${targetUrl}`);
    }

    // Wait and poll for up to 8 seconds for the scripts to decode window._econfig and fetch stream
    const startTime = Date.now();
    while (Date.now() - startTime < 8000) {
      if (detectedVideoUrl) break;
      await new Promise(resolve => setTimeout(resolve, 500));
    }

  } catch (error) {
    console.error(`[Sniffer] Error durante sniffing de ${targetUrl}:`, error.message);
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  console.log(`[Sniffer] Analisis finalizado para ${targetUrl}. Resultado: ${detectedVideoUrl ? 'EXITO' : 'FALLIDO'}`);
  return detectedVideoUrl ? { url: detectedVideoUrl, referer: detectedReferer } : null;
}

const OFFICIAL_IPTV_FALLBACKS = [
  'https://iptv-org.github.io/iptv/countries/mx.m3u',
  'https://iptv-org.github.io/iptv/countries/ar.m3u',
  'https://iptv-org.github.io/iptv/countries/co.m3u',
  'https://iptv-org.github.io/iptv/countries/cl.m3u',
  'https://iptv-org.github.io/iptv/countries/pe.m3u',
  'https://iptv-org.github.io/iptv/countries/es.m3u',
  'https://iptv-org.github.io/iptv/countries/us.m3u',
  'https://iptv-org.github.io/iptv/categories/sports.m3u',
  'https://iptv-org.github.io/iptv/categories/news.m3u',
  'https://iptv-org.github.io/iptv/categories/movies.m3u',
  'https://iptv-org.github.io/iptv/categories/music.m3u'
];

/**
 * Searches Brave Search for public IPTV playlist URLs (.m3u).
 * Uses Puppeteer to bypass rate limit blocks.
 * @returns {Promise<string[]>} Array of discovered playlist URLs
 */
async function searchBraveForIPTVPlaylists() {
  console.log("[SportsScraper] Buscando playlists de IPTV en Brave Search...");
  const queries = [
    "iptv mx m3u github",
    "iptv ar m3u github",
    "iptv co m3u github",
    "iptv cl m3u github",
    "iptv latam m3u github",
    "iptv sports m3u github"
  ];

  let browser = null;
  const discoveredUrls = new Set();

  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.setViewport({ width: 1280, height: 800 });

    for (const query of queries) {
      try {
        const searchUrl = `https://search.brave.com/search?q=${encodeURIComponent(query)}`;
        await page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 12000 });
        await new Promise(r => setTimeout(r, 1000));
        
        const html = await page.content();
        const $ = cheerio.load(html);

        $('a').each((i, el) => {
          const href = $(el).attr('href');
          if (!href) return;
          
          let cleanHref = href;
          if (cleanHref.startsWith('/')) {
            cleanHref = new URL(cleanHref, 'https://search.brave.com').toString();
          }
          if (!cleanHref.startsWith('http')) return;

          if (
            (cleanHref.includes('iptv-org.github.io/iptv/') && cleanHref.endsWith('.m3u')) ||
            (cleanHref.includes('raw.githubusercontent.com') && cleanHref.endsWith('.m3u')) ||
            (cleanHref.includes('github.com') && cleanHref.includes('/blob/') && cleanHref.endsWith('.m3u'))
          ) {
            let finalUrl = cleanHref;
            if (cleanHref.includes('github.com') && cleanHref.includes('/blob/')) {
              finalUrl = cleanHref.replace('github.com', 'raw.githubusercontent.com').replace('/blob/', '/');
            }
            discoveredUrls.add(finalUrl);
          }
        });
      } catch (err) {
        console.error(`[SportsScraper] Error en busqueda Brave de "${query}":`, err.message);
      }
    }
  } catch (error) {
    console.error("[SportsScraper] Error Puppeteer en busqueda IPTV:", error.message);
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  return Array.from(discoveredUrls);
}

/**
 * Fetches and parses discovered M3U playlists, structuring channels by country and theme.
 * @param {string[]} urls Array of M3U playlist URLs
 * @returns {Promise<object[]>} Array of parsed channel source objects
 */
async function fetchAndParseIPTVPlaylists(urls) {
  const importedSources = [];
  const importedIds = new Set();

  console.log(`[IPTVScraper] Descargando y analizando ${urls.length} listas de canales M3U...`);

  for (const url of urls) {
    try {
      const res = await axios.get(url, { timeout: 8000 });
      const m3uContent = res.data;

      let defaultCategory = 'Canales en Vivo';
      const lowerUrl = url.toLowerCase();
      if (lowerUrl.includes('/countries/mx.m3u') || lowerUrl.includes('mexico')) defaultCategory = 'Canales - México';
      else if (lowerUrl.includes('/countries/ar.m3u') || lowerUrl.includes('argentina')) defaultCategory = 'Canales - Argentina';
      else if (lowerUrl.includes('/countries/co.m3u') || lowerUrl.includes('colombia')) defaultCategory = 'Canales - Colombia';
      else if (lowerUrl.includes('/countries/cl.m3u') || lowerUrl.includes('chile')) defaultCategory = 'Canales - Chile';
      else if (lowerUrl.includes('/countries/pe.m3u') || lowerUrl.includes('peru')) defaultCategory = 'Canales - Perú';
      else if (lowerUrl.includes('/countries/es.m3u') || lowerUrl.includes('espana') || lowerUrl.includes('spain')) defaultCategory = 'Canales - España';
      else if (lowerUrl.includes('/countries/us.m3u') || lowerUrl.includes('usa') || lowerUrl.includes('unitedstates')) defaultCategory = 'Canales - Estados Unidos';
      else if (lowerUrl.includes('/categories/sports.m3u') || lowerUrl.includes('deportes')) defaultCategory = 'Canales - Deportes';
      else if (lowerUrl.includes('/categories/news.m3u') || lowerUrl.includes('noticias')) defaultCategory = 'Canales - Noticias';
      else if (lowerUrl.includes('/categories/movies.m3u') || lowerUrl.includes('movies') || lowerUrl.includes('cine')) defaultCategory = 'Canales - Cine & Series';
      else if (lowerUrl.includes('/categories/music.m3u') || lowerUrl.includes('music') || lowerUrl.includes('musica')) defaultCategory = 'Canales - Música';

      const lines = m3uContent.split(/\r?\n/);
      let currentSource = null;
      let channelsParsedFromFile = 0;

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (line.startsWith('#EXTINF:')) {
          let title = 'Canal Importado';
          const commaIndex = line.lastIndexOf(',');
          if (commaIndex !== -1) {
            title = line.substring(commaIndex + 1).trim();
          }

          let poster = 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400';
          const logoMatch = line.match(/tvg-logo="([^"]+)"/i) || line.match(/logo="([^"]+)"/i);
          if (logoMatch && logoMatch[1]) {
            poster = logoMatch[1];
          }

          let category = defaultCategory;
          const countryMatch = line.match(/tvg-country="([^"]+)"/i);
          if (countryMatch && countryMatch[1] && category === 'Canales en Vivo') {
            const countryCode = countryMatch[1].toUpperCase();
            if (countryCode === 'MX') category = 'Canales - México';
            else if (countryCode === 'AR') category = 'Canales - Argentina';
            else if (countryCode === 'CO') category = 'Canales - Colombia';
            else if (countryCode === 'CL') category = 'Canales - Chile';
            else if (countryCode === 'PE') category = 'Canales - Perú';
            else if (countryCode === 'ES') category = 'Canales - España';
            else if (countryCode === 'US') category = 'Canales - Estados Unidos';
          }

          const groupMatch = line.match(/group-title="([^"]+)"/i);
          if (groupMatch && groupMatch[1] && category === 'Canales en Vivo') {
            const groupName = groupMatch[1].toLowerCase();
            if (groupName.includes('sport') || groupName.includes('deport')) category = 'Canales - Deportes';
            else if (groupName.includes('news') || groupName.includes('notic')) category = 'Canales - Noticias';
            else if (groupName.includes('movie') || groupName.includes('cine') || groupName.includes('pelic')) category = 'Canales - Cine & Series';
            else if (groupName.includes('music') || groupName.includes('music')) category = 'Canales - Música';
          }

          const cleanTitle = title.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
          const channelId = `tv_${cleanTitle}`;

          currentSource = {
            id: channelId,
            title,
            type: 'tv',
            category,
            poster,
            description: `Canal de TV en vivo de la categoría ${category} transmitido en formato libre.`
          };
        } else if (line.startsWith('http') && currentSource) {
          currentSource.streams = [{
            name: 'Stream Directo',
            url: line,
            resolver: 'direct'
          }];

          // Max 25 channels per playlist to prevent db bloating
          if (!importedIds.has(currentSource.id) && channelsParsedFromFile < 25) {
            importedIds.add(currentSource.id);
            importedSources.push(currentSource);
            channelsParsedFromFile++;
          }
          currentSource = null;
        }
      }
      console.log(`[IPTVScraper] Lista procesada: ${url}. Importados ${channelsParsedFromFile} canales.`);
    } catch (e) {
      console.error(`[IPTVScraper] Error procesando lista ${url}:`, e.message);
    }
  }

  return importedSources;
}
const OFFICIAL_VOD_FALLBACKS = [
  'https://raw.githubusercontent.com/maurilap/lista1/main/Peliculas%20Espa%C3%B1ol%20y%20Latino.m3u',
  'https://raw.githubusercontent.com/Socket80/Peliculas/main/m3u'
];

/**
 * Searches Brave Search for public movie & series playlist URLs (.m3u).
 * Uses Puppeteer to bypass rate limit blocks.
 * @returns {Promise<string[]>} Array of discovered VOD playlist URLs
 */
/**
 * Searches Brave Search for public movie & series playlist URLs (.m3u) AND movie streaming pages.
 * Uses Puppeteer to bypass rate limit blocks.
 * @returns {Promise<object>} Object containing arrays of discovered playlists and pages
 */
async function searchBraveForMovieContent() {
  console.log("[VODScraper] Buscando playlists VOD y páginas de streaming (Pelisplus, Cuevana, etc.) en Brave Search...");
  const queries = [
    "peliculas espanol latino m3u github",
    "series espanol latino m3u github",
    "site:pelisplushd.mx/pelicula",
    "site:verpelistv.com/pelicula",
    "site:pelisyaske.com/pelicula",
    "site:pelisonline.ws/pelicula",
    "site:cuevana3.ch/pelicula",
    "site:pelisplushd.mx/serie",
    "site:cuevana3.ch/serie",
    "cuevana 3 peliculas gratis",
    "pelisplus ver online latino"
  ];

  let browser = null;
  const playlists = new Set();
  const pages = [];

  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.setViewport({ width: 1280, height: 800 });

    for (const query of queries) {
      try {
        const searchUrl = `https://search.brave.com/search?q=${encodeURIComponent(query)}`;
        await page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 12000 });
        await new Promise(r => setTimeout(r, 1000));
        
        const html = await page.content();
        const $ = cheerio.load(html);

        $('a').each((i, el) => {
          const href = $(el).attr('href');
          if (!href) return;
          
          let cleanHref = href;
          if (cleanHref.startsWith('/')) {
            cleanHref = new URL(cleanHref, 'https://search.brave.com').toString();
          }
          if (!cleanHref.startsWith('http')) return;

          const lowerHref = cleanHref.toLowerCase();

          // 1. Check if it's a playlist URL
          if (
            lowerHref.endsWith('.m3u') || 
            lowerHref.endsWith('.m3u8') ||
            (lowerHref.includes('github.com') && lowerHref.includes('/blob/') && (lowerHref.includes('m3u') || lowerHref.includes('playlist') || lowerHref.includes('pelicula') || lowerHref.includes('serie'))) ||
            (lowerHref.includes('raw.githubusercontent.com') && (lowerHref.includes('m3u') || lowerHref.includes('playlist') || lowerHref.includes('pelicula') || lowerHref.includes('serie')))
          ) {
            let finalUrl = cleanHref;
            if (cleanHref.includes('github.com') && cleanHref.includes('/blob/')) {
              finalUrl = cleanHref.replace('github.com', 'raw.githubusercontent.com').replace('/blob/', '/');
            }
            playlists.add(finalUrl);
          }
          
          // 2. Check if it's a movie page on one of the target streaming sites
          else if (
            lowerHref.includes('pelisplushd.mx') ||
            lowerHref.includes('verpelistv.com') ||
            lowerHref.includes('pelisyaske.com') ||
            lowerHref.includes('pelisonline.ws') ||
            lowerHref.includes('cuevana')
          ) {
            const isMovieOrSeriesPage = 
              lowerHref.includes('/pelicula/') || 
              lowerHref.includes('/movie/') || 
              lowerHref.includes('/serie/') || 
              lowerHref.includes('/tv/');
              
            if (isMovieOrSeriesPage) {
              const parts = cleanHref.split('/');
              const slug = parts[parts.length - 1] || parts[parts.length - 2] || '';
              if (slug && slug.length > 2 && !slug.includes('?') && !slug.includes('category') && !slug.includes('genero')) {
                const title = slug
                  .split('-')
                  .map(word => word.charAt(0).toUpperCase() + word.slice(1))
                  .join(' ');
                  
                const isSeries = lowerHref.includes('/serie/') || lowerHref.includes('/tv/');
                
                let siteName = 'PelisPlus';
                if (lowerHref.includes('cuevana')) siteName = 'Cuevana';
                else if (lowerHref.includes('pelisonline')) siteName = 'PelisOnline';
                else if (lowerHref.includes('verpelistv')) siteName = 'VerPelisTV';
                else if (lowerHref.includes('pelisyaske')) siteName = 'PelisYaske';

                pages.push({
                  url: cleanHref,
                  title,
                  isSeries,
                  siteName
                });
              }
            }
          }
        });
      } catch (err) {
        console.error(`[VODScraper] Error en búsqueda Brave para VOD "${query}":`, err.message);
      }
    }
  } catch (error) {
    console.error("[VODScraper] Error Puppeteer en búsqueda VOD:", error.message);
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  return {
    playlists: Array.from(playlists),
    pages: pages
  };
}

/**
 * Fetches and parses discovered M3U VOD playlists, structuring them into movies and series.
 * @param {string[]} urls Array of VOD playlist URLs
 * @returns {Promise<object[]>} Array of parsed movie and series source objects
 */
async function fetchAndParseMoviePlaylists(urls) {
  const importedSourcesMap = new Map();

  console.log(`[VODScraper] Descargando y analizando ${urls.length} listas VOD...`);

  for (const url of urls) {
    try {
      const res = await axios.get(url, { timeout: 8000 });
      const m3uContent = res.data;

      const lines = m3uContent.split(/\r?\n/);
      let currentSource = null;
      let itemsParsedFromFile = 0;

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (line.startsWith('#EXTINF:')) {
          let title = 'Contenido VOD';
          const commaIndex = line.lastIndexOf(',');
          if (commaIndex !== -1) {
            title = line.substring(commaIndex + 1).trim();
          }

          // Clean title (remove prefixes like ESTRENOS, VOD, etc.)
          let cleanTitle = title.replace(/^(estrenos|vod free|vod|pelicula|peliculas|cine|movie|movies)\s*:\s*/i, '').trim();

          let poster = 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400';
          const logoMatch = line.match(/tvg-logo="([^"]+)"/i) || line.match(/logo="([^"]+)"/i);
          if (logoMatch && logoMatch[1]) {
            poster = logoMatch[1];
          }

          let groupTitle = '';
          const groupMatch = line.match(/group-title="([^"]+)"/i);
          if (groupMatch && groupMatch[1]) {
            groupTitle = groupMatch[1].trim();
          }

          // Determine type: movie or series
          const seriesPattern = /(s\d+e\d+|t\d+e\d+|\d+x\d+|capitulo\s*\d+|cap\.\s*\d+|ep\.\s*\d+|episodio\s*\d+|temporada\s*\d+)/i;
          const isSeries = seriesPattern.test(cleanTitle) || 
                           groupTitle.toLowerCase().includes('series') || 
                           groupTitle.toLowerCase().includes('temporada') || 
                           groupTitle.toLowerCase().includes('tvshow');

          const contentType = isSeries ? 'series' : 'movie';

          // Classify Category
          let category = isSeries ? 'Series' : 'Películas';
          if (groupTitle) {
            if (isSeries) {
              category = `Series - ${groupTitle.replace(/^(series|temporadas)\s*[-_]?\s*/i, '').trim() || 'Descubiertas'}`;
            } else {
              category = `Películas - ${groupTitle.replace(/^(peliculas|cine|movies)\s*[-_]?\s*/i, '').trim() || 'Descubiertas'}`;
            }
          } else {
            category = isSeries ? 'Series - Descubiertas' : 'Películas - Descubiertas';
          }

          const idKey = cleanTitle.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
          const channelId = `${contentType}_${idKey}`;

          currentSource = {
            id: channelId,
            title: cleanTitle,
            type: contentType,
            category,
            poster,
            description: `${contentType === 'series' ? 'Serie' : 'Película'} descubierta en la web.`
          };
        } else if (line.startsWith('http') && currentSource) {
          // Avoid importing live streams that end up in VOD lists
          const lowerUrl = line.toLowerCase();
          const looksLikeLive = lowerUrl.includes('/live/') || lowerUrl.includes('/c/') || (lowerUrl.includes('/stream/') && !lowerUrl.includes('.mp4') && !lowerUrl.includes('.mkv') && !lowerUrl.includes('.avi') && !lowerUrl.includes('movies'));

          if (!looksLikeLive && itemsParsedFromFile < 50) {
            if (importedSourcesMap.has(currentSource.id)) {
              const existing = importedSourcesMap.get(currentSource.id);
              if (!existing.streams.some(s => s.url === line)) {
                existing.streams.push({
                  name: `Servidor ${existing.streams.length + 1}`,
                  url: line,
                  resolver: 'direct'
                });
              }
            } else {
              currentSource.streams = [{
                name: 'Servidor 1',
                url: line,
                resolver: 'direct'
              }];
              importedSourcesMap.set(currentSource.id, currentSource);
              itemsParsedFromFile++;
            }
          }
          currentSource = null;
        }
      }
      console.log(`[VODScraper] Lista procesada: ${url}. Importados/Actualizados ${itemsParsedFromFile} películas/series.`);
    } catch (e) {
      console.error(`[VODScraper] Error procesando lista VOD ${url}:`, e.message);
    }
  }

  return Array.from(importedSourcesMap.values());
}

module.exports = {
  scrapeRojadirectaMatches,
  discoverActiveMirror,
  sniffVideoUrl,
};

// ================================================================
// CATALOG SCRAPER: Movies & Series from Spanish streaming sites
// ================================================================

const CATALOG_SITES = [
  {
    name: 'PelisPlus',
    movieUrls: ['https://www.pelisplushd.la/peliculas/', 'https://www.pelisplushd.la/estrenos/'],
    seriesUrls: ['https://www.pelisplushd.la/series/'],
    pageFn: (base, p) => `${base}page/${p}/`
  },
  {
    name: 'VerPelisTV',
    movieUrls: ['https://verpelistv.com/peliculas/', 'https://verpelistv.com/inicio/'],
    seriesUrls: ['https://verpelistv.com/series/'],
    pageFn: (base, p) => `${base}page/${p}/`
  },
  {
    name: 'PelisOnline',
    movieUrls: ['https://pelisonline.ws/'],
    seriesUrls: [], // PelisOnline has no series
    pageFn: (base, p) => `${base}page/${p}/`
  },
  {
    name: 'PoseidonHD',
    movieUrls: ['https://www.poseidonhd2.co/peliculas/'],
    seriesUrls: ['https://www.poseidonhd2.co/series/'],
    pageFn: (base, p) => `${base}page/${p}/`
  },
  {
    name: 'Cuevana',
    movieUrls: ['https://cuevana3x.xyz/peliculas/'],
    seriesUrls: ['https://cuevana3x.xyz/series/'],
    pageFn: (base, p) => `${base}page/${p}/`
  }
];

const GENRE_MAP = {
  'accion': 'Acción', 'action': 'Acción',
  'drama': 'Drama', 'comedy': 'Comedia', 'comedia': 'Comedia',
  'terror': 'Terror', 'horror': 'Terror',
  'sci-fi': 'Sci-Fi', 'ciencia ficcion': 'Sci-Fi', 'ciencia ficción': 'Sci-Fi',
  'romance': 'Romance', 'aventura': 'Aventura', 'adventure': 'Aventura',
  'animacion': 'Animación', 'animation': 'Animación', 'animación': 'Animación',
  'documental': 'Documental', 'documentary': 'Documental',
  'suspenso': 'Suspenso', 'thriller': 'Suspenso',
  'familia': 'Familia', 'family': 'Familia',
  'crimen': 'Crimen', 'crime': 'Crimen',
  'misterio': 'Misterio', 'mystery': 'Misterio',
  'fantasia': 'Fantasía', 'fantasy': 'Fantasía', 'fantasía': 'Fantasía',
  'historia': 'Historia', 'historical': 'Historia',
  'musical': 'Musical', 'musica': 'Musical', 'music': 'Musical',
  'guerra': 'Guerra', 'war': 'Guerra',
  'western': 'Western', 'infantil': 'Infantil', 'kids': 'Infantil'
};

function normalizeGenre(g) {
  if (!g) return null;
  const lower = g.toLowerCase().trim();
  return GENRE_MAP[lower] || (g.length > 1 ? g.charAt(0).toUpperCase() + g.slice(1) : null);
}

async function scrapeItemsFromPage(page, url, type, siteName) {
  const items = [];
  try {
    console.log(`[CatalogScraper] Navegando a ${url}...`);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await new Promise(r => setTimeout(r, 4000));

    const raw = await page.evaluate((sName, sType) => {
      const res = [];

      if (sName === 'PelisPlus') {
        const cards = document.querySelectorAll('a.Posters-link');
        cards.forEach(a => {
          const titleEl = a.querySelector('.listing-content p');
          const img = a.querySelector('img.Posters-img');
          const ratingEl = a.querySelector('.rating span');
          
          let title = titleEl ? titleEl.textContent.trim() : a.getAttribute('data-title');
          if (title && title.startsWith('VER ')) {
            title = title.replace(/^VER\s+/i, '').replace(/\s+Online\s+Gratis\s+HD$/i, '').trim();
          }
          
          let poster = img ? (img.getAttribute('src') || img.src) : null;
          if (poster && poster.startsWith('/')) {
            poster = 'https://www.pelisplushd.la' + poster;
          }
          
          let rating = ratingEl ? parseFloat(ratingEl.textContent) : null;
          
          if (title && a.href) {
            let itemType = sType;
            if (a.href.includes('/pelicula/') || a.href.includes('/movie/')) {
              itemType = 'movie';
            } else if (a.href.includes('/serie/') || a.href.includes('/tv/')) {
              itemType = 'series';
            }
            res.push({
              title,
              poster,
              sourceUrl: a.href,
              rating,
              year: null,
              genres: ['General'],
              siteName: sName,
              type: itemType
            });
          }
        });
      } else if (sName === 'VerPelisTV') {
        const cards = document.querySelectorAll('.card');
        cards.forEach(card => {
          const linkEl = card.querySelector('a.poster') || card.querySelector('a');
          if (!linkEl) return;
          
          const titleEl = card.querySelector('.item-title') || card.querySelector('.card-body p') || linkEl;
          const img = card.querySelector('img');
          
          let title = titleEl ? titleEl.textContent.trim() : '';
          title = title.replace(/HD$/i, '').trim();
          
          let poster = img ? (img.getAttribute('data-src') || img.src) : null;
          
          if (title && linkEl.href) {
            res.push({
              title,
              poster,
              sourceUrl: linkEl.href,
              rating: null,
              year: null,
              genres: ['General'],
              siteName: sName,
              type: sType
            });
          }
        });
      } else if (sName === 'PelisOnline') {
        const cards = document.querySelectorAll('.item');
        cards.forEach(item => {
          const linkEl = item.querySelector('a');
          if (!linkEl) return;
          
          const img = item.querySelector('img');
          let title = linkEl.getAttribute('title') || linkEl.textContent.trim();
          if (title && title.includes(' / ')) {
            title = title.split(' / ')[0].trim();
          }
          
          let poster = img ? img.src : null;
          
          if (title && linkEl.href) {
            res.push({
              title,
              poster,
              sourceUrl: linkEl.href,
              rating: null,
              year: null,
              genres: ['General'],
              siteName: sName,
              type: sType
            });
          }
        });
      } else if (sName === 'PoseidonHD') {
        const cards = document.querySelectorAll('.TPost');
        cards.forEach(card => {
          const linkEl = card.querySelector('a');
          if (!linkEl) return;
          
          const titleEl = card.querySelector('.Title');
          const img = card.querySelector('img');
          const ratingEl = card.querySelector('.Vote');
          const yearEl = card.querySelector('.Year') || card.querySelector('.Date');
          const genreEl = card.querySelector('.Genre');
          const descEl = card.querySelector('.Description p');
          
          const title = titleEl ? titleEl.textContent.trim() : '';
          let poster = img ? (img.getAttribute('src') || img.src) : null;
          
          if (poster && poster.includes('url=')) {
            const match = poster.match(/url=([^&]+)/);
            if (match && match[1]) {
              poster = decodeURIComponent(match[1]);
            }
          } else if (poster && poster.startsWith('/')) {
            poster = 'https://www.poseidonhd2.co' + poster;
          }
          
          const rating = ratingEl ? parseFloat(ratingEl.textContent) : null;
          const year = yearEl ? parseInt(yearEl.textContent) : null;
          const desc = descEl ? descEl.textContent.trim() : '';
          
          let genres = ['General'];
          if (genreEl) {
            const genreText = genreEl.textContent.replace('Género:', '').trim();
            if (genreText) {
              genres = genreText.split(',').map(g => g.trim());
            }
          }
          
          if (title && linkEl.href) {
            res.push({
              title,
              poster,
              sourceUrl: linkEl.href,
              rating,
              year,
              genres,
              description: desc,
              siteName: sName,
              type: sType
            });
          }
        });
      } else if (sName === 'Cuevana') {
        const anchors = document.querySelectorAll('a');
        anchors.forEach(a => {
          const detail = a.querySelector('.item-detail');
          if (!detail) return;
          
          const img = a.querySelector('img.poster');
          const yearEl = a.querySelector('.year');
          const ratingEl = a.querySelector('.card-hover-rating');
          const descEl = a.querySelector('.card-hover-info-overview p');
          
          const title = detail.textContent.trim();
          const poster = img ? (img.getAttribute('src') || img.src) : null;
          const year = yearEl ? parseInt(yearEl.textContent) : null;
          
          let rating = null;
          if (ratingEl) {
            const ratText = ratingEl.textContent.replace('☆', '').trim();
            rating = parseFloat(ratText);
          }
          
          const desc = descEl ? descEl.textContent.trim() : '';
          
          if (title && a.href) {
            res.push({
              title,
              poster,
              sourceUrl: a.href,
              rating,
              year,
              genres: ['General'],
              description: desc,
              siteName: sName,
              type: sType
            });
          }
        });
      }

      return res.slice(0, 30);
    }, siteName, type);

    for (const item of raw) {
      if (!item.title || item.title.length < 2 || !item.sourceUrl) continue;
      if (item.sourceUrl === url || !item.sourceUrl.startsWith('http')) continue;

      const genres = (item.genres || []).map(normalizeGenre).filter(Boolean);

      // Node-side URL classification:
      let finalType = item.type || type;
      const lowerUrl = item.sourceUrl.toLowerCase();
      if (lowerUrl.includes('/pelicula/') || lowerUrl.includes('/movie/')) {
        finalType = 'movie';
      } else if (lowerUrl.includes('/serie/') || lowerUrl.includes('/tv/') || lowerUrl.includes('/series/')) {
        finalType = 'series';
      }

      items.push({
        id: `catalog_${siteName.toLowerCase().replace(/\s+/g, '')}_${finalType}_${item.title.replace(/[^a-z0-9]/gi, '').toLowerCase().slice(0, 28)}_${Date.now() % 9999}`,
        title: item.title,
        type: finalType,
        poster: item.poster || null,
        year: item.year || null,
        rating: item.rating || null,
        genres: genres.length > 0 ? genres : ['General'],
        description: item.description || '',
        siteName: item.siteName,
        sourceUrl: item.sourceUrl,
        featured: false,
        streams: [
          { name: item.siteName, url: item.sourceUrl, resolver: 'iframe' }
        ]
      });
    }

    console.log(`[CatalogScraper] ${siteName} ${type}: ${items.length} items de ${url}`);
  } catch (e) {
    console.log(`[CatalogScraper] Error en ${url}: ${e.message.slice(0, 80)}`);
  }
  return items;
}

async function scrapeMovieCatalog(maxPagesPerUrl = 3) {
  console.log('[CatalogScraper] ▶ Iniciando scraping de catálogo de películas y series...');
  let browser = null;
  const allMovies = [];
  const allSeries = [];
  const seenIds = new Set();

  const addUnique = (items) => {
    for (const item of items) {
      const key = `${item.title.toLowerCase().trim()}_${item.type}`;
      if (!seenIds.has(key)) {
        seenIds.add(key);
        item.id = `catalog_${item.siteName.toLowerCase().replace(/\s+/g,'')}_${item.type}_${item.title.replace(/[^a-z0-9]/gi,'').toLowerCase().slice(0,28)}`;
        if (item.type === 'series') {
          allSeries.push(item);
        } else {
          allMovies.push(item);
        }
      }
    }
  };

  try {
    browser = await puppeteer.launch({
      headless: true,
      args: [
        '--no-sandbox', '--disable-setuid-sandbox',
        '--disable-web-security', '--disable-features=IsolateOrigins',
        '--disable-blink-features=AutomationControlled',
        '--window-size=1280,800'
      ]
    });

    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36');
    await page.setViewport({ width: 1280, height: 800 });
    await page.setExtraHTTPHeaders({ 'Accept-Language': 'es-MX,es;q=0.9,en;q=0.5' });

    for (const site of CATALOG_SITES) {
      // Scrape movies
      for (const baseUrl of site.movieUrls) {
        for (let p = 1; p <= maxPagesPerUrl; p++) {
          if (site.name === 'PelisOnline' && p > 1) break; // PelisOnline doesn't support page pagination
          
          const url = p === 1 ? baseUrl : site.pageFn(baseUrl, p);
          const items = await scrapeItemsFromPage(page, url, 'movie', site.name);
          addUnique(items);
          if (items.length < 3) break;
        }
      }

      // Scrape series
      for (const baseUrl of site.seriesUrls) {
        if (site.name === 'PelisOnline') continue; // PelisOnline has no series
        
        for (let p = 1; p <= maxPagesPerUrl; p++) {
          const url = p === 1 ? baseUrl : site.pageFn(baseUrl, p);
          const items = await scrapeItemsFromPage(page, url, 'series', site.name);
          addUnique(items);
          if (items.length < 3) break;
        }
      }
    }

  } catch (e) {
    console.error('[CatalogScraper] Error general:', e.message);
  } finally {
    if (browser) {
      try { await browser.close(); } catch (_) {}
    }
  }

  console.log(`[CatalogScraper] Completado: ${allMovies.length} peliculas, ${allSeries.length} series`);
  return { movies: allMovies, series: allSeries };
}
// ================================================================
// PLUTO TV SCRAPER: Live & On-Demand
// ================================================================
const PLUTO_REGION_IP = '189.201.128.1';
const PLUTO_CLIENT_ID = 'vision-plus-pluto-client-' + Math.random().toString(36).substring(7);

async function getPlutoBootData() {
  const d = new Date();
  const clientTime = encodeURI(d.toISOString());
  const headers = {
    'X-Forwarded-For': PLUTO_REGION_IP,
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  };
  
  const bootUrl = `https://boot.pluto.tv/v4/start?appName=web&appVersion=7.9.0-a9cca6b89aea4dc0998b92a51989d2adb9a9025d&deviceVersion=16.2.0&deviceModel=web&deviceMake=Chrome&deviceType=web&clientID=${PLUTO_CLIENT_ID}&clientModelNumber=1.0.0&channelID=5a4d3a00ad95e4718ae8d8db&serverSideAds=true&constraints=&drmCapabilities=&blockingMode=&clientTime=${clientTime}`;
  
  const resp = await axios.get(bootUrl, { headers });
  return resp.data;
}

async function scrapePlutoTVLive() {
  console.log(`[PlutoTV] Iniciando importacion de TV en vivo (Mexico)...`);
  try {
    const bootData = await getPlutoBootData();
    const headers = {
      Authorization: `Bearer ${bootData.sessionToken}`,
      'X-Forwarded-For': PLUTO_REGION_IP
    };

    const channelsResp = await axios.get(`https://service-channels.clusters.pluto.tv/v2/guide/channels?channelIds=&offset=0&limit=1000&sort=number%3Aasc`, { headers });
    const categoriesResp = await axios.get('https://service-channels.clusters.pluto.tv/v2/guide/categories', { headers });

    const channelList = channelsResp.data.data;
    const categoryList = categoriesResp.data.data;

    const importedSources = [];

    for (let c of channelList) {
      if (!c.categoryIDs || c.categoryIDs.length === 0) continue;

      const category = categoryList.find(cat => cat.id === c.categoryIDs[0]);
      const catname = category ? `Pluto TV - ${category.name}` : 'Pluto TV - Live';
      
      const poster = c.images && c.images.length > 0 ? c.images[0].url : 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400';

      const path = c.stitched.path;
      if (!path) continue;

      importedSources.push({
        id: `plutotv_${c.id}`,
        title: c.name,
        type: 'tv',
        category: catname,
        poster,
        description: c.summary || `Canal de TV en vivo de la categoria ${catname}`,
        streams: [{
          name: 'Pluto TV Stream',
          url: `pluto-tv://${path}`,
          resolver: 'direct'
        }]
      });
    }

    console.log(`[PlutoTV] Completado! Se importaron ${importedSources.length} canales en vivo.`);
    return importedSources;
  } catch (err) {
    console.error(`[PlutoTV] Error scrapeando TV en vivo: ${err.message}`);
    return [];
  }
}

async function scrapePlutoTVOnDemand() {
  console.log(`[PlutoTV] Iniciando importacion de Peliculas VOD (Mexico)...`);
  try {
    const bootData = await getPlutoBootData();
    const headers = {
      Authorization: `Bearer ${bootData.sessionToken}`,
      'X-Forwarded-For': PLUTO_REGION_IP
    };

    const categoriesResp = await axios.get('https://service-vod.clusters.pluto.tv/v4/vod/categories?includeItems=false&includeCategoryFields=iconSvg&offset=1000&page=1&sort=number%3Aasc', { headers });
    const categoriesList = categoriesResp.data.categories;

    const importedSources = [];

    let catsProcessed = 0;
    for (let cat of categoriesList) {
      if (!cat) continue;
      if (catsProcessed >= 15) break; // Límite de categorías
      
      catsProcessed++;
      const itemsResp = await axios.get(`https://service-vod.clusters.pluto.tv/v4/vod/categories/${cat._id}/items?offset=0&page=1`, { headers });
      const items = itemsResp.data.items;
      const catname = itemsResp.data.name;

      let itemsProcessed = 0;
      for (let item of items) {
        if (item.type !== 'movie') continue;
        if (itemsProcessed >= 20) break; // Límite de items por categoría
        itemsProcessed++;

        const vodItemResp = await axios.get(`https://service-vod.clusters.pluto.tv/v4/vod/items?ids=${item._id}`, { headers });
        if (!vodItemResp.data || vodItemResp.data.length === 0) continue;
        
        const vodItem = vodItemResp.data[0];
        const path = vodItem.stitched.path || (vodItem.stitched.paths && vodItem.stitched.paths.find(e => e.type === 'hls')?.path) || false;
        if (!path) continue;

        const poster = vodItem.featuredImage ? vodItem.featuredImage.path : 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400';

        importedSources.push({
          id: `plutovod_${vodItem._id}`,
          title: vodItem.name,
          type: 'movie',
          category: `Pluto TV - ${catname}`,
          poster,
          description: vodItem.description || `Pelicula On Demand de Pluto TV`,
          streams: [{
            name: 'Pluto TV Stream',
            url: `pluto-tv://${path}`,
            resolver: 'direct'
          }]
        });
      }
    }

    console.log(`[PlutoTV] Completado! Se importaron ${importedSources.length} peliculas VOD.`);
    return importedSources;
  } catch (err) {
    console.error(`[PlutoTV] Error scrapeando VOD: ${err.message}`);
    return [];
  }
}
// Merge catalog exports into the main module.exports
Object.assign(module.exports, {
  searchBraveForIPTVPlaylists,
  fetchAndParseIPTVPlaylists,
  searchBraveForMovieContent,
  fetchAndParseMoviePlaylists,
  scrapeMovieCatalog,
  CANDIDATE_DOMAINS,
  OFFICIAL_IPTV_FALLBACKS,
  OFFICIAL_VOD_FALLBACKS,
  scrapeEpisodesFromSeriesPage,
  scrapePlutoTVLive,
  scrapePlutoTVOnDemand,
  scrapeAnimeCatalog,
  scrapeAnimeEpisodesFromSeriesPage
});

/**
 * Scrapes a series page to extract seasons and episodes.
 * Returns an array of Season objects, each with an array of Episode objects.
 */
async function scrapeEpisodesFromSeriesPage(seriesUrl, siteName) {
  console.log(`[CatalogScraper] Extrayendo episodios de serie en ${siteName}: ${seriesUrl}`);
  const seasons = [];
  try {
    const axios = require('axios');
    const cheerio = require('cheerio');
    
    const response = await axios.get(seriesUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept-Language': 'es-MX,es;q=0.9,en;q=0.5'
      },
      timeout: 10000
    });
    
    const $ = cheerio.load(response.data);
    
    if (siteName === 'PelisPlus') {
      // PelisPlus uses .tab-content with #season-1, #season-2 etc or #pills-vertical-1
      $('.tab-content .tab-pane').each((i, seasonEl) => {
        const seasonId = $(seasonEl).attr('id'); // e.g. season-1 or pills-vertical-1
        let seasonNum = i + 1;
        if (seasonId) {
           const match = seasonId.match(/(\d+)/);
           if (match) seasonNum = parseInt(match[1]);
        }
        
        const episodes = [];
        $(seasonEl).find('.btn-container a, a').each((j, epEl) => {
          const epUrl = $(epEl).attr('href');
          let epTitle = $(epEl).find('.title').text().trim() || $(epEl).text().trim() || `Episodio ${j + 1}`;
          
          if (epUrl && (epUrl.includes('/episodio/') || epUrl.includes('/capitulo/'))) {
            episodes.push({
              id: `s${seasonNum}e${j + 1}`,
              title: epTitle,
              episodeNum: j + 1,
              url: epUrl.startsWith('http') ? epUrl : `https://www.pelisplushd.la${epUrl}`
            });
          }
        });
        
        if (episodes.length > 0) {
          seasons.push({
            seasonNum,
            title: `Temporada ${seasonNum}`,
            episodes
          });
        }
      });
    } else if (siteName === 'Cuevana') {
      // Cuevana uses ul.MovieList.TpRwCont with li items, usually separated by headers or grouped
      // Some versions use .TPlayerNv class for season selection.
      // A common layout: .Wdgt.AAIco-video_library with li
      $('.TpRwCont li, .MovieList li').each((i, epEl) => {
        const epUrl = $(epEl).find('a').attr('href');
        const titleEl = $(epEl).find('.Title');
        const episodeName = titleEl.text().trim();
        
        // Extract season/episode from URL or text
        const sMatch = epUrl ? epUrl.match(/-(\d+)x(\d+)$/) : null;
        const seasonNum = sMatch ? parseInt(sMatch[1]) : 1;
        const epNum = sMatch ? parseInt(sMatch[2]) : (i + 1);
        
        if (epUrl && (epUrl.includes('/episodio/') || epUrl.includes('x'))) {
          let season = seasons.find(s => s.seasonNum === seasonNum);
          if (!season) {
            season = { seasonNum, title: `Temporada ${seasonNum}`, episodes: [] };
            seasons.push(season);
          }
          
          season.episodes.push({
            id: `s${seasonNum}e${epNum}`,
            title: episodeName || `Episodio ${epNum}`,
            episodeNum: epNum,
            url: epUrl
          });
        }
      });
    } else if (siteName === 'PoseidonHD') {
      const nextScript = $('#__NEXT_DATA__').text();
      if (nextScript) {
        try {
          const nextData = JSON.parse(nextScript);
          const seriesData = nextData.props?.pageProps?.thisSerie || nextData.props?.pageProps?.thisSeries;
          
          if (seriesData && Array.isArray(seriesData.seasons)) {
            const basePath = seriesData.url && seriesData.url.slug 
              ? seriesData.url.slug.replace(/^series\//, 'serie/')
              : `serie/${seriesData.TMDbId || seriesData.id}/${seriesData.slug || 'serie'}`;
              
            seriesData.seasons.forEach((seasonObj) => {
              const seasonNum = seasonObj.number || seasonObj.season_number;
              const episodes = [];
              
              if (Array.isArray(seasonObj.episodes)) {
                seasonObj.episodes.forEach((ep) => {
                  const epNum = ep.number || ep.episode_number;
                  let epUrl = '';
                  if (ep.url && ep.url.slug) {
                    epUrl = `https://www.poseidonhd2.co/${ep.url.slug.replace(/^series\//, 'serie/').replace(/\/seasons\//, '/temporada/').replace(/\/episodes\//, '/episodio/')}`;
                  } else {
                    epUrl = `https://www.poseidonhd2.co/${basePath}/temporada/${seasonNum}/episodio/${epNum}`;
                  }
                  
                  episodes.push({
                    id: `s${seasonNum}e${epNum}`,
                    title: ep.title || ep.name || `Episodio ${epNum}`,
                    episodeNum: epNum,
                    url: epUrl
                  });
                });
              }
              
              if (episodes.length > 0) {
                seasons.push({
                  seasonNum,
                  title: seasonObj.title || seasonObj.name || `Temporada ${seasonNum}`,
                  episodes
                });
              }
            });
          }
        } catch (e) {
          console.error("PoseidonHD next data parse error:", e.message);
        }
      }
    } else if (siteName === 'VerPelisTV') {
      $('.episodios li').each((i, epEl) => {
        const epUrl = $(epEl).find('a').attr('href');
        const numText = $(epEl).find('.numerando').text().trim(); // format "1 - 1"
        const titleText = $(epEl).find('.episodiotitle a').text().trim() || `Episodio ${i+1}`;
        
        if (epUrl) {
          let seasonNum = 1, epNum = i + 1;
          const match = numText.match(/(\d+)\s*-\s*(\d+)/);
          if (match) {
            seasonNum = parseInt(match[1]);
            epNum = parseInt(match[2]);
          }
          
          let season = seasons.find(s => s.seasonNum === seasonNum);
          if (!season) {
            season = { seasonNum, title: `Temporada ${seasonNum}`, episodes: [] };
            seasons.push(season);
          }
          
          season.episodes.push({
            id: `s${seasonNum}e${epNum}`,
            title: titleText,
            episodeNum: epNum,
            url: epUrl
          });
        }
      });
    }

    // Sort seasons and episodes
    seasons.sort((a, b) => a.seasonNum - b.seasonNum);
    seasons.forEach(s => s.episodes.sort((a, b) => a.episodeNum - b.episodeNum));

  } catch (err) {
    console.error(`[CatalogScraper] Error escrapeando episodios en ${seriesUrl}: ${err.message}`);
  }
  
  return seasons;
}

// ================================================================
// ANIME SCRAPER: AnimeFLV.net & AnimeFLV.one
// ================================================================

async function scrapeAnimeCatalog(maxPages = 2) {
  console.log('[AnimeScraper] ▶ Iniciando scraping de Anime (animeflv.net y animeflv.one)...');
  let browser = null;
  const animeMap = new Map();

  const addOrMerge = (item) => {
    if (!item.title || !item.sourceUrl) return;
    const cleanKey = item.title.toLowerCase().trim().replace(/[^a-z0-9]/gi, '');
    if (animeMap.has(cleanKey)) {
      const existing = animeMap.get(cleanKey);
      // Merge streams (si es que ya tienen una)
      if (item.streams && item.streams[0]) {
        existing.streams.push(item.streams[0]);
      }
    } else {
      item.id = `anime_${item.type}_${cleanKey}_${Date.now() % 9999}`;
      animeMap.set(cleanKey, item);
    }
  };

  try {
    browser = await puppeteer.launch({
      headless: true,
      args: [
        '--no-sandbox', '--disable-setuid-sandbox',
        '--disable-web-security', '--disable-features=IsolateOrigins',
        '--window-size=1280,800'
      ]
    });

    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.setViewport({ width: 1280, height: 800 });

    // 1. Scrape AnimeFLV.net
    for (let p = 1; p <= maxPages; p++) {
      const url = `https://www4.animeflv.net/browse?page=${p}`;
      console.log(`[AnimeScraper] Visitando: ${url}`);
      try {
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
        const items = await page.evaluate(() => {
          const res = [];
          document.querySelectorAll('.ListAnimes li article.Anime').forEach(card => {
            const link = card.querySelector('a');
            const title = card.querySelector('h3.Title');
            const img = card.querySelector('img');
            const typeBadge = card.querySelector('.Type');
            
            if (link && title) {
              const url = link.href;
              const isMovie = typeBadge && typeBadge.textContent.toLowerCase().includes('pelicula');
              let poster = img ? (img.getAttribute('src') || img.src) : '';
              if (poster.startsWith('/')) poster = 'https://www4.animeflv.net' + poster;

              res.push({
                title: title.textContent.trim(),
                sourceUrl: url,
                poster,
                type: isMovie ? 'anime_movie' : 'anime_series',
                siteName: 'AnimeFLV.net',
                description: 'Ver anime online',
                genres: ['Anime'],
                streams: [{ name: 'AnimeFLV.net', url: url, resolver: 'iframe' }]
              });
            }
          });
          return res;
        });
        items.forEach(addOrMerge);
      } catch (e) {
        console.warn(`[AnimeScraper] Error en AnimeFLV.net pag ${p}:`, e.message);
      }
    }

    // 2. Scrape AnimeFLV.one
    for (let p = 1; p <= maxPages; p++) {
      const url = `https://vww.animeflv.one/animes?page=${p}`;
      console.log(`[AnimeScraper] Visitando: ${url}`);
      try {
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
        const items = await page.evaluate(() => {
          const res = [];
          document.querySelectorAll('.post-item, .anime, article').forEach(card => {
            const link = card.querySelector('a');
            const title = card.querySelector('h2, h3, .title');
            const img = card.querySelector('img');
            
            if (link && title) {
              const url = link.href;
              const isMovie = url.toLowerCase().includes('/pelicula') || url.toLowerCase().includes('-movie-');
              let poster = img ? (img.getAttribute('src') || img.src) : '';
              
              if (title.textContent.trim().length > 1 && url.includes(window.location.hostname)) {
                res.push({
                  title: title.textContent.trim(),
                  sourceUrl: url,
                  poster,
                  type: isMovie ? 'anime_movie' : 'anime_series',
                  siteName: 'AnimeFLV.one',
                  description: 'Ver anime online',
                  genres: ['Anime'],
                  streams: [{ name: 'AnimeFLV.one', url: url, resolver: 'iframe' }]
                });
              }
            }
          });
          return res;
        });
        items.forEach(addOrMerge);
      } catch (e) {
        console.warn(`[AnimeScraper] Error en AnimeFLV.one pag ${p}:`, e.message);
      }
    }

    // 3. Scrape AnimeOnline.ninja (Movies)
    for (let p = 1; p <= maxPages; p++) {
      const url = p === 1 ? `https://ww3.animeonline.ninja/pelicula/` : `https://ww3.animeonline.ninja/pelicula/page/${p}/`;
      console.log(`[AnimeScraper] Visitando: ${url}`);
      try {
        const response = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
        if (response && (response.status() === 403 || response.status() === 503)) {
          console.warn(`[AnimeScraper] AnimeOnline.ninja bloqueado por Cloudflare (status ${response.status()}). Saltando...`);
          break;
        }
        
        const title = await page.title();
        if (title.includes('Just a moment') || title.includes('Un momento')) {
          console.warn(`[AnimeScraper] AnimeOnline.ninja bloqueado por Cloudflare challenge. Saltando...`);
          break;
        }
        
        await page.waitForSelector('article.item, .result-item', { timeout: 3000 }).catch(() => {});
        const items = await page.evaluate(() => {
          const res = [];
          document.querySelectorAll('article.item, .result-item').forEach(card => {
            const link = card.querySelector('a');
            const title = card.querySelector('h3, .title');
            const img = card.querySelector('img');
            
            if (link && title) {
              const url = link.href;
              let poster = img ? (img.getAttribute('src') || img.src) : '';
              
              res.push({
                title: title.textContent.trim(),
                sourceUrl: url,
                poster,
                type: 'anime_movie',
                siteName: 'AnimeOnline.ninja',
                description: 'Película de anime',
                genres: ['Anime', 'Película'],
                streams: [{ name: 'AnimeOnline.ninja', url: url, resolver: 'iframe' }]
              });
            }
          });
          return res;
        });
        items.forEach(addOrMerge);
      } catch (e) {
        console.warn(`[AnimeScraper] Error en AnimeOnline.ninja pag ${p}:`, e.message);
      }
    }

    // 4. Scrape AnimeFLV.net Movies (Alternative source for movies since AnimeOnline is protected)
    for (let p = 1; p <= maxPages; p++) {
      const url = `https://www4.animeflv.net/browse?type[]=movie&page=${p}`;
      console.log(`[AnimeScraper] Visitando películas en AnimeFLV.net: ${url}`);
      try {
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
        const items = await page.evaluate(() => {
          const res = [];
          document.querySelectorAll('.ListAnimes li article.Anime').forEach(card => {
            const link = card.querySelector('a');
            const title = card.querySelector('h3.Title');
            const img = card.querySelector('img');
            
            if (link && title) {
              const url = link.href;
              let poster = img ? (img.getAttribute('src') || img.src) : '';
              if (poster.startsWith('/')) poster = 'https://www4.animeflv.net' + poster;

              res.push({
                title: title.textContent.trim(),
                sourceUrl: url,
                poster,
                type: 'anime_movie',
                siteName: 'AnimeFLV.net',
                description: 'Ver película de anime online',
                genres: ['Anime', 'Película'],
                streams: [{ name: 'AnimeFLV.net', url: url, resolver: 'iframe' }]
              });
            }
          });
          return res;
        });
        items.forEach(addOrMerge);
      } catch (e) {
        console.warn(`[AnimeScraper] Error en AnimeFLV.net películas pag ${p}:`, e.message);
      }
    }

    // 5. Scrape JKAnime.net (Movies)
    for (let p = 1; p <= maxPages; p++) {
      const url = `https://jkanime.net/directorio?tipo=peliculas&page=${p}`;
      console.log(`[AnimeScraper] Visitando: ${url}`);
      try {
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
        
        // JKanime loads catalog via internal JS variable, but renders to DOM. Wait for DOM or parse JS:
        await page.waitForSelector('.anime__item', { timeout: 8000 }).catch(() => {});
        
        const items = await page.evaluate(() => {
          const res = [];
          
          // Try fetching from the internal JS variable if available (much faster & reliable)
          if (window.animes && window.animes.data) {
             window.animes.data.forEach(jkItem => {
                 res.push({
                   title: jkItem.title,
                   sourceUrl: jkItem.url,
                   poster: jkItem.image,
                   type: 'anime_movie',
                   siteName: 'JKAnime.net',
                   description: jkItem.synopsis || 'Película de anime',
                   genres: ['Anime', 'Película'],
                   streams: [{ name: 'JKAnime.net', url: jkItem.url, resolver: 'iframe' }]
                 });
             });
             return res;
          }
          
          // Fallback to DOM parsing
          document.querySelectorAll('.anime__item').forEach(card => {
            const link = card.querySelector('a');
            const title = card.querySelector('h5');
            const img = card.querySelector('.anime__item__pic');
            
            if (link && title) {
              const url = link.href;
              let poster = '';
              if (img && img.style.backgroundImage) {
                 poster = img.style.backgroundImage.replace(/url\(['"]?(.*?)['"]?\)/i, '$1');
              }
              
              res.push({
                title: title.textContent.trim(),
                sourceUrl: url,
                poster,
                type: 'anime_movie',
                siteName: 'JKAnime.net',
                description: 'Película de anime',
                genres: ['Anime', 'Película'],
                streams: [{ name: 'JKAnime.net', url: url, resolver: 'iframe' }]
              });
            }
          });
          return res;
        });
        items.forEach(addOrMerge);
      } catch (e) {
        console.warn(`[AnimeScraper] Error en JKAnime.net pag ${p}:`, e.message);
      }
    }

  } catch (err) {
    console.error(`[AnimeScraper] Error crítico:`, err.message);
  } finally {
    if (browser) await browser.close();
  }

  const result = Array.from(animeMap.values());
  const animeMovies = result.filter(i => i.type === 'anime_movie');
  const animeSeries = result.filter(i => i.type === 'anime_series');
  console.log(`[AnimeScraper] Completado. Encontradas ${animeMovies.length} películas y ${animeSeries.length} series de anime.`);
  
  return { animeMovies, animeSeries };
}

async function scrapeAnimeEpisodesFromSeriesPage(seriesUrl, siteName) {
  console.log(`[AnimeScraper] Extrayendo episodios de anime en ${siteName}: ${seriesUrl}`);
  const seasons = [{ seasonNum: 1, title: 'Temporada 1', episodes: [] }];
  let browser = null;

  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36');
    await page.goto(seriesUrl, { waitUntil: 'domcontentloaded', timeout: 15000 });

    const episodes = await page.evaluate((url, sName) => {
      const eps = [];
      if (url.includes('animeflv.net')) {
        // AnimeFLV.net usually exposes `var episodes = [[number, id], ...]` in script
        const scripts = document.querySelectorAll('script');
        let epData = null;
        for (let s of scripts) {
          if (s.textContent.includes('var episodes =')) {
            const match = s.textContent.match(/var episodes\s*=\s*(\[\[.+\]\]);/);
            if (match && match[1]) {
              try { epData = JSON.parse(match[1]); } catch(e){}
            }
            break;
          }
        }
        
        if (epData && Array.isArray(epData)) {
          // epData: [[id, num], ...] or [[num, id], ...]
          // Actually, AnimeFLV uses [[episodeNum, episodeId], ...]
          const parts = url.split('/');
          const slug = parts[parts.length - 1]; // e.g. "one-piece"
          
          epData.forEach(ep => {
            const epNum = ep[0];
            const epId = ep[1];
            eps.push({
              id: `s1e${epNum}`,
              title: `Episodio ${epNum}`,
              episodeNum: epNum,
              url: `https://www4.animeflv.net/ver/${epId}/${slug}-${epNum}`
            });
          });
        } else {
          // Fallback parsing DOM if lists are available
          document.querySelectorAll('.ListEpisodes li a').forEach(a => {
            const epNumMatch = a.textContent.match(/Episodio\s+(\d+)/i);
            const num = epNumMatch ? parseInt(epNumMatch[1]) : eps.length + 1;
            eps.push({
              id: `s1e${num}`,
              title: a.textContent.trim() || `Episodio ${num}`,
              episodeNum: num,
              url: a.href
            });
          });
        }
      } else {
        // AnimeFLV.one fallback
        document.querySelectorAll('.episodios-list a, .episodes a, li a[href*="/ver/"]').forEach(a => {
          if (!a.href.includes('/ver/')) return;
          const text = a.textContent.trim();
          const epNumMatch = text.match(/\b(\d+)\b/);
          const num = epNumMatch ? parseInt(epNumMatch[1]) : eps.length + 1;
          eps.push({
            id: `s1e${num}`,
            title: text || `Episodio ${num}`,
            episodeNum: num,
            url: a.href
          });
        });
      }
      return eps;
    }, seriesUrl, siteName);

    // Sort episodes ascending (AnimeFLV usually lists descending)
    episodes.sort((a, b) => a.episodeNum - b.episodeNum);
    seasons[0].episodes = episodes;

  } catch (err) {
    console.error(`[AnimeScraper] Error: ${err.message}`);
  } finally {
    if (browser) await browser.close();
  }

  return seasons;
}

