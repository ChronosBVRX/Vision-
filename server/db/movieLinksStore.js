const { db, getQuery, allQuery, runQuery } = require('./database');

// ── Server priority dictionary (static reputation) ──
const SERVER_PRIORITY = {
  'fembed': 1,
  'mega': 2,
  'streamwish': 3,
  'voesx': 4,
  'voe': 4,
  'filemoon': 5,
  'mixdrop': 6,
  'okru': 7,
  'netu': 8,
  'mp4movies': 9,
  'desconocido': 99
};

function getServerPriority(serverName) {
  if (!serverName) return 99;
  const key = serverName.toLowerCase().replace(/www\./g, '').split('.')[0];
  return SERVER_PRIORITY[key] ?? 99;
}

// ── TMDB resolution helpers ──
const TMDB_KEY = process.env.TMDB_API_KEY || '';

async function searchTMDB(title, year = null, type = 'movie') {
  if (!TMDB_KEY) return null;
  try {
    const axios = require('axios');
    const tmdbType = type === 'series' ? 'tv' : 'movie';
    let url = `https://api.themoviedb.org/3/search/${tmdbType}?api_key=${TMDB_KEY}&query=${encodeURIComponent(title)}&language=es-MX`;
    if (year) url += `&year=${year}`;
    const res = await axios.get(url, { timeout: 5000 });
    const results = res.data?.results || [];
    if (results.length > 0) {
      const t = results[0];
      return {
        tmdb_id: t.id,
        title: t.title || t.name,
        year: (t.release_date || t.first_air_date || '').split('-')[0],
        poster: t.poster_path ? `https://image.tmdb.org/t/p/w500${t.poster_path}` : null,
        backdrop: t.backdrop_path ? `https://image.tmdb.org/t/p/w1280${t.backdrop_path}` : null,
        description: t.overview || '',
        rating: t.vote_average ? parseFloat(t.vote_average.toFixed(1)) : null,
        genres: (t.genre_ids || []).map(() => null)
      };
    }
  } catch (e) {
    console.warn(`[MovieLinks] TMDB search failed for "${title}": ${e.message}`);
  }
  return null;
}

// ── Core operations ──

async function upsertMovie({ title, year, type = 'movie', tmdbId, poster, backdrop, description, rating, genres, duration, languages, director, youtubeId }) {
  const existing = tmdbId ? await getQuery(`SELECT * FROM movies WHERE tmdb_id = ?`, [tmdbId]) : null;
  if (existing) return existing;

  // Try to find by title+year
  let movie = null;
  if (title && year) {
    movie = await getQuery(`SELECT * FROM movies WHERE title = ? AND year = ?`, [title, year]);
  }
  if (!movie) {
    movie = await getQuery(`SELECT * FROM movies WHERE title = ?`, [title]);
  }
  if (movie) return movie;

  const id = `mv_${Date.now().toString(36)}_${Math.random().toString(36).substr(2, 8)}`;
  const genresStr = genres ? (Array.isArray(genres) ? JSON.stringify(genres) : genres) : '[]';
  const langsStr = languages ? (Array.isArray(languages) ? JSON.stringify(languages) : languages) : '[]';

  await runQuery(`
    INSERT INTO movies (id, tmdb_id, title, year, type, poster, backdrop, description, rating, genres, duration, languages, director, youtube_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `, [id, tmdbId || null, title, year || null, type, poster || '', backdrop || '', description || '',
       rating || null, genresStr, duration || null, langsStr, director || '', youtubeId || '']);

  const created = await getQuery(`SELECT * FROM movies WHERE id = ?`, [id]);
  return created;
}

async function upsertMovieLink(movieId, { sourceWebsite, serverName, url, language = 'Español Latino', resolver = 'iframe', quality = 'HD' }) {
  const existing = await getQuery(`SELECT * FROM movie_links WHERE movie_id = ? AND url = ?`, [movieId, url]);
  if (existing) {
    await runQuery(`UPDATE movie_links SET last_checked = CURRENT_TIMESTAMP, is_active = 1 WHERE id = ?`, [existing.id]);
    return existing;
  }
  const id = `link_${Date.now().toString(36)}_${Math.random().toString(36).substr(2, 8)}`;
  await runQuery(`
    INSERT INTO movie_links (id, movie_id, source_website, server_name, url, language, resolver, quality)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `, [id, movieId, sourceWebsite, serverName, url, language, resolver, quality]);
  return await getQuery(`SELECT * FROM movie_links WHERE id = ?`, [id]);
}

// ── Query / Play endpoint ──

