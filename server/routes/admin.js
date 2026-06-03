const express = require('express');
const router = express.Router();
const { allQuery, getQuery, runQuery } = require('../db/database');

// Basic Auth Middleware
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';

const authMiddleware = (req, res, next) => {
  // We'll use a simple x-admin-password header for now, or query param
  const token = req.headers['x-admin-password'] || req.query.admin_pass;
  if (token === ADMIN_PASSWORD) {
    next();
  } else {
    res.status(401).json({ error: 'No autorizado. Se requiere contraseña de administrador.' });
  }
};

router.use(authMiddleware);

// ==========================================
// SOURCES CRUD
// ==========================================
router.post('/sources', async (req, res) => {
  const newSource = req.body;
  if (!newSource.title || !newSource.type || !newSource.category) {
    return res.status(400).json({ error: 'Title, type, and category are required' });
  }

  newSource.id = newSource.id || 'src_' + Date.now().toString(36);
  try {
    await runQuery(
      `INSERT OR REPLACE INTO sources (id, type, category, data) VALUES (?, ?, ?, ?)`,
      [newSource.id, newSource.type, newSource.category, JSON.stringify(newSource)]
    );
    res.status(201).json(newSource);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

router.put('/sources/:id', async (req, res) => {
  const id = req.params.id;
  const updatedSource = req.body;
  updatedSource.id = id;

  try {
    const row = await getQuery(`SELECT * FROM sources WHERE id = ?`, [id]);
    if (!row) {
      return res.status(404).json({ error: 'Source not found' });
    }
    
    const category = updatedSource.category || updatedSource.siteName || (updatedSource.genres && updatedSource.genres[0]) || row.category;
    
    await runQuery(
      `UPDATE sources SET type = ?, category = ?, data = ? WHERE id = ?`,
      [updatedSource.type || row.type, category, JSON.stringify(updatedSource), id]
    );
    res.json(updatedSource);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

router.delete('/sources/:id', async (req, res) => {
  const id = req.params.id;
  try {
    const result = await runQuery(`DELETE FROM sources WHERE id = ?`, [id]);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Source not found' });
    }
    res.json({ message: 'Source deleted successfully' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ==========================================
// CATEGORIES
// ==========================================
router.post('/categories', async (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'Category name is required' });

  try {
    await runQuery(`INSERT OR IGNORE INTO categories (name) VALUES (?)`, [name]);
    const cats = await allQuery(`SELECT name FROM categories`);
    res.status(201).json(cats.map(c => c.name));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

router.delete('/categories', async (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'Category name is required' });

  try {
    await runQuery(`DELETE FROM categories WHERE name = ?`, [name]);
    await runQuery(`DELETE FROM sources WHERE category = ?`, [name]);
    
    const cats = await allQuery(`SELECT name FROM categories`);
    res.json(cats.map(c => c.name));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ==========================================
// SETTINGS
// ==========================================
router.post('/settings', async (req, res) => {
  try {
    for (const [key, value] of Object.entries(req.body)) {
      await runQuery(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, [key, JSON.stringify(value)]);
    }
    
    const rows = await allQuery(`SELECT * FROM settings`);
    const settings = {};
    rows.forEach(r => { settings[r.key] = JSON.parse(r.value); });
    res.json(settings);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
