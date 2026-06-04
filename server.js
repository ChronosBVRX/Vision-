process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

// Cargar archivo .env manualmente si existe
try {
  const envPath = path.join(__dirname, '.env');
  if (fs.existsSync(envPath)) {
    const lines = fs.readFileSync(envPath, 'utf8').split('\n');
    lines.forEach(line => {
      const match = line.trim().match(/^([^#=]+)=(.*)$/);
      if (match) {
        const key = match[1].trim();
        let val = match[2].trim();
        if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
          val = val.slice(1, -1);
        }
        process.env[key] = val;
      }
    });
    console.log("[Env] Variables de entorno cargadas correctamente.");
  }
} catch (e) {
  console.warn("[Env] No se pudo cargar el archivo .env:", e.message);
}

const axios = require('axios');
const https = require('https');
const http = require('http');
const dns = require('dns');

// Configure global Node DNS servers for Google DNS and Cloudflare DNS
try {
  dns.setServers(['8.8.8.8', '1.1.1.1', '8.8.4.4']);
} catch (e) {
  console.warn("Failed to set DNS servers:", e.message);
}

// Custom lookup to resolve hostnames via public DNS, bypassing local system DNS lag/errors
const customLookup = (hostname, options, callback) => {
  if (typeof options === 'function') {
    callback = options;
    options = {};
  }
  
  dns.resolve4(hostname, (err, addresses) => {
    if (err || !addresses || addresses.length === 0) {
      return dns.lookup(hostname, options, callback);
    }
    
    if (options.all) {
      const results = addresses.map(addr => ({ address: addr, family: 4 }));
      return callback(null, results);
    }
    
    callback(null, addresses[0], 4);
  });
};

const customHttpsAgent = new https.Agent({
  rejectUnauthorized: false,
  lookup: customLookup
});

const customHttpAgent = new http.Agent({
  lookup: customLookup
});

// Apply custom HTTP/HTTPS agents globally to all Axios requests
axios.defaults.httpsAgent = customHttpsAgent;
axios.defaults.httpAgent = customHttpAgent;

const { 
  scrapeRojadirectaMatches, 
  discoverActiveMirror, 
  sniffVideoUrl, 
  searchBraveForIPTVPlaylists, 
  fetchAndParseIPTVPlaylists, 
  OFFICIAL_IPTV_FALLBACKS,
  searchBraveForMovieContent,
  fetchAndParseMoviePlaylists,
  scrapeMovieCatalog,
  OFFICIAL_VOD_FALLBACKS,
  scrapeEpisodesFromSeriesPage,
  getPlutoBootData,
  scrapePlutoTVLive,
  scrapePlutoTVOnDemand,
  scrapeAnimeCatalog,
  scrapeAnimeEpisodesFromSeriesPage,
  scrapePlanetaPlayLive
} = require('./scraper');

const { db, initDB, allQuery, getQuery, runQuery } = require('./server/db/database');

const app = express();
const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'database.json');

let memDB = { categories: [], sources: [], movieCatalog: [], seriesCatalog: [], animeMovieCatalog: [], animeSeriesCatalog: [], settings: {} };

// Inicializar SQLite y cargar en memoria para endpoints legacy
async function initializeDB() {
  await initDB();
  const cats = await allQuery('SELECT name, type FROM categories');
  const srcs = await allQuery('SELECT * FROM sources');
  const sets = await allQuery('SELECT * FROM settings');

  memDB.categories = cats.map(c => ({ name: c.name, type: c.type || 'movie' }));
  
  memDB.sources = [];
  memDB.movieCatalog = [];
  memDB.seriesCatalog = [];
  memDB.animeMovieCatalog = [];
  memDB.animeSeriesCatalog = [];

  for (const row of srcs) {
    try {
      const parsed = JSON.parse(row.data);
      if (row.type === 'movie') memDB.movieCatalog.push(parsed);
      else if (row.type === 'series') memDB.seriesCatalog.push(parsed);
      else if (row.type === 'anime_movie') memDB.animeMovieCatalog.push(parsed);
      else if (row.type === 'anime_series') memDB.animeSeriesCatalog.push(parsed);
      else memDB.sources.push(parsed);
    } catch (e) {}
  }

  sets.forEach(s => {
    try { memDB.settings[s.key] = JSON.parse(s.value); } catch(e) {}
  });

  console.log(`[DB] Memoria sincronizada: ${memDB.sources.length} sources, ${memDB.movieCatalog.length} pelis, ${memDB.seriesCatalog.length} series.`);
}

// DB Initialization moved to bottom before app.listen
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));

// Serve frontend static files in production
app.use(express.static(path.join(__dirname, 'frontend/dist')));
app.use(express.static(path.join(__dirname, 'frontend/public')));

// Helper to read database
function readDB() {
  return memDB;
}

// Helper to write database
function writeDB(data) {
  memDB = data;
  
  // Flush asíncrono a SQLite para endpoints legacy
  (async () => {
    try {
      for (const cat of data.categories || []) {
        const catName = typeof cat === 'string' ? cat : cat.name;
        const catType = typeof cat === 'string' ? 'movie' : cat.type;
        await runQuery(`INSERT OR REPLACE INTO categories (name, type) VALUES (?, ?)`, [catName, catType]);
      }
      
      const allSources = [
        ...(data.sources || []),
        ...(data.movieCatalog || []),
        ...(data.seriesCatalog || []),
        ...(data.animeMovieCatalog || []),
        ...(data.animeSeriesCatalog || [])
      ];
      
      // Bulk insert/replace
      for (const item of allSources) {
        if (!item.id) continue;
        let category = item.category || item.siteName || (item.genres && item.genres[0]) || 'General';
        await runQuery(
          `INSERT OR REPLACE INTO sources (id, type, category, data) VALUES (?, ?, ?, ?)`,
          [item.id, item.type || 'tv', category, JSON.stringify(item)]
        );
      }
      
      for (const [key, value] of Object.entries(data.settings || {})) {
        await runQuery(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, [key, JSON.stringify(value)]);
      }
    } catch(err) {
      console.error('[DB] Error en flush asíncrono:', err.message);
    }
  })();
  return true;
}

// In-Memory Cache for Sports Matches
let sportsCache = {
  matches: [],
  activeUrl: '',
  lastUpdated: null,
  loading: false,
  error: null
};

// Worker function to automatically search and refresh Live TV IPTV channels
let tvRefreshInProgress = false;

async function refreshLiveTVChannels() {
  if (tvRefreshInProgress) return;
  tvRefreshInProgress = true;
  console.log(`[TVWorker] Iniciando descubrimiento e importación de canales en vivo...`);

  try {
    // 1. Search Brave Search for public IPTV links
    let discoveredPlaylists = [];
    try {
      discoveredPlaylists = await searchBraveForIPTVPlaylists();
      console.log(`[TVWorker] Discovered ${discoveredPlaylists.length} playlists via Brave Search.`);
    } catch (e) {
      console.error("[TVWorker] Fallo en búsqueda Brave Search para IPTV:", e.message);
    }

    // 2. Combine with official fallbacks
    const allUrls = [...new Set([...discoveredPlaylists, ...OFFICIAL_IPTV_FALLBACKS])];
    console.log(`[TVWorker] Total de listas M3U a procesar: ${allUrls.length}`);

    // 3. Fetch and parse all channels
    const parsedChannels = await fetchAndParseIPTVPlaylists(allUrls);
    console.log(`[TVWorker] Se parsearon un total de ${parsedChannels.length} canales interactivos.`);

    if (parsedChannels.length === 0) {
      tvRefreshInProgress = false;
      return;
    }

    // 4. Update database
    const db = readDB();
    
    // Track existing channels to avoid duplicates
    const existingIds = new Set(db.sources.map(s => s.id));
    const existingCategories = new Set(db.categories.map(c => typeof c === 'string' ? c : c.name));

    let newChannelsAdded = 0;

    for (const channel of parsedChannels) {
      // Add category if missing
      if (!existingCategories.has(channel.category)) {
        db.categories.push({ name: channel.category, type: 'tv' });
        existingCategories.add(channel.category);
      }

      // Add channel if missing
      if (!existingIds.has(channel.id)) {
        db.sources.push(channel);
        existingIds.add(channel.id);
        newChannelsAdded++;
      }
    }

    if (newChannelsAdded > 0) {
      writeDB(db);
      console.log(`[TVWorker] Importación exitosa! Se añadieron ${newChannelsAdded} canales nuevos a la base de datos.`);
    } else {
      console.log(`[TVWorker] La base de datos ya está al día. No se requirieron canales nuevos.`);
    }
  } catch (error) {
    console.error("[TVWorker] Error en el worker de canales en vivo:", error.message);
  } finally {
    tvRefreshInProgress = false;
  }
}

let movieRefreshInProgress = false;

