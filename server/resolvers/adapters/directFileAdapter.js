const { validatePlayableUrl } = require('../sourceValidator');
const { isAuthorized } = require('../browserResolver');

module.exports = {
  name: 'directFileAdapter',
  
  /**
   * Checks if this adapter can handle the given stream source.
   * @param {Object} source - The source candidate.
   * @returns {boolean} True if source points to a direct stream file.
   */
  canHandle(source) {
    if (!source || !source.url) return false;
    if (!source.url.startsWith('http')) return false;
    if (isAuthorized(source.url)) return false; // Let authorizedEmbedAdapter sniff it instead
    
    const url = source.url.toLowerCase();
    
    // Check if the URL filename contains direct stream formats
    try {
      const pathname = new URL(source.url).pathname.toLowerCase();
      if (pathname.endsWith('.m3u8') || pathname.endsWith('.mp4') || pathname.endsWith('.mpd')) {
        return true;
      }
    } catch (e) {}

    // Check query params or sub-strings as fallback
    return url.includes('.m3u8') || url.includes('.mp4') || url.includes('.mpd');
  },

  /**
   * Validates and resolves the direct stream URL.
   * @param {Object} source - The source candidate.
   * @param {Object} options - Resolution options.
   * @returns {Promise<Object>} The resolved stream object or failure details.
   */
  async resolve(source, options = {}) {
    console.log(`[directFileAdapter] ⚡ Validando archivo directo: ${source.url}`);
    
    const result = await validatePlayableUrl(source.url);
    
    if (result.success) {
      return {
        success: true,
        stream: {
          url: result.url,
          type: result.type,
          headers: result.headers || {},
          quality: 'auto'
        }
      };
    }
    
    return {
      success: false,
      reason: result.reason || 'INVALID_URL'
    };
  }
};
