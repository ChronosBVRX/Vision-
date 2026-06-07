const { TelegramClient, Api } = require("telegram");
const { StringSession } = require("telegram/sessions");
const fs = require("fs");
const path = require("path");

// Configuración cargada desde .env o hardcodeada provisoriamente
const apiId = parseInt(process.env.TELEGRAM_API_ID || "0", 10);
const apiHash = process.env.TELEGRAM_API_HASH || "";
const stringSession = new StringSession(process.env.TELEGRAM_STRING_SESSION || "");

let client = null;
let isConnected = false;
let initPromise = null;

// Conecta el cliente
async function initTelegramClient() {
  if (client && isConnected) {
    return true;
  }

  if (initPromise) {
    return initPromise;
  }

  console.log(`[TelegramClient] Iniciando con API_ID: ${apiId}, HASH: ${apiHash ? "SET" : "NOT SET"}`);
  if (!apiId || !apiHash) {
    console.warn("[TelegramClient] API_ID y API_HASH no configurados. Saltando inicio.");
    return false;
  }

  initPromise = (async () => {
    try {
      client = new TelegramClient(stringSession, apiId, apiHash, {
        connectionRetries: 5,
      });

      console.log("[TelegramClient] Conectando a Telegram...");
      await client.connect();
      isConnected = true;
      console.log("[TelegramClient] ¡Conectado exitosamente!");
      return true;
    } catch (error) {
      isConnected = false;
      client = null;

      if (error?.errorMessage === 'AUTH_KEY_DUPLICATED' || error?.code === 406) {
        console.warn("[TelegramClient] Sesión duplicada o inválida. Se desactiva Telegram hasta reiniciar con una sesión limpia.");
        return false;
      }

      console.error("[TelegramClient] Error al conectar:", error);
      return false;
    } finally {
      initPromise = null;
    }
  })();

  return initPromise;
}

// Retorna el cliente conectado
function getClient() {
  return client;
}

// Función para manejar el streaming del video
// Requiere la cabecera Range del request para pedir bytes específicos
async function streamTelegramFile(messageId, req, res, channelId) {
  if (!isConnected || !client) {
    return res.status(500).send("Telegram no está conectado.");
  }

  try {
    const channel = channelId || process.env.TELEGRAM_CHANNEL_ID;
    
    // Obtener el mensaje exacto
    const messages = await client.invoke(new Api.channels.GetMessages({
        channel: channel,
        id: [new Api.InputMessageID({ id: parseInt(messageId) })]
    }));

    if (!messages || !messages.messages || messages.messages.length === 0) {
      return res.status(404).send("Mensaje no encontrado.");
    }

    const message = messages.messages[0];
    const media = message.media;

    if (!media || !media.document) {
      return res.status(404).send("El mensaje no contiene un documento/video.");
    }

    const document = media.document;
    const fileSize = document.size;

    // Manejar el Range Request
    const range = req.headers.range;
    if (!range) {
      // Si no hay range, podríamos enviar todo (no recomendado para 4GB)
      res.status(400).send("Requiere cabecera Range");
      return;
    }

    const parts = range.replace(/bytes=/, "").split("-");
    const start = parseInt(parts[0], 10);
    const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
    const chunksize = (end - start) + 1;

    console.log(`[TelegramClient] Streaming msg ${messageId} bytes ${start}-${end} (${chunksize}b)`);

    res.writeHead(206, {
      "Content-Range": `bytes ${start}-${end}/${fileSize}`,
      "Accept-Ranges": "bytes",
      "Content-Length": chunksize,
      "Content-Type": "video/mp4", // Asumimos mp4
    });

    // Calcular en qué offset y limit pedir a Telegram (limit en bloques de 512kb = 524288)
    const chunkSizeToDownload = 512 * 1024; // 512 KB
    let currentOffset = start;

    // Bucle para bajar los chunks desde Telegram y mandarlos directo al response
    while (currentOffset <= end) {
      const bytesToRead = Math.min(chunkSizeToDownload, (end - currentOffset) + 1);
      
      const filePart = await client.invoke(
          new Api.upload.GetFile({
              location: new Api.InputDocumentFileLocation({
                  id: document.id,
                  accessHash: document.accessHash,
                  fileReference: document.fileReference,
                  thumbSize: "",
              }),
              offset: currentOffset,
              limit: bytesToRead,
          })
      );

      if (!filePart || !filePart.bytes) {
         break; 
      }

      const buffer = Buffer.from(filePart.bytes);
      // Validar si el cliente abortó la conexión antes de escribir
      if (req.socket.destroyed) {
         break;
      }

      res.write(buffer);
      currentOffset += buffer.length;
    }

    res.end();

  } catch (err) {
    console.error("[TelegramClient] Error en stream:", err);
    if (!res.headersSent) {
        res.status(500).send("Error interno transmitiendo desde Telegram");
    } else {
        res.end();
    }
  }
}

