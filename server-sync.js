/**
 * server-sync.js — Auto-pull en tiempo real (Servidor Remoto)
 * [antigravity]
 *
 * Uso: node server-sync.js
 *
 * Hace git fetch cada 3 segundos. Si detecta commits nuevos en origin/main
 * que no están en local, ejecuta git pull automáticamente.
 *
 * Diseñado para correr en el servidor (Chronos) como proceso persistente.
 * Recomendado: ejecutar con pm2 o como servicio.
 */

const { execSync } = require('child_process');

// ─── Configuración ───────────────────────────────────────────────
const POLL_INTERVAL_MS = 3000;   // Verificar cada 3 segundos
const BRANCH = 'main';
const REMOTE = 'origin';

// ─── Colores ─────────────────────────────────────────────────────
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
  const colors = { INFO: c.cyan, OK: c.green, WARN: c.yellow, ERROR: c.red, PULL: c.bold + c.green };
  console.log(`${c.gray}[${time}]${c.reset} ${colors[level] || ''}[${level}]${c.reset} ${msg}`);
}

// ─── Obtener el commit HEAD local ─────────────────────────────────
function getLocalHead() {
  return execSync(`git rev-parse ${BRANCH}`, { encoding: 'utf8' }).trim();
}

// ─── Obtener el commit HEAD remoto ────────────────────────────────
function getRemoteHead() {
  execSync(`git fetch ${REMOTE} ${BRANCH} --quiet`, { stdio: 'pipe' });
  return execSync(`git rev-parse ${REMOTE}/${BRANCH}`, { encoding: 'utf8' }).trim();
}

// ─── Ejecutar pull ───────────────────────────────────────────────
function doPull() {
  try {
    const output = execSync(`git pull ${REMOTE} ${BRANCH} --ff-only`, { encoding: 'utf8' });
    const changedFiles = execSync('git diff-tree --no-commit-id -r --name-only HEAD', { encoding: 'utf8' })
      .trim()
      .split('\n')
      .filter(Boolean);

    log('PULL', `✓ Pull exitoso — ${changedFiles.length} archivo(s) actualizado(s):`);
    changedFiles.forEach(f => log('INFO', `  → ${c.yellow}${f}${c.reset}`));

    // Si el servidor (server.js o subcarpetas de servidor) fue modificado, reiniciar el servidor
    if (changedFiles.some(f => f === 'server.js' || f === 'scraper.js' || f.startsWith('server/') || f.startsWith('services/'))) {
      log('WARN', 'Código del servidor modificado — reiniciando servidor...');
      try {
        execSync('pm2 restart vision-plus --silent', { stdio: 'pipe' });
        log('OK', '✓ Servidor reiniciado via pm2.');
      } catch {
        // Buscar proceso en puerto 5000 y finalizarlo para que run_server.bat lo levante
        try {
          const netstatOut = execSync('netstat -aon', { encoding: 'utf8' });
          const lines = netstatOut.split('\n');
          let pidToKill = null;
          for (const line of lines) {
            if (line.includes(':5000') && line.includes('LISTENING')) {
              const parts = line.trim().split(/\s+/);
              pidToKill = parts[parts.length - 1];
              break;
            }
          }
          if (pidToKill) {
            execSync(`taskkill /f /pid ${pidToKill}`, { stdio: 'pipe' });
            log('OK', `✓ Servidor en puerto 5000 (PID ${pidToKill}) finalizado para auto-reinicio.`);
          } else {
            log('WARN', 'No se encontró ningún proceso escuchando en el puerto 5000 para reiniciar.');
          }
        } catch (killErr) {
          log('ERROR', `Error al intentar reiniciar el servidor en puerto 5000: ${killErr.message}`);
        }
      }
    }

    // Si el frontend fue modificado, recompilar
    if (changedFiles.some(f => f.startsWith('frontend/') && !f.startsWith('frontend/dist/'))) {
      log('WARN', 'Archivos de frontend modificados — iniciando recompilación...');
      try {
        execSync('npm run build --prefix frontend', { stdio: 'inherit' });
        log('OK', '✓ Frontend recompilado con éxito.');
      } catch (buildErr) {
        log('ERROR', `Error al compilar el frontend: ${buildErr.message}`);
      }
    }

  } catch (err) {
    const msg = err.stderr?.toString() || err.message || '';
    if (msg.includes('Already up to date')) {
      // silencioso
    } else if (msg.includes('CONFLICT') || msg.includes('merge conflict')) {
      log('ERROR', 'CONFLICTO DE MERGE detectado. Intervención manual requerida.');
      log('ERROR', 'Ejecuta: git status  →  resuelve conflictos  →  git pull');
    } else {
      log('ERROR', `Pull fallido: ${msg.slice(0, 200)}`);
    }
  }
}

// ─── Bucle principal ─────────────────────────────────────────────
async function main() {
  console.log(`\n${c.bold}${c.cyan}═══════════════════════════════════════${c.reset}`);
  console.log(`${c.bold}  Vision+ Server-Sync — Servidor Remoto${c.reset}`);
  console.log(`${c.bold}${c.cyan}═══════════════════════════════════════${c.reset}\n`);

  // Verificar repo git
  try {
    execSync('git rev-parse --git-dir', { stdio: 'pipe' });
  } catch {
    log('ERROR', 'No es un repositorio git. Ejecuta desde la raíz del proyecto.');
    process.exit(1);
  }

  log('INFO', `Rama: ${c.yellow}${REMOTE}/${BRANCH}${c.reset}`);
  log('INFO', `Intervalo de verificación: ${POLL_INTERVAL_MS}ms`);
  log('OK', '✓ Watcher activo — esperando cambios desde GitHub\n');

  let consecutiveErrors = 0;

  setInterval(() => {
    try {
      const local = getLocalHead();
      const remote = getRemoteHead();

      if (local !== remote) {
        log('INFO', `Nuevo commit detectado: ${c.yellow}${remote.slice(0, 8)}${c.reset} (local: ${local.slice(0, 8)})`);
        doPull();
      }

      consecutiveErrors = 0;
    } catch (err) {
      consecutiveErrors++;
      if (consecutiveErrors <= 3) {
        log('ERROR', `Error verificando git: ${err.message?.slice(0, 100)}`);
      }
      if (consecutiveErrors === 5) {
        log('WARN', 'Múltiples errores consecutivos — verifica la conexión a GitHub.');
      }
    }
  }, POLL_INTERVAL_MS);

  process.on('SIGINT', () => {
    console.log(`\n${c.yellow}Server-Sync detenido.${c.reset}\n`);
    process.exit(0);
  });
}

main();
