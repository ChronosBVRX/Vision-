const axios = require('axios');

const THESPORTSDB_KEY = process.env.THESPORTSDB_KEY || '3';
const THESPORTSDB_BASE = 'https://www.thesportsdb.com/api/v1/json';

async function fetchFromSportsDB(endpoint) {
  try {
    const url = `${THESPORTSDB_BASE}/${THESPORTSDB_KEY}/${endpoint}`;
    const res = await axios.get(url, { timeout: 15000 });
    return res.data;
  } catch (err) {
    console.error(`[SportsAPI] TheSportsDB error: ${err.message}`);
    return null;
  }
}

function parseTheSportsDBEvent(event) {
  const dateStr = event.dateEvent || event.strDate || '';
  const timeStr = event.strTime || '';
  const startTime = dateStr ? `${dateStr}T${timeStr || '00:00:00'}Z` : null;
  const league = event.strLeague || event.strSport || '';
  const homeTeam = event.strHomeTeam || '';
  const awayTeam = event.strAwayTeam || '';
  const title = `${homeTeam} vs ${awayTeam}`;
  const sport = event.strSport || 'Unknown';
  const season = event.strSeason || '';
  const country = event.strCountry || '';
  const poster = event.strThumb || event.strFanArt || event.strPoster || '';
  const status = event.strStatus || 'scheduled';
  const eventId = event.idEvent ? `tsdb_${event.idEvent}` : `sports_${Date.now()}_${Math.random().toString(36).substr(2,5)}`;

  return {
    id: eventId,
    sport,
    league,
    season,
    home_team: homeTeam,
    away_team: awayTeam,
    title,
    start_time_utc: startTime,
    status,
    country,
    poster,
    interest_score: 0,
    source_api: 'thesportsdb',
    raw_json: JSON.stringify(event)
  };
}

async function fetchUpcomingEvents(days = 7) {
  const events = [];

  const now = new Date();
  const endDate = new Date(now.getTime() + days * 24 * 60 * 60 * 1000);
  const dateStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;

  const sportsEndpoints = [
    `eventsday.php?d=${dateStr}`,
    `eventsday.php?d=${dateStr}&s=Soccer`,
    `eventsday.php?d=${dateStr}&s=Basketball`,
    `eventsday.php?d=${dateStr}&s=Baseball`,
    `eventsday.php?d=${dateStr}&s=Motorsport`,
    `eventsday.php?d=${dateStr}&s=Tennis`,
    `eventsday.php?d=${dateStr}&s=Boxing`,
    `eventsday.php?d=${dateStr}&s=MMA`,
    `eventsday.php?d=${dateStr}&s=American_Football`
  ];

  for (const endpoint of sportsEndpoints) {
    try {
      const data = await fetchFromSportsDB(endpoint);
      if (data && data.events) {
        for (const ev of data.events) {
          const parsed = parseTheSportsDBEvent(ev);
          if (!events.find(e => e.id === parsed.id)) {
            events.push(parsed);
          }
        }
      }
    } catch (err) {
      console.warn(`[SportsAPI] Error fetching ${endpoint}: ${err.message}`);
    }
  }

  return events;
}

async function fetchLookupTable() {
  const data = await fetchFromSportsDB('all_sports.php');
  if (data && data.sports) return data.sports;
  return [];
}

module.exports = { fetchUpcomingEvents, fetchLookupTable, parseTheSportsDBEvent };
