const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

const dbPath = path.join(__dirname, '../../data/database.sqlite');
const jsonDbPath = path.join(__dirname, '../../database.json');

// Ensure data dir exists
if (!fs.existsSync(path.join(__dirname, '../../data'))) {
  fs.mkdirSync(path.join(__dirname, '../../data'), { recursive: true });
}

const db = new sqlite3.Database(dbPath);

// Helper for Promises
const runQuery = (query, params = []) => {
  return new Promise((resolve, reject) => {
    db.run(query, params, function (err) {
      if (err) reject(err);
      else resolve(this);
    });
  });
};

const getQuery = (query, params = []) => {
  return new Promise((resolve, reject) => {
    db.get(query, params, (err, row) => {
      if (err) reject(err);
      else resolve(row);
    });
  });
};

const allQuery = (query, params = []) => {
  return new Promise((resolve, reject) => {
    db.all(query, params, (err, rows) => {
      if (err) reject(err);
      else resolve(rows);
    });
  });
};

// Initialize DB schema and migrate JSON if needed
const initDB = async () => {
  await runQuery(`
    CREATE TABLE IF NOT EXISTS categories (
      name TEXT PRIMARY KEY,
      type TEXT DEFAULT 'movie'
    )
  `);

  try {
    await runQuery(`ALTER TABLE categories ADD COLUMN type TEXT DEFAULT 'movie'`);
  } catch (e) {
    // Ignorar si la columna ya existe
  }

  await runQuery(`
    CREATE TABLE IF NOT EXISTS sources (
      id TEXT PRIMARY KEY,
      type TEXT,
      category TEXT,
      data JSON
    )
  `);

  await runQuery(`
    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value JSON
    )
  `);

  // ── New normalized schema: movies + movie_links ──
  await runQuery(`
    CREATE TABLE IF NOT EXISTS movies (
      id TEXT PRIMARY KEY,
      tmdb_id INTEGER UNIQUE,
      imdb_id TEXT,
      title TEXT NOT NULL,
      year INTEGER,
      type TEXT DEFAULT 'movie',
      poster TEXT,
      backdrop TEXT,
      description TEXT,
      rating REAL,
      genres TEXT,
      duration INTEGER,
      languages TEXT,
      cast_cache TEXT,
      director TEXT,
      youtube_id TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
  `);

  await runQuery(`
    CREATE TABLE IF NOT EXISTS movie_links (
      id TEXT PRIMARY KEY,
      movie_id TEXT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
      source_website TEXT NOT NULL,
      server_name TEXT NOT NULL,
      url TEXT NOT NULL,
      language TEXT DEFAULT 'Español Latino',
      resolver TEXT DEFAULT 'iframe',
      quality TEXT DEFAULT 'HD',
      score INTEGER DEFAULT 100,
      latency_ms INTEGER DEFAULT 0,
      last_checked DATETIME,
      is_active INTEGER DEFAULT 1,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(movie_id, url)
    )
  `);

  await runQuery(`CREATE INDEX IF NOT EXISTS idx_movie_links_movie_id ON movie_links(movie_id)`);
  await runQuery(`CREATE INDEX IF NOT EXISTS idx_movie_links_score ON movie_links(score DESC)`);
  await runQuery(`CREATE INDEX IF NOT EXISTS idx_movie_links_active ON movie_links(is_active)`);

  // ── Sports schema: events, channel matches, watch options ──
  await runQuery(`
    CREATE TABLE IF NOT EXISTS sports_events (
      id TEXT PRIMARY KEY,
      sport TEXT,
      league TEXT,
      season TEXT,
      home_team TEXT,
      away_team TEXT,
      title TEXT,
      start_time_utc TEXT,
      status TEXT,
      country TEXT,
      poster TEXT,
      interest_score INTEGER DEFAULT 0,
      source_api TEXT,
      raw_json TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
  `);

  await runQuery(`
    CREATE TABLE IF NOT EXISTS sports_event_channels (
      id TEXT PRIMARY KEY,
      event_id TEXT NOT NULL,
      channel_id TEXT,
      channel_name TEXT,
      confidence INTEGER DEFAULT 0,
      match_reason TEXT,
      FOREIGN KEY(event_id) REFERENCES sports_events(id)
    )
  `);

  await runQuery(`
    CREATE TABLE IF NOT EXISTS sports_watch_options (
      id TEXT PRIMARY KEY,
      event_id TEXT NOT NULL,
      type TEXT,
      label TEXT,
      url TEXT,
      channel_id TEXT,
      legal_status TEXT DEFAULT 'unknown',
      priority INTEGER DEFAULT 0,
      FOREIGN KEY(event_id) REFERENCES sports_events(id)
    )
  `);

  await runQuery(`CREATE INDEX IF NOT EXISTS idx_sports_events_start ON sports_events(start_time_utc)`);
  await runQuery(`CREATE INDEX IF NOT EXISTS idx_sports_events_score ON sports_events(interest_score DESC)`);
  await runQuery(`CREATE INDEX IF NOT EXISTS idx_sec_event_id ON sports_event_channels(event_id)`);
  await runQuery(`CREATE INDEX IF NOT EXISTS idx_swo_event_id ON sports_watch_options(event_id)`);

  // ── Migrate existing sources into new schema ──
  try {
    const moviesCount = await getQuery(`SELECT COUNT(*) as count FROM movies`);
    if (moviesCount.count === 0) {
      console.log('[DB] Migrando fuentes existentes al nuevo schema movies/movie_links...');
      const srcRows = await allQuery(`SELECT * FROM sources WHERE type IN ('movie', 'series')`);
      let migrated = 0;
      for (const row of srcRows) {
        try {
          const data = JSON.parse(row.data);
          if (!data.title) continue;
          const movieId = data.id || `mv_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
          const tmdbId = data.tmdbId || null;
          await runQuery(`
            INSERT OR IGNORE INTO movies (id, tmdb_id, title, year, type, poster, backdrop, description, rating, genres, duration, languages, director, youtube_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `, [
            movieId, tmdbId, data.title, data.year || null, data.type || 'movie',
            data.poster || '', data.backdrop || '', data.description || '',
            data.rating || null, JSON.stringify(data.genres || []), data.duration || null,
            JSON.stringify(data.languages || []), data.director || '', data.youtubeId || ''
          ]);
          // Migrate streams to movie_links
          const streams = data.streams || [];
          for (const stream of streams) {
            if (!stream.url) continue;
            const linkId = `link_${Date.now()}_${Math.random().toString(36).substr(2, 8)}`;
            const serverName = stream.name || 'Desconocido';
            await runQuery(`
              INSERT OR IGNORE INTO movie_links (id, movie_id, source_website, server_name, url, language, resolver, quality)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            `, [
              linkId, movieId, data.siteName || 'Legacy', serverName, stream.url,
              stream.language || 'Español Latino', stream.resolver || 'iframe',
              stream.quality || 'HD'
            ]);
          }
          migrated++;
        } catch (e) {
          console.warn(`[DB] Error migrando source ${row.id}: ${e.message}`);
        }
      }
      console.log(`[DB] Migración a nuevo schema completada: ${migrated} películas/series migradas.`);
    }
  } catch (e) {
    console.warn(`[DB] Error en migración a nuevo schema: ${e.message}`);
  }

  // Check if we need to migrate from database.json
  const sourcesCount = await getQuery(`SELECT COUNT(*) as count FROM sources`);
  if (sourcesCount.count === 0 && fs.existsSync(jsonDbPath)) {
    console.log('[DB] Iniciando migración desde database.json a SQLite...');
    try {
      const rawData = fs.readFileSync(jsonDbPath, 'utf8');
      const jsonData = JSON.parse(rawData);

      // Migrate all sources (sources, movieCatalog, seriesCatalog)
      const allSources = [
        ...(jsonData.sources || []),
        ...(jsonData.movieCatalog || []),
        ...(jsonData.seriesCatalog || [])
      ];

      // Migrate Categories
      if (jsonData.categories) {
        for (const cat of jsonData.categories) {
          let catType = 'movie';
          const firstSource = allSources.find(s => s.category === cat);
          if (firstSource) {
            catType = firstSource.type || 'movie';
          }
          await runQuery(`INSERT OR IGNORE INTO categories (name, type) VALUES (?, ?)`, [cat, catType]);
        }
      }



      for (const item of allSources) {
        // Ensure ID
        const itemId = item.id || 'src_' + Date.now().toString(36) + Math.random().toString(36).substr(2, 5);
        item.id = itemId;
        
        // Derive category
        let category = item.category || item.siteName;
        if (!category && item.genres && item.genres.length > 0) category = item.genres[0];
        if (!category) category = item.type === 'movie' ? 'Películas - General' : item.type === 'series' ? 'Series - General' : 'Canales en Vivo';

        await runQuery(`
          INSERT OR REPLACE INTO sources (id, type, category, data) 
          VALUES (?, ?, ?, ?)
        `, [itemId, item.type || 'tv', category, JSON.stringify(item)]);
      }

      // Migrate Settings
      if (jsonData.settings) {
        for (const [key, value] of Object.entries(jsonData.settings)) {
          await runQuery(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, [key, JSON.stringify(value)]);
        }
      }

      console.log('[DB] Migración a SQLite completada exitosamente.');
      
      // Opcionalmente renombrar el json para evitar migraciones dobles si falla el conteo por alguna razón,
      // pero con el count === 0 check es suficiente. Renombrarlo es más seguro:
      fs.renameSync(jsonDbPath, jsonDbPath + '.backup');

    } catch (e) {
      console.error('[DB] Error durante la migración de database.json:', e);
    }
  } else {
    console.log('[DB] SQLite inicializado.');
  }
};

module.exports = {
  db,
  initDB,
  runQuery,
  getQuery,
  allQuery
};