async function refreshMoviesAndSeries() {
  if (movieRefreshInProgress) return;
  movieRefreshInProgress = true;
  console.log(`[VODWorker] Iniciando descubrimiento e importación de películas y series...`);

  try {
    // 1. Search Brave Search for public movie & series VOD content
    let searchResult = { playlists: [], pages: [] };
    try {
      searchResult = await searchBraveForMovieContent();
      console.log(`[VODWorker] Discovered ${searchResult.playlists.length} VOD playlists and ${searchResult.pages.length} streaming pages via Brave Search.`);
    } catch (e) {
      console.error("[VODWorker] Fallo en búsqueda Brave Search para VOD:", e.message);
    }

    // 2. Combine playlists with official fallbacks
    const allUrls = [...new Set([...searchResult.playlists, ...OFFICIAL_VOD_FALLBACKS])];
    console.log(`[VODWorker] Total de listas VOD a procesar: ${allUrls.length}`);

    // 3. Fetch and parse all playlist items
    const parsedVOD = await fetchAndParseMoviePlaylists(allUrls);
    console.log(`[VODWorker] Se parsearon un total de ${parsedVOD.length} películas y series desde listas M3U.`);

    // 4. Combine playlist items and direct page items
    const finalVODList = [...parsedVOD];
    
    for (const pageItem of searchResult.pages) {
      const contentType = pageItem.isSeries ? 'series' : 'movie';
      const category = pageItem.isSeries ? `Series - ${pageItem.siteName}` : `Películas - ${pageItem.siteName}`;
      const cleanTitle = pageItem.title;
      const idKey = cleanTitle.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
      const itemId = `${contentType}_${idKey}`;
      
      finalVODList.push({
        id: itemId,
        title: cleanTitle,
        type: contentType,
        category,
        poster: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400',
        description: `Ver ${cleanTitle} online gratis en ${pageItem.siteName}.`,
        streams: [{
          name: pageItem.siteName,
          url: pageItem.url,
          resolver: 'direct'
        }]
      });
    }

    if (finalVODList.length === 0) {
      movieRefreshInProgress = false;
      return;
    }

    // 5. Update database
    const db = readDB();
    
    // Track existing items to avoid duplicates or update streams
    const existingMap = new Map(db.sources.map(s => [s.id, s]));
    const existingCategories = new Set(db.categories.map(c => typeof c === 'string' ? c : c.name));

    let newItemsAdded = 0;
    let itemsUpdated = 0;

    for (const item of finalVODList) {
      // Add category if missing
      if (!existingCategories.has(item.category)) {
        db.categories.push({ name: item.category, type: item.type || 'movie' });
        existingCategories.add(item.category);
      }

      if (existingMap.has(item.id)) {
        // Item already exists. Merge new streams.
        const existingItem = existingMap.get(item.id);
        let updated = false;
        
        for (const newStream of item.streams) {
          if (!existingItem.streams.some(s => s.url === newStream.url)) {
            existingItem.streams.push({
              name: newStream.name || `Servidor ${existingItem.streams.length + 1}`,
              url: newStream.url,
              resolver: 'direct'
            });
            updated = true;
          }
        }
        if (updated) {
          itemsUpdated++;
        }
      } else {
        // Add new item
        db.sources.push(item);
        existingMap.set(item.id, item);
        newItemsAdded++;
      }
    }

    if (newItemsAdded > 0 || itemsUpdated > 0) {
      writeDB(db);
      console.log(`[VODWorker] Importación exitosa! Se añadieron ${newItemsAdded} ítems nuevos y se actualizaron ${itemsUpdated} existentes.`);
    } else {
      console.log(`[VODWorker] La base de datos de películas y series ya está al día.`);
    }
  } catch (error) {
    console.error("[VODWorker] Error en el worker de películas y series:", error.message);
  } finally {
    movieRefreshInProgress = false;
  }
}

// Helper functions for team logo enrichment
async function getTeamLogo(teamName) {
  if (!teamName || teamName.length < 2) return null;
  try {
    const cleanName = encodeURIComponent(teamName.trim());
    const res = await axios.get(`https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t=${cleanName}`, { timeout: 2000 });
    if (res.data && res.data.teams && res.data.teams.length > 0) {
      return res.data.teams[0].strBadge || null;
    }
  } catch (err) {
    // Fail silently
  }
  return null;
}

function getTeamsFromTitle(title) {
  let parts = [];
  if (title.toLowerCase().includes(' vs ')) {
    parts = title.split(/ vs /i);
  } else if (title.toLowerCase().includes(' v ')) {
    parts = title.split(/ v /i);
  } else if (title.includes(' - ')) {
    parts = title.split(' - ');
  }
  return parts.map(p => p.replace(/en vivo/i, '').replace(/live/i, '').trim()).filter(p => p.length > 0);
}

async function enrichMatchesWithLogos(matches) {
  console.log(`[SportsWorker] Buscando logotipos para ${matches.length} partidos...`);
  try {
    const enriched = await Promise.all(matches.map(async (match) => {
      const teams = getTeamsFromTitle(match.title);
      if (teams.length >= 2) {
        const [logo1, logo2] = await Promise.all([
          getTeamLogo(teams[0]),
          getTeamLogo(teams[1])
        ]);
        return {
          ...match,
          team1: teams[0],
          team2: teams[1],
          logo1,
          logo2
        };
      }
      return match;
    }));
    return enriched;
  } catch (e) {
    console.error("[SportsWorker] Fallo al enriquecer logotipos:", e.message);
    return matches;
  }
}

// Function to refresh sports cache
async function refreshSportsCache() {
  if (sportsCache.loading) return;
  sportsCache.loading = true;
  sportsCache.error = null;
  console.log(`[SportsWorker] Iniciando refresco de cache de deportes...`);

  try {
    const result = await discoverActiveMirror();
    
    // Enrich matches with official team logos from TheSportsDB
    const enrichedMatches = await enrichMatchesWithLogos(result.matches);
    
    sportsCache.matches = enrichedMatches;
    sportsCache.activeUrl = result.activeUrl;
    sportsCache.lastUpdated = new Date();
    sportsCache.loading = false;
    sportsCache.error = null;
    console.log(`[SportsWorker] Cache de deportes actualizado con exito. Espejo activo: ${result.activeUrl}`);

    // Update settings in database.json to persist the latest working mirror
    const db = readDB();
    db.settings.rojadirectaUrl = result.activeUrl;
    writeDB(db);
  } catch (error) {
    sportsCache.loading = false;
    sportsCache.error = error.message;
    console.error(`[SportsWorker] Error al actualizar cache de deportes: ${error.message}`);
  }
}

// Endpoints
const { resolveBestVideoSource } = require('./server/resolvers/videoSourceResolver');

app.get('/api/movies/:id/resolve', async (req, res) => {
  const movieId = req.params.id;
  const preferredLanguage = req.query.lang || 'auto';
  const debug = req.query.debug === 'true';
  
  let excludeServers = [];
  if (req.query.excludeServer) {
    if (Array.isArray(req.query.excludeServer)) {
      excludeServers = req.query.excludeServer;
    } else {
      excludeServers = [req.query.excludeServer];
    }
  }

  try {
    const result = await resolveBestVideoSource({
      movieId,
      preferredLanguage,
      excludeServers,
      debug
    });
    
    // Always return 200 with result payload so the frontend can read status & attempts easily
    res.json(result);
  } catch (err) {
    console.error(`[API Resolve] Error al resolver película ${movieId}:`, err.message);
    res.status(500).json({
      success: false,
      movieId,
      error: "Error interno al resolver la fuente de video",
      attempts: []
    });
  }
});

