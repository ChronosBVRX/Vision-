/**
 * watch-sync.js — Auto-push en tiempo real (PC Local)
 * [antigravity]
 *
 * Uso: node watch-sync.js
 *
 * Detecta cualquier cambio en el proyecto, espera 2s de inactividad
 * y luego hace commit + push automático a GitHub.
 *
 * Excluye: node_modules, .git, database.json, server.log, agent.lock
 */

const { execSync, exec } = require('child_process');
const fs = require('fs');
const path = require('path');

// ─── Configuración ───────────────────────────────────────────────
const DEBOUNCE_MS = 2000;       // ms de espera tras el último cambio
const IGNORE_PATTERNS = [
  'node_modules',
  '.git',
  'database.json',
  'database.json.backup',
  'database.sqlite',
  'server.log',
  'agent.lock',
  'watch-sync.js',
  '.env',
  '*.apk',
  'scratch',
];

// ─── Estado ──────────────────────────────────────────────────────
let debounceTimer = null;
let pendingFiles = new Set();
let isSyncing = false;

// ─── Colores para consola ─────────────────────────────────────────
const c = {
  reset: '\x1b[0m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  cyan: '\x1b[36m',
  red: '\x1b[31m',
  gray: '\x1b[90m',
  bold: '\x1b[1m',
};

function log(level, msg) {
  const time = new Date().toLocaleTimeString('es-MX', { hour12: false });
  const colors = { INFO: c.cyan, OK: c.green, WARN: c.yellow, ERROR: c.red, SYNC: c.bold + c.green };
  console.log(`${c.gray}[${time}]${c.reset} ${colors[level] || ''}[${level}]${c.reset} ${msg}`);
}

// ─── Verificar si un path está ignorado ──────────────────────────
function isIgnored(filePath) {
  const normalized = filePath.replace(/\\/g, '/');
  return IGNORE_PATTERNS.some(pattern => {
    if (pattern.startsWith('*')) {
      return normalized.endsWith(pattern.slice(1));
    }
    return normalized.includes(pattern);
  });
}

// ─── Hacer el push ───────────────────────────────────────────────
function doSync() {
  if (isSyncing) return;
  isSyncing = true;

  const files = [...pendingFiles];
  pendingFiles.clear();

  const shortNames = files.map(f => path.basename(f)).join(', ');
  log('SYNC', `Sincronizando: ${c.yellow}${shortNames}${c.reset}`);

  try {
    // Añadir solo los archivos modificados (excluye los ignorados por .gitignore)
    execSync('git add -A', { stdio: 'pipe' });

    // Verificar si hay algo que commitear
    const status = execSync('git status --porcelain', { encoding: 'utf8' }).trim();
    if (!status) {
      log('INFO', 'Sin cambios rastreados por git, omitiendo commit.');
      isSyncing = false;
      return;
    }

    const msg = `sync: ${shortNames.length > 60 ? shortNames.slice(0, 57) + '...' : shortNames}`;
    execSync(`git commit -m "${msg}"`, { stdio: 'pipe' });
    log('INFO', `Commit creado: "${msg}"`);

    execSync('git push origin main', { stdio: 'pipe' });
    log('OK', `✓ Push exitoso → GitHub (${files.length} archivo${files.length > 1 ? 's' : ''})`);

  } catch (err) {
    const errMsg = err.stderr?.toString() || err.message || '';
    if (errMsg.includes('nothing to commit')) {
      log('INFO', 'Nada nuevo para commitear.');
    } else if (errMsg.includes('rejected')) {
      log('WARN', 'Push rechazado — hay cambios en el servidor. Haciendo pull primero...');
      try {
        execSync('git pull --rebase origin main', { stdio: 'pipe' });
        execSync('git push origin main', { stdio: 'pipe' });
        log('OK', '✓ Rebase + push exitoso.');
      } catch (e2) {
        log('ERROR', `Conflicto de merge: ${e2.message}. Resuelve manualmente.`);
      }
    } else {
      log('ERROR', errMsg.slice(0, 200));
    }
  }

  isSyncing = false;
}

// ─── Watcher manual usando fs.watch ──────────────────────────────
function watchDir(dirPath) {
  try {
    const watcher = fs.watch(dirPath, { recursive: true }, (eventType, filename) => {
      if (!filename) return;
      const fullPath = path.join(dirPath, filename);
      if (isIgnored(fullPath)) return;

      pendingFiles.add(filename);
      log('INFO', `Cambio detectado: ${c.yellow}${filename}${c.reset}`);

      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(doSync, DEBOUNCE_MS);
    });

    watcher.on('error', (err) => {
      log('ERROR', `Watcher error: ${err.message}`);
    });

    return watcher;
  } catch (err) {
    log('ERROR', `No se pudo iniciar watcher en ${dirPath}: ${err.message}`);
    return null;
  }
}

// ─── Main ─────────────────────────────────────────────────────────
function main() {
  console.log(`\n${c.bold}${c.cyan}═══════════════════════════════════════${c.reset}`);
  console.log(`${c.bold}  Vision+ Watch-Sync — PC Local${c.reset}`);
  console.log(`${c.bold}${c.cyan}═══════════════════════════════════════${c.reset}\n`);

  // Verificar que estamos en un repo git
  try {
    execSync('git rev-parse --git-dir', { stdio: 'pipe' });
  } catch {
    log('ERROR', 'No es un repositorio git. Ejecuta desde la raíz del proyecto.');
    process.exit(1);
  }

  const projectRoot = process.cwd();
  log('INFO', `Vigilando: ${c.yellow}${projectRoot}${c.reset}`);
  log('INFO', `Ignorando: ${c.gray}${IGNORE_PATTERNS.join(', ')}${c.reset}`);
  log('INFO', `Debounce: ${DEBOUNCE_MS}ms`);
  log('INFO', `Destino: ${c.yellow}origin/main${c.reset} (GitHub)\n`);
  log('OK', '✓ Watcher activo — guarda cualquier archivo para sincronizar\n');

  watchDir(projectRoot);

  // Keep alive
  process.on('SIGINT', () => {
    console.log(`\n${c.yellow}Watcher detenido.${c.reset}\n`);
    process.exit(0);
  });
}

main();
