// Explicit, repeatable local seed through the same authorized APIs used by the browser.
import { existsSync } from 'node:fs';
if (existsSync('.env')) process.loadEnvFile('.env');
const base = process.env.API_URL || 'http://localhost:8080/api/v1';
const origin = (process.env.ALLOWED_ORIGINS || 'http://localhost:3000').split(',')[0];
const password = process.env.DEMO_PASSWORD;
if (!password || password.length < 12 || !process.env.ADMIN_EMAIL || !process.env.ADMIN_PASSWORD) throw new Error('Set DEMO_PASSWORD (12+ characters), ADMIN_EMAIL and ADMIN_PASSWORD in your environment or .env.');
async function request(path, method='GET', body, token) {
  const response = await fetch(base+path, {method, headers:{'Content-Type':'application/json',Origin:origin,...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined});
  const data = response.status===204?null:await response.json();
  if (!response.ok) { const error=new Error(`${method} ${path}: ${response.status} ${data.message ?? ''}`); error.status=response.status; throw error; }
  return data;
}
async function account(role, data) {
  try { return await request(`/auth/register/${role}`,'POST',{...data,password}); }
  catch(error) { if(error.status!==409)throw error; return request('/auth/login','POST',{email:data.email,password}); }
}
const admin=await request('/auth/login','POST',{email:process.env.ADMIN_EMAIL,password:process.env.ADMIN_PASSWORD});
const profiles=[
  {fullName:'Dr. Ananya Rao',email:'ananya@carebridge.example',specialization:'Cardiology',qualification:'MD, Cardiology',experienceYears:12,consultationFee:900,clinicAddress:'24 Garden Road, Bengaluru',biography:'A demo cardiology profile for exploring the CareBridge appointment workflow.'},
  {fullName:'Dr. Arjun Mehta',email:'arjun@carebridge.example',specialization:'General Medicine',qualification:'MD, Internal Medicine',experienceYears:8,consultationFee:600,clinicAddress:'8 Lake View Road, Bengaluru',biography:'A demo general medicine profile. These fictional accounts are for local development only.'},
  {fullName:'Dr. Maya Sen',email:'maya@carebridge.example',specialization:'Dermatology',qualification:'MD, Dermatology',experienceYears:10,consultationFee:800,clinicAddress:'42 Park Avenue, Bengaluru',biography:'A demo dermatology profile for testing doctor discovery and appointment coordination.'}
];
const doctorSessions=[];
for(const profile of profiles) {
  const {fullName,email,specialization,qualification}=profile;
  const session=await account('doctor',{fullName,email,specialization,qualification,timezone:'Asia/Kolkata'});
  const {email:ignored,...fields}=profile; void ignored;
  const doctor=await request('/doctors/me','PUT',{...fields,timezone:'Asia/Kolkata'},session.accessToken);
  await request(`/admin/doctors/${doctor.id}/verify`,'PATCH',{verified:true},admin.accessToken);
  const windows=await request('/doctors/me/availability','GET',undefined,session.accessToken);
  for(let day=1;day<=7;day++) if(!windows.some(w=>w.dayOfWeek===day)) await request('/doctors/me/availability','POST',{dayOfWeek:day,startTime:'09:00',endTime:'17:00',slotDurationMinutes:30,active:true},session.accessToken);
  doctorSessions.push({...session,doctor});
}
const patient=await account('patient',{fullName:'Jamie Patel',email:'patient@carebridge.example'});
const history=await request('/appointments/me','GET',undefined,patient.accessToken);
if(history.totalElements===0) {
  const tomorrow=new Date(Date.now()+86400000).toLocaleDateString('en-CA',{timeZone:'Asia/Kolkata'});
  const doctor=doctorSessions[0]; const slots=await request(`/doctors/${doctor.doctor.id}/slots?date=${tomorrow}`);
  if(slots.length) { const a=await request('/appointments','POST',{doctorId:doctor.doctor.id,...slots[0],reason:'Demo consultation'},patient.accessToken); await request(`/appointments/${a.id}/confirm`,'PATCH',undefined,doctor.accessToken); }
}
console.log('Demo data ready. Patient: patient@carebridge.example. Doctors: '+profiles.map(p=>p.email).join(', ')+'. Password: your DEMO_PASSWORD value.');