async function getBestStream(movieId, preferredLanguage = 'auto', excludeServers = []) {
  const movie = await getQuery(`SELECT * FROM movies WHERE id = ?`, [movieId]);
  if (!movie) return null;

  let links = await allQuery(`SELECT * FROM movie_links WHERE movie_id = ? AND is_active = 1 AND score > 0`, [movieId]);
  if (!links || links.length === 0) return null;

  // Deduplicate by URL
  const seen = new Set();
  links = links.filter(l => {
    const key = l.url;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  // Filter excluded
  if (excludeServers.length > 0) {
    const excLower = excludeServers.map(s => s.toLowerCase());
    links = links.filter(l => !excLower.some(exc => (l.server_name || '').toLowerCase().includes(exc)));
  }

  // Language preference order
  const langOrder = ['Español Latino', 'Subtitulado', 'Español España', 'Inglés', 'Cualquier fuente disponible'];
  const primaryLang = preferredLanguage === 'auto' ? 'Español Latino' : langOrder.find(l => l.toLowerCase().includes(preferredLanguage.toLowerCase())) || 'Español Latino';
  const langScore = l => {
    const idx = langOrder.indexOf(l.language);
    return idx === -1 ? 99 : idx;
  };

  // Sort: language → server priority → score desc → latency asc
  links.sort((a, b) => {
    const langA = langScore(a);
    const langB = langScore(b);
    // Put primary language first
    if (a.language === primaryLang && b.language !== primaryLang) return -1;
    if (a.language !== primaryLang && b.language === primaryLang) return 1;
    if (langA !== langB) return langA - langB;

    const priA = getServerPriority(a.server_name);
    const priB = getServerPriority(b.server_name);
    if (priA !== priB) return priA - priB;

    if (b.score !== a.score) return b.score - a.score;

    return (a.latency_ms || 0) - (b.latency_ms || 0);
  });

  return {
    movie,
    bestLink: links[0],
    alternatives: links.slice(1)
  };
}

async function getMovieWithLinks(movieId) {
  const movie = await getQuery(`SELECT * FROM movies WHERE id = ?`, [movieId]);
  if (!movie) return null;
  const links = await allQuery(`SELECT * FROM movie_links WHERE movie_id = ? AND is_active = 1 ORDER BY score DESC, latency_ms ASC`, [movieId]);
  return { movie, links };
}

async function getOrCreateMovieByTMDB(tmdbId, type = 'movie') {
  let movie = await getQuery(`SELECT * FROM movies WHERE tmdb_id = ?`, [tmdbId]);
  if (movie) return movie;

  if (!TMDB_KEY) return null;
  try {
    const axios = require('axios');
    const tmdbType = type === 'series' ? 'tv' : 'movie';
    const res = await axios.get(`https://api.themoviedb.org/3/${tmdbType}/${tmdbId}?api_key=${TMDB_KEY}&language=es-MX`, { timeout: 6000 });
    const t = res.data;
    const movieData = {
      title: t.title || t.name,
      year: (t.release_date || t.first_air_date || '').split('-')[0],
      type,
      poster: t.poster_path ? `https://image.tmdb.org/t/p/w500${t.poster_path}` : '',
      backdrop: t.backdrop_path ? `https://image.tmdb.org/t/p/w1280${t.backdrop_path}` : '',
      description: t.overview || '',
      rating: t.vote_average ? parseFloat(t.vote_average.toFixed(1)) : null,
      genres: (t.genres || []).map(g => g.name),
      duration: t.runtime || (t.episode_run_time && t.episode_run_time[0]) || null,
      tmdbId: t.id
    };
    return await upsertMovie(movieData);
  } catch (e) {
    console.warn(`[MovieLinks] Error fetching TMDB ${tmdbId}: ${e.message}`);
    return null;
  }
}

// ── Health check / scoring ──

async function updateLinkScore(linkId, latencyMs, success) {
  const link = await getQuery(`SELECT * FROM movie_links WHERE id = ?`, [linkId]);
  if (!link) return;

  let score = link.score;
  if (success) {
    if (latencyMs < 300) score = Math.min(100, score + 10);
    else if (latencyMs < 1000) score = Math.min(100, score + 5);
    else if (latencyMs > 3000) score = Math.max(0, score - 10);
  } else {
    score = Math.max(0, score - 20);
  }

  await runQuery(`
    UPDATE movie_links SET score = ?, latency_ms = ?, last_checked = CURRENT_TIMESTAMP, is_active = ?
    WHERE id = ?
  `, [score, latencyMs, score > 0 ? 1 : 0, linkId]);
}

async function reportFailedLink(linkId) {
  await runQuery(`
    UPDATE movie_links SET score = MAX(0, score - 25), last_checked = CURRENT_TIMESTAMP
    WHERE id = ?
  `, [linkId]);
}

async function runHealthCheck(limit = 20) {
  const stale = await allQuery(`
    SELECT ml.*, m.title as movie_title
    FROM movie_links ml
    JOIN movies m ON m.id = ml.movie_id
    WHERE ml.is_active = 1
    ORDER BY ml.last_checked ASC NULLS FIRST
    LIMIT ?
  `, [limit]);

  let checked = 0;
  for (const link of stale) {
    try {
      const axios = require('axios');
      const start = Date.now();
      const resp = await axios.head(link.url, { timeout: 5000 });
      const latency = Date.now() - start;
      const success = resp.status >= 200 && resp.status < 400;
      await updateLinkScore(link.id, latency, success);
      checked++;
    } catch (e) {
      await updateLinkScore(link.id, 9999, false);
      checked++;
    }
  }
  return checked;
}

// ── Search / List ──

async function searchMovies(query, type = null) {
  const typeFilter = type ? `AND type = ?` : '';
  const rows = await allQuery(`
    SELECT * FROM movies
    WHERE (title LIKE ? OR description LIKE ?) ${typeFilter}
    ORDER BY rating DESC NULLS LAST
    LIMIT 50
  `, [`%${query}%`, `%${query}%`, ...(type ? [type] : [])]);
  return rows;
}

async function listMovies(type = null, limit = 100, offset = 0) {
  const typeFilter = type ? `WHERE type = ?` : '';
  const rows = await allQuery(`
    SELECT m.*, (SELECT COUNT(*) FROM movie_links ml WHERE ml.movie_id = m.id AND ml.is_active = 1) as link_count
    FROM movies m ${typeFilter}
    ORDER BY m.rating DESC NULLS LAST, m.updated_at DESC
    LIMIT ? OFFSET ?
  `, [...(type ? [type] : []), limit, offset]);
  return rows;
}

module.exports = {
  upsertMovie,
  upsertMovieLink,
  getBestStream,
  getMovieWithLinks,
  getOrCreateMovieByTMDB,
  updateLinkScore,
  reportFailedLink,
  runHealthCheck,
  searchMovies,
  listMovies,
  getServerPriority,
  searchTMDB
};
