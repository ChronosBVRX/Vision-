const { TelegramClient } = require("telegram");
const { StringSession } = require("telegram/sessions");
const input = require("input"); // npm i input
const fs = require("fs");
const path = require("path");

const apiId = parseInt(process.env.TELEGRAM_API_ID || "2040", 10);
const apiHash = process.env.TELEGRAM_API_HASH || "b18441a1ff607e10a989891a5462e627";

const stringSession = new StringSession(""); // Empty string creates a new session

async function setup() {
  console.log("Iniciando configuración de Telegram para Vision+...");
  console.log("Se te pedirá tu número de teléfono y el código de inicio de sesión.");
  console.log("Usando API_ID público:", apiId);

  const client = new TelegramClient(stringSession, apiId, apiHash, {
    connectionRetries: 5,
  });

  await client.start({
    phoneNumber: async () => await input.text("Ingresa tu número de teléfono (ej. +521234567890): "),
    password: async () => await input.text("Ingresa tu contraseña (si tienes verificación en dos pasos): "),
    phoneCode: async () => await input.text("Ingresa el código que te llegó a tu app de Telegram: "),
    onError: (err) => console.log(err),
  });

  console.log("\n¡Conectado exitosamente!");
  
  const sessionString = client.session.save();
  console.log("\n--- TU STRING SESSION ES EL SIGUIENTE ---");
  console.log(sessionString);
  console.log("-----------------------------------------\n");

  // Guardarlo en .env automáticamente
  const envPath = path.join(__dirname, ".env");
  let envFile = fs.readFileSync(envPath, "utf8");
  
  if (envFile.includes("TELEGRAM_STRING_SESSION=")) {
    envFile = envFile.replace(/TELEGRAM_STRING_SESSION=.*/g, `TELEGRAM_STRING_SESSION=${sessionString}`);
  } else {
    envFile += `\nTELEGRAM_STRING_SESSION=${sessionString}\n`;
  }

  // Pedir el canal
  const channelName = await input.text("Por último, ingresa el @usuario o ID del canal que usarás como feed (ej. @MisPelisFeed): ");
  if (envFile.includes("TELEGRAM_CHANNEL_ID=")) {
    envFile = envFile.replace(/TELEGRAM_CHANNEL_ID=.*/g, `TELEGRAM_CHANNEL_ID=${channelName}`);
  } else {
    envFile += `\nTELEGRAM_CHANNEL_ID=${channelName}\n`;
  }

  fs.writeFileSync(envPath, envFile, "utf8");
  
  console.log("\n¡Listo! Tu .env ha sido actualizado con tu sesión de Telegram y tu canal.");
  console.log("Ya puedes borrar este archivo setup_telegram.js o ignorarlo. Reinicia tu servidor Vision+.");
  process.exit(0);
}

setup();
