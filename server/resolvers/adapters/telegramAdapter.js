/**
 * Adaptador para resolver URLs internas de Telegram.
 * Formato esperado de la fuente en base de datos:
 * { "url": "telegram://12345", "server": "Telegram" }
 */
const telegramAdapter = {
    name: 'telegramAdapter',
    
    canHandle: (source) => {
        return source && source.url && source.url.startsWith('telegram://');
    },
    
    resolve: async (source, options = {}) => {
        try {
            // Extraer el messageId
            // Ej: telegram://12345 -> 12345
            const messageId = source.url.replace('telegram://', '').split('/')[0];
            
            if (!messageId) {
                return { success: false, reason: 'INVALID_MESSAGE_ID', details: 'Message ID no válido en la URL de Telegram' };
            }
            
            // La URL final que el reproductor (frontend) usará
            // Nuestro backend Express servirá el stream en este endpoint
            const streamUrl = `/api/stream/telegram/${messageId}`;
            
            return {
                success: true,
                stream: {
                    url: streamUrl,
                    type: 'video/mp4',
                    headers: {},
                    quality: '1080p',
                    isM3U8: false
                }
            };
        } catch (error) {
            console.error(`[telegramAdapter] Error resolviendo URL ${source.url}:`, error.message);
            return { success: false, reason: 'TELEGRAM_RESOLVE_ERROR', details: error.message };
        }
    }
};

module.exports = telegramAdapter;
