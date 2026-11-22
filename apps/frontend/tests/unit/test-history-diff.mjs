import assert from 'node:assert/strict';
import {buildHistoryDiff} from '../../lib/history-diff.ts';

function check(before, after) {
 const result=buildHistoryDiff(before,after);
 assert.equal(result.rows.map(row=>row.before?.text||'').join(''),before);
 assert.equal(result.rows.map(row=>row.after?.text||'').join(''),after);
 assert.deepEqual(result.rows.flatMap(row=>row.before?[row.before.number]:[]),Array.from({length:result.rows.filter(row=>row.before).length},(_,i)=>i+1));
 assert.deepEqual(result.rows.flatMap(row=>row.after?[row.after.number]:[]),Array.from({length:result.rows.filter(row=>row.after).length},(_,i)=>i+1));
 for(const row of result.rows){
  if(row.kind==='same')assert.equal(row.before.text,row.after.text);
  for(const side of ['before','after'])if(row[side])assert.equal(row[side].segments.map(part=>part.text).join(''),row[side].text.replace(/\r?\n$/,''));
 }
 return result;
}
assert.equal(check('same\n','same\n').counts.modified,0);
assert.equal(check('','new\n').counts.added,1);
assert.equal(check('gone\n','').counts.removed,1);
assert.equal(check('a\nc\n','a\nb\nc\n').counts.added,1);
assert.equal(check('a\nb\nc\n','a\nc\n').counts.removed,1);
const separated=check('old first\nKEEP\nold last\n','new first\nKEEP\nnew last\n');
assert.equal(separated.counts.modified,2);
assert.equal(separated.rows[1].kind,'same');
const inline=check('alpha old middle old omega\n','alpha new middle newer omega\n').rows[0];
assert.equal(inline.kind,'modified');
assert.equal(inline.before.segments.filter(p=>p.changed).map(p=>p.text).join(''),'oldold');
assert.equal(inline.after.segments.filter(p=>p.changed).map(p=>p.text).join(''),'newnewer');
assert.ok(inline.after.segments.some(p=>!p.changed&&p.text.includes(' middle ')));
for(const [a,b] of [['a\n\nb\n','a\nb\n'],['a\r\nb\r\n','a\r\nx\r\nb\r\n'],['a','a\n'],['😀 Việt Nam\n','😀 Việt mới\n'],['x\nx\ny\n','x\ny\nx\n'],['<script>x</script>\n','<img onerror=x>\n']])check(a,b);
assert.equal(check('a','a\n').rows[0].endingChanged,true);
let seed=17;const random=()=>((seed=(seed*1664525+1013904223)>>>0)%5);
for(let trial=0;trial<250;trial++)check(Array.from({length:random()+2},()=>['x\n','y\n','z\n','\n','??\n'][random()]).join(''),Array.from({length:random()+2},()=>['x\n','y\n','z\n','\n','??\n'][random()]).join(''));
const large=Array.from({length:15000},(_,i)=>`line ${i}\n`);
const changed=[...large];changed[10]='changed first\n';changed[14000]='changed last\n';
const start=performance.now(),sparse=check(large.join(''),changed.join(''));
assert.equal(sparse.counts.modified,2);assert.equal(sparse.approximate,false);assert.ok(performance.now()-start<2000);
const rewrite=check('x\n'.repeat(1500),'y\n'.repeat(1500));assert.equal(rewrite.approximate,true);
assert.equal(rewrite.counts.modified,1500);
console.log('HISTORY_DIFF_OK exact text, additions/deletions, separated changes, inline gaps, unicode/CRLF/EOF, repeated lines and bounded large rewrites');
