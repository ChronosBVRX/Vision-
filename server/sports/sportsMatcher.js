const CHANNEL_KEYWORDS = {
  soccer: ['tudn', 'azteca', 'canal 5', 'espn', 'fox sports', 'bein', 'dazn', 'tnt sports', 'hbo max', 'sportv', 'tyc', 'directv sports'],
  liga_mx: ['tudn', 'azteca', 'canal 5', 'fox sports'],
  champions: ['tnt sports', 'hbo max', 'espn', 'bein', 'dazn'],
  libertadores: ['espn', 'fox sports', 'bein', 'telefe'],
  nba: ['espn', 'nba tv', 'tnt'],
  mlb: ['espn', 'fox sports', 'mlb network'],
  ufc: ['espn', 'fox sports', 'ufc'],
  f1: ['fox sports', 'espn', 'f1 tv'],
  boxing: ['espn', 'dazn', 'showtime', 'hbo'],
  tennis: ['espn', 'tcn', 'tennis channel'],
  american_football: ['espn', 'fox sports', 'nfl network', 'nfl']
};

function matchChannelsForEvent(event, channels) {
  const eventText = `${event.sport || ''} ${event.league || ''} ${event.title || ''} ${event.home_team || ''} ${event.away_team || ''}`.toLowerCase();
  const matches = [];

  for (const channel of channels) {
    const channelText = `${channel.title || ''} ${channel.category || ''}`.toLowerCase();
    let confidence = 0;
    const reasons = [];

    if (eventText.includes('soccer') || eventText.includes('football') || eventText.includes('futbol')) {
      for (const kw of CHANNEL_KEYWORDS.soccer) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 60);
          reasons.push(`Canal deportivo: ${kw}`);
        }
      }
    }

    if (eventText.includes('liga mx') || eventText.includes('ligamx')) {
      for (const kw of CHANNEL_KEYWORDS.liga_mx) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 80);
          reasons.push(`Canal común para Liga MX`);
        }
      }
    }

    if (eventText.includes('champions') || eventText.includes('uefa')) {
      for (const kw of CHANNEL_KEYWORDS.champions) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 75);
          reasons.push(`Canal común para Champions League`);
        }
      }
    }

    if (eventText.includes('libertadores')) {
      for (const kw of CHANNEL_KEYWORDS.libertadores) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 80);
          reasons.push(`Canal común para Copa Libertadores`);
        }
      }
    }

    if (eventText.includes('nba')) {
      for (const kw of CHANNEL_KEYWORDS.nba) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 70);
          reasons.push(`Canal común para NBA`);
        }
      }
    }

    if (eventText.includes('mlb') || eventText.includes('baseball')) {
      for (const kw of CHANNEL_KEYWORDS.mlb) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 65);
          reasons.push(`Canal común para MLB`);
        }
      }
    }

    if (eventText.includes('ufc') || eventText.includes('mma')) {
      for (const kw of CHANNEL_KEYWORDS.ufc) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 65);
          reasons.push(`Canal común para UFC`);
        }
      }
    }

    if (eventText.includes('formula 1') || eventText.includes('f1')) {
      for (const kw of CHANNEL_KEYWORDS.f1) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 65);
          reasons.push(`Canal común para Fórmula 1`);
        }
      }
    }

    if (eventText.includes('tennis') || eventText.includes('grand slam')) {
      for (const kw of CHANNEL_KEYWORDS.tennis) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 60);
          reasons.push(`Canal común para Tenis`);
        }
      }
    }

    if (eventText.includes('nfl') || eventText.includes('super bowl')) {
      for (const kw of CHANNEL_KEYWORDS.american_football) {
        if (channelText.includes(kw)) {
          confidence = Math.max(confidence, 65);
          reasons.push(`Canal común para NFL`);
        }
      }
    }

    if (confidence >= 50) {
      matches.push({
        channel_id: channel.id,
        channel_name: channel.title,
        poster: channel.poster,
        confidence,
        match_reason: reasons.join('; ')
      });
    }
  }

  matches.sort((a, b) => b.confidence - a.confidence);
  return matches;
}

function extractIPTVChannels(memDB) {
  const channels = [];
  const allSources = [
    ...(memDB.sources || []),
    ...(memDB.movieCatalog || []),
    ...(memDB.seriesCatalog || [])
  ];

  for (const src of allSources) {
    if (src.type === 'tv' && src.streams && src.streams.length > 0) {
      channels.push(src);
    }
  }

  return channels;
}

module.exports = { matchChannelsForEvent, extractIPTVChannels, CHANNEL_KEYWORDS };
