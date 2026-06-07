const { fetchUpcomingEvents } = require('./sportsApiClient');
const { scoreEvent } = require('./sportsRanker');
const { matchChannelsForEvent, extractIPTVChannels } = require('./sportsMatcher');

async function refreshSportsEvents(memDB, allQuery, runQuery) {
  console.log('[SportsWorker] Iniciando actualización de agenda deportiva...');

  try {
    const events = await fetchUpcomingEvents(7);
    console.log(`[SportsWorker] Se obtuvieron ${events.length} eventos de TheSportsDB`);

    let inserted = 0;
    let updated = 0;

    for (const event of events) {
      const { score, regionTags } = scoreEvent(event);
      event.interest_score = score;

      try {
        const existing = await allQuery('SELECT id FROM sports_events WHERE id = ?', [event.id]);
        if (existing && existing.length > 0) {
          await runQuery(`
            UPDATE sports_events SET
              sport = ?, league = ?, season = ?, home_team = ?, away_team = ?,
              title = ?, start_time_utc = ?, status = ?, country = ?,
              poster = ?, interest_score = ?, source_api = ?, raw_json = ?,
              updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
          `, [
            event.sport, event.league, event.season, event.home_team, event.away_team,
            event.title, event.start_time_utc, event.status, event.country,
            event.poster, event.interest_score, event.source_api, event.raw_json,
            event.id
          ]);
          updated++;
        } else {
          await runQuery(`
            INSERT INTO sports_events (id, sport, league, season, home_team, away_team, title, start_time_utc, status, country, poster, interest_score, source_api, raw_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `, [
            event.id, event.sport, event.league, event.season, event.home_team, event.away_team,
            event.title, event.start_time_utc, event.status, event.country,
            event.poster, event.interest_score, event.source_api, event.raw_json
          ]);
          inserted++;
        }

        const channels = extractIPTVChannels(memDB);
        const channelMatches = matchChannelsForEvent(event, channels);

        if (channelMatches.length > 0) {
          await runQuery('DELETE FROM sports_event_channels WHERE event_id = ?', [event.id]);
          for (const match of channelMatches) {
            const matchId = `sec_${event.id}_${match.channel_id || Math.random().toString(36).substr(2,8)}`;
            await runQuery(`
              INSERT OR REPLACE INTO sports_event_channels (id, event_id, channel_id, channel_name, confidence, match_reason)
              VALUES (?, ?, ?, ?, ?, ?)
            `, [matchId, event.id, match.channel_id, match.channel_name, match.confidence, match.match_reason]);
          }
        }
      } catch (err) {
        console.warn(`[SportsWorker] Error procesando evento ${event.id}: ${err.message}`);
      }
    }

    console.log(`[SportsWorker] Completado: ${inserted} insertados, ${updated} actualizados`);
    return { inserted, updated };
  } catch (err) {
    console.error(`[SportsWorker] Error general: ${err.message}`);
    return { inserted: 0, updated: 0, error: err.message };
  }
}

module.exports = { refreshSportsEvents };
