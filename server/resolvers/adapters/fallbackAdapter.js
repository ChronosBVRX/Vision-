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
   * Discards the source if it is an HTML page or invalid.
   * @param {Object} source - The source candidate.
   * @param {Object} options - Resolution options.
   * @returns {Promise<Object>} Failure details.
   */
  async resolve(source, options = {}) {
    console.log(`[fallbackAdapter] ⚠️ Evaluando fuente restante como descarte: ${source.url}`);
    
    if (source.url && source.url.startsWith('http')) {
      return {
        success: false,
        reason: 'HTML_PAGE_NOT_PLAYABLE',
        details: 'La fuente es una página web externa que requiere iframe y no entrega video directo.'
      };
    }
    
    return {
      success: false,
      reason: 'INVALID_URL',
      details: 'La dirección URL no es válida o está ausente.'
    };
  }
};
