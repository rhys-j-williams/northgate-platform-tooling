'use strict';
// Shared helpers for the mock scanner CLIs. Zero dependencies on purpose: these run on the Node 14
// agent as well as the Node 18 one, and installing anything on the RHEL 7 image is a TOOL ticket.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DEFAULT_EXCLUDES = [
  'node_modules', 'dist', 'coverage', 'target', '.git', '.angular', 'build', 'out-tsc',
  '.cx-reports', '.sonar-reports', '.xray-reports', '.helm-out', '.terraform', 'storage', '.verdaccio',
  'karma-results', 'cypress/videos', 'cypress/screenshots',
];

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('-D')) {
      // sonar style -Dkey=value
      const eq = a.indexOf('=');
      out.D = out.D || {};
      out.D[a.slice(2, eq)] = a.slice(eq + 1);
    } else if (a.startsWith('--')) {
      const key = a.slice(2);
      const next = argv[i + 1];
      if (next !== undefined && !next.startsWith('-')) {
        out[key] = next;
        i++;
      } else {
        out[key] = true;
      }
    } else {
      out._.push(a);
    }
  }
  return out;
}

/**
 * Just enough YAML for checkmarx.yml. Block maps, block lists of scalars or of maps, flow lists
 * `[a, b]`, quoted scalars, comments. Not a YAML parser. If you need anchors you need a real
 * tool and a TOOL ticket for the agent image.
 */
function parseYamlLite(text) {
  const lines = text.split(/\r?\n/)
    .map((l) => l.replace(/\s+#.*$/, '').replace(/^\s*#.*$/, ''))
    .filter((l) => l.trim().length > 0);

  let idx = 0;

  function indentOf(line) {
    return line.match(/^ */)[0].length;
  }

  function scalar(raw) {
    let v = raw.trim();
    if (v === '') return null;
    if (v.startsWith('[') && v.endsWith(']')) {
      return v.slice(1, -1).split(',').map((s) => scalar(s)).filter((s) => s !== null);
    }
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      return v.slice(1, -1);
    }
    if (v === 'true') return true;
    if (v === 'false') return false;
    if (/^-?\d+(\.\d+)?$/.test(v)) return Number(v);
    return v;
  }

  function parseBlock(indent) {
    const first = lines[idx];
    if (first === undefined) return null;
    if (first.trim().startsWith('- ')) return parseList(indent);
    return parseMap(indent);
  }

  function parseMap(indent) {
    const obj = {};
    while (idx < lines.length) {
      const line = lines[idx];
      const ind = indentOf(line);
      if (ind < indent) break;
      if (ind > indent) throw new Error('yaml-lite: unexpected indent at line: ' + line);
      if (line.trim().startsWith('- ')) break;
      const m = line.trim().match(/^([^:]+):\s*(.*)$/);
      if (!m) throw new Error('yaml-lite: cannot parse line: ' + line);
      const key = m[1].trim().replace(/^["']|["']$/g, '');
      const rest = m[2];
      idx++;
      if (rest.trim() === '') {
        const next = lines[idx];
        if (next !== undefined && indentOf(next) > indent) {
          obj[key] = parseBlock(indentOf(next));
        } else if (next !== undefined && indentOf(next) === indent && next.trim().startsWith('- ')) {
          obj[key] = parseList(indent);
        } else {
          obj[key] = null;
        }
      } else {
        obj[key] = scalar(rest);
      }
    }
    return obj;
  }

  function parseList(indent) {
    const arr = [];
    while (idx < lines.length) {
      const line = lines[idx];
      const ind = indentOf(line);
      if (ind < indent || !line.trim().startsWith('- ')) break;
      if (ind > indent) throw new Error('yaml-lite: unexpected list indent at line: ' + line);
      const rest = line.trim().slice(2);
      if (/^[^:\[\]"']+:\s*(.*)$/.test(rest) && !rest.startsWith('"')) {
        // list of maps: rewrite the first entry as an indented line and parse a map
        const inner = indent + 2;
        lines[idx] = ' '.repeat(inner) + rest;
        arr.push(parseMap(inner));
      } else {
        arr.push(scalar(rest));
        idx++;
      }
    }
    return arr;
  }

  return parseBlock(indentOf(lines[0] || '')) || {};
}

function parseProperties(text) {
  const out = {};
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const eq = line.indexOf('=');
    if (eq < 0) continue;
    out[line.slice(0, eq).trim()] = line.slice(eq + 1).trim();
  }
  return out;
}

function globToRegExp(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*') {
      if (glob[i + 1] === '*') {
        re += '.*';
        i++;
        if (glob[i + 1] === '/') i++;
      } else {
        re += '[^/]*';
      }
    } else if (c === '?') {
      re += '[^/]';
    } else if ('.+^${}()|[]\\'.includes(c)) {
      re += '\\' + c;
    } else {
      re += c;
    }
  }
  return new RegExp('(^|/)' + re + '$');
}

function makeExcluder(patterns) {
  const all = DEFAULT_EXCLUDES.concat(patterns || []);
  const plain = all.filter((p) => !/[*?]/.test(p));
  const globs = all.filter((p) => /[*?]/.test(p)).map(globToRegExp);
  return function excluded(rel) {
    const parts = rel.split('/');
    if (parts.some((p) => plain.includes(p))) return true;
    if (plain.some((p) => p.includes('/') && (rel === p || rel.startsWith(p + '/')))) return true;
    return globs.some((g) => g.test(rel));
  };
}

function walk(root, options) {
  const opts = options || {};
  const excluded = makeExcluder(opts.exclude);
  const maxBytes = opts.maxBytes || 512 * 1024;
  const results = [];
  function visit(dir) {
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch (e) {
      return;
    }
    entries.sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      const rel = path.relative(root, full).split(path.sep).join('/');
      if (excluded(rel)) continue;
      if (entry.isSymbolicLink()) continue;
      if (entry.isDirectory()) {
        visit(full);
      } else if (entry.isFile()) {
        let stat;
        try {
          stat = fs.statSync(full);
        } catch (e) {
          continue;
        }
        if (stat.size > maxBytes) continue;
        results.push({ rel, full, size: stat.size });
      }
    }
  }
  visit(root);
  return results;
}

