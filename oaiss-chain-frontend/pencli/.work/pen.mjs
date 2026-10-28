// Sends page-builder snippets to pen.dev via MCP stdio (execute tool).
// node pen.mjs <specfile.mjs> [--dry] [--only "01" ...]
import { spawn } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { HELPERS_SRC } from './lib.js';

export const PEN = 'pencli/UI_Desgin.pen';
const EXE = 'C:/Users/LiShuai/AppData/Local/Programs/Pen/resources/app.asar.unpacked/out/mcp-server-windows-x64.exe';

export function openSession() {
  const child = spawn(EXE, ['--app', 'desktop'], { stdio: ['pipe', 'pipe', 'pipe'] });
  let buf = '';
  const pending = new Map();
  child.stdout.on('data', (d) => {
    buf += d.toString();
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (!line) continue;
      try {
        const m = JSON.parse(line);
        if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
      } catch {}
    }
  });
  child.stderr.on('data', () => {});
  const req = (method, params, id) => new Promise((r) => {
    pending.set(id, r);
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
  });
  return { child, req };
}

export async function withSession(fn) {
  const { child, req } = openSession();
  let id = 1;
  await req('initialize', { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'zcode', version: '1' } }, id++);
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
  const call = async (name, input) => {
    const r = await req('tools/call', { name, arguments: input }, id++);
    if (r.error) return { ok: false, text: JSON.stringify(r.error) };
    const c = r.result?.content || [];
    const text = c.filter((x) => x.type === 'text').map((x) => x.text).join('\n');
    const images = c.filter((x) => x.type === 'image').map((x) => ({ mime: x.mimeType, data: x.data }));
    return { ok: !r.result?.isError, text, images };
  };
  try {
    return await fn(call);
  } finally {
    child.kill();
  }
}

export async function execute(input) {
  const res = await withSession((call) => call('execute', { filePath: PEN, input }));
  // The execute tool is stateful: a failed block rolls back and expects an edited retry.
  const editId = res.text.match(/"editId":\s*"([^"]+)"/)?.[1];
  if (!res.ok && editId) return { ...res, editId };
  return res;
}

// Retry a failed execute by patching the snippet under the same editId.
export async function executeEdit(editId, oldText, newText) {
  return withSession((call) => call('execute', { filePath: PEN, editId, edits: [{ find: oldText, replace: newText }] }));
}

async function main() {
  const file = process.argv[2];
  const dry = process.argv.includes('--dry');
  const onlyIdx = process.argv.indexOf('--only');
  const only = onlyIdx > 0 ? process.argv.slice(onlyIdx + 1) : null;

  const mod = await import(pathToFileURL(file).href);
  const pages = (mod.pages || []).filter((p) => !only || only.some((o) => p.name.startsWith(o)));

  for (const p of pages) {
    const src = HELPERS_SRC + '\n' + p.code;
    if (dry) { console.log(`--- ${p.name} (${src.length} chars) ---`); continue; }
    const res = await execute(src);
    const printed = res.text.split('\n').filter((l) => l && !/^[A-Za-z_$][\w$]*=/.test(l)).slice(0, 6).join('\n');
    console.log(`\n=== ${p.name} === ${res.ok ? 'OK' : 'FAIL'}`);
    if (printed.trim()) console.log(printed.slice(0, 900));
  }
  process.exit(0);
}

if (process.argv[1] && process.argv[1].endsWith('pen.mjs')) main();
