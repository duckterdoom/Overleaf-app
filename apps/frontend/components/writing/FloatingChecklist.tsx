"use client";
import {useLayoutEffect,useId,useRef,useState,type ReactNode} from 'react';
import {createPortal} from 'react-dom';
import {ChevronDown,ChevronUp,GripHorizontal,RotateCcw,X} from 'lucide-react';

type Point={x:number;y:number};
export default function FloatingChecklist({title,onClose,children}:{title:string;onClose:()=>void;children:ReactNode}){
 const titleId=useId(),hintId=useId(),contentId=useId(),panel=useRef<HTMLElement>(null),close=useRef<HTMLButtonElement>(null);
 const closeAction=useRef(onClose);closeAction.current=onClose;
 const drag=useRef<{pointer:number;offset:Point}|null>(null);
 const [position,setPosition]=useState<Point|null>(null);
 const [collapsed,setCollapsed]=useState(false);
 useLayoutEffect(()=>{setPosition(old=>old?constrain(old):old);},[collapsed]);
 function constrain(point:Point):Point{
  const rect=panel.current?.getBoundingClientRect();
  const top=(document.querySelector('.app-header')?.getBoundingClientRect().bottom||0)+8;
  return {x:Math.max(8,Math.min(point.x,innerWidth-(rect?.width||420)-8)),y:Math.max(top,Math.min(point.y,innerHeight-(rect?.height||300)-8))};
 }
 function reset(){setPosition(constrain({x:innerWidth-444,y:120}));}
 useLayoutEffect(()=>{
  const trigger=document.activeElement instanceof HTMLElement?document.activeElement:null;
  const windowElement=panel.current;
  reset();close.current?.focus({preventScroll:true});
  const resize=()=>setPosition(old=>old?constrain(old):old);
  const escape=(e:KeyboardEvent)=>{if(e.key==='Escape'){e.preventDefault();e.stopImmediatePropagation();closeAction.current();}};
  window.addEventListener('resize',resize);window.addEventListener('keydown',escape);
  return()=>{window.removeEventListener('resize',resize);window.removeEventListener('keydown',escape);if(windowElement?.contains(document.activeElement)&&trigger?.isConnected&&trigger!==document.body)trigger.focus({preventScroll:true});};
 },[]);
 return createPortal(<aside ref={panel} role="dialog" aria-modal="false" aria-labelledby={titleId} aria-describedby={hintId} className={`floating-checklist${collapsed?' collapsed':''}`} style={{left:position?.x??8,top:position?.y??120,maxHeight:'min(600px, 60dvh, calc(100dvh - 140px))',visibility:position?'visible':'hidden'}}>
  <header><button type="button" className="checklist-drag-handle" aria-label="Move checklist" onPointerDown={e=>{if(e.button!==0)return;const rect=panel.current!.getBoundingClientRect();drag.current={pointer:e.pointerId,offset:{x:e.clientX-rect.left,y:e.clientY-rect.top}};e.currentTarget.setPointerCapture(e.pointerId);}}
   onPointerMove={e=>{const current=drag.current;if(current?.pointer===e.pointerId)setPosition(constrain({x:e.clientX-current.offset.x,y:e.clientY-current.offset.y}));}}
   onPointerUp={()=>{drag.current=null;}} onPointerCancel={()=>{drag.current=null;}} onLostPointerCapture={()=>{drag.current=null;}}
   onKeyDown={e=>{const directions:Record<string,Point>={ArrowLeft:{x:-20,y:0},ArrowRight:{x:20,y:0},ArrowUp:{x:0,y:-20},ArrowDown:{x:0,y:20}};const step=directions[e.key];if(step){e.preventDefault();setPosition(old=>constrain({x:(old?.x||0)+step.x,y:(old?.y||0)+step.y}));}}}><GripHorizontal size={18} aria-hidden="true"/><span id={titleId}>{title==='Checklist score'?title:<>Checklist · {title}</>}</span></button>
   <button type="button" onClick={()=>setCollapsed(value=>!value)} aria-expanded={!collapsed} aria-controls={contentId} aria-label={collapsed?'Expand checklist':'Collapse checklist'} title={collapsed?'Expand checklist':'Collapse checklist'}>{collapsed?<ChevronDown size={16}/>:<ChevronUp size={16}/>}</button>
   <button type="button" onClick={reset} title="Reset position" aria-label="Reset checklist position"><RotateCcw size={16}/></button><button ref={close} type="button" onClick={onClose} aria-label="Close checklist"><X size={18}/></button></header>
  <p hidden={collapsed} id={hintId} className="checklist-move-hint">Drag the title in any direction or use arrow keys. Collapse to focus on the text.</p>
  <div id={contentId} hidden={collapsed} className="floating-checklist-content">{children}</div>
 </aside>,document.body);
}
