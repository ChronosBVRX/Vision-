const fs = require('fs');
const path = require('path');

const LOG_FILE = path.join(__dirname, 'server.log');
const USAGE = `
Uso: node read-logs.js [opciones]

Opciones:
  --errors, -e       Solo mostrar errores ([ERROR])
  --last N, -l N     Últimas N líneas
  --module M, -m M   Filtrar por módulo (ej: SportsWorker, Resolver, BrowserResolver)
  --since T, -s T     Desde una fecha/hora (ej: "2026-06-04 15:00")
  --until T, -u T     Hasta una fecha/hora
  --search Q, -g Q   Búsqueda de texto (grep)
  --json, -j          Salida en JSON (para agents)
  --watch, -w         Modo watch (sigue el archivo, como tail -f)
  --count, -c         Solo contar líneas (con filtros aplicados)
  --help, -h          Muestra esta ayuda

Ejemplos:
  node read-logs.js --errors
  node read-logs.js --last 20
  node read-logs.js --module Resolver --errors
  node read-logs.js --since "2026-06-04 15:00" --until "2026-06-04 15:10"
  node read-logs.js --search "ENOTFOUND"
  node read-logs.js --json --errors --count
`;

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = { errors: false, last: null, module: null, since: null, until: null, search: null, json: false, watch: false, count: false };
  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case '--errors': case '-e': opts.errors = true; break;
      case '--last': case '-l': opts.last = parseInt(args[++i]) || 50; break;
      case '--module': case '-m': opts.module = args[++i]; break;
      case '--since': case '-s': opts.since = args[++i]; break;
      case '--until': case '-u': opts.until = args[++i]; break;
      case '--search': case '-g': opts.search = args[++i]; break;
      case '--json': case '-j': opts.json = true; break;
      case '--watch': case '-w': opts.watch = true; break;
      case '--count': case '-c': opts.count = true; break;
      case '--help': case '-h': console.log(USAGE); process.exit(0);
      default: console.error(`Opción desconocida: ${args[i]}\n${USAGE}`); process.exit(1);
    }
  }
  return opts;
}

function getTimestamp(line) {
  const m = line.match(/^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]/);
  return m ? m[1] : null;
}

function passesFilter(line, opts) {
  if (opts.errors && !line.includes('[ERROR]')) return false;
  if (opts.module && !line.includes(`[${opts.module}]`)) return false;
  if (opts.search && !line.toLowerCase().includes(opts.search.toLowerCase())) return false;
  if (opts.since || opts.until) {
    const ts = getTimestamp(line);
    if (!ts) return false;
    if (opts.since && ts < opts.since) return false;
    if (opts.until && ts > opts.until) return false;
  }
  return true;
}

function readLogs() {
  if (!fs.existsSync(LOG_FILE)) {
    console.error(`No se encontró ${LOG_FILE}. Haz 'git pull origin main' primero.`);
    process.exit(1);
  }
  const opts = parseArgs();
  const content = fs.readFileSync(LOG_FILE, 'utf8');
  let lines = content.split('\n').filter(l => l.trim());

  let filtered = lines.filter(l => passesFilter(l, opts));

  if (opts.last && opts.last < filtered.length) {
    filtered = filtered.slice(-opts.last);
  }

  if (opts.count) {
    if (opts.json) {
      console.log(JSON.stringify({ count: filtered.length, total: lines.length }));
    } else {
      console.log(`Líneas: ${filtered.length} de ${lines.length} total`);
    }
    return;
  }

  if (opts.json) {
    const entries = filtered.map(line => {
      const ts = getTimestamp(line);
      const isError = line.includes('[ERROR]');
      const modMatch = line.match(/\[(\w+)\]/g);
      const module = modMatch ? modMatch.find(m => m !== '[ERROR]' && m !== '[INFO]')?.replace(/[[\]]/g, '') : null;
      return { timestamp: ts, level: isError ? 'ERROR' : 'INFO', module, message: line };
    });
    console.log(JSON.stringify(entries, null, 2));
    return;
  }

  const errorCount = filtered.filter(l => l.includes('[ERROR]')).length;
  if (!opts.errors && errorCount > 0) {
    console.log(`\x1b[33m⚠ Encontrados ${errorCount} errores. Usa --errors para verlos.\x1b[0m`);
  }

  filtered.forEach(line => {
    if (line.includes('[ERROR]')) {
      console.log(`\x1b[31m${line}\x1b[0m`);
    } else if (line.includes('[WARN]')) {
      console.log(`\x1b[33m${line}\x1b[0m`);
    } else {
      console.log(line);
    }
  });
}

if (require.main === module) {
  const hasWatch = process.argv.includes('--watch') || process.argv.includes('-w');
  if (hasWatch) {
    const cp = require('child_process');
    const script = process.argv.filter(a => a !== '--watch' && a !== '-w');
    if (process.platform === 'win32') {
      cp.spawn('powershell', ['-Command', `Get-Content "${LOG_FILE}" -Tail 10 -Wait`], { stdio: 'inherit' });
    } else {
      cp.spawn('tail', ['-f', '-n', '10', LOG_FILE], { stdio: 'inherit' });
    }
  } else {
    readLogs();
  }
}

module.exports = { readLogs, parseArgs, passesFilter };
