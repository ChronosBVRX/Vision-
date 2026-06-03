/**
 * Normalizes different spelling variations of languages into a clean, unified label.
 * @param {string} langText - The language text from the source page.
 * @returns {string} One of: 'Español Latino', 'Español España', 'Subtitulado', 'Inglés', or 'Cualquier fuente disponible'.
 */
function normalizeLanguage(langText) {
  if (!langText) return 'Cualquier fuente disponible';
  
  const text = langText.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim();
  
  // Latino / LAT
  if (text.includes('latino') || text === 'lat' || text.includes('lat ') || text.includes(' lat') || text.includes('lati')) {
    return 'Español Latino';
  }
  
  // Castellano / Español / España
  if (text.includes('castellano') || text.includes('espana') || text.includes('cast') || text === 'es' || text === 'esp') {
    return 'Español España';
  }
  
  // Subtitulado / VOSE / Sub
  if (text.includes('subtitulado') || text.includes('subt') || text.includes('vose') || text === 'sub' || text === 'vos' || text.includes('subs')) {
    return 'Subtitulado';
  }
  
  // Inglés / English
  if (text.includes('ingles') || text.includes('english') || text === 'en' || text === 'eng') {
    return 'Inglés';
  }
  
  // Generic Spanish
  if (text.includes('espanol') || text.includes('spanish')) {
    return 'Español Latino'; // Default Spanish to Latino in Latin American context
  }

  return 'Cualquier fuente disponible';
}

/**
 * Sorts sources based on language priority.
 * @param {Array} sources - Array of stream source options.
 * @param {string} preferredLanguage - The user's preferred language ('auto', 'latino', 'es', 'subtitulado', 'en').
 * @returns {Array} Sorted array of sources.
 */
function sortSourcesByPriority(sources, preferredLanguage = 'auto') {
  if (!sources || !Array.isArray(sources)) return [];

  // Map preferredLanguage parameter to normalized language categories
  const langPrefMap = {
    'latino': 'Español Latino',
    'es': 'Español España',
    'subtitulado': 'Subtitulado',
    'en': 'Inglés'
  };

  const primaryPref = langPrefMap[preferredLanguage.toLowerCase()] || 'Español Latino';

  // Default priority order fallback
  const defaultOrder = [
    'Español Latino',
    'Español España',
    'Subtitulado',
    'Inglés',
    'Cualquier fuente disponible'
  ];

  // Reorder default list to put primaryPref first
  const orderList = [primaryPref];
  defaultOrder.forEach(l => {
    if (!orderList.includes(l)) {
      orderList.push(l);
    }
  });

  return [...sources].sort((a, b) => {
    const langA = normalizeLanguage(a.language);
    const langB = normalizeLanguage(b.language);

    const idxA = orderList.indexOf(langA);
    const idxB = orderList.indexOf(langB);

    const posA = idxA === -1 ? 99 : idxA;
    const posB = idxB === -1 ? 99 : idxB;

    if (posA !== posB) {
      return posA - posB;
    }

    // Secondary priority: direct files first over iframe embeds
    const isDirectA = a.resolver === 'direct' || (a.url && (a.url.includes('.m3u8') || a.url.includes('.mp4')));
    const isDirectB = b.resolver === 'direct' || (b.url && (b.url.includes('.m3u8') || b.url.includes('.mp4')));
    if (isDirectA && !isDirectB) return -1;
    if (!isDirectA && isDirectB) return 1;

    // Tertiary priority: stable servers (e.g. streamwish, voesx over others)
    const nameA = (a.name || '').toLowerCase();
    const nameB = (b.name || '').toLowerCase();
    const stableServers = ['streamwish', 'voesx', 'voe', 'filemoon'];
    const idxServerA = stableServers.findIndex(s => nameA.includes(s));
    const idxServerB = stableServers.findIndex(s => nameB.includes(s));
    
    const sPosA = idxServerA === -1 ? 99 : idxServerA;
    const sPosB = idxServerB === -1 ? 99 : idxServerB;
    
    return sPosA - sPosB;
  });
}

module.exports = {
  normalizeLanguage,
  sortSourcesByPriority
};
