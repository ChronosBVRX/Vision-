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

      // Migrate all sources (sources, movieCatalog, seriesCatalog)
      const allSources = [
        ...(jsonData.sources || []),
        ...(jsonData.movieCatalog || []),
        ...(jsonData.seriesCatalog || [])
      ];

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
