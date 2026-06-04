/**
 * Adaptador para resolver URLs internas de Telegram.
 * Formato esperado de la fuente en base de datos:
 * { "url": "telegram://12345", "server": "Telegram" }
 */
const telegramAdapter = {
    name: 'telegramAdapter',
    
    canHandle: (url) => {
        return url && url.startsWith('telegram://');
    },
    
    resolve: async (url, options = {}) => {
        try {
            // Extraer el messageId
            // Ej: telegram://12345 -> 12345
            const messageId = url.replace('telegram://', '').split('/')[0];
            
            if (!messageId) {
                throw new Error("Message ID no válido en la URL de Telegram");
            }
            
            // La URL final que el reproductor (frontend) usará
            // Nuestro backend Express servirá el stream en este endpoint
            const streamUrl = `/api/stream/telegram/${messageId}`;
            
            return {
                url: streamUrl,
                quality: '1080p', // O la que detectemos
                isM3U8: false,    // Servimos un mp4 directo vía HTTP Range
                type: 'video/mp4' // Asumimos MP4 por ahora
            };
        } catch (error) {
            console.error(`[telegramAdapter] Error resolviendo URL ${url}:`, error.message);
            return null;
        }
    }
};

module.exports = telegramAdapter;
