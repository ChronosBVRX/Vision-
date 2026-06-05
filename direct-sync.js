/**
 * direct-sync.js — Espejo en Vivo a Servidor + Auto-push a GitHub
 * [antigravity]
 *
 * Este script reemplaza al watch-sync.js tradicional.
 * 1. Copia instantáneamente (EN VIVO) cualquier archivo modificado a la carpeta del servidor remoto.
 * 2. Hace commit y push a GitHub en segundo plano para mantener el control de versiones.
 *
 * Requiere configurar REMOTE_SERVER_PATH en el archivo .env
 * Ejemplo: REMOTE_SERVER_PATH=\\CHRONOS\Vision+  (o la ruta de red mapeada Z:\Vision+)
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
require('dotenv').config(); // Cargar variables de entorno

// ─── Configuración ───────────────────────────────────────────────
const REMOTE_DEST = process.env.REMOTE_SERVER_PATH;
const DEBOUNCE_MS = 2000;       // ms de espera para Git Push
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
  'direct-sync.js'
];

let debounceTimer = null;
let pendingGitFiles = new Set();
let isSyncingGit = false;

const c = {
  reset: '\x1b[0m', green: '\x1b[32m', yellow: '\x1b[33m', cyan: '\x1b[36m', red: '\x1b[31m', gray: '\x1b[90m', bold: '\x1b[1m'
};

function log(level, msg) {
  const time = new Date().toLocaleTimeString('es-MX', { hour12: false });
  const colors = { INFO: c.cyan, OK: c.green, WARN: c.yellow, ERROR: c.red, MIRROR: c.cyan + c.bold, GIT: c.green + c.bold };
  console.log(`${c.gray}[${time}]${c.reset} ${colors[level] || ''}[${level}]${c.reset} ${msg}`);
}

function isIgnored(filePath) {
  const normalized = filePath.replace(/\\/g, '/');
  return IGNORE_PATTERNS.some(pattern => {
    if (pattern.startsWith('*')) return normalized.endsWith(pattern.slice(1));
    return normalized.includes(pattern);
  });
}

// ─── Reflejo en Vivo (Mirror) ────────────────────────────────────
function mirrorFileToRemote(localPath) {
  if (!REMOTE_DEST) return;

  try {
    const relativePath = path.relative(process.cwd(), localPath);
    const destPath = path.join(REMOTE_DEST, relativePath);
    const destDir = path.dirname(destPath);

    if (!fs.existsSync(destDir)) {
      fs.mkdirSync(destDir, { recursive: true });
    }

    // Copia instantánea
    fs.copyFileSync(localPath, destPath);
    log('MIRROR', `➜ Copiado al servidor: ${c.yellow}${relativePath}${c.reset}`);
  } catch (err) {
    log('ERROR', `Error al copiar al servidor remoto: ${err.message}`);
  }
}

// ─── Git Push ────────────────────────────────────────────────────
function doGitSync() {
  if (isSyncingGit) return;
  isSyncingGit = true;

  const files = [...pendingGitFiles];
  pendingGitFiles.clear();
  const shortNames = files.map(f => path.basename(f)).join(', ');

  try {
    execSync('git add -A', { stdio: 'pipe' });
    const status = execSync('git status --porcelain', { encoding: 'utf8' }).trim();
    
    if (status) {
      const msg = `sync: ${shortNames.length > 60 ? shortNames.slice(0, 57) + '...' : shortNames}`;
      execSync(`git commit -m "${msg}"`, { stdio: 'pipe' });
      execSync('git push origin main', { stdio: 'pipe' });
      log('GIT', `✓ Push a GitHub exitoso (${files.length} archivo/s)`);
    }
  } catch (err) {
    const errMsg = err.stderr?.toString() || err.message || '';
    if (errMsg.includes('rejected')) {
      try {
        execSync('git pull --rebase origin main', { stdio: 'pipe' });
        execSync('git push origin main', { stdio: 'pipe' });
        log('GIT', '✓ Rebase + push exitoso.');
      } catch (e2) {
        log('ERROR', `Conflicto en Git: ${e2.message}`);
      }
    }
  }
  isSyncingGit = false;
}

// ─── Watcher ─────────────────────────────────────────────────────
function watchDir(dirPath) {
  try {
    const watcher = fs.watch(dirPath, { recursive: true }, (eventType, filename) => {
      if (!filename) return;
      const fullPath = path.join(dirPath, filename);
      if (isIgnored(fullPath)) return;

      // 1. Espejo en vivo inmediato
      if (fs.existsSync(fullPath) && fs.statSync(fullPath).isFile()) {
        mirrorFileToRemote(fullPath);
      }

      // 2. Encolar para Git Push (con debounce)
      pendingGitFiles.add(filename);
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(doGitSync, DEBOUNCE_MS);
    });
    return watcher;
  } catch (err) {
    log('ERROR', `Error en watcher: ${err.message}`);
    return null;
  }
}

// ─── Main ────────────────────────────────────────────────────────
function main() {
  console.log(`\n${c.bold}${c.cyan}═══════════════════════════════════════════════════════${c.reset}`);
  console.log(`${c.bold}  Vision+ Direct-Sync — Espejo en Vivo + Git Push${c.reset}`);
  console.log(`${c.bold}${c.cyan}═══════════════════════════════════════════════════════${c.reset}\n`);

  if (!REMOTE_DEST) {
    log('WARN', `La variable REMOTE_SERVER_PATH no está definida en el .env`);
    log('WARN', `El espejo en vivo está DESACTIVADO. Solo se hará push a GitHub.`);
    log('WARN', `Para activar el espejo, añade: REMOTE_SERVER_PATH=\\\\IP_SERVIDOR\\Vision+ en tu .env\n`);
  } else {
    log('OK', `✓ Espejo Remoto: ${c.yellow}${REMOTE_DEST}${c.reset}`);
  }

  log('OK', `✓ Git Sync: origin/main (espera de ${DEBOUNCE_MS}ms)`);
  log('INFO', `Vigilando: ${process.cwd()}\n`);

  watchDir(process.cwd());

  process.on('SIGINT', () => {
    console.log(`\n${c.yellow}Direct-Sync detenido.${c.reset}\n`);
    process.exit(0);
  });
}

main();
