const fs = require('fs');
const path = require('path');
const cheerio = require('cheerio');
const axios = require('axios');
const { searchCustom } = require('./customSearch');
// Import utilities
const { sortSourcesByPriority, normalizeLanguage } = require('./languageNormalizer');
const { validatePlayableUrl } = require('./sourceValidator');

// Utility for making Axios GET requests with automatic retry on timeout or network errors
async function axiosGetWithRetry(url, options = {}, retries = 2, delay = 1000) {
  for (let i = 0; i < retries; i++) {
    try {
      return await axios.get(url, options);
    } catch (err) {
      const isTimeoutOrNetwork = err.code === 'ECONNABORTED' || !err.response;
      if (isTimeoutOrNetwork && i < retries - 1) {
        console.warn(`[Resolver Retry] Axios GET to ${url} failed (attempt ${i + 1}/${retries}): ${err.message}. Retrying in ${delay}ms...`);
        await new Promise(resolve => setTimeout(resolve, delay));
      } else {
        throw err;
      }
    }
  }
}

// Import adapters
const directFileAdapter = require('./adapters/directFileAdapter');
const authorizedEmbedAdapter = require('./adapters/authorizedEmbedAdapter');
const plutoAdapter = require('./adapters/plutoAdapter');
const fallbackAdapter = require('./adapters/fallbackAdapter');

const adapters = [
  plutoAdapter,
  directFileAdapter,
  authorizedEmbedAdapter,
  fallbackAdapter
];

// Global in-memory log for tracking resolution attempts
const globalAttemptsLog = [];
const MAX_LOG_SIZE = 500;

function saveAttemptLog(attempt) {
  globalAttemptsLog.unshift(attempt); // Add newest first
  if (globalAttemptsLog.length > MAX_LOG_SIZE) {
    globalAttemptsLog.pop(); // Evict oldest
  }
}

const { getQuery } = require('../db/database');

async function getSourceFromDB(movieId) {
  try {
    const row = await getQuery('SELECT data FROM sources WHERE id = ?', [movieId]);
    if (row && row.data) {
      return JSON.parse(row.data);
    }
  } catch (e) {
    console.error('[Resolver] Error al buscar en SQLite:', e.message);
  }
  return null;
}

/**
 * Scrapes an external movie page to extract active streaming player links (options).
 * @param {string} pageUrl - The movie detail page URL.
 * @param {string} siteName - The name of the provider site (e.g. PelisPlus, Cuevana).
 * @returns {Promise<Array<Object>>} Extracted option items.
 */
