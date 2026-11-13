import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import vm from 'node:vm';
import ts from 'typescript';
const filename=path.resolve('lib/writing/types.ts');
const source=fs.readFileSync(filename,'utf8');
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,esModuleInterop:true}}).outputText;
const scope={exports:{},require:createRequire(filename)};
vm.runInNewContext(compiled,scope,{filename});
const {checklistScores}=scope.exports;
const codes=['TA','CC','LR','GRA'];
const profile={task:1,family:'academic',label:'Scoring fixture',focus:[],checks:Object.fromEntries(['intro','body','whole'].map(kind=>[kind,[{id:kind,text:kind,codes}]]))};
const section=(kind,state,paragraph)=>({kind,checks:{[kind]:state},paragraph,title:kind,note:'',quote:''});
const score=(sections)=>checklistScores(profile,sections,1);
for(const [state,expected] of [['met',9],['partial',4.5],['missing',0]]){
 const actual=score(['intro','body','whole'].map((kind,index)=>section(kind,state,index)));
 assert.ok(Object.values(actual).every(n=>n===expected),state);
}
assert.equal(score([section('intro','unreviewed',0)]),null);
assert.equal(score([section('intro','na',0)]),null);
const repeated=score([section('intro','met',0),section('body','partial',1),section('body','missing',2),section('whole','met',-1)]);
assert.ok(Object.values(repeated).every(n=>n===6),'Repeated criterion uses lowest result once');
const excluded=score([section('intro','met',0),section('body','na',1),section('whole','missing',-1)]);
assert.ok(Object.values(excluded).every(n=>n===4.5),'NA is excluded from denominator');
const uncovered={...profile,checks:{intro:[{id:'intro',text:'Only TA',codes:['TA']}]}};
assert.equal(checklistScores(uncovered,[section('intro','met',0)],1),null);
console.log('CHECKLIST_SCORES_OK met; partial; missing; unreviewed; NA; repeated criterion; uncovered IELTS codes');
