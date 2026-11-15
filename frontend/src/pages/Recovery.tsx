import {useState} from "react";
import {Link} from "react-router-dom";
import {useQuery,useMutation,useQueryClient} from "@tanstack/react-query";
import {api,get} from "../api";
import {dateTime,Empty,ErrorBox,Field,Heading} from "../components/UI";
import {WorkflowForm,formText,formInstant} from "../components/WorkflowForm";
import type {Doctor,Page} from "../types";
import type {Offer,WaitingEntry} from "../careTypes";

export function Recovery(){
  const [preference,setPreference]=useState("provider");const [search,setSearch]=useState("");
  const doctors=useQuery({queryKey:["recovery","providers",search],queryFn:()=>get<Page<Doctor>>("/doctors",{q:search,size:100})});
  const specialties=useQuery({queryKey:["specializations"],queryFn:()=>get<string[]>("/doctors/specializations")});
  const entries=useQuery({queryKey:["recovery","entries"],queryFn:()=>get<WaitingEntry[]>("/recovery/waiting-list")});
  const offers=useQuery({queryKey:["recovery","offers"],queryFn:()=>get<Offer[]>("/recovery/offers"),refetchInterval:15000});
  return <section className="page"><Heading eyebrow="WHEN A TIME OPENS UP" title="Appointment waiting list"/><p className="intro">Tell us when you can attend. When another patient cancels, eligible patients can receive a time-limited offer.</p>
    <h2>Your appointment offers</h2><ErrorBox error={offers.error}/>{offers.isPending?<p>Loading offers…</p>:offers.data?.length===0?<Empty title="No offers yet">Offers will appear here when a cancelled visit matches your preferences.</Empty>:offers.data?.map(o=><OfferCard key={o.id} offer={o}/>)}
    <div className="care-columns"><section className="panel"><h2>Join the waiting list</h2><WorkflowForm path="/recovery/waiting-list" label="Join waiting list" body={d=>({...(preference==="provider"?{doctorId:formText(d,"doctorId")}:{specialty:formText(d,"specialty")}),availableFrom:formInstant(d,"availableFrom"),availableTo:formInstant(d,"availableTo"),notifyInApp:d.get("notifyInApp")==="on"})}>
      <Field label="Match by"><select value={preference} onChange={e=>setPreference(e.target.value)}><option value="provider">Preferred provider</option><option value="specialty">Specialty</option></select></Field>
      {preference==="provider"?<><Field label="Find preferred provider"><input value={search} onChange={e=>setSearch(e.target.value)}/></Field><Field label="Preferred provider"><select name="doctorId" required defaultValue=""><option value="">Choose a provider</option>{doctors.data?.content.map(d=><option value={d.id} key={d.id}>{d.fullName} · {d.specialization}</option>)}</select></Field></>:<Field label="Preferred specialty"><select name="specialty" required defaultValue=""><option value="">Choose a specialty</option>{specialties.data?.map(s=><option key={s}>{s}</option>)}</select></Field>}
      <Field label="Available from"><input type="datetime-local" name="availableFrom" required/></Field><Field label="Available until"><input type="datetime-local" name="availableTo" required/></Field><label className="checkbox"><input type="checkbox" name="notifyInApp" defaultChecked/>Notify me in the app</label><p className="muted">Choose a continuous availability window within the next 90 days. Offers are not reservations; the first valid acceptance books the slot.</p><ErrorBox error={doctors.error||specialties.error}/>
    </WorkflowForm></section><section className="panel"><h2>Your preferences</h2><ErrorBox error={entries.error}/>{entries.isPending?<p>Loading preferences…</p>:entries.data?.length===0?<Empty title="No waiting-list preferences"/>:entries.data?.map(e=><EntryCard key={`${e.id}-${e.active}`} entry={e}/>)}</section></div>
  </section>;
}
function EntryCard({entry:e}:{entry:WaitingEntry}){return <article className="care-entry"><h3>{e.specialty??"Preferred provider"}</h3>{e.doctorId&&<Link to={`/doctors/${e.doctorId}`}>View provider</Link>}<p>{dateTime(e.availableFrom)} – {dateTime(e.availableTo)}</p><p>{e.active?"Waiting":"Inactive"} · {e.notifyInApp?"In-app notifications on":"Notifications off"}</p>{e.active&&<WorkflowForm path={`/recovery/waiting-list/${e.id}`} method="delete" label="Leave waiting list"/>}</article>;}
function OfferCard({offer:o}:{offer:Offer}){
  // Keep the same retry key while this offer is mounted, including after network errors.
  const [key]=useState(()=>crypto.randomUUID());const cache=useQueryClient();
  const claim=useMutation({mutationFn:()=>api.post(`/recovery/offers/${o.id}/accept`,{idempotencyKey:key}),onSuccess:()=>{cache.invalidateQueries({queryKey:["recovery"]});cache.invalidateQueries({queryKey:["appointments"]});},onError:()=>cache.invalidateQueries({queryKey:["recovery","offers"]})});
  const open=o.status==="OPEN"&&new Date(o.expiresAt)>new Date();
  return <article className="panel"><span className={`status ${o.status==="ACCEPTED"?"confirmed":"pending"}`}>{o.status.toLowerCase()}</span><h3>{o.providerName}</h3><p>{dateTime(o.startTime)} – {dateTime(o.endTime)}</p>{open&&<p>Offer expires {dateTime(o.expiresAt)}</p>}<ErrorBox error={claim.error}/>{open&&<button className="btn" disabled={claim.isPending} onClick={()=>claim.mutate()}>{claim.isPending?"Booking…":"Accept appointment offer"}</button>}{o.status==="ACCEPTED"&&<Link to="/appointments" className="text-link">View your booked appointment →</Link>}</article>;
}
