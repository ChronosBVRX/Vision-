module.exports = {
  name: 'planetaplayAdapter',
  
  /**
   * Checks if this adapter can handle the given stream source.
   */
  canHandle(source) {
    return source && source.name === 'Planeta Play Stream';
  },
  
  /**
   * Directly resolves the URL without validation to ensure immediate loading.
   */
  async resolve(source, options = {}) {
    console.log(`[planetaplayAdapter] ⚡ Resolviendo Planeta Play directo: ${source.url}`);
    
    // Si es un iframe de youtube u otro embed conocido
    const isYoutube = source.url.includes('youtube.com') || source.url.includes('youtu.be');
    
    return {
      success: true,
      stream: {
        url: source.url,
        // Los iframes se renderizan nativamente, las m3u8 usan hls.js
        type: isYoutube ? 'iframe' : 'hls',
        headers: {},
        quality: 'auto'
      }
    };
  }
};
