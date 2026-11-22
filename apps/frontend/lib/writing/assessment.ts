export type AssessmentGroup='ielts'|'research';
export function assessmentGroup(category?:string):AssessmentGroup {
    return !category||category==='ielts'?'ielts':'research';
}
export const assessmentLabels={ielts:'IELTS Task',research:'Research'};
export const documentLabels={ielts:'IELTS Task',report:'Report',research:'Research paper'};
