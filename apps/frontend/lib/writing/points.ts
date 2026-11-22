import type {Assignment,Review} from './types';

export function pointRows(assignment:Pick<Assignment,'tasks'|'profiles'|'rubricCatalog'|'assessmentCategories'|'scoring'>){
 const rows=assignment.tasks.slice().sort().flatMap(task=>{
  const profile=assignment.rubricCatalog?.[assignment.profiles[String(task)]];
  return Object.entries(profile?.checks||{}).filter(([kind])=>!assignment.assessmentCategories?.[String(task)]||assignment.assessmentCategories[String(task)].includes(kind))
   .flatMap(([kind,checks])=>checks.map(check=>({task,kind,key:kind+':'+check.id,text:check.text,weight:check.weight??2,max:0})));
 });
 const cents=Math.round((assignment.scoring?.total||10)*100),sum=rows.reduce((n,r)=>n+r.weight,0);
 if(!rows.length||!Number.isFinite(sum)||sum<=0||!Number.isFinite(cents)||cents<rows.length)return rows;
 const raw=rows.map(r=>(cents-rows.length)*r.weight/sum),allocated=raw.map(n=>Math.floor(n+1e-9)+1);
 const order=raw.map((n,i)=>({i,remainder:n-Math.floor(n+1e-9)})).sort((a,b)=>b.remainder-a.remainder||a.i-b.i);
 for(const {i} of order.slice(0,cents-allocated.reduce((a,b)=>a+b,0)))allocated[i]++;
 return rows.map((r,i)=>({...r,max:allocated[i]/100}));
}
export function pointProgress(assignment:Assignment,review:Review){
 const rows=pointRows(assignment);let score=0,graded=0;
 for(const row of rows){const n=review.tasks[String(row.task)]?.criterionScores?.[row.key];if(n!==undefined&&n!==null){score+=n;graded++;}}
 return {score:Math.round(score*100)/100,graded,count:rows.length};
}
