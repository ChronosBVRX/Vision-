const fs = require('fs');
const path = require('path');

const LOCK_FILE = path.join(__dirname, 'agent.lock');
const USAGE = `
Uso: node agent-lock.js <comando> [args]

Comandos:
  acquire <agente> <archivo> [tarea]  — Adquiere lock para editar un archivo
  release                              — Libera el lock actual
  check [archivo]                      — Verifica si hay lock (opcionalmente para un archivo)
  status                               — Muestra el lock actual como JSON

Ejemplos:
  node agent-lock.js acquire opencode server.js "Fix bug en parser"
  node agent-lock.js acquire antigravity App.jsx "Agregar sidebar"
  node agent-lock.js check server.js
  node agent-lock.js release
  node agent-lock.js status
`;

function getLock() {
  try {
    if (fs.existsSync(LOCK_FILE)) {
      return JSON.parse(fs.readFileSync(LOCK_FILE, 'utf8'));
    }
  } catch (e) {}
  return null;
}

function setLock(data) {
  fs.writeFileSync(LOCK_FILE, JSON.stringify(data, null, 2), 'utf8');
}

function releaseLock() {
  if (fs.existsSync(LOCK_FILE)) {
    fs.unlinkSync(LOCK_FILE);
    return true;
  }
  return false;
}

const cmd = process.argv[2];

if (!cmd || cmd === '--help' || cmd === '-h') {
  console.log(USAGE);
  process.exit(0);
}

switch (cmd) {
  case 'acquire': {
    const agent = process.argv[3];
    const file = process.argv[4];
    const task = process.argv.slice(5).join(' ') || 'Sin descripción';

    if (!agent || !file) {
      console.error('❌ Uso: node agent-lock.js acquire <agente> <archivo> [tarea]');
      process.exit(1);
    }

    const existing = getLock();
    if (existing) {
      if (existing.file === file) {
        console.error(`❌ BLOQUEADO: ${existing.agent} ya está editando ${file} desde ${existing.since}`);
        console.error(JSON.stringify(existing, null, 2));
        process.exit(1);
      }
      console.warn(`⚠ CUIDADO: ${existing.agent} ya tiene un lock activo (${existing.file}).`);
      console.warn('  Se creará un lock adicional. Asegúrate de que no haya conflictos.');
    }

    const lock = {
      agent,
      file,
      task,
      since: new Date().toISOString(),
      host: require('os').hostname()
    };
    setLock(lock);
    console.log(`🔒 Lock adquirido: ${agent} → ${file} (${task})`);
    break;
  }

  case 'release': {
    const lock = getLock();
    if (lock) {
      const elapsed = Math.floor((new Date() - new Date(lock.since)) / 1000);
      console.log(`🔓 Lock liberado: ${lock.agent} editó ${lock.file} por ${elapsed}s`);
      releaseLock();
    } else {
      console.log('ℹ No hay lock activo.');
    }
    break;
  }

  case 'check': {
    const targetFile = process.argv[3];
    const lock = getLock();
    if (!lock) {
      if (targetFile) {
        console.log(JSON.stringify({ locked: false }));
      } else {
        console.log('ℹ No hay lock activo.');
      }
      process.exit(0);
    }
    if (targetFile) {
      const isLocked = lock.file === targetFile;
      console.log(JSON.stringify({ locked: isLocked, agent: lock.agent, file: lock.file, since: lock.since, task: lock.task }));
    } else {
      console.log(JSON.stringify(lock, null, 2));
    }
    break;
  }

  case 'status': {
    const lock = getLock();
    if (lock) {
      console.log(JSON.stringify(lock, null, 2));
    } else {
      console.log(JSON.stringify({ locked: false }));
    }
    break;
  }

  default:
    console.error(`Comando desconocido: ${cmd}`);
    console.log(USAGE);
    process.exit(1);
}
