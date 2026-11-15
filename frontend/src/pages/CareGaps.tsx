import {useState} from "react";
import {Link} from "react-router-dom";
import {useQuery} from "@tanstack/react-query";
import {get} from "../api";
import {useAuth} from "../auth";
import {dateTime,Empty,ErrorBox,Field,Heading,Pager} from "../components/UI";
import {WorkflowForm,formText} from "../components/WorkflowForm";
import type {Page} from "../types";
import type {Gap,GapRule} from "../careTypes";

export function CareGaps(){
  const {user}=useAuth();const [page,setPage]=useState(0);const [resolved,setResolved]=useState(false);
  const [type,setType]=useState("FOLLOW_UP");const [trigger,setTrigger]=useState("OVERDUE");
  const query=useQuery({queryKey:["gaps",page,resolved],queryFn:()=>get<Page<Gap>>("/care/gaps",{page,size:15,resolved}),refetchInterval:30000});
  const rules=useQuery({queryKey:["rules"],queryFn:()=>get<GapRule[]>("/care/gaps/rules"),enabled:user?.role!=="PATIENT"});
  return <section className="page"><Heading eyebrow="KEEP CARE MOVING" title="Care gaps & follow-ups"/>
    <p className="intro">Workflow alerts from rules configured by your care team. Completing the activity resolves its alert.</p>
    <div className="toolbar"><Field label="Alert status"><select value={String(resolved)} onChange={e=>{setResolved(e.target.value==="true");setPage(0);}}><option value="false">Open alerts</option><option value="true">Resolved alerts</option></select></Field>
      {user?.role==="ADMIN"&&<WorkflowForm path="/care/gaps/evaluate" label="Evaluate configured rules"/>}</div>
    <ErrorBox error={query.error}/>{query.isPending?<p>Loading alerts…</p>:query.data?.content.length===0?<Empty title={resolved?"No resolved alerts":"No open care gaps"}>Only explicitly configured rules generate alerts.</Empty>:query.data?.content.map(g=><article className="panel" key={g.id}>
      <span className={`status ${g.severity==="HIGH"?"rejected":"pending"}`}>{g.severity.toLowerCase()}</span><h2>{g.ruleName}</h2><p>{g.patientName} · {g.activityType.toLowerCase().replaceAll("_"," ")}</p><p>Detected {dateTime(g.createdAt)}{g.resolvedAt&&` · Resolved ${dateTime(g.resolvedAt)}`}</p><Link to={`/care/${g.planId}`} className="text-link">Review care plan →</Link></article>)}<Pager data={query.data} onPage={setPage}/>
    {user?.role!=="PATIENT"&&<><h2>Follow-up rules</h2><ErrorBox error={rules.error}/>{rules.isPending?<p>Loading rules…</p>:rules.data?.length===0?<p className="notice">No rules configured. Add a rule to start detecting workflow gaps.</p>:rules.data?.map(r=><article className="panel" key={`${r.id}-${r.version}`}><h3>{r.name}</h3><p>{r.activityType} · {r.triggerCondition} · {r.intervalHours} hour grace period · {r.severity}</p><p>Notify: {r.notificationPolicy} · Responsible: {r.responsibleRole}</p>
      <WorkflowForm path={`/care/gaps/rules/${r.id}`} method="patch" label={r.enabled?"Disable rule":"Enable new rule revision"} body={()=>({version:r.version,enabled:!r.enabled})}/></article>)}
      <details className="panel"><summary>Configure a follow-up rule</summary><p className="muted">Providers' rules apply to their own plans. Admin rules apply across the platform. No clinical rule is inferred automatically.</p>
        <WorkflowForm path="/care/gaps/rules" label="Create rule" body={d=>({name:formText(d,"name"),activityType:type,triggerCondition:trigger,intervalHours:Number(formText(d,"intervalHours")),severity:formText(d,"severity"),notificationPolicy:formText(d,"notificationPolicy"),responsibleRole:formText(d,"responsibleRole")})}>
          <Field label="Rule name"><input name="name" required maxLength={160}/></Field><Field label="Activity type"><select value={type} onChange={e=>{setType(e.target.value);if(e.target.value!=="REFERRAL")setTrigger("OVERDUE");}}>{["FOLLOW_UP","INVESTIGATION","CONSULTATION","REFERRAL"].map(t=><option key={t}>{t}</option>)}</select></Field>
          <Field label="Trigger condition"><select value={trigger} onChange={e=>setTrigger(e.target.value)}><option>OVERDUE</option>{type==="REFERRAL"&&<option>UNSCHEDULED</option>}</select></Field>
          <Field label="Grace period (hours)"><input type="number" min={0} max={8760} name="intervalHours" required defaultValue={0}/></Field><Field label="Severity"><select name="severity"><option>INFO</option><option>WARNING</option><option>HIGH</option></select></Field>
          <Field label="Notification policy"><select name="notificationPolicy"><option value="IN_APP">Notify in app</option><option value="NONE">Dashboard only</option></select></Field><Field label="Responsible role"><select name="responsibleRole"><option>DOCTOR</option>{user?.role==="ADMIN"&&<option>ADMIN</option>}</select></Field>
        </WorkflowForm></details></>}
  </section>;
}
