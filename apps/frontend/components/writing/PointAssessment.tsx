"use client";
import {assessmentCategoryTitle,type Assignment,type Review} from '../../lib/writing/types';
import {pointRows,pointProgress} from '../../lib/writing/points';

export default function PointAssessment({assignment,review,task,category,editable,onScore,onTotal}:{assignment:Assignment;review:Review;task:number;category?:string;editable:boolean;onScore:(key:string,value:number|null)=>void;onTotal?:(value:number|null)=>void}){
 const rows=pointRows(assignment).filter(row=>row.task===task&&(!category||row.kind===category));
 const progress=pointProgress(assignment,review);
 return <section className="point-assessment" aria-label="Criterion points">
  <div className="point-assessment-total"><span>{progress.graded}/{progress.count} criteria scored</span><strong>{editable&&onTotal?<input aria-label="Awarded total score" type="number" min={0} max={assignment.scoring?.total} step={0.01} value={review.awardedTotal??progress.score} onChange={e=>onTotal(e.target.value===''?null:Number(e.target.value))}/>:review.awardedTotal??progress.score} / {assignment.scoring?.total}</strong></div>
  {rows.map(row=><label className="point-assessment-row" key={row.key}><span>{row.text}<small>{assessmentCategoryTitle(assignment.rubricCatalog?.[assignment.profiles[String(task)]],row.kind)}</small></span>
   {editable?<input aria-label={`Points ${row.key}`} type="number" min={0} max={row.max} step={.01} value={review.tasks[String(task)]?.criterionScores?.[row.key]??''} placeholder="—" onChange={e=>onScore(row.key,e.target.value===''?null:Number(e.target.value))}/>:<b>{review.tasks[String(task)]?.criterionScores?.[row.key]??'—'}</b>}
   <small>/ {row.max.toFixed(2)}</small>
  </label>)}
 </section>;
}
