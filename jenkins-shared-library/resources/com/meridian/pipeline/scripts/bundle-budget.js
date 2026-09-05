#!/usr/bin/env node
// Reads the Webpack stats.json emitted by @angular-devkit/build-angular:browser and prints the
// initial bundle size against the budget. TOOL-1207: the esbuild builder does not emit stats.json
// in the same shape; this script will need rewriting when a repository moves to it.
'use strict';
const fs = require('fs');
const path = require('path');

const distDir = process.argv[2] || 'dist';
const budgetKb = Number(process.env.INITIAL_BUDGET_KB || 2048);

function findStats(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...findStats(full));
    else if (entry.name === 'stats.json') out.push(full);
  }
  return out;
}

const stats = findStats(distDir);
if (stats.length === 0) {
  console.log('bundle-budget: no stats.json under ' + distDir + ', skipping');
  process.exit(0);
}

const parsed = JSON.parse(fs.readFileSync(stats[0], 'utf8'));
const initial = (parsed.assets || [])
  .filter((a) => /^(main|polyfills|runtime|vendor|styles)\.[a-f0-9]*\.?js$/.test(a.name) || /^styles\.[a-f0-9]*\.?css$/.test(a.name))
  .reduce((sum, a) => sum + a.size, 0);

const kb = Math.round(initial / 1024);
console.log('bundle-budget: initial ' + kb + ' KB, budget ' + budgetKb + ' KB');
if (kb > budgetKb) {
  console.error('bundle-budget: over budget');
  process.exit(1);
}
