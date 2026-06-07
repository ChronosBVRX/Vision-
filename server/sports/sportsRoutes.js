const express = require('express');
const router = express.Router();
const { refreshSportsEvents } = require('./sportsWorker');

let memDBRef = null;
let allQueryRef = null;
let runQueryRef = null;
let getQueryRef = null;

function initSportsRoutes(memDB, allQuery, getQuery, runQuery) {
  memDBRef = memDB;
  allQueryRef = allQuery;
  getQueryRef = getQuery;
  runQueryRef = runQuery;
}

router.get('/sports/events', async (req, res) => {
  try {
    const { region, days, sport, minScore, limit } = req.query;
    let query = 'SELECT * FROM sports_events WHERE 1=1';
    const params = [];

    if (sport) {
      query += ' AND LOWER(sport) LIKE ?';
      params.push(`%${sport.toLowerCase()}%`);
    }

    if (minScore) {
      query += ' AND interest_score >= ?';
      params.push(parseInt(minScore));
    }

    query += ' ORDER BY interest_score DESC, start_time_utc ASC';

    if (limit) {
      query += ' LIMIT ?';
      params.push(parseInt(limit));
    } else {
      query += ' LIMIT 100';
    }

    const events = await allQueryRef(query, params);

    const enriched = [];
    for (const event of events) {
      const channels = await allQueryRef('SELECT * FROM sports_event_channels WHERE event_id = ? ORDER BY confidence DESC', [event.id]);
      const watchOptions = await allQueryRef('SELECT * FROM sports_watch_options WHERE event_id = ? ORDER BY priority DESC', [event.id]);

      let eventData;
      try { eventData = JSON.parse(event.raw_json || '{}'); } catch(e) { eventData = {}; }

      enriched.push({
        id: event.id,
        sport: event.sport,
        league: event.league,
        season: event.season,
        homeTeam: event.home_team,
        awayTeam: event.away_team,
        title: event.title,
        startTime: event.start_time_utc,
        status: event.status,
        country: event.country,
        poster: event.poster,
        interestScore: event.interest_score,
        sourceApi: event.source_api,
        homeTeamBadge: eventData.strHomeTeamBadge || '',
        awayTeamBadge: eventData.strAwayTeamBadge || '',
        channels: channels.map(c => ({
          channelId: c.channel_id,
          channelName: c.channel_name,
          confidence: c.confidence,
          matchReason: c.match_reason
        })),
        watchOptions: watchOptions.map(w => ({
          type: w.type,
          label: w.label,
          url: w.url,
          channelId: w.channel_id,
          legalStatus: w.legal_status,
          priority: w.priority
        }))
      });
    }

    res.json({ success: true, events: enriched, count: enriched.length });
  } catch (err) {
    console.error('[SportsRoutes] Error GET /sports/events:', err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

router.get('/sports/events/:id', async (req, res) => {
  try {
    const event = await getQueryRef('SELECT * FROM sports_events WHERE id = ?', [req.params.id]);
    if (!event) {
      return res.status(404).json({ success: false, error: 'Evento no encontrado' });
    }

    const channels = await allQueryRef('SELECT * FROM sports_event_channels WHERE event_id = ? ORDER BY confidence DESC', [event.id]);
    const watchOptions = await allQueryRef('SELECT * FROM sports_watch_options WHERE event_id = ? ORDER BY priority DESC', [event.id]);

    let eventData;
    try { eventData = JSON.parse(event.raw_json || '{}'); } catch(e) { eventData = {}; }

    res.json({
      success: true,
      event: {
        id: event.id,
        sport: event.sport,
        league: event.league,
        season: event.season,
        homeTeam: event.home_team,
        awayTeam: event.away_team,
        title: event.title,
        startTime: event.start_time_utc,
        status: event.status,
        country: event.country,
        poster: event.poster,
        interestScore: event.interest_score,
        sourceApi: event.source_api,
        homeTeamBadge: eventData.strHomeTeamBadge || '',
        awayTeamBadge: eventData.strAwayTeamBadge || '',
        channels: channels.map(c => ({
          channelId: c.channel_id,
          channelName: c.channel_name,
          confidence: c.confidence,
          matchReason: c.match_reason
        })),
        watchOptions: watchOptions.map(w => ({
          type: w.type,
          label: w.label,
          url: w.url,
          channelId: w.channel_id,
          legalStatus: w.legal_status,
          priority: w.priority
        }))
      }
    });
  } catch (err) {
    console.error('[SportsRoutes] Error GET /sports/events/:id:', err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

router.get('/sports/events/:id/watch-options', async (req, res) => {
  try {
    const event = await getQueryRef('SELECT * FROM sports_events WHERE id = ?', [req.params.id]);
    if (!event) {
      return res.status(404).json({ success: false, error: 'Evento no encontrado' });
    }

    const channels = await allQueryRef('SELECT * FROM sports_event_channels WHERE event_id = ? ORDER BY confidence DESC', [req.params.id]);
    const watchOptions = await allQueryRef('SELECT * FROM sports_watch_options WHERE event_id = ? ORDER BY priority DESC', [req.params.id]);

    const options = [];

    for (const ch of channels) {
      options.push({
        type: 'internal_channel',
        label: `Ver en ${ch.channel_name} desde Vision+`,
        channelId: ch.channel_id,
        channelName: ch.channel_name,
        confidence: ch.confidence,
        legalStatus: 'public'
      });
    }

    for (const wo of watchOptions) {
      options.push({
        type: wo.type,
        label: wo.label,
        url: wo.url,
        channelId: wo.channel_id,
        legalStatus: wo.legal_status,
        priority: wo.priority
      });
    }

    res.json({ success: true, options });
  } catch (err) {
    console.error('[SportsRoutes] Error GET /sports/events/:id/watch-options:', err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/sports/events/refresh', async (req, res) => {
  try {
    const result = await refreshSportsEvents(memDBRef, allQueryRef, runQueryRef);
    res.json({ success: true, ...result });
  } catch (err) {
    console.error('[SportsRoutes] Error POST /sports/events/refresh:', err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/sports/events/:id/match-channels', async (req, res) => {
  try {
    const event = await getQueryRef('SELECT * FROM sports_events WHERE id = ?', [req.params.id]);
    if (!event) {
      return res.status(404).json({ success: false, error: 'Evento no encontrado' });
    }

    const { matchChannelsForEvent, extractIPTVChannels } = require('./sportsMatcher');
    const channels = extractIPTVChannels(memDBRef);
    const matches = matchChannelsForEvent(event, channels);

    await runQueryRef('DELETE FROM sports_event_channels WHERE event_id = ?', [event.id]);
    for (const match of matches) {
      const matchId = `sec_${event.id}_${match.channel_id || Math.random().toString(36).substr(2,8)}`;
      await runQueryRef(`
        INSERT OR REPLACE INTO sports_event_channels (id, event_id, channel_id, channel_name, confidence, match_reason)
        VALUES (?, ?, ?, ?, ?, ?)
      `, [matchId, event.id, match.channel_id, match.channel_name, match.confidence, match.match_reason]);
    }

    res.json({ success: true, matches, count: matches.length });
  } catch (err) {
    console.error('[SportsRoutes] Error POST /sports/events/:id/match-channels:', err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

router.get('/sports/channels/matched', async (req, res) => {
  try {
    const rows = await allQueryRef(`
      SELECT sec.*, se.title as event_title, se.sport, se.start_time_utc, se.interest_score
      FROM sports_event_channels sec
      JOIN sports_events se ON sec.event_id = se.id
      ORDER BY se.interest_score DESC, sec.confidence DESC
      LIMIT 200
    `);
    res.json({ success: true, matches: rows });
  } catch (err) {
    console.error('[SportsRoutes] Error GET /sports/channels/matched:', err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = { router, initSportsRoutes };