// Sincroniza el canal de Telegram buscando videos y los añade a SQLite
async function syncTelegramChannel() {
  if (!isConnected || !client) {
    console.warn("[TelegramSync] No conectado. Saltando sync.");
    return;
  }
  
  const channel = process.env.TELEGRAM_CHANNEL_ID;
  if (!channel) {
    console.warn("[TelegramSync] TELEGRAM_CHANNEL_ID no configurado.");
    return;
  }

  console.log(`[TelegramSync] Buscando videos en el canal: ${channel}`);
  
  try {
    const { runQuery, getQuery } = require('./db/database');

    // Obtener últimos 50 mensajes
    const messages = await client.invoke(new Api.messages.GetHistory({
      peer: channel,
      limit: 50,
    }));

    let addedCount = 0;

    for (const msg of messages.messages) {
      if (msg.media && msg.media.document) {
        const doc = msg.media.document;
        // Check if it's a video (mp4, mkv, etc.)
        const isVideo = doc.attributes.some(attr => attr.className === 'DocumentAttributeVideo') || 
                        (doc.mimeType && doc.mimeType.startsWith('video/'));
        
        if (isVideo) {
          const messageId = msg.id;
          
          // Determine title from caption or filename
          let title = msg.message; // Caption
          if (!title) {
             const filenameAttr = doc.attributes.find(a => a.className === 'DocumentAttributeFilename');
             title = filenameAttr ? filenameAttr.fileName : `Película de Telegram ${messageId}`;
          }
          
          // Limpiar título básico
          title = title.replace(/\.(mp4|mkv|avi)/i, '').trim();

          const movieId = `telegram_${messageId}`;

          // Revisar si ya existe en DB
          const existing = await getQuery(`SELECT id FROM sources WHERE id = ?`, [movieId]);
          if (!existing) {
            const movieItem = {
              id: movieId,
              title: title,
              type: 'movie',
              category: 'Telegram Feed',
              poster: 'https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=400', // Default poster
              description: 'Importado automáticamente desde Telegram.',
              streams: [
                {
                  name: 'Telegram Stream',
                  url: `telegram://${messageId}`,
                  resolver: 'telegramAdapter',
                  language: 'Latino/Original'
                }
              ]
            };

            await runQuery(`
              INSERT OR REPLACE INTO sources (id, type, category, data) 
              VALUES (?, ?, ?, ?)
            `, [movieId, 'movie', 'Telegram Feed', JSON.stringify(movieItem)]);

            addedCount++;
          }
        }
      }
    }

    console.log(`[TelegramSync] Sincronización completada. Se añadieron ${addedCount} películas nuevas.`);
  } catch (err) {
    console.error("[TelegramSync] Error al sincronizar canal:", err.message);
  }
}

module.exports = {
  initTelegramClient,
  getClient,
  streamTelegramFile,
  syncTelegramChannel
};
