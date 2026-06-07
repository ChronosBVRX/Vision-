const LATAM_SIGNALS = [
  'Mexico', 'México', 'Argentina', 'Brazil', 'Brasil',
  'Colombia', 'Chile', 'Peru', 'Uruguay', 'Ecuador',
  'Liga MX', 'CONCACAF', 'CONMEBOL', 'Copa Libertadores',
  'Copa Sudamericana', 'Leagues Cup', 'MLS'
];

const GLOBAL_SIGNALS = [
  'World Cup', 'Champions League', 'Premier League',
  'LaLiga', 'Serie A', 'Bundesliga', 'NBA', 'MLB',
  'NFL', 'UFC', 'Formula 1', 'Wimbledon', 'Olympics',
  'Super Bowl', 'Supercopa', 'Euro', 'Copa America'
];

const SPORT_BASE_SCORE = {
  'Soccer': 100,
  'Basketball': 85,
  'Baseball': 75,
  'Motorsport': 70,
  'Tennis': 70,
  'Boxing': 65,
  'MMA': 65,
  'American Football': 60,
  'American_Football': 60
};

function scoreEvent(event) {
  let score = 0;

  const text = [
    event.sport || '',
    event.league || '',
    event.home_team || '',
    event.away_team || '',
    event.country || '',
    event.title || ''
  ].join(' ').toLowerCase();

  const sportKey = event.sport || '';
  score += SPORT_BASE_SCORE[sportKey] || 40;

  const lower = text;

  if (lower.includes('liga mx') || lower.includes('ligamx')) score += 70;
  if (lower.includes('copa libertadores')) score += 85;
  if (lower.includes('copa sudamericana')) score += 75;
  if (lower.includes('champions league') || lower.includes('uefa champions')) score += 90;
  if (lower.includes('premier league') || lower.includes('epl')) score += 80;
  if (lower.includes('laliga') || lower.includes('la liga')) score += 75;
  if (lower.includes('serie a') || lower.includes('calcio')) score += 70;
  if (lower.includes('bundesliga')) score += 70;
  if (lower.includes('world cup') || lower.includes('mundial')) score += 95;
  if (lower.includes('copa america')) score += 90;
  if (lower.includes('nba')) score += 65;
  if (lower.includes('ufc')) score += 60;
  if (lower.includes('formula 1') || lower.includes('f1')) score += 60;
  if (lower.includes('super bowl') || lower.includes('nfl')) score += 60;
  if (lower.includes('mlb') || lower.includes('grandes ligas')) score += 55;
  if (lower.includes('wimbledon') || lower.includes('grand slam')) score += 55;
  if (lower.includes('concacaf')) score += 60;
  if (lower.includes('leagues cup')) score += 60;
  if (lower.includes('ligue 1')) score += 50;
  if (lower.includes('eredivisie')) score += 45;
  if (lower.includes('primeira liga')) score += 45;
  if (lower.includes('mls')) score += 45;

  for (const signal of LATAM_SIGNALS) {
    if (lower.includes(signal.toLowerCase())) score += 50;
  }

  for (const signal of GLOBAL_SIGNALS) {
    if (lower.includes(signal.toLowerCase())) score += 30;
  }

  const regionTags = [];
  for (const signal of LATAM_SIGNALS) {
    if (lower.includes(signal.toLowerCase()) && !regionTags.includes('LATAM')) {
      regionTags.push('LATAM');
    }
  }
  for (const signal of GLOBAL_SIGNALS) {
    if (lower.includes(signal.toLowerCase()) && !regionTags.includes('Global')) {
      regionTags.push('Global');
    }
  }

  if (lower.includes('mexico') || lower.includes('méxico')) regionTags.push('Mexico');
  if (lower.includes('argentina')) regionTags.push('Argentina');
  if (lower.includes('brasil') || lower.includes('brazil')) regionTags.push('Brasil');

  return { score, regionTags };
}

module.exports = { scoreEvent, SPORT_BASE_SCORE, LATAM_SIGNALS, GLOBAL_SIGNALS };