// GET episodes for a series
app.get('/api/series/:id/episodes', async (req, res) => {
  const seriesId = req.params.id;
  const db = readDB();
  
  let series = null;
  if (db.sources) series = db.sources.find(s => s.id === seriesId);
  if (!series && db.seriesCatalog) series = db.seriesCatalog.find(s => s.id === seriesId);
  if (!series && db.animeSeriesCatalog) series = db.animeSeriesCatalog.find(s => s.id === seriesId);
  
  if (!series || !series.streams || series.streams.length === 0) {
    return res.status(404).json({ error: "Serie no encontrada o sin origen válido." });
  }
  
  const originUrl = series.streams[0].url;
  const siteName = series.siteName || series.streams[0].name;
  
  try {
    let seasons = [];
    if (originUrl.startsWith('pluto-tv://')) {
      const plutoId = seriesId.replace(/^plutovod_/, '');
      const bootData = await getPlutoBootData();
      const headers = {
        Authorization: `Bearer ${bootData.sessionToken}`,
        'X-Forwarded-For': '189.201.128.1' // PLUTO_REGION_IP
      };
      const axios = require('axios');
      const resp = await axios.get(`https://service-vod.clusters.pluto.tv/v4/vod/series/${plutoId}/seasons?offset=0&limit=100`, { headers });
      seasons = (resp.data.seasons || []).map(s => ({
        title: `Temporada ${s.number}`,
        episodes: (s.episodes || []).map(ep => {
          const path = ep.stitched?.path || (ep.stitched?.paths && ep.stitched.paths.find(p => p.type === 'hls')?.path) || '';
          return {
            title: ep.name || `Episodio ${ep.number}`,
            episodeNum: ep.number,
            url: `pluto-tv://${path}`
          };
        }).filter(ep => ep.url !== 'pluto-tv://')
      }));
    } else if (siteName === 'AnimeFLV.net' || siteName === 'AnimeFLV.one') {
      seasons = await scrapeAnimeEpisodesFromSeriesPage(originUrl, siteName);
    } else {
      seasons = await scrapeEpisodesFromSeriesPage(originUrl, siteName);
    }
    res.json({ success: true, seasons, seriesId });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// RESOLVE an individual episode
app.get('/api/series/episode/resolve', async (req, res) => {
  const { url, siteName } = req.query;
  const preferredLanguage = req.query.lang || 'auto';
  const debug = req.query.debug === 'true';
  
  if (!url) return res.status(400).json({ error: "Missing url parameter" });
  
  try {
    // Generate a temporary movie object for the resolver
    const tempMovieId = "ep_" + Date.now();
    const tempMovie = {
      id: tempMovieId,
      title: "Episodio",
      type: "series",
      siteName: siteName || "Desconocido",
      streams: [{ url, name: siteName || "Desconocido", resolver: "iframe" }]
    };
    
    // Call the resolver
    const result = await resolveBestVideoSource({
      movieId: tempMovieId,
      movieObj: tempMovie,
      preferredLanguage,
      excludeServers: [],
      debug
    });
    
    res.json(result);
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/api/sources', (req, res) => {
  const db = readDB();
  const includeCatalog = req.query.includeCatalog === 'true';
  const includePlutoTV = req.query.includePlutoTV === 'true';

  let customSources = db.sources || [];
  if (!includePlutoTV) {
    customSources = customSources.filter(s => !(s.id.startsWith('plutotv_') || s.id.startsWith('plutovod_')));
  }

  if (!includeCatalog) {
    const categoriesSet = new Set();
    (db.categories || []).forEach(c => {
      categoriesSet.add(typeof c === 'string' ? c : c.name);
    });
    const categoriesDetailed = Array.from(categoriesSet).map(name => {
      const found = (db.categories || []).find(c => (typeof c === 'string' ? c : c.name) === name);
      return {
        name,
        type: found ? (typeof found === 'string' ? 'movie' : found.type) : 'movie'
      };
    });
    return res.json({
      categories: Array.from(categoriesSet),
      categoriesDetailed,
      sources: customSources,
      settings: db.settings
    });
  }

  // Combine db.sources with movieCatalog and seriesCatalog
  const normalizedMovies = (db.movieCatalog || []).map(m => ({
    ...m,
    category: m.category || m.siteName || (m.genres && m.genres[0]) || 'Películas - General'
  }));

  const normalizedSeries = (db.seriesCatalog || []).map(s => ({
    ...s,
    category: s.category || s.siteName || (s.genres && s.genres[0]) || 'Series - General'
  }));

  const combinedSources = [
    ...customSources,
    ...normalizedMovies,
    ...normalizedSeries
  ];

  // Compile a list of unique categories
  const categoriesSet = new Set();
  (db.categories || []).forEach(c => {
    categoriesSet.add(typeof c === 'string' ? c : c.name);
  });
  combinedSources.forEach(s => {
    if (s.category) {
      categoriesSet.add(s.category);
    }
  });

  const categoriesDetailed = Array.from(categoriesSet).map(name => {
    const found = (db.categories || []).find(c => (typeof c === 'string' ? c : c.name) === name);
    return {
      name,
      type: found ? (typeof found === 'string' ? 'movie' : found.type) : 'movie'
    };
  });

  res.json({
    categories: Array.from(categoriesSet),
    categoriesDetailed,
    sources: combinedSources,
    settings: db.settings
  });
});

app.post('/api/sources', (req, res) => {
  const db = readDB();
  const newSource = req.body;
  
  if (!newSource.title || !newSource.type || !newSource.category) {
    return res.status(400).json({ error: 'Title, type, and category are required' });
  }

  newSource.id = newSource.id || 'src_' + Date.now().toString(36);
  db.sources.push(newSource);
  writeDB(db);
  res.status(201).json(newSource);
});

app.put('/api/sources/:id', (req, res) => {
  const db = readDB();
  const id = req.params.id;
  const updatedSource = req.body;

  // 1. Search in db.sources
  let index = (db.sources || []).findIndex(s => s.id === id);
  if (index !== -1) {
    updatedSource.id = id;
    db.sources[index] = updatedSource;
    writeDB(db);
    return res.json(updatedSource);
  }

  // 2. Search in db.movieCatalog
  index = (db.movieCatalog || []).findIndex(s => s.id === id);
  if (index !== -1) {
    const existing = db.movieCatalog[index];
    const updated = {
      ...existing,
      ...updatedSource,
      id
    };
    if (updated.category) {
      updated.genres = [updated.category];
      updated.siteName = updated.category;
    }
    db.movieCatalog[index] = updated;
    writeDB(db);
    return res.json(updated);
  }

  // 3. Search in db.seriesCatalog
  index = (db.seriesCatalog || []).findIndex(s => s.id === id);
  if (index !== -1) {
    const existing = db.seriesCatalog[index];
    const updated = {
      ...existing,
      ...updatedSource,
      id
    };
    if (updated.category) {
      updated.genres = [updated.category];
      updated.siteName = updated.category;
    }
    db.seriesCatalog[index] = updated;
    writeDB(db);
    return res.json(updated);
  }

  return res.status(404).json({ error: 'Source not found' });
});

app.delete('/api/sources/:id', (req, res) => {
  const db = readDB();
  const id = req.params.id;
  let deleted = false;

  const initialSourcesLength = (db.sources || []).length;
  db.sources = (db.sources || []).filter(s => s.id !== id);
  if (db.sources.length !== initialSourcesLength) {
    deleted = true;
  }

  if (!deleted) {
    const initialMoviesLength = (db.movieCatalog || []).length;
    db.movieCatalog = (db.movieCatalog || []).filter(s => s.id !== id);
    if (db.movieCatalog.length !== initialMoviesLength) {
      deleted = true;
    }
  }

  if (!deleted) {
    const initialSeriesLength = (db.seriesCatalog || []).length;
    db.seriesCatalog = (db.seriesCatalog || []).filter(s => s.id !== id);
    if (db.seriesCatalog.length !== initialSeriesLength) {
      deleted = true;
    }
  }

  if (!deleted) {
    const initialAnimeMoviesLength = (db.animeMovieCatalog || []).length;
    db.animeMovieCatalog = (db.animeMovieCatalog || []).filter(s => s.id !== id);
    if (db.animeMovieCatalog.length !== initialAnimeMoviesLength) {
      deleted = true;
    }
  }

  if (!deleted) {
    const initialAnimeSeriesLength = (db.animeSeriesCatalog || []).length;
    db.animeSeriesCatalog = (db.animeSeriesCatalog || []).filter(s => s.id !== id);
    if (db.animeSeriesCatalog.length !== initialAnimeSeriesLength) {
      deleted = true;
    }
  }

  if (!deleted) {
    return res.status(404).json({ error: 'Source not found' });
  }

  writeDB(db);
  res.json({ message: 'Source deleted successfully' });
});

// Categories
app.post('/api/categories', (req, res) => {
  const db = readDB();
  const { name, type } = req.body;

  if (!name) {
    return res.status(400).json({ error: 'Category name is required' });
  }

  const exists = (db.categories || []).some(c => (typeof c === 'string' ? c : c.name).toLowerCase() === name.toLowerCase());
  if (exists) {
    return res.status(400).json({ error: 'Category already exists' });
  }

  db.categories.push({ name, type: type || 'movie' });
  writeDB(db);
  const flatCats = db.categories.map(c => typeof c === 'string' ? c : c.name);
  res.status(201).json(flatCats);
});

app.delete('/api/categories', (req, res) => {
  const db = readDB();
  const { name } = req.body;

  if (!name) {
    return res.status(400).json({ error: 'Category name is required' });
  }

  // Remove category from categories array
  db.categories = db.categories.filter(c => (typeof c === 'string' ? c : c.name) !== name);
  
  // Remove all sources that belong to this category
  db.sources = db.sources.filter(s => s.category !== name);

  // Clean catalog elements that belong to this category / genre / siteName
  db.movieCatalog = (db.movieCatalog || []).filter(s => 
    s.category !== name && 
    s.siteName !== name && 
    !(s.genres && s.genres.includes(name))
  );
  db.seriesCatalog = (db.seriesCatalog || []).filter(s => 
    s.category !== name && 
    s.siteName !== name && 
    !(s.genres && s.genres.includes(name))
  );
  db.animeMovieCatalog = (db.animeMovieCatalog || []).filter(s => 
    s.category !== name && 
    s.siteName !== name && 
    !(s.genres && s.genres.includes(name))
  );
  db.animeSeriesCatalog = (db.animeSeriesCatalog || []).filter(s => 
    s.category !== name && 
    s.siteName !== name && 
    !(s.genres && s.genres.includes(name))
  );

  writeDB(db);
  const flatCats = db.categories.map(c => typeof c === 'string' ? c : c.name);
  res.json(flatCats);
});

// Settings Endpoints
app.get('/api/settings', (req, res) => {
  const db = readDB();
  res.json(db.settings || { rojadirectaUrl: "https://www.rojadirectatvmas.com" });
});

app.post('/api/settings', (req, res) => {
  const db = readDB();
  db.settings = { ...db.settings, ...req.body };
  writeDB(db);
  res.json(db.settings);
});

// Endpoint to manually refresh Live TV Channels in background
app.post('/api/channels/refresh', (req, res) => {
  if (tvRefreshInProgress) {
    return res.json({ success: true, message: "El refresco de canales ya está en progreso." });
  }
  refreshLiveTVChannels();
  res.json({ success: true, message: "Refresco de canales en vivo iniciado en segundo plano." });
});

// Endpoint to manually refresh Movies & Series in background
app.post('/api/movies-series/refresh', (req, res) => {
  if (movieRefreshInProgress) {
    return res.json({ success: true, message: "El refresco de películas y series ya está en progreso." });
  }
  refreshMoviesAndSeries();
  res.json({ success: true, message: "Refresco de películas y series iniciado en segundo plano." });
});

// Sports Scraping Endpoints (Instantly returns cached data)
app.get('/api/sports/matches', (req, res) => {
  res.json({
    success: sportsCache.error ? false : true,
    matches: sportsCache.matches,
    activeUrl: sportsCache.activeUrl,
    lastUpdated: sportsCache.lastUpdated,
    loading: sportsCache.loading,
    error: sportsCache.error
  });
});

// Manually trigger a refresh in background
app.post('/api/sports/refresh', async (req, res) => {
  if (sportsCache.loading) {
    return res.json({ success: true, message: "El refresco ya esta en progreso.", status: sportsCache });
  }
  refreshSportsCache();
  res.json({ success: true, message: "Refresco iniciado en segundo plano." });
});

// Endpoint to recursively resolve iframe links to direct stream URLs
app.get('/api/sports/resolve-stream', async (req, res) => {
  const streamUrl = req.query.url;
  if (!streamUrl) {
    return res.status(400).json({ error: "Missing url parameter" });
  }

  try {
    const sniffResult = await sniffVideoUrl(streamUrl);
    if (sniffResult && sniffResult.url) {
      res.json({ success: true, result: { type: 'direct', url: sniffResult.url, referer: sniffResult.referer } });
    } else {
      res.json({ success: true, result: { type: 'iframe', url: streamUrl } });
    }
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
});

// Endpoint to resolve and play using sequential auto-hop sniffing
app.post('/api/sports/resolve-and-play', async (req, res) => {
  const { streams } = req.body;
  if (!streams || !Array.isArray(streams) || streams.length === 0) {
    return res.status(400).json({ error: "Missing or invalid streams parameter" });
  }

  console.log(`[Server] Iniciando resolve-and-play para ${streams.length} opciones...`);

  for (let i = 0; i < streams.length; i++) {
    const stream = streams[i];
    console.log(`[Server] Probando servidor ${i + 1}/${streams.length}: ${stream.name} (${stream.url})`);
    
    try {
      const sniffResult = await sniffVideoUrl(stream.url);
      if (sniffResult && sniffResult.url) {
        const videoUrl = sniffResult.url;
        const referer = sniffResult.referer;
        console.log(`[Server] Video detectado: ${videoUrl}. Referer: ${referer}. Verificando conectividad...`);
        let isValidStream = false;

        let checkReferer = referer || '';
        if (!checkReferer) {
          try {
            const originUrl = new URL(stream.url).origin;
            if (videoUrl.includes('54434687.net') || videoUrl.includes('verfutbol')) {
              checkReferer = 'https://verfutbol.at/';
            } else {
              checkReferer = originUrl;
            }
          } catch (e) {}
        }

        // Step 1: Try a quick HEAD request
        try {
          const checkRes = await axios({
            method: 'head',
            url: videoUrl,
            timeout: 4000,
            headers: {
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
              'Referer': checkReferer
            },
            validateStatus: (status) => status >= 200 && status < 400
          });
          console.log(`[Server] ¡Validación HEAD exitosa! Status: ${checkRes.status}`);
          isValidStream = true;
        } catch (headErr) {
          console.log(`[Server] HEAD falló (${headErr.message}), intentando GET parcial de respaldo...`);
          // Step 2: Try a quick GET range request as fallback (some HLS servers block HEAD)
          try {
            const checkResGet = await axios({
              method: 'get',
              url: videoUrl,
              timeout: 4000,
              headers: { 
                Range: 'bytes=0-100',
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Referer': checkReferer
              },
              validateStatus: (status) => status >= 200 && status < 400
            });
            console.log(`[Server] ¡Validación GET exitosa! Status: ${checkResGet.status}`);
            isValidStream = true;
          } catch (getErr) {
            console.warn(`[Server] Validación de conectividad fallida para: ${videoUrl}. Error: ${getErr.message}`);
          }
        }

        if (isValidStream) {
          console.log(`[Server] ¡Éxito! Servidor ${stream.name} validado en: ${videoUrl}`);
          return res.json({
            success: true,
            url: videoUrl,
            referer: checkReferer,
            name: stream.name,
            resolver: 'direct'
          });
        }
      }
      console.log(`[Server] Servidor ${stream.name} no retornó video válido.`);
    } catch (err) {
      console.error(`[Server] Error probando servidor ${stream.name}:`, err.message);
    }
  }

  console.log(`[Server] Fallaron todos los servidores. Retornando primer servidor como iframe fallback.`);
  return res.json({
    success: false,
    error: "No se pudo extraer video directo de ningún servidor activo.",
    fallback: {
      url: streams[0].url,
      name: streams[0].name,
      resolver: 'iframe'
    }
  });
});

// Import plutoAdapter for direct live resolution
const plutoAdapter = require('./server/resolvers/adapters/plutoAdapter');

// Endpoint to resolve Live TV and Sports mimicking the movie resolver behavior
app.post('/api/live/resolve', async (req, res) => {
  const { streams, type, excludeServer } = req.body;
  if (!streams || !Array.isArray(streams) || streams.length === 0) {
    return res.status(400).json({ success: false, error: "Missing or invalid streams parameter", attempts: [] });
  }

  let excludeServers = [];
  if (excludeServer) {
    if (Array.isArray(excludeServer)) {
      excludeServers = excludeServer;
    } else {
      excludeServers = [excludeServer];
    }
  }

  console.log(`[Server] Iniciando live resolve para ${streams.length} opciones... (Excluyendo: [${excludeServers.join(', ')}])`);

  const attempts = [];
  
  for (let i = 0; i < streams.length; i++) {
    const stream = streams[i];
    const serverName = stream.name || `Servidor ${i + 1}`;
    
    // Filter excluded servers
    const isExcluded = excludeServers.some(exc => (serverName).toLowerCase().includes(exc.toLowerCase()));
    if (isExcluded) {
      console.log(`[Server] Excluyendo servidor: ${serverName}`);
      continue;
    }

    console.log(`[Server] Probando servidor ${i + 1}/${streams.length}: ${serverName} (${stream.url})`);
    
    try {
      if (plutoAdapter.canHandle({ url: stream.url })) {
        console.log(`[Server] Redirigiendo a plutoAdapter...`);
        const result = await plutoAdapter.resolve({ url: stream.url });
        if (result.success) {
          return res.json({
            success: true,
            selectedServer: serverName,
            stream: result.stream,
            attempts
          });
        } else {
          attempts.push({
            sourceName: serverName,
            language: type === 'tv' ? 'En Vivo' : 'Deportes',
            server: serverName,
            status: 'FAILED',
            reason: result.reason || 'PLUTO_ERROR',
            timestamp: new Date()
          });
          continue;
        }
      }

      let videoUrl = stream.url;
      let referer = '';
      
      const isDirectStream = videoUrl.toLowerCase().includes('.m3u8') || 
                             videoUrl.toLowerCase().includes('.mp4') ||
                             videoUrl.toLowerCase().includes('.mkv') ||
                             videoUrl.toLowerCase().includes('.avi');
                             
      if (!isDirectStream) {
        const sniffResult = await sniffVideoUrl(stream.url);
        if (sniffResult && sniffResult.url) {
          videoUrl = sniffResult.url;
          referer = sniffResult.referer;
        } else {
          console.log(`[Server] Servidor ${serverName} no retornó video válido.`);
          attempts.push({
            sourceName: serverName,
            language: type === 'tv' ? 'En Vivo' : 'Deportes',
            server: serverName,
            status: 'FAILED',
            reason: 'SERVER_DOWN',
            timestamp: new Date()
          });
          continue;
        }
      }

      console.log(`[Server] Video detectado: ${videoUrl}. Referer: ${referer}. Verificando conectividad...`);
      let isValidStream = false;

        let checkReferer = referer || '';
        if (!checkReferer) {
          try {
            const originUrl = new URL(stream.url).origin;
            if (videoUrl.includes('54434687.net') || videoUrl.includes('verfutbol')) {
              checkReferer = 'https://verfutbol.at/';
            } else {
              checkReferer = originUrl;
            }
          } catch (e) {}
        }

        try {
          const checkRes = await axios({
            method: 'head',
            url: videoUrl,
            timeout: 4000,
            headers: {
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
              'Referer': checkReferer
            },
            validateStatus: (status) => status >= 200 && status < 400
          });
          console.log(`[Server] ¡Validación HEAD exitosa! Status: ${checkRes.status}`);
          isValidStream = true;
        } catch (headErr) {
          console.log(`[Server] HEAD falló (${headErr.message}), intentando GET parcial de respaldo...`);
          try {
            const checkResGet = await axios({
              method: 'get',
              url: videoUrl,
              timeout: 4000,
              headers: { 
                Range: 'bytes=0-100',
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Referer': checkReferer
              },
              validateStatus: (status) => status >= 200 && status < 400
            });
            console.log(`[Server] ¡Validación GET exitosa! Status: ${checkResGet.status}`);
            isValidStream = true;
          } catch (getErr) {
            console.warn(`[Server] Validación de conectividad fallida para: ${videoUrl}. Error: ${getErr.message}`);
          }
        }

        if (isValidStream) {
          console.log(`[Server] ¡Éxito! Servidor ${serverName} validado en: ${videoUrl}`);
          const attempt = {
            sourceName: serverName,
            language: type === 'tv' ? 'En Vivo' : 'Deportes',
            server: serverName,
            status: 'SUCCESS',
            reason: 'SUCCESS',
            timestamp: new Date()
          };
          attempts.push(attempt);
          return res.json({
            success: true,
            selectedLanguage: type === 'tv' ? 'En Vivo' : 'Deportes',
            selectedServer: serverName,
            stream: {
              name: serverName,
              url: videoUrl,
              headers: { referer: checkReferer },
              resolver: 'direct',
              type: 'direct'
            },
            attempts
          });
        }

        if (!isValidStream) {
          // Fallback if isValidStream fails
          console.log(`[Server] Servidor ${serverName} falló validación de conectividad.`);
          attempts.push({
            sourceName: serverName,
            language: type === 'tv' ? 'En Vivo' : 'Deportes',
            server: serverName,
            status: 'FAILED',
            reason: 'SERVER_DOWN',
            timestamp: new Date()
          });
        }
    } catch (err) {
      console.error(`[Server] Error probando servidor ${serverName}:`, err.message);
      attempts.push({
        sourceName: serverName,
        language: type === 'tv' ? 'En Vivo' : 'Deportes',
        server: serverName,
        status: 'FAILED',
        reason: 'ERROR',
        timestamp: new Date()
      });
    }
  }

  console.log(`[Server] Fallaron todos los servidores en live resolve. Retornando fallback iframe.`);
  return res.json({
    success: true, // We return success: true so VideoPlayer can mount the iframe!
    selectedLanguage: type === 'tv' ? 'En Vivo' : 'Deportes',
    selectedServer: streams[0].name || 'Servidor Original',
    stream: {
      name: streams[0].name || 'Servidor Original',
      url: streams[0].url,
      resolver: 'iframe',
      type: 'iframe'
    },
    attempts
  });
});

// In-memory image proxy cache (key: url, value: { data, contentType, timestamp })
const imgCache = new Map();
const IMG_CACHE_TTL = 60 * 60 * 1000; // 1 hour

// Lightweight image proxy with caching to avoid 429s from Imgur etc.
app.get('/api/img-proxy', async (req, res) => {
  const targetUrl = req.query.url;
  if (!targetUrl) return res.status(400).send('Missing url');

  // Serve from cache if available
  const cached = imgCache.get(targetUrl);
  if (cached && Date.now() - cached.timestamp < IMG_CACHE_TTL) {
    res.setHeader('Content-Type', cached.contentType);
    res.setHeader('Cache-Control', 'public, max-age=3600');
    res.setHeader('Access-Control-Allow-Origin', '*');
    return res.send(cached.data);
  }

  try {
    const response = await axios({
      method: 'get',
      url: targetUrl,
      responseType: 'arraybuffer',
      timeout: 8000,
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; VisionPlusBot/1.0)',
        'Accept': 'image/*,*/*'
      }
    });
    const contentType = response.headers['content-type'] || 'image/jpeg';
    const data = Buffer.from(response.data);
    imgCache.set(targetUrl, { data, contentType, timestamp: Date.now() });
    // Evict old entries if cache grows too large
    if (imgCache.size > 500) {
      const firstKey = imgCache.keys().next().value;
      imgCache.delete(firstKey);
    }
    res.setHeader('Content-Type', contentType);
    res.setHeader('Cache-Control', 'public, max-age=3600');
    res.setHeader('Access-Control-Allow-Origin', '*');
    return res.send(data);
  } catch (err) {
    // Return transparent 1x1 PNG on failure
    const emptyPng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=', 'base64');
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'public, max-age=60');
    return res.send(emptyPng);
  }
});

// Movie seeder: populates DB with real movies from jsonfakery.com (uses TMDB data + vidsrc.to streaming)
app.post('/api/seed-movies', async (req, res) => {
  const db = readDB();
  const existingIds = new Set(db.sources.map(s => s.id));
  let added = 0;

  const addItem = (item, type) => {
    const tmdbId = item.movie_id || item.id;
    const id = `${type}_tmdb${tmdbId}`;
    if (existingIds.has(id)) return;
    const title = item.original_title || item.title || item.name || 'Sin título';
    const posterPath = item.poster_path || '';
    const poster = posterPath 
      ? posterPath.replace('/original/', '/w500/')
      : 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400';
    const overview = item.overview || '';
    const category = type === 'series' ? 'Series - General' : 'Películas - General';
    const tmdbType = type === 'series' ? 'tv' : 'movie';
    const source = {
      id, title, type, category, tmdbId,
      poster, 
      description: overview || `${type === 'series' ? 'Serie' : 'Película'} disponible en streaming.`,
      streams: [
        { name: 'VidSrc', url: `https://vidsrc.to/embed/${tmdbType}/${tmdbId}`, resolver: 'iframe' },
        { name: 'VidSrc.me', url: `https://vidsrc.me/embed/${tmdbType}?tmdb=${tmdbId}`, resolver: 'iframe' },
        { name: '2Embed', url: `https://www.2embed.cc/embed/${tmdbId}`, resolver: 'iframe' }
      ]
    };
    if (!db.categories.some(c => (typeof c === 'string' ? c : c.name) === category)) {
      db.categories.push({ name: category, type: type });
    }
    db.sources.push(source);
    existingIds.add(id);
    added++;
  };

  // Fetch movies from free no-auth jsonfakery API (uses TMDB data)
  for (let page = 1; page <= 15; page++) {
    try {
      const r = await axios.get(`https://jsonfakery.com/movies/paginated?page=${page}`, { timeout: 10000 });
      if (r.data && r.data.data && r.data.data.length > 0) {
        r.data.data.forEach(m => addItem(m, 'movie'));
      } else {
        break; // No more pages
      }
    } catch (e) {
      console.error(`[Seeder] Error on page ${page}:`, e.message);
      break;
    }
  }

  writeDB(db);
  res.json({ success: true, added, total: db.sources.length });
});

app.post('/api/seed-plutotv-live', async (req, res) => {
  try {
    const newSources = await scrapePlutoTVLive();
    if (!newSources || newSources.length === 0) {
      return res.status(500).json({ success: false, message: 'No se encontraron canales de Pluto TV en vivo.' });
    }

    const db = readDB();
    const existingIds = new Set(db.sources.map(s => s.id));
    let added = 0;

    for (let source of newSources) {
      if (!existingIds.has(source.id)) {
        if (!db.categories.some(c => (typeof c === 'string' ? c : c.name) === source.category)) {
          db.categories.push({ name: source.category, type: 'tv' });
        }
        db.sources.push(source);
        existingIds.add(source.id);
        added++;
      }
    }

    writeDB(db);
    res.json({ success: true, added, total: db.sources.length });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/seed-planetaplay-live', async (req, res) => {
  try {
    const newSources = await scrapePlanetaPlayLive();
    if (!newSources || newSources.length === 0) {
      return res.status(500).json({ success: false, message: 'No se encontraron canales de Planeta Play en vivo.' });
    }

    const db = readDB();
    const existingIds = new Set(db.sources.map(s => s.id));
    let added = 0;

    for (let source of newSources) {
      if (!existingIds.has(source.id)) {
        if (!db.categories.some(c => (typeof c === 'string' ? c : c.name) === source.category)) {
          db.categories.push({ name: source.category, type: 'tv' });
        }
        db.sources.push(source);
        existingIds.add(source.id);
        added++;
      }
    }

    writeDB(db);
    res.json({ success: true, added, total: db.sources.length });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/seed-plutotv-vod', async (req, res) => {
  try {
    const newSources = await scrapePlutoTVOnDemand();
    if (!newSources || newSources.length === 0) {
      return res.status(500).json({ success: false, message: 'No se encontraron peliculas de Pluto TV VOD.' });
    }

    const db = readDB();
    const existingIds = new Set(db.sources.map(s => s.id));
    let added = 0;

    for (let source of newSources) {
      if (!existingIds.has(source.id)) {
        if (!db.categories.some(c => (typeof c === 'string' ? c : c.name) === source.category)) {
          db.categories.push({ name: source.category, type: source.type || 'movie' });
        }
        db.sources.push(source);
        existingIds.add(source.id);
        added++;
      }
    }

    writeDB(db);
    res.json({ success: true, added, total: db.sources.length });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================================
// MOVIE & SERIES CATALOG ENDPOINTS
// ==========================================================

let catalogSyncInProgress = false;

async function runCatalogSync() {
  if (catalogSyncInProgress) {
    console.log('[CatalogWorker] Ya hay una sincronización en progreso.');
    return;
  }
  catalogSyncInProgress = true;
  console.log('[CatalogWorker] Iniciando sincronización de catálogo...');
  try {
    const result = await scrapeMovieCatalog(5);
    const db = readDB();
    if (!db.movieCatalog) db.movieCatalog = [];
    if (!db.seriesCatalog) db.seriesCatalog = [];

    const existingMovieKeys = new Set(db.movieCatalog.map(m => m.title.toLowerCase().trim()));
    let addedMovies = 0;
    for (const m of result.movies) {
      if (!existingMovieKeys.has(m.title.toLowerCase().trim())) {
        db.movieCatalog.push(m);
        existingMovieKeys.add(m.title.toLowerCase().trim());
        addedMovies++;
      }
    }

    const existingSeriesKeys = new Set(db.seriesCatalog.map(s => s.title.toLowerCase().trim()));
    let addedSeries = 0;
    for (const s of result.series) {
      if (!existingSeriesKeys.has(s.title.toLowerCase().trim())) {
        db.seriesCatalog.push(s);
        existingSeriesKeys.add(s.title.toLowerCase().trim());
        addedSeries++;
      }
    }

    // Mark top items as featured
    [...db.movieCatalog].sort((a, b) => (b.rating || 0) - (a.rating || 0)).slice(0, 20).forEach(m => { m.featured = true; });
    [...db.seriesCatalog].sort((a, b) => (b.rating || 0) - (a.rating || 0)).slice(0, 10).forEach(s => { s.featured = true; });

    writeDB(db);
    console.log(`[CatalogWorker] ✅ Sync completado. Películas: +${addedMovies} (total ${db.movieCatalog.length}), Series: +${addedSeries} (total ${db.seriesCatalog.length})`);
  } catch (e) {
    console.error('[CatalogWorker] Error:', e.message);
  } finally {
    catalogSyncInProgress = false;
  }
}

let animeSyncInProgress = false;

async function runAnimeSync() {
  if (animeSyncInProgress) {
    console.log('[AnimeWorker] Ya hay una sincronización de anime en progreso.');
    return;
  }
  animeSyncInProgress = true;
  console.log('[AnimeWorker] Iniciando sincronización de catálogo de anime...');
  try {
    const result = await scrapeAnimeCatalog(2);
    const db = readDB();
    if (!db.animeMovieCatalog) db.animeMovieCatalog = [];
    if (!db.animeSeriesCatalog) db.animeSeriesCatalog = [];

    const existingAnimeMovieKeys = new Set(db.animeMovieCatalog.map(m => m.title.toLowerCase().trim()));
    let addedMovies = 0;
    for (const m of result.animeMovies) {
      if (!existingAnimeMovieKeys.has(m.title.toLowerCase().trim())) {
        db.animeMovieCatalog.push(m);
        existingAnimeMovieKeys.add(m.title.toLowerCase().trim());
        addedMovies++;
      }
    }

    const existingAnimeSeriesKeys = new Set(db.animeSeriesCatalog.map(s => s.title.toLowerCase().trim()));
    let addedSeries = 0;
    for (const s of result.animeSeries) {
      if (!existingAnimeSeriesKeys.has(s.title.toLowerCase().trim())) {
        db.animeSeriesCatalog.push(s);
        existingAnimeSeriesKeys.add(s.title.toLowerCase().trim());
        addedSeries++;
      }
    }

    writeDB(db);
    console.log(`[AnimeWorker] ✅ Sync completado. Anime Películas: +${addedMovies} (total ${db.animeMovieCatalog.length}), Anime Series: +${addedSeries} (total ${db.animeSeriesCatalog.length})`);
  } catch (e) {
    console.error('[AnimeWorker] Error:', e.message);
  } finally {
    animeSyncInProgress = false;
  }
}

// GET /api/catalog/:type — Returns movie or series catalog
app.get('/api/catalog/:type', (req, res) => {
  const { type } = req.params; // 'movie', 'series', 'anime_movie', or 'anime_series'
  const allowedTypes = ['movie', 'series', 'anime_movie', 'anime_series'];
  if (!allowedTypes.includes(type)) {
    return res.status(400).json({ error: 'invalid type' });
  }
  const db = readDB();
  
  // Get catalog items
  let catalogItems = [];
  if (type === 'movie') catalogItems = db.movieCatalog || [];
  else if (type === 'series') catalogItems = db.seriesCatalog || [];
  else if (type === 'anime_movie') catalogItems = db.animeMovieCatalog || [];
  else if (type === 'anime_series') catalogItems = db.animeSeriesCatalog || [];
  
  // Get custom sources of the same type and map them to catalog item shape
  const customItems = (db.sources || [])
    .filter(s => s.type === type)
    .map(s => ({
      ...s,
      genres: s.genres || (s.category ? [s.category] : ['General'])
    }));

  // Combine them
  const items = [...catalogItems, ...customItems];
  
  let syncing = false;
  if (type === 'movie' || type === 'series') syncing = catalogSyncInProgress;
  if (type === 'anime_movie' || type === 'anime_series') syncing = animeSyncInProgress;

  res.json({ items, total: items.length, syncing });
});

// GET /api/plutotv - Returns only Pluto TV sources
app.get('/api/plutotv', (req, res) => {
  const db = readDB();
  const plutoSources = (db.sources || []).filter(s => s.id.startsWith('plutotv_') || s.id.startsWith('plutovod_'));
  
  // Extract categories
  const categoriesSet = new Set();
  plutoSources.forEach(s => {
    if (s.category) categoriesSet.add(s.category);
  });

  res.json({
    items: plutoSources,
    categories: Array.from(categoriesSet),
    total: plutoSources.length
  });
});

// POST /api/catalog/sync — Trigger background catalog sync
app.post('/api/catalog/sync', (req, res) => {
  if (catalogSyncInProgress) {
    return res.json({ success: true, message: 'Sincronización ya en progreso.', syncing: true });
  }
  runCatalogSync();
  res.json({ success: true, message: 'Sincronización iniciada en segundo plano.' });
});

app.post('/api/catalog/sync-anime', (req, res) => {
  if (animeSyncInProgress) {
    return res.json({ success: true, message: 'Sincronización de anime ya en progreso.', syncing: true });
  }
  runAnimeSync();
  res.json({ success: true, message: 'Sincronización de anime iniciada en segundo plano.' });
});

// POST /api/catalog/clear — Clear catalog (for reset)
app.post('/api/catalog/clear', (req, res) => {
  const db = readDB();
  const { type } = req.body;
  if (!type || type === 'movie') db.movieCatalog = [];
  if (!type || type === 'series') db.seriesCatalog = [];
  if (!type || type === 'anime_movie') db.animeMovieCatalog = [];
  if (!type || type === 'anime_series') db.animeSeriesCatalog = [];
  writeDB(db);
  res.json({ success: true, message: 'Catálogo limpiado.' });
});

// Helper to clean search titles for TMDB API search
function cleanTitle(rawTitle) {
  if (!rawTitle) return { title: '', year: null };
  let title = rawTitle;
  
  // Extract year if present (e.g., "(2023)" or "2023")
  let year = null;
  const yearMatch = title.match(/\b(19\d\d|20\d\d)\b/);
  if (yearMatch) {
    year = yearMatch[1];
  }
  
  // Strip out brackets and their contents: [Dual Latino], [1080p], etc.
  title = title.replace(/\[[^\]]+\]/g, ' ');
  // Strip out parentheses and their contents: (2023), (Dual), etc.
  title = title.replace(/\([^)]+\)/g, ' ');
  
  // Strip out common quality/audio/host keywords
  const keywords = [
    /\bdual\b/gi, /\blatino\b/gi, /\blat\b/gi, /\bcastellano\b/gi, /\bespañol\b/gi,
    /\bsubtitulado\b/gi, /\bsub\b/gi, /\benglish\b/gi, /\bingles\b/gi,
    /\b1080p\b/gi, /\b720p\b/gi, /\b4k\b/gi, /\b2160p\b/gi, /\bhd\b/gi, /\bbluray\b/gi,
    /\bwebrip\b/gi, /\bhdrip\b/gi, /\bdvdrip\b/gi, /\bx264\b/gi, /\bx265\b/gi,
    /\bh264\b/gi, /\bh265\b/gi, /\bmkv\b/gi, /\bmp4\b/gi, /\byts\b/gi,
    /\bcompleta\b/gi, /\bseason\b/gi, /\btemporada\b/gi
  ];
  
  keywords.forEach(pattern => {
    title = title.replace(pattern, ' ');
  });
  
  // Clean up whitespace
  title = title.replace(/\s+/g, ' ').trim();
  
  return { title, year };
}

// Dynamic flag to disable TMDB integration if the key is invalid (e.g. 401 Unauthorized)
let tmdbKeyValid = true;

// GET /api/metadata/:id — Returns enriched metadata for a movie/series item
// Uses TMDB if TMDB_API_KEY is set and item has tmdbId, otherwise returns stored data
app.get('/api/metadata/:id', async (req, res) => {
  const { id } = req.params;
  const { title, type, year } = req.query;
  const TMDB_KEY = process.env.TMDB_API_KEY || '3905c909305d58a27f2de32e4b6038e7';

  try {
    const db = readDB();
    // Search in all catalogs
    let item = null;
    if (db.movieCatalog) item = db.movieCatalog.find(m => m.id === id);
    if (!item && db.seriesCatalog) item = db.seriesCatalog.find(s => s.id === id);
    if (!item && db.animeMovieCatalog) item = db.animeMovieCatalog.find(s => s.id === id);
    if (!item && db.animeSeriesCatalog) item = db.animeSeriesCatalog.find(s => s.id === id);
    if (!item && db.sources) item = db.sources.find(s => s.id === id);

    // Build base metadata from stored item or query params
    const base = {
      id,
      title: item?.title || title || 'Sin título',
      type: item?.type || type || 'movie',
      year: item?.year || year || null,
      rating: item?.rating || null,
      genres: item?.genres || [],
      description: item?.description || '',
      poster: item?.poster || '',
      duration: item?.duration || null,
      languages: ['Español Latino', 'Inglés'],
      resolution: '4K Ultra HD',
      tmdbId: item?.tmdbId || null,
      backdrop: item?.backdrop || '',
      tagline: '',
      director: '',
      production: '',
      cast: [],
      youtubeId: '',
    };

    // If we have TMDB key, it is marked valid, and has tmdbId, fetch real data
    if (TMDB_KEY && tmdbKeyValid && base.tmdbId) {
      try {
        const tmdbType = base.type === 'series' ? 'tv' : 'movie';
        const tmdbRes = await axios.get(
          `https://api.themoviedb.org/3/${tmdbType}/${base.tmdbId}?api_key=${TMDB_KEY}&language=es-MX&append_to_response=credits,videos`,
          { timeout: 6000 }
        );
        const t = tmdbRes.data;
        if (t) {
          base.title = t.title || t.name || base.title;
          base.description = t.overview || base.description;
          base.year = (t.release_date || t.first_air_date || '').split('-')[0] || base.year;
          base.rating = t.vote_average ? parseFloat(t.vote_average.toFixed(1)) : base.rating;
          base.genres = (t.genres || []).map(g => g.name).slice(0, 4);
          base.duration = t.runtime || (t.episode_run_time && t.episode_run_time[0]) || base.duration;
          if (t.poster_path) base.poster = `https://image.tmdb.org/t/p/w500${t.poster_path}`;
          if (t.backdrop_path) base.backdrop = `https://image.tmdb.org/t/p/w1280${t.backdrop_path}`;
          base.tagline = t.tagline || '';
          base.production = (t.production_companies || []).slice(0, 2).map(p => p.name).join(', ');

          // Get director/creator
          if (base.type === 'series' || base.type === 'anime_series') {
            base.director = (t.created_by || []).map(c => c.name).join(', ');
          } else {
            base.director = (t.credits?.crew || []).filter(c => c.job === 'Director').map(c => c.name).join(', ');
          }

          // Get cast
          base.cast = (t.credits?.cast || []).slice(0, 6).map(c => ({
            name: c.name,
            character: c.character,
            profile_path: c.profile_path ? `https://image.tmdb.org/t/p/w185${c.profile_path}` : null
          }));

          // Get YouTube trailer
          const trailer = (t.videos?.results || []).find(v => v.site === 'YouTube' && v.type === 'Trailer') || (t.videos?.results || []).find(v => v.site === 'YouTube');
          if (trailer) base.youtubeId = trailer.key;

          // Languages from spoken_languages
          if (t.spoken_languages && t.spoken_languages.length > 0) {
            const langMap = { es: 'Español Latino', en: 'Inglés', pt: 'Portugués', fr: 'Francés', de: 'Alemán', it: 'Italiano' };
            base.languages = t.spoken_languages.map(l => langMap[l.iso_639_1] || l.name).filter(Boolean);
          }
        }
      } catch (tmdbErr) {
        console.warn(`[Metadata] TMDB fetch failed for tmdbId ${base.tmdbId}:`, tmdbErr.message);
        if (tmdbErr.response && tmdbErr.response.status === 401) {
          console.warn('[Metadata] ⚠️ La clave de TMDB no es válida (401). Configura TMDB_API_KEY en tu archivo .env para habilitar detalles de reparto y tráileres. Desactivando consultas a TMDB temporalmente.');
          tmdbKeyValid = false;
        }
      }
    } else if (TMDB_KEY && tmdbKeyValid && (base.title || title)) {
      // Search TMDB by title
      try {
        const rawSearchTitle = base.title || title;
        const cleaned = cleanTitle(rawSearchTitle);
        const searchTitle = cleaned.title || rawSearchTitle;
        const searchYear = cleaned.year || base.year || year || '';
        const tmdbType = base.type === 'series' ? 'tv' : 'movie';
        
        let searchUrl = `https://api.themoviedb.org/3/search/${tmdbType}?api_key=${TMDB_KEY}&query=${encodeURIComponent(searchTitle)}&language=es-MX`;
        if (searchYear) {
          searchUrl += `&year=${searchYear}`;
        }
        
        const searchRes = await axios.get(searchUrl, { timeout: 6000 });
        const results = searchRes.data?.results || [];
        if (results.length > 0) {
          const t = results[0];
          base.description = t.overview || base.description;
          base.year = (t.release_date || t.first_air_date || '').split('-')[0] || base.year;
          base.rating = t.vote_average ? parseFloat(t.vote_average.toFixed(1)) : base.rating;
          base.tmdbId = t.id;
          if (t.poster_path && !base.poster) base.poster = `https://image.tmdb.org/t/p/w500${t.poster_path}`;
          if (t.backdrop_path) base.backdrop = `https://image.tmdb.org/t/p/w1280${t.backdrop_path}`;

          // Fetch full details for duration/genres/credits/videos
          try {
            const detailRes = await axios.get(
              `https://api.themoviedb.org/3/${tmdbType}/${t.id}?api_key=${TMDB_KEY}&language=es-MX&append_to_response=credits,videos`,
              { timeout: 6000 }
            );
            const d = detailRes.data;
            if (d) {
              base.genres = (d.genres || []).map(g => g.name).slice(0, 4);
              base.duration = d.runtime || (d.episode_run_time && d.episode_run_time[0]) || base.duration;
              base.tagline = d.tagline || '';
              base.production = (d.production_companies || []).slice(0, 2).map(p => p.name).join(', ');
              
              // Get director/creator
              if (base.type === 'series' || base.type === 'anime_series') {
                base.director = (d.created_by || []).map(c => c.name).join(', ');
              } else {
                base.director = (d.credits?.crew || []).filter(c => c.job === 'Director').map(c => c.name).join(', ');
              }
              
              // Get cast
              base.cast = (d.credits?.cast || []).slice(0, 6).map(c => ({
                name: c.name,
                character: c.character,
                profile_path: c.profile_path ? `https://image.tmdb.org/t/p/w185${c.profile_path}` : null
              }));

              // Get YouTube trailer
              const trailer = (d.videos?.results || []).find(v => v.site === 'YouTube' && v.type === 'Trailer') || (d.videos?.results || []).find(v => v.site === 'YouTube');
              if (trailer) base.youtubeId = trailer.key;
            }
          } catch(e) {
            if (e.response && e.response.status === 401) {
              console.warn('[Metadata] ⚠️ La clave de TMDB no es válida (401). Configura TMDB_API_KEY en tu archivo .env para habilitar detalles de reparto y tráileres. Desactivando consultas a TMDB.');
              tmdbKeyValid = false;
            }
          }
        }
      } catch (searchErr) {
        console.warn(`[Metadata] TMDB search failed for "${base.title}":`, searchErr.message);
        if (searchErr.response && searchErr.response.status === 401) {
          console.warn('[Metadata] ⚠️ La clave de TMDB no es válida (401). Configura TMDB_API_KEY en tu archivo .env para habilitar detalles de reparto y tráileres. Desactivando consultas a TMDB.');
          tmdbKeyValid = false;
        }
      }
    }

    // Resolution heuristic based on available streams
    const streams = item?.streams || [];
    const hasHighQuality = streams.some(s => s.quality === '4k' || s.quality === '2160p');
    const hasHD = streams.some(s => s.quality === '1080p' || s.quality === 'hd');
    if (hasHighQuality) base.resolution = '4K Ultra HD';
    else if (hasHD) base.resolution = '1080p HD';
    else if (streams.length > 0) base.resolution = '1080p HD';
    else base.resolution = 'HD';

    // Determine languages from stream names
    const streamNames = streams.map(s => (s.name || '').toLowerCase());
    const langList = [];
    if (streamNames.some(n => n.includes('latino') || n.includes('español') || n.includes('lat'))) langList.push('Español Latino');
    if (streamNames.some(n => n.includes('ingles') || n.includes('english') || n.includes('sub'))) langList.push('Inglés');
    if (langList.length > 0) base.languages = langList;
    if (!base.languages.includes('Español Latino')) base.languages.unshift('Español Latino');

    res.json({ success: true, metadata: base });
  } catch (err) {
    console.error(`[Metadata] Error:`, err.message);
    res.json({
      success: false,
      metadata: {
        id,
        title: title || 'Sin título',
        type: type || 'movie',
        description: '',
        genres: [],
        rating: null,
        year: null,
        duration: null,
        languages: ['Español Latino'],
        resolution: 'HD',
        poster: '',
      }
    });
  }
});

// Auto-sync catalog on startup (after 5s delay, only if empty)
setTimeout(() => {
  const db = readDB();
  const movieCount = (db.movieCatalog || []).length;
  const seriesCount = (db.seriesCatalog || []).length;
  const animeMovieCount = (db.animeMovieCatalog || []).length;
  const animeSeriesCount = (db.animeSeriesCatalog || []).length;
  
  if (movieCount === 0 && seriesCount === 0) {
    console.log('[CatalogWorker] Catálogo vacío. Iniciando primera sincronización...');
    runCatalogSync();
  } else {
    console.log(`[CatalogWorker] Catálogo existente: ${movieCount} películas, ${seriesCount} series.`);
  }

  if (animeMovieCount === 0 && animeSeriesCount === 0) {
    console.log('[AnimeWorker] Catálogo anime vacío. Iniciando primera sincronización...');
    runAnimeSync();
  } else {
    console.log(`[AnimeWorker] Catálogo anime existente: ${animeMovieCount} películas, ${animeSeriesCount} series.`);
  }
}, 5000);

// Proxy to bypass CORS issues on direct streams (like .mp4, HLS playlists, or images)
app.get('/api/proxy', async (req, res) => {
  const targetUrl = req.query.url;
  const refererParam = req.query.referer;

  if (!targetUrl) {
    return res.status(400).send('Missing url parameter');
  }

  try {
    const isM3U8 = targetUrl.toLowerCase().includes('.m3u8');

    const resolveUrlSafely = (relative, base) => {
      try {
        return new URL(relative, base).toString();
      } catch (e) {
        return relative;
      }
    };

    let proxyReferer = refererParam || '';
    if (!proxyReferer) {
      try {
        const parsed = new URL(targetUrl);
        if (parsed.hostname.includes('54434687.net') || parsed.hostname.includes('verfutbol')) {
          proxyReferer = 'https://verfutbol.at/';
        } else {
          proxyReferer = parsed.origin;
        }
      } catch (e) {}
    }

    if (isM3U8) {
      // Fetch HLS playlist as text to rewrite child links
      const response = await axios({
        method: 'get',
        url: targetUrl,
        responseType: 'text',
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'Accept': '*/*',
          'Referer': proxyReferer
        }
      });

      let playlistText = response.data;
      const targetQuery = new URL(targetUrl).search; // Extract HLS session tokens (e.g. ?s=...&e=...)

      // Parse line by line to rewrite segments and sub-playlists
      const lines = playlistText.split('\n');
      const rewrittenLines = lines.map(line => {
        let trimmed = line.trim();
        if (trimmed.length === 0) return line;

        // Rewrite tags that have URI attribute (like keys)
        if (trimmed.startsWith('#')) {
          if (trimmed.includes('URI=')) {
            // Find key URIs, e.g. URI="https://..." or URI="key.key"
            const match = trimmed.match(/URI="([^"]+)"/);
            if (match && match[1]) {
              const originalUri = match[1];
              let resolvedUri = originalUri;
              if (!originalUri.startsWith('http')) {
                resolvedUri = resolveUrlSafely(originalUri, targetUrl);
              }
              if (targetQuery && !resolvedUri.includes('?')) {
                resolvedUri += targetQuery;
              }
              let proxyUri = `/api/proxy?url=${encodeURIComponent(resolvedUri)}`;
              if (refererParam) {
                proxyUri += `&referer=${encodeURIComponent(refererParam)}`;
              }
              trimmed = trimmed.replace(`URI="${originalUri}"`, `URI="${proxyUri}"`);
            }
          }
          return trimmed;
        }

        // It is a URI (relative or absolute) for a segment or sub-playlist
        let resolvedUrl = trimmed;
        if (!trimmed.startsWith('http')) {
          resolvedUrl = resolveUrlSafely(trimmed, targetUrl);
        }

        // Append HLS session query parameters if missing in the segment
        if (targetQuery && !resolvedUrl.includes('?')) {
          resolvedUrl += targetQuery;
        }

        // Return rewritten path to go through this proxy
        let rewritten = `/api/proxy?url=${encodeURIComponent(resolvedUrl)}`;
        if (refererParam) {
          rewritten += `&referer=${encodeURIComponent(refererParam)}`;
        }
        return rewritten;
      });

      res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
      // Set CORS headers manually just in case
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.setHeader('Access-Control-Allow-Headers', '*');
      return res.send(rewrittenLines.join('\n'));
    }

    // For TS segments, MP4, and images: pipe the stream directly with HTTP Range support
    const requestHeaders = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Accept': '*/*',
      'Referer': proxyReferer
    };

    if (req.headers.range) {
      requestHeaders['Range'] = req.headers.range;
    }

    const response = await axios({
      method: 'get',
      url: targetUrl,
      responseType: 'stream',
      headers: requestHeaders,
      validateStatus: () => true // Allow handling 206 Partial Content
    });

    res.status(response.status);
    
    // Copy important headers
    const contentType = response.headers['content-type'];
    if (contentType) res.setHeader('Content-Type', contentType);
    
    const contentLength = response.headers['content-length'];
    if (contentLength) res.setHeader('Content-Length', contentLength);

    const contentRange = response.headers['content-range'];
    if (contentRange) res.setHeader('Content-Range', contentRange);

    const acceptRanges = response.headers['accept-ranges'];
    if (acceptRanges) res.setHeader('Accept-Ranges', acceptRanges);

    // Set CORS headers manually
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Headers', '*');

    response.data.pipe(res);
  } catch (error) {
    console.error('Proxy error for url:', targetUrl, error.message);
    res.status(500).send(error.message);
  }
});

// Import M3U playlists
app.post('/api/import-m3u', async (req, res) => {
  const { url, rawText, category, type } = req.body;
  let m3uContent = '';
  const itemType = type || 'tv';

  if (url) {
    try {
      const response = await axios.get(url);
      m3uContent = response.data;
    } catch (error) {
      return res.status(400).json({ error: 'Failed to fetch M3U URL: ' + error.message });
    }
  } else if (rawText) {
    m3uContent = rawText;
  } else {
    return res.status(400).json({ error: 'Either url or rawText is required' });
  }

  const targetCategory = category || 'IPTV Importado';
  const db = readDB();

  // Ensure category exists
  if (!db.categories.some(c => (typeof c === 'string' ? c : c.name) === targetCategory)) {
    db.categories.push({ name: targetCategory, type: itemType });
  }

  // Simple M3U parser
  const lines = m3uContent.split(/\r?\n/);
  const importedSources = [];
  let currentSource = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith('#EXTINF:')) {
      currentSource = {
        id: 'iptv_' + Date.now().toString(36) + '_' + Math.random().toString(36).substr(2, 5),
        type: itemType,
        category: targetCategory,
        poster: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400',
        description: itemType === 'tv' ? 'Canal de TV importado vía playlist M3U.' : 'Contenido importado vía playlist M3U.'
      };

      const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
      if (logoMatch && logoMatch[1]) {
        currentSource.poster = logoMatch[1];
      }

      const groupMatch = line.match(/group-title="([^"]+)"/i);
      if (groupMatch && groupMatch[1] && !category) {
        const groupName = groupMatch[1].trim();
        if (groupName) {
          currentSource.category = groupName;
          if (!db.categories.some(c => (typeof c === 'string' ? c : c.name) === groupName)) {
            db.categories.push({ name: groupName, type: itemType });
          }
        }
      }

      const commaIndex = line.lastIndexOf(',');
      if (commaIndex !== -1) {
        currentSource.title = line.substring(commaIndex + 1).trim();
      } else {
        currentSource.title = 'Canal Importado ' + (importedSources.length + 1);
      }
    } else if (line.startsWith('http') && currentSource) {
      currentSource.streams = [{
        name: 'Stream Directo',
        url: line,
        resolver: 'direct'
      }];
      importedSources.push(currentSource);
      currentSource = null;
    }
  }

  // Save to DB
  if (importedSources.length > 0) {
    db.sources.push(...importedSources);
    writeDB(db);
  }

  res.json({
    message: `Se importaron con éxito ${importedSources.length} canales.`,
    count: importedSources.length
  });
});

// Scrape helper for extracting video source URLs from common media hosting pages
app.post('/api/scrape', async (req, res) => {
  const { url } = req.body;
  if (!url) {
    return res.status(400).json({ error: 'URL is required' });
  }

  try {
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      }
    });

    const html = response.data;
    
    const m3u8Regex = /(https?:\/\/[^"'\s>]+\.m3u8[^"'\s>]*)/gi;
    const mp4Regex = /(https?:\/\/[^"'\s>]+\.mp4[^"'\s>]*)/gi;

    const m3u8Matches = html.match(m3u8Regex) || [];
    const mp4Matches = html.match(mp4Regex) || [];

    const streams = [];

    const uniqueM3u8 = [...new Set(m3u8Matches)].filter(link => !link.includes('google') && !link.includes('facebook'));
    const uniqueMp4 = [...new Set(mp4Matches)].filter(link => !link.includes('google') && !link.includes('facebook'));

    uniqueM3u8.forEach(link => {
      streams.push({ name: 'Auto-Detectado (HLS)', url: link, resolver: 'direct' });
    });

    uniqueMp4.forEach(link => {
      streams.push({ name: 'Auto-Detectado (MP4)', url: link, resolver: 'direct' });
    });

    if (streams.length > 0) {
      return res.json({ success: true, streams });
    }

    return res.json({
      success: false,
      message: 'No se encontraron enlaces directos de video, se sugiere usar reproducción vía Iframe Protegido.',
      streams: [{
        name: 'Reproductor Externo (Iframe)',
        url: url,
        resolver: 'iframe'
      }]
    });
  } catch (error) {
    console.error('Error scraping:', error);
    res.status(500).json({ error: 'Error al intentar analizar la página: ' + error.message });
  }
});

initializeDB().then(() => {
  app.listen(PORT, () => {
    console.log(`Servidor corriendo en el puerto ${PORT}`);
    // Inicializar cache de deportes en segundo plano al arrancar el servidor
    refreshSportsCache().catch(err => {
      console.error("[Startup] Error al inicializar cache de deportes:", err.message);
    });
  });
}).catch(err => {
  console.error("[DB] Error fatal al inicializar la base de datos:", err);
  process.exit(1);
});
