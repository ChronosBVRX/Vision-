const { sniffAuthorizedEmbed } = require('../browserResolver');
const { validatePlayableUrl } = require('../sourceValidator');

module.exports = {
  name: 'fallbackAdapter',

  /**
   * Catch-all check.
   * @param {Object} source - The source candidate.
   * @returns {boolean} Always true.
   */
  canHandle(source) {
    return true;
  },

  /**
   * Attempts to sniff any HTTP URL with Playwright as last resort,
   * then discards if not playable.
   * @param {Object} source - The source candidate.
   * @param {Object} options - Resolution options.
   * @returns {Promise<Object>} Resolved stream or failure details.
   */
  async resolve(source, options = {}) {
    console.log(`[fallbackAdapter] ⚠️ Evaluando fuente restante: ${source.url}`);

    if (source.url && source.url.startsWith('http')) {
      // Try Playwright sniffing as last resort (bypasses authorized domain check)
      console.log(`[fallbackAdapter] 🌐 Intentando sniffing genérico con Playwright...`);
      const sniffResult = await sniffAuthorizedEmbed(source.url, { bypassAuth: true });
      if (sniffResult.success) {
        console.log(`[fallbackAdapter] 🎥 Stream sniffed: ${sniffResult.url}. Validando...`);
        const validation = await validatePlayableUrl(sniffResult.url, sniffResult.headers);
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
          reason: validation.reason || 'INVALID_STREAM',
          details: 'El stream sniffed no es reproducible.'
        };
      }

      return {
        success: false,
        reason: sniffResult.reason || 'HTML_PAGE_NOT_PLAYABLE',
        details: 'La fuente es una página web externa y no se pudo extraer un stream directo.'
      };
    }

    return {
      success: false,
      reason: 'INVALID_URL',
      details: 'La dirección URL no es válida o está ausente.'
    };
  }
};
