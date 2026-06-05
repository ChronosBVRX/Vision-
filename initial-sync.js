const fs = require('fs');
const path = require('path');
require('dotenv').config();

const REMOTE_DEST = process.env.REMOTE_SERVER_PATH || '\\\\192.168.1.120\\VisionPlus';
const IGNORE_PATTERNS = [
  'node_modules',
  '.git',
  'database.json',
  'database.json.backup',
  'database.sqlite',
  'server.log',
  'agent.lock',
  '.env',
  '*.apk',
  'scratch',
  'direct-sync.js',
  'initial-sync.js',
  'android', // No mandar código nativo
  'data' // No mandar la carpeta data local (sqlite)
];

function isIgnored(filePath) {
  const normalized = filePath.replace(/\\/g, '/');
  return IGNORE_PATTERNS.some(pattern => {
    if (pattern.startsWith('*')) return normalized.endsWith(pattern.slice(1));
    return normalized.includes('/' + pattern + '/') || normalized.endsWith('/' + pattern) || normalized === pattern || normalized.startsWith(pattern + '/');
  });
}

function copyRecursiveSync(src, dest) {
  if (isIgnored(src)) return;

  const exists = fs.existsSync(src);
  const stats = exists && fs.statSync(src);
  const isDirectory = exists && stats.isDirectory();

  if (isDirectory) {
    if (!fs.existsSync(dest)) {
      fs.mkdirSync(dest, { recursive: true });
    }
    fs.readdirSync(src).forEach(childItemName => {
      copyRecursiveSync(path.join(src, childItemName), path.join(dest, childItemName));
    });
  } else {
    // Es archivo
    try {
      fs.copyFileSync(src, dest);
      console.log('✓ Copiado:', src, '->', dest);
    } catch (e) {
      console.error('Error copiando', src, e.message);
    }
  }
}

console.log('Iniciando sincronización inicial hacia:', REMOTE_DEST);
if (!fs.existsSync(REMOTE_DEST)) {
  console.log('Creando directorio remoto...');
  fs.mkdirSync(REMOTE_DEST, { recursive: true });
}

const currentDir = process.cwd();
fs.readdirSync(currentDir).forEach(item => {
  copyRecursiveSync(path.join(currentDir, item), path.join(REMOTE_DEST, item));
});

console.log('¡Sincronización inicial completada!');
