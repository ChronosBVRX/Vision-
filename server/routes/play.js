const express = require('express');
const router = express.Router();
const { getBestStream, getMovieWithLinks, listMovies, searchMovies, runHealthCheck, reportFailedLink, updateLinkScore } = require('../db/movieLinksStore');

// GET /api/movies/:id/play — Resolves best playable stream via adapter pipeline
router.get('/movies/:id/play', async (req, res) => {
  const movieId = req.params.id;
  const preferredLanguage = req.query.lang || 'auto';
  let excludeServers = [];
  if (req.query.excludeServer) {
    excludeServers = Array.isArray(req.query.excludeServer) ? req.query.excludeServer : [req.query.excludeServer];
  }

  try {
    // Try normalized DB first
    const result = await getBestStream(movieId, preferredLanguage, excludeServers);
    if (result) {
      // Reconstruct movie object from normalized DB and pass through the adapter pipeline
      const { resolveBestVideoSource } = require('../resolvers/videoSourceResolver');
      const movieObj = {
        id: movieId,
        title: result.movie.title,
        type: result.movie.type,
        siteName: result.bestLink.source_website || 'VOD',
        streams: [result.bestLink, ...result.alternatives].map(l => ({
          url: l.url,
          name: l.server_name,
          language: l.language,
          resolver: l.resolver || 'iframe',
          quality: l.quality || 'HD'
        }))
      };
      const resolved = await resolveBestVideoSource({ movieId, movieObj, preferredLanguage, excludeServers });
      if (resolved.success) {
        resolved.alternatives = result.alternatives.map(l => ({
          id: l.id, server: l.server_name, source: l.source_website,
          url: l.url, language: l.language, quality: l.quality,
          score: l.score, latency_ms: l.latency_ms
        }));
        return res.json(resolved);
      }
    }

    // Fallback: try legacy resolver directly from sources table
    const { resolveBestVideoSource } = require('../resolvers/videoSourceResolver');
    const legacyResult = await resolveBestVideoSource({ movieId, preferredLanguage, excludeServers });
    if (legacyResult.success) return res.json(legacyResult);

    // Last resort: return raw URL from normalized DB if available
    if (result) {
      return res.json({
        success: true,
        movieId,
        title: result.movie.title,
        poster: result.movie.poster,
        year: result.movie.year,
        type: result.movie.type,
        selectedLanguage: result.bestLink.language,
        selectedServer: result.bestLink.server_name,
        stream: {
          url: result.bestLink.url,
          name: result.bestLink.server_name,
          resolver: result.bestLink.resolver || 'direct',
          quality: result.bestLink.quality || 'HD',
          headers: {}
        },
        alternatives: result.alternatives.map(l => ({
          id: l.id, server: l.server_name, source: l.source_website,
          url: l.url, language: l.language, quality: l.quality,
          score: l.score, latency_ms: l.latency_ms
        })),
        attempts: []
      });
    }

    res.json({ success: false, movieId, error: 'No hay fuentes reproducibles disponibles.', attempts: [] });
  } catch (err) {
    console.error(`[Play] Error:`, err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

// GET /api/movies/:id/links — List all links for a movie
router.get('/movies/:id/links', async (req, res) => {
  try {
    const data = await getMovieWithLinks(req.params.id);
    if (!data) return res.status(404).json({ error: 'Movie not found' });
    res.json({ success: true, movie: data.movie, links: data.links });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/movies/:linkId/report-fail — Report a failed link (user feedback)
router.post('/movies/link/:linkId/report-fail', async (req, res) => {
  try {
    await reportFailedLink(req.params.linkId);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/movies/:linkId/score — Update link score (from adapter results)
router.post('/movies/link/:linkId/score', async (req, res) => {
  const { latencyMs, success } = req.body;
  try {
    await updateLinkScore(req.params.linkId, latencyMs || 0, success !== false);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/movies/search?q=&type= — Search normalized movies
router.get('/movies/search', async (req, res) => {
  const { q, type } = req.query;
  if (!q) return res.status(400).json({ error: 'Missing query parameter q' });
  try {
    const results = await searchMovies(q, type || null);
    res.json({ success: true, items: results, total: results.length });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GET /api/movies/list — List normalized movies
router.get('/movies/list', async (req, res) => {
  const { type, limit = 100, offset = 0 } = req.query;
  try {
    const items = await listMovies(type || null, parseInt(limit), parseInt(offset));
    res.json({ success: true, items, total: items.length });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST /api/healthcheck/links — Trigger background health check
router.post('/healthcheck/links', async (req, res) => {
  try {
    const checked = await runHealthCheck(req.query.limit || 20);
    res.json({ success: true, checked });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