function isProbablyBinary(buf) {
  const len = Math.min(buf.length, 2048);
  for (let i = 0; i < len; i++) {
    if (buf[i] === 0) return true;
  }
  return false;
}

function sha1(text) {
  return crypto.createHash('sha1').update(text).digest('hex');
}

function stableId(prefix, seed) {
  return prefix + '-' + sha1(seed).slice(0, 12);
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function writeJson(file, obj) {
  fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
}

function readIfExists(file) {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch (e) {
    return null;
  }
}

function findFiles(root, predicate, options) {
  return walk(root, options).filter((f) => predicate(f.rel));
}

/** Compare dotted numeric versions. Returns -1, 0, 1. Prerelease suffixes are ignored. */
function compareVersions(a, b) {
  const pa = String(a).replace(/^[^\d]*/, '').split(/[.-]/).map((x) => parseInt(x, 10)).filter((x) => !isNaN(x));
  const pb = String(b).replace(/^[^\d]*/, '').split(/[.-]/).map((x) => parseInt(x, 10)).filter((x) => !isNaN(x));
  const n = Math.max(pa.length, pb.length);
  for (let i = 0; i < n; i++) {
    const x = pa[i] || 0;
    const y = pb[i] || 0;
    if (x < y) return -1;
    if (x > y) return 1;
  }
  return 0;
}

/** range like "<1.2.6" or ">=2.0.0 <2.0.4" or "<1.2.6 || >=2.0.0 <2.0.4" */
function satisfies(version, range) {
  return range.split('||').some((alt) => alt.trim().split(/\s+/).every((clause) => {
    const m = clause.match(/^(<=|>=|<|>|=)?\s*(.+)$/);
    if (!m) return false;
    const op = m[1] || '=';
    const cmp = compareVersions(version, m[2]);
    switch (op) {
      case '<': return cmp < 0;
      case '<=': return cmp <= 0;
      case '>': return cmp > 0;
      case '>=': return cmp >= 0;
      default: return cmp === 0;
    }
  }));
}

const SEVERITY_ORDER = { critical: 0, high: 1, medium: 2, moderate: 2, low: 3, info: 4 };

function severityRank(sev) {
  const r = SEVERITY_ORDER[String(sev).toLowerCase()];
  return r === undefined ? 5 : r;
}

function normaliseSeverity(sev) {
  const s = String(sev || 'info').toLowerCase();
  if (s === 'moderate') return 'Medium';
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Elapsed ms, or 0 under SOURCE_DATE_EPOCH so reports stay byte identical between runs. */
function elapsed(started) {
  return process.env.SOURCE_DATE_EPOCH ? 0 : Date.now() - started;
}

function round(n, places) {
  const f = Math.pow(10, places === undefined ? 1 : places);
  return Math.round(n * f) / f;
}

function timestamp() {
  // Deterministic when SOURCE_DATE_EPOCH is set, which the Jenkins library does for reproducible
  // report diffs between runs of the same commit.
  const epoch = process.env.SOURCE_DATE_EPOCH;
  return (epoch ? new Date(Number(epoch) * 1000) : new Date()).toISOString();
}

module.exports = {
  DEFAULT_EXCLUDES,
  parseArgs,
  parseYamlLite,
  parseProperties,
  globToRegExp,
  makeExcluder,
  walk,
  isProbablyBinary,
  sha1,
  stableId,
  escapeHtml,
  ensureDir,
  writeJson,
  readIfExists,
  findFiles,
  compareVersions,
  satisfies,
  severityRank,
  normaliseSeverity,
  timestamp,
  round,
  elapsed,
};
