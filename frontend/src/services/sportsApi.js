const BASE = '/api/sports';

async function fetchJson(url) {
  const res = await fetch(url);
  return res.json();
}

async function postJson(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  return res.json();
}

export async function getSportsEvents(params = {}) {
  const q = new URLSearchParams();
  if (params.region) q.set('region', params.region);
  if (params.days) q.set('days', String(params.days));
  if (params.sport) q.set('sport', params.sport);
  if (params.minScore) q.set('minScore', String(params.minScore));
  if (params.limit) q.set('limit', String(params.limit));
  const query = q.toString() ? `?${q.toString()}` : '';
  return fetchJson(`${BASE}/events${query}`);
}

export async function getSportsEvent(id) {
  return fetchJson(`${BASE}/events/${id}`);
}

export async function getWatchOptions(eventId) {
  return fetchJson(`${BASE}/events/${eventId}/watch-options`);
}

export async function refreshSportsEvents() {
  return postJson(`${BASE}/events/refresh`);
}

export async function matchChannels(eventId) {
  return postJson(`${BASE}/events/${eventId}/match-channels`);
}

export async function getMatchedChannels() {
  return fetchJson(`${BASE}/channels/matched`);
}