async function extractStreamsFromMoviePage(pageUrl, siteName) {
  const options = [];
  if (!pageUrl || !pageUrl.startsWith('http')) return options;

  try {
    console.log(`[MoviePageParser] 🔍 Analizando página externa de ${siteName || 'Proveedor'}: ${pageUrl}`);
    
    let html = '';
    const isPoseidon = pageUrl.includes('poseidon') || (siteName && siteName.toLowerCase().includes('poseidon'));
    
    if (isPoseidon) {
      const { fetchHtmlWithBrowser } = require('./browserFetcher');
      html = await fetchHtmlWithBrowser(pageUrl);
    } else {
      try {
        const response = await axiosGetWithRetry(pageUrl, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept-Language': 'es-MX,es;q=0.9,en;q=0.5'
          },
          timeout: 12000
        });
        html = response.data;
      } catch (axiosErr) {
        const status = axiosErr.response?.status;
        const shouldFallback = status === 403 || status === 429 || status === 503 || axiosErr.code === 'ECONNABORTED' || !axiosErr.response;
        if (shouldFallback) {
          console.warn(`[MoviePageParser] Axios falló (status: ${status || 'TIMEOUT/NETWORK'}). Reintentando con Playwright BrowserFetcher para ${pageUrl}...`);
          const { fetchHtmlWithBrowser } = require('./browserFetcher');
          html = await fetchHtmlWithBrowser(pageUrl);
        } else {
          throw axiosErr;
        }
      }
    }

    const $ = cheerio.load(html);

    // -- A) PELISPLUS STRUCTURE --
    if (siteName === 'PelisPlus' || pageUrl.includes('pelisplushd')) {
      $('li.playurl, li[data-url]').each((i, el) => {
        const url = $(el).attr('data-url');
        const language = $(el).attr('data-name') || 'Subtitulado';
        const serverName = $(el).find('a').text().trim() || 'Desconocido';
        
        if (url && url.startsWith('http')) {
          options.push({
            url,
            name: serverName,
            language: normalizeLanguage(language),
            resolver: 'iframe'
          });
        }
      });
      
      // Series episode parsing fallback for PelisPlus
      if (options.length === 0) {
        $('.VideoPlayer ul li').each((i, el) => {
          const serverName = $(el).text().trim() || 'Desconocido';
          const dataId = $(el).attr('data-id');
          if (dataId) {
            const span = $(`#link_url span[lid="${dataId}"]`);
            const url = span.attr('url');
            if (url && url.startsWith('http')) {
              options.push({
                url,
                name: serverName,
                language: 'Español Latino', // By default PelisPlus series are mostly Latino
                resolver: 'iframe'
              });
            }
          }
        });
      }
    }
    // -- B) PELISONLINE STRUCTURE --
    else if (siteName === 'PelisOnline' || pageUrl.includes('pelisonline')) {
      // 1. Map tabs to option languages (#option1 -> Latino, #option2 -> Sub, etc.)
      const optLangs = {};
      $('a[href^="#option"]').each((i, el) => {
        const href = $(el).attr('href');
        const txt = $(el).text().trim();
        const num = href.replace('#option', '');
        optLangs[num] = txt;
      });

      // 2. Parse inline script video array
      $('script').each((i, el) => {
        const text = $(el).text();
        if (text.includes('video[')) {
          const matches = text.match(/video\[(\d+)\]\s*=\s*(?:'([^']*)'|"([^"]*)")/g);
          if (matches) {
            matches.forEach(m => {
              const parts = m.match(/video\[(\d+)\]\s*=\s*(?:'([^']*)'|"([^"]*)")/);
              if (parts && parts[1]) {
                const idx = parts[1];
                const iframeHtml = parts[2] || parts[3];
                if (iframeHtml) {
                  // Extract iframe src
                  const iframeMatch = iframeHtml.match(/src="([^"]+)"/) || iframeHtml.match(/src='([^']+)'/);
                  if (iframeMatch && iframeMatch[1]) {
                    const url = iframeMatch[1];
                    const language = optLangs[idx] || 'Latino';
                    
                    let serverName = 'Desconocido';
                    try {
                      serverName = new URL(url).hostname.replace('www.', '').split('.')[0];
                    } catch (e) {}
                    
                    options.push({
                      url,
                      name: serverName,
                      language: normalizeLanguage(language),
                      resolver: 'iframe'
                    });
                  }
                }
              }
            });
          }
        }
      });
    }
    // -- C) CUEVANA STRUCTURE --
    else if (siteName === 'Cuevana' || pageUrl.includes('cuevana')) {
      let verUrl = pageUrl;
      
      // If we are on the main detail page, look for the /ver.html page
      if (!pageUrl.includes('ver.html')) {
        const verLink = $('a[href*="ver.html"]').attr('href');
        if (verLink) {
          try {
            verUrl = new URL(verLink, pageUrl).toString();
          } catch (e) {
            verUrl = verLink;
          }
        } else {
          // Fallback guess: append ver.html
          verUrl = pageUrl.endsWith('/') ? `${pageUrl}ver.html` : `${pageUrl}/ver.html`;
        }
      }

      console.log(`[MoviePageParser] 🔗 Accediendo a subpágina de reproducción de Cuevana: ${verUrl}`);
      
      try {
        const verResponse = await axiosGetWithRetry(verUrl, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
          },
          timeout: 12000
        });

        const $ver = cheerio.load(verResponse.data);
        
        // Cuevana hardcodes the active player stream in videojs setups
        $ver('script').each((i, el) => {
          const text = $ver(el).text();
          if (text.includes('player.src') || text.includes('my-video') || text.includes('mp4movies.us')) {
            const srcMatch = text.match(/src\s*:\s*['"]([^'"]+)['"]/i);
            if (srcMatch && srcMatch[1]) {
              options.push({
                url: srcMatch[1],
                name: 'Cuevana Directo',
                language: 'Español Latino', // Default language on main player
                resolver: 'direct'
              });
            } else {
              // Fallback: extract any URL containing mp4movies.us or ending in .mp4/.m3u8
              const urls = text.match(/https?:\/\/[^"'\s>]+/g) || [];
              urls.forEach(url => {
                if (url.includes('mp4movies.us') || url.toLowerCase().includes('.mp4') || url.toLowerCase().includes('.m3u8')) {
                  options.push({
                    url,
                    name: 'Cuevana Directo (Auto)',
                    language: 'Español Latino',
                    resolver: 'direct'
                  });
                }
              });
            }
          }
        });
      } catch (verErr) {
        console.warn(`[MoviePageParser] Fallo al cargar Cuevana ver.html: ${verErr.message}`);
      }
    }
    // -- D) POSEIDONHD STRUCTURE --
    else if (siteName === 'PoseidonHD' || pageUrl.includes('poseidonhd')) {
      const nextScript = $('#__NEXT_DATA__').text();
      if (nextScript) {
        try {
          const nextData = JSON.parse(nextScript);
          const thisMovie = nextData.props?.pageProps?.thisMovie || nextData.props?.pageProps?.episode || nextData.props?.pageProps?.thisEpisode;
          if (thisMovie && thisMovie.videos) {
            for (const [langKey, videoList] of Object.entries(thisMovie.videos)) {
              if (Array.isArray(videoList)) {
                videoList.forEach(vid => {
                  if (vid.result && vid.result.startsWith('http')) {
                    let serverName = vid.name || vid.cyberlocker || 'Poseidon';
                    try {
                      serverName = new URL(vid.result).hostname.replace('www.', '').split('.')[0];
                    } catch (e) {}
                    
                    options.push({
                      url: vid.result,
                      name: serverName,
                      language: normalizeLanguage(langKey),
                      resolver: 'iframe'
                    });
                  }
                });
              }
            }
          }
        } catch (e) {
          console.error("[MoviePageParser] Error parsing PoseidonHD NEXT_DATA:", e.message);
        }
      }
    }
    // -- E) ANIMEFLV STRUCTURE --
    else if ((siteName === 'AnimeFLV.net' || siteName === 'AnimeFLV.one' || pageUrl.includes('animeflv')) && !pageUrl.includes('jkanime')) {
      let finalData = response.data;
      if (pageUrl.includes('/anime/')) {
        const match = finalData.match(/var episodes\s*=\s*(\[\[.+\]\]);/);
        if (match && match[1]) {
           try {
             const epData = JSON.parse(match[1]);
             if (epData.length > 0) {
               const firstEp = epData[epData.length - 1];
               const epNum = firstEp[0];
               const epId = firstEp[1];
               const slug = pageUrl.split('/').pop();
               const verUrl = `https://www4.animeflv.net/ver/${epId}/${slug}-${epNum}`;
               console.log(`[MoviePageParser] 🔗 Redirigiendo película de AnimeFLV a episodio 1: ${verUrl}`);
               const verResp = await axiosGetWithRetry(verUrl, { headers: { 'User-Agent': 'Mozilla/5.0' }, timeout: 12000 });
               finalData = verResp.data;
             }
           } catch(e) {}
        }
      }

      const $ = cheerio.load(finalData);
      $('script').each((i, el) => {
        const text = $(el).text();
        if (text.includes('var videos =')) {
          const match = text.match(/var videos\s*=\s*(\{.*?\});/);
          if (match && match[1]) {
            try {
              const videos = JSON.parse(match[1]);
              // videos is usually { "SUB": [{server: "fembed", title: "Fembed", code: "url"}, ...], "LAT": [...] }
              for (const [langKey, serverList] of Object.entries(videos)) {
                if (Array.isArray(serverList)) {
                  serverList.forEach(srv => {
                    let url = srv.code;
                    // AnimeFLV sometimes just puts the embed ID, or a full URL
                    if (url && !url.startsWith('http') && srv.server === 'okru') {
                      url = `https://ok.ru/videoembed/${url}`;
                    } else if (url && !url.startsWith('http')) {
                      // They might use base64 or just a path, but usually it's a full URL in 'code' for new servers
                      // If it's just an ID and we don't know the server, skip it or try to deduce
                      if (url.length > 20 && !url.includes(' ')) {
                         // some servers provide embed URLs directly
                      } else {
                         return; // skip unparseable
                      }
                    }

                    if (url && url.startsWith('http')) {
                      options.push({
                        url,
                        name: srv.title || srv.server,
                        language: langKey === 'SUB' ? 'Japonés Subtitulado' : (langKey === 'LAT' ? 'Español Latino' : 'Desconocido'),
                        resolver: 'iframe'
                      });
                    }
                  });
                }
              }
            } catch (e) {
              console.warn('[MoviePageParser] Fallo parseando var videos de AnimeFLV:', e.message);
            }
          }
        }
      });
      
      // Fallback for iframe elements if `var videos` is not present (e.g. AnimeFLV.one)
      if (options.length === 0) {
        $('iframe').each((i, el) => {
          const src = $(el).attr('src') || $(el).attr('data-src');
          if (src && src.startsWith('http') && !src.includes('google') && !src.includes('facebook') && !src.includes('disqus')) {
            let serverName = 'Iframe Anime';
            try { serverName = new URL(src).hostname.replace('www.', '').split('.')[0]; } catch (e) {}
            options.push({
              url: src,
              name: serverName,
              language: 'Japonés Subtitulado', // Default assumption for anime
              resolver: 'iframe'
            });
          }
        });
      }
    }
    // -- F) JKANIME STRUCTURE --
    else if (siteName === 'JKAnime.net' || pageUrl.includes('jkanime')) {
      let verUrl = pageUrl;
      // JKAnime movies usually play on episode 1 URL
      if (!verUrl.match(/\/\d+\/?$/)) {
        verUrl = verUrl.endsWith('/') ? `${verUrl}1/` : `${verUrl}/1/`;
      }
      
      console.log(`[MoviePageParser] 🔗 Accediendo a reproducción de JKAnime: ${verUrl}`);
      try {
        const verResponse = await axiosGetWithRetry(verUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36' },
          timeout: 12000
        });
        const $ver = cheerio.load(verResponse.data);
        
        let matchedScript = '';
        $ver('script').each((i, el) => {
          const text = $ver(el).text();
          if (text.includes('var servers =')) {
            matchedScript = text;
          }
        });
        
        if (matchedScript) {
          const match = matchedScript.match(/var servers\s*=\s*(\[.*?\]);/);
          if (match && match[1]) {
            try {
              const servers = JSON.parse(match[1]);
              servers.forEach(srv => {
                if (srv.remote) {
                  try {
                    const base64Str = srv.remote.trim().replace(/\s/g, '');
                    const decodedUrl = Buffer.from(base64Str, 'base64').toString('utf8').trim();
                    if (decodedUrl && decodedUrl.startsWith('http')) {
                      options.push({
                        url: decodedUrl,
                        name: srv.server || 'JKAnime Player',
                        language: 'Japonés Subtitulado',
                        resolver: 'iframe'
                      });
                    }
                  } catch (e) {
                    console.warn(`[MoviePageParser] Fallo al decodificar base64 de servidor JKAnime:`, e.message);
                  }
                }
              });
            } catch (jsonErr) {
              console.warn(`[MoviePageParser] Fallo al parsear JSON de servidores de JKAnime:`, jsonErr.message);
            }
          }
        }
        
        // Fallback for iframe elements if var servers wasn't found or returned empty options
        if (options.length === 0) {
          $ver('iframe').each((i, el) => {
            const src = $ver(el).attr('src') || $ver(el).attr('data-src');
            if (src && src.startsWith('http')) {
              let serverName = 'JKAnime';
              try { serverName = new URL(src).hostname.replace('www.', '').split('.')[0]; } catch (e) {}
              options.push({
                url: src,
                name: serverName,
                language: 'Japonés Subtitulado',
                resolver: 'iframe'
              });
            }
          });
        }
      } catch (e) {
        console.warn(`[MoviePageParser] Fallo al cargar JKAnime video page: ${e.message}`);
      }
    }
    // -- G) GENERAL PARSER / FALLBACK --
    else {
      // 1. Scan direct iframes
      $('iframe').each((i, el) => {
        const src = $(el).attr('src') || $(el).attr('data-src');
        if (src && src.startsWith('http') && !src.includes('google') && !src.includes('facebook') && !src.includes('ads')) {
          let serverName = 'Iframe';
          try {
            serverName = new URL(src).hostname.replace('www.', '').split('.')[0];
          } catch (e) {}
          
          options.push({
            url: src,
            name: serverName,
            language: 'Cualquier fuente disponible',
            resolver: 'iframe'
          });
        }
      });

      // 2. Scan data-url embeds
      $('[data-url], [data-video], [data-embed]').each((i, el) => {
        const url = $(el).attr('data-url') || $(el).attr('data-video') || $(el).attr('data-embed');
        if (url && url.startsWith('http')) {
          let serverName = 'Embed';
          try {
            serverName = new URL(url).hostname.replace('www.', '').split('.')[0];
          } catch (e) {}
          
          options.push({
            url,
            name: serverName,
            language: 'Cualquier fuente disponible',
            resolver: 'iframe'
          });
        }
      });
    }

  } catch (err) {
    console.error(`[MoviePageParser] Error haciendo scrape del catálogo para ${siteName}:`, err.message);
  }

  // Deduplicate options by URL and language
  const seen = new Set();
  const uniqueOptions = options.filter(opt => {
    const key = `${opt.url}_${opt.language}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  return uniqueOptions;
}

/**
 * Resolves the best playable video source for a movie or series.
 */
async function resolveBestVideoSource({
  movieId,
  movieObj = null,
  preferredLanguage = "auto",
  excludeServers = [],
  debug = false
}) {
  console.log(`[Resolver] ▶ Iniciando resolución automática para movieId: "${movieId}" (Idioma pref: ${preferredLanguage}, Servidores excluidos: [${excludeServers.join(', ')}])`);
  
  const attempts = [];
  

  // -- 2. Lookup the movie in our catalog or sources database --
  let movie = movieObj;
  if (!movie) {
    movie = await getSourceFromDB(movieId);
  }

  if (!movie) {
    console.error(`[Resolver] Movie ID "${movieId}" no encontrado en base de datos.`);
    return {
      success: false,
      movieId,
      error: "La película no existe en el catálogo",
      attempts
    };
  }

  console.log(`[Resolver] Película encontrada: "${movie.title}"`);

  // -- 3. Collect options --
  const streams = movie.streams || [];
  const candidateOptions = [];

  for (const stream of streams) {
    // Only scrape if the URL is a movie DETAIL PAGE (not a video embed)
    const isDetailPage =
      stream.url.includes('/pelicula/') ||
      stream.url.includes('/serie/') ||
      stream.url.includes('/movie/') ||
      stream.url.includes('cuevana') ||
      stream.url.includes('pelisplus') ||
      stream.url.includes('poseidonhd') ||
      stream.url.includes('pelisonline') ||
      stream.url.includes('animeflv') ||
      stream.url.includes('animeonline') ||
      stream.url.includes('jkanime') ||
      stream.url.includes('/ver/');

    if (isDetailPage) {
      // Scrape the detail page to extract embed/stream URLs
      const pageOptions = await extractStreamsFromMoviePage(stream.url, stream.name || movie.siteName);
      console.log(`[Resolver] Página de detalle scrapeada: ${pageOptions.length} opciones extraídas de ${stream.url}`);
      candidateOptions.push(...pageOptions);
    } else if (stream.resolver === 'iframe') {
      // It's already an embed URL (e.g. voe.sx, streamwish.to) — send to adapter pipeline
      // authorizedEmbedAdapter will use Playwright to sniff the real .m3u8
      candidateOptions.push({
        url: stream.url,
        name: stream.name || 'Embed',
        language: stream.language || 'Español Latino',
        resolver: 'iframe'  // Keep as iframe so authorizedEmbedAdapter picks it up
      });
    } else {
      // Treat as direct stream candidate
      candidateOptions.push({
        url: stream.url,
        name: stream.name || 'Servidor Directo',
        language: stream.language || 'Español Latino',
        resolver: 'direct'
      });
    }
  }


  if (candidateOptions.length === 0) {
    // Before giving up, try custom search
    const customResults = await searchCustom(movie.title || '');
    if (customResults && customResults.length) {
      customResults.forEach(url => {
        candidateOptions.push({
          url,
          name: 'Custom',
          language: 'Español Latino',
          resolver: 'direct'
        });
      });
    }
    if (candidateOptions.length === 0) {
      console.log(`[Resolver] No se encontraron servidores ni opciones candidatos.`);
      return {
        success: false,
        movieId,
        error: "No hay fuentes reproducibles disponibles",
        attempts
      };
    }
  }




  // -- 4. Normalize and Sort options by language priority --
  const sortedOptions = sortSourcesByPriority(candidateOptions, preferredLanguage);

  // -- 5. Filter out excluded servers --
  const filteredOptions = sortedOptions.filter(opt => {
    const nameLower = (opt.name || '').toLowerCase();
    const isExcluded = excludeServers.some(exc => nameLower.includes(exc.toLowerCase()));
    if (isExcluded) {
      console.log(`[Resolver] Excluyendo servidor: ${opt.name} (${opt.url})`);
    }
    return !isExcluded;
  });

  console.log(`[Resolver] Total de opciones filtradas a evaluar: ${filteredOptions.length}`);

  // -- 6. Evaluate candidates IN PARALLEL with concurrency limit (max 3) + 30s timeout --
  console.log(`[Resolver] Lanzando ${Math.min(filteredOptions.length, 3)} opciones en paralelo (max 3)...`);
  let selectedOption = null;
  let resolvedStream = null;

  // Helper: timeout promise that REJECTS after ms
  const withTimeout = (ms) => new Promise((_, reject) =>
    setTimeout(() => reject(new Error(`TIMEOUT_${ms}ms`)), ms)
  );

  // Limit concurrency to 3 to avoid RAM exhaustion from too many Playwright instances
  const CONCURRENCY = 3;
  const candidates = filteredOptions.slice(0, CONCURRENCY);

  try {
    // Promise.any — resolves with first SUCCESS, rejects (AggregateError) only when ALL fail
    const result = await Promise.race([
      Promise.any(
        candidates.map(async (option) => {
          console.log(`[Resolver] ▶ Probando en paralelo: [${option.language}] ${option.name}`);
          let adapter = adapters.find(a => a.canHandle(option));
          if (!adapter) adapter = fallbackAdapter;
          const res = await adapter.resolve(option);
          if (res.success) return { option, res };
          // Must THROW (not return) for Promise.any to move to next candidate
          throw new Error(`FAILED:${option.name}`);
        })
      ),
      // Global hard timeout — prevents hanging if no candidate ever resolves
      withTimeout(30000)
    ]);

    selectedOption = result.option;
    resolvedStream = result.res.stream;

    console.log(`[Resolver] ✅ Primer éxito paralelo: ${selectedOption.name} -> ${resolvedStream.url}`);

    candidates.forEach(opt => {
      const attempt = {
        movieId,
        sourceName: opt.name,
        language: opt.language,
        server: opt.name,
        status: opt === selectedOption ? 'SUCCESS' : 'ATTEMPTED',
        reason: opt === selectedOption ? 'SUCCESS' : 'PARALLEL_RACE',
        timestamp: new Date()
      };
      attempts.push(attempt);
      saveAttemptLog(attempt);
    });
  } catch (err) {
    // AggregateError = all failed; TIMEOUT_... = timed out; log and continue to fallback
    const reason = err.name === 'AggregateError' ? 'Todos los servidores fallaron' : err.message;
    console.warn(`[Resolver] ⚠️ Evaluación paralela terminó sin éxito: ${reason}`);
    candidates.forEach(opt => {
      const attempt = {
        movieId, sourceName: opt.name, language: opt.language, server: opt.name,
        status: 'FAILED', reason: 'SERVER_DOWN', timestamp: new Date()
      };
      attempts.push(attempt);
      saveAttemptLog(attempt);
    });
  }


  if (resolvedStream) {
    return {
      success: true,
      movieId,
      title: movie.title,
      selectedLanguage: selectedOption.language,
      selectedServer: selectedOption.name,
      stream: resolvedStream,
      attempts
    };
  }

  // -- 7. If all candidates failed, try custom search as a final fallback --
  console.log(`[Resolver] ❌ Fallaron todos los candidatos. Intentando búsqueda personalizada...`);
  const customResults = await searchCustom(movie.title || '');
  if (customResults && customResults.length) {
    console.log(`[Resolver] Búsqueda personalizada devolvió ${customResults.length} URLs. Evaluando...`);
    for (const url of customResults) {
      const option = { url, name: 'Custom', language: 'Español Latino', resolver: 'direct' };
      // Choose adapter (directFileAdapter should handle)
      let adapter = adapters.find(a => a.canHandle(option));
      if (!adapter) adapter = fallbackAdapter;
      let res = { success: false, reason: 'SERVER_DOWN' };
      try { res = await adapter.resolve(option); } catch (err) {
        console.error(`[Resolver] Error ejecutando adapter ${adapter.name} (custom):`, err.message);
        res = { success: false, reason: 'SERVER_DOWN', details: err.message };
      }
      const attempt = {
        movieId,
        sourceName: option.name,
        language: option.language,
        server: option.name,
        status: res.success ? 'SUCCESS' : 'FAILED',
        reason: res.success ? 'SUCCESS' : (res.reason || 'SERVER_DOWN'),
        timestamp: new Date()
      };
      attempts.push(attempt);
      saveAttemptLog(attempt);
      if (res.success) {
        console.log(`[Resolver] ✅ Éxito! Fuente personalizada resuelta: ${option.name} (${option.language}) -> ${res.stream.url}`);
        return {
          success: true,
          movieId,
          title: movie.title,
          selectedLanguage: option.language,
          selectedServer: option.name,
          stream: res.stream,
          attempts
        };
      }
    }
  }

  // If Seekee also fails, return final failure
  return {
    success: false,
    movieId,
    error: "No hay fuentes reproducibles disponibles",
    attempts,
    candidates: filteredOptions
  };
}

module.exports = {
  resolveBestVideoSource,
  globalAttemptsLog
};
