const { sniffAuthorizedEmbed, isAuthorized } = require('../browserResolver');
const { validatePlayableUrl } = require('../sourceValidator');

module.exports = {
  name: 'authorizedEmbedAdapter',

  /**
   * Checks if this adapter can handle the given stream source.
   * @param {Object} source - The source candidate.
   * @returns {boolean} True if the source URL belongs to an authorized embed domain.
   */
  canHandle(source) {
    if (!source || !source.url) return false;
    return isAuthorized(source.url);
  },

  /**
   * Resolves the embed source using the browser sniffer and validates the extracted link.
   * @param {Object} source - The source candidate.
   * @param {Object} options - Resolution options.
   * @returns {Promise<Object>} The resolved stream object or failure details.
   */
  async resolve(source, options = {}) {
    console.log(`[authorizedEmbedAdapter] 🌐 Resolviendo iframe de dominio autorizado: ${source.url}`);
    
    // Sniff the direct link using Playwright Chromium
    const result = await sniffAuthorizedEmbed(source.url);
    
    if (result.success) {
      console.log(`[authorizedEmbedAdapter] 🎥 Video sniffed: ${result.url}. Validando conectividad...`);
      
      // Validate that the sniffed stream URL is active and playable
      const validation = await validatePlayableUrl(result.url, result.headers);
      
      if (validation.success) {
        return {
          success: true,
          stream: {
            url: validation.url,
            type: validation.type,
            headers: validation.headers || {},
            quality: 'auto'
          }
        };
      }
      
      return {
        success: false,
        reason: validation.reason || 'INVALID_URL'
      };
    }
    
    return {
      success: false,
      reason: result.reason || 'TIMEOUT'
    };
  }
};
