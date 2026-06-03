const axios = require('axios');

/**
 * Validates whether a URL refers to a playable video/audio stream.
 * @param {string} url - The URL to validate.
 * @param {Object} headers - Optional headers to pass in the request.
 * @returns {Promise<Object>} An object indicating validation results: { success: boolean, type: string, url: string, headers: Object, reason: string }
 */
async function validatePlayableUrl(url, headers = {}) {
  if (!url || typeof url !== 'string' || !url.startsWith('http')) {
    return { success: false, reason: 'INVALID_URL' };
  }

  // Merge default user agent to avoid bot blocks
  const reqHeaders = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    ...headers
  };

  // Follow redirects and track the final URL
  let finalUrl = url;
  let finalContentType = '';
  
  // Acceptable content types
  const acceptableContentTypes = [
    'application/vnd.apple.mpegurl',
    'application/x-mpegurl',
    'audio/mpegurl',
    'video/mp4',
    'application/dash+xml',
    'video/quicktime',
    'video/3gpp',
    'video/x-flv',
    'video/mp2t' // TS files
  ];

  // Quick extension check helper
  const hasPlayableExtension = (urlString) => {
    try {
      const pathname = new URL(urlString).pathname.toLowerCase();
      return pathname.endsWith('.m3u8') || pathname.endsWith('.mp4') || pathname.endsWith('.mpd');
    } catch (e) {
      return false;
    }
  };

  // Step 1: Try HEAD request (faster, doesn't download body)
  try {
    console.log(`[SourceValidator] Probando HEAD para: ${url}`);
    const headRes = await axios({
      method: 'head',
      url: url,
      headers: reqHeaders,
      timeout: 4000,
      maxRedirects: 5,
      validateStatus: (status) => status >= 200 && status < 400
    });

    finalUrl = headRes.request.res.responseUrl || url;
    finalContentType = (headRes.headers['content-type'] || '').toLowerCase();

    console.log(`[SourceValidator] HEAD exitoso. Content-Type: ${finalContentType}, Final URL: ${finalUrl}`);
    
    // Check if Content-Type is acceptable
    const isContentTypeOk = acceptableContentTypes.some(type => finalContentType.includes(type));
    if (isContentTypeOk) {
      return {
        success: true,
        url: finalUrl,
        type: finalContentType.includes('mpegurl') ? 'hls' : (finalContentType.includes('dash') ? 'dash' : 'mp4'),
        headers: reqHeaders,
        contentType: finalContentType
      };
    }

    if (finalContentType.includes('text/html')) {
      console.log(`[SourceValidator] HEAD devuelto text/html. Rechazado.`);
      return { success: false, reason: 'HTML_PAGE_NOT_PLAYABLE' };
    }

  } catch (err) {
    console.log(`[SourceValidator] HEAD falló: ${err.message}. Intentando GET parcial...`);
  }

  // Step 2: Try partial GET request (some servers block HEAD or return 405 Method Not Allowed)
  try {
    console.log(`[SourceValidator] Probando GET con rango de bytes para: ${url}`);
    const getRes = await axios({
      method: 'get',
      url: url,
      headers: {
        ...reqHeaders,
        'Range': 'bytes=0-100' // Fetch only first 100 bytes
      },
      timeout: 5000,
      maxRedirects: 5,
      validateStatus: (status) => status >= 200 && status < 300 || status === 206 // 206 is Partial Content
    });

    finalUrl = getRes.request.res.responseUrl || url;
    finalContentType = (getRes.headers['content-type'] || '').toLowerCase();
    
    console.log(`[SourceValidator] GET parcial exitoso. Content-Type: ${finalContentType}, Final URL: ${finalUrl}`);

    const isContentTypeOk = acceptableContentTypes.some(type => finalContentType.includes(type));
    if (isContentTypeOk) {
      return {
        success: true,
        url: finalUrl,
        type: finalContentType.includes('mpegurl') ? 'hls' : (finalContentType.includes('dash') ? 'dash' : 'mp4'),
        headers: reqHeaders,
        contentType: finalContentType
      };
    }

    // Fallback: If content-type is empty or generic/octet-stream, check file extension in final url
    if (!finalContentType || finalContentType.includes('application/octet-stream') || finalContentType.includes('binary/octet-stream')) {
      if (hasPlayableExtension(finalUrl)) {
        console.log(`[SourceValidator] Content-Type genérico pero extensión reproducible encontrada.`);
        return {
          success: true,
          url: finalUrl,
          type: finalUrl.toLowerCase().includes('.m3u8') ? 'hls' : (finalUrl.toLowerCase().includes('.mpd') ? 'dash' : 'mp4'),
          headers: reqHeaders,
          contentType: finalContentType || 'application/octet-stream'
        };
      }
    }

    if (finalContentType.includes('text/html')) {
      return { success: false, reason: 'HTML_PAGE_NOT_PLAYABLE' };
    }

    return { success: false, reason: 'UNSUPPORTED_CONTENT_TYPE', details: finalContentType };

  } catch (err) {
    console.error(`[SourceValidator] GET parcial falló para ${url}:`, err.message);
    
    // If the server blocked our check but the final URL has a clear reproducible media extension, we can accept it as fallback
    if (hasPlayableExtension(url)) {
      console.log(`[SourceValidator] Validación fallida pero URL tiene extensión reproducible. Aceptando con advertencia.`);
      return {
        success: true,
        url: url,
        type: url.toLowerCase().includes('.m3u8') ? 'hls' : (url.toLowerCase().includes('.mpd') ? 'dash' : 'mp4'),
        headers: reqHeaders,
        contentType: 'unknown'
      };
    }

    if (err.response) {
      const status = err.response.status;
      if (status === 403) return { success: false, reason: 'HTTP_403' };
      if (status === 404) return { success: false, reason: 'HTTP_404' };
      return { success: false, reason: `HTTP_${status}` };
    }
    
    if (err.code === 'ECONNABORTED' || err.message.includes('timeout')) {
      return { success: false, reason: 'TIMEOUT' };
    }

    return { success: false, reason: 'SERVER_DOWN' };
  }
}

module.exports = {
  validatePlayableUrl
};
