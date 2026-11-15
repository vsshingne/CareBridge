import {test,expect,type Page} from "@playwright/test";

async function login(page:Page,email:string,password:string){
  await page.goto("/login");await page.getByLabel("Email address").fill(email);await page.getByLabel("Password",{exact:true}).fill(password);
  await page.getByRole("button",{name:"Sign in",exact:true}).click();await expect(page).toHaveURL(/dashboard/);
}
test("patient registration, provider plan, referral, configured gap and completion use real services",async({page,browser,request,baseURL})=>{
  test.setTimeout(120000);
  const password=process.env.DEMO_PASSWORD!;expect(password).toBeTruthy();
  const email=`journey-${Date.now()}@example.test`;
  await page.goto("/register/patient");await page.getByLabel("Full name",{exact:true}).fill("Synthetic Journey Patient");
  await page.getByLabel("Email address").fill(email);await page.getByLabel("Password",{exact:true}).fill(password);
  await page.getByRole("button",{name:"Create account"}).click();await expect(page).toHaveURL(/dashboard/);
  const headers={Origin:baseURL!};
  const doctorLogin=await request.post("/api/v1/auth/login",{headers,data:{email:"ananya@carebridge.example",password}});expect(doctorLogin.ok()).toBeTruthy();
  const doctorToken=(await doctorLogin.json()).accessToken;const doctorHeaders={...headers,Authorization:`Bearer ${doctorToken}`};
  const profile=await (await request.get("/api/v1/doctors/me",{headers:doctorHeaders})).json();
  const date=new Date(Date.now()+4*86400000).toLocaleDateString("en-CA",{timeZone:"Asia/Kolkata"});
  await page.goto(`/doctors/${profile.id}`);await page.getByLabel(/Appointment date/).fill(date);await expect(page.locator(".slot").first()).toBeVisible();await page.locator(".slot").first().click();
  const reason=`Journey visit ${Date.now()}`;await page.getByLabel("Reason for visit (optional)").fill(reason);await page.getByRole("button",{name:"Request appointment"}).click();await expect(page).toHaveURL(/appointments$/);
  const doctorContext=await browser.newContext();const doctorPage=await doctorContext.newPage();
  try{
    await login(doctorPage,"ananya@carebridge.example",password);await doctorPage.goto("/appointments");
    const visit=doctorPage.locator(".appointment-card").filter({hasText:reason});await visit.getByRole("button",{name:"Confirm",exact:true}).click();await visit.getByRole("link",{name:"Create care plan"}).click();
    const title=`Synthetic plan ${Date.now()}`;await doctorPage.getByLabel("Plan title").fill(title);await doctorPage.getByLabel("Care goals").fill("Complete provider-directed review and specialist referral.");
    await doctorPage.getByRole("button",{name:"Create plan",exact:true}).click();await expect(doctorPage).toHaveURL(/\/care\/[a-f0-9-]+$/);
    const planId=doctorPage.url().split("/").at(-1)!;
    await doctorPage.getByLabel("Receiving provider",{exact:true}).selectOption({label:"Dr. Arjun Mehta · General Medicine"});
    await doctorPage.getByLabel("Clinical reason").fill("Synthetic coordination review");await doctorPage.getByLabel("Referral deadline").fill(`${date}T14:00`);await doctorPage.getByRole("button",{name:"Create referral",exact:true}).click();
    await expect(doctorPage.getByText("Changes saved.")).toBeVisible();
    await doctorPage.goto("/referrals");const referral=doctorPage.locator("article").filter({hasText:"Synthetic Journey Patient"}).filter({hasText:"Synthetic coordination review"}).last();
    await referral.locator("summary").click();await referral.getByRole("button",{name:"Mark pending acceptance"}).click();
    await expect(referral.locator(".status")).toHaveText("pending acceptance");
    // A near-term synthetic deadline exercises the scheduler without changing application time or database state.
    const activityResponse=await request.post(`/api/v1/care/plans/${planId}/activities`,{headers:doctorHeaders,data:{type:"FOLLOW_UP",title:"Synthetic deadline review",dueAt:new Date(Date.now()+5000).toISOString()}});expect(activityResponse.status()).toBe(201);
    await doctorPage.goto("/gaps");await doctorPage.getByText("Configure a follow-up rule",{exact:true}).click();
    await doctorPage.getByLabel("Rule name").fill(`Journey follow-up ${Date.now()}`);await doctorPage.getByLabel("Severity",{exact:true}).selectOption("WARNING");await doctorPage.getByRole("button",{name:"Create rule",exact:true}).click();await expect(doctorPage.getByText("Changes saved.")).toBeVisible();
    const adminLogin=await request.post("/api/v1/auth/login",{headers,data:{email:process.env.ADMIN_EMAIL,password:process.env.ADMIN_PASSWORD}});expect(adminLogin.ok()).toBeTruthy();const adminToken=(await adminLogin.json()).accessToken;
    await expect.poll(async()=>{const response=await request.post("/api/v1/care/gaps/evaluate",{headers:{...headers,Authorization:`Bearer ${adminToken}`}});expect(response.status()).toBe(204);const gaps=await (await request.get("/api/v1/care/gaps",{headers:doctorHeaders})).json();return gaps.content.some((g:{planId:string})=>g.planId===planId);},{timeout:20000}).toBeTruthy();
    await page.goto("/care");await page.getByRole("link",{name:title,exact:true}).click();await expect(page.getByText("Synthetic deadline review",{exact:true})).toBeVisible();
    await page.goto("/gaps");await expect(page.getByText("Synthetic Journey Patient",{exact:false}).first()).toBeVisible();
    await doctorPage.goto(`/care/${planId}`);let activity=doctorPage.locator("article").filter({hasText:"Synthetic deadline review"});await activity.locator("summary").click();await activity.getByLabel("Next activity status").selectOption("IN_PROGRESS");await activity.getByRole("button",{name:"Update activity status"}).click();
    await expect(activity.locator(".status")).toHaveText("in progress");await activity.locator("summary").click();await activity.getByLabel("Next activity status").selectOption("COMPLETED");await activity.getByLabel("Outcome (required for completion)").fill("Synthetic review completed by responsible provider.");await activity.getByRole("button",{name:"Update activity status"}).click();await expect(activity.locator(".status")).toHaveText("completed");
    await page.goto(`/care/${planId}`);await expect(page.getByText("Synthetic review completed by responsible provider.",{exact:false})).toBeVisible();
    await page.getByRole("link",{name:"Care journey",exact:true}).click();await page.getByLabel("Activity filter").selectOption("FOLLOW_UP");await page.getByLabel("Status filter").selectOption("COMPLETED");await expect(page.getByRole("heading",{name:"care activity completed",exact:true})).toBeVisible();
    await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
    await page.screenshot({path:"test-results/care-journey-mobile.png",fullPage:true});
  }finally{await doctorContext.close();}
});

test("waiting list receives a Kafka-driven offer and accepts it",async({page,request,baseURL})=>{
  test.setTimeout(90000);const password=process.env.DEMO_PASSWORD!;const headers={Origin:baseURL!};
  const email=`waiting-${Date.now()}@example.test`;
  const registered=await request.post("/api/v1/auth/register/patient",{headers,data:{fullName:"Synthetic Waiting Patient",email,password}});expect(registered.status()).toBe(201);
  const patientLogin=await request.post("/api/v1/auth/login",{headers,data:{email:"patient@carebridge.example",password}});const patientToken=(await patientLogin.json()).accessToken;
  const doctors=await (await request.get("/api/v1/doctors?size=100")).json();const doctor=doctors.content.find((d:{fullName:string})=>d.fullName==="Dr. Maya Sen");
  const date=new Date(Date.now()+5*86400000).toLocaleDateString("en-CA",{timeZone:"Asia/Kolkata"});const slots=await (await request.get(`/api/v1/doctors/${doctor.id}/slots?date=${date}`)).json();const slot=slots[0];expect(slot).toBeTruthy();
  const booked=await request.post("/api/v1/appointments",{headers:{...headers,Authorization:`Bearer ${patientToken}`},data:{doctorId:doctor.id,...slot,reason:"Synthetic recovery visit"}});expect(booked.status()).toBe(201);const appointment=await booked.json();
  await login(page,email,password);await page.goto("/recovery");await page.getByLabel("Preferred provider",{exact:true}).selectOption(doctor.id);
  const local=(value:string)=>{const d=new Date(value);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
  await page.getByLabel("Available from").fill(local(slot.startTime));await page.getByLabel("Available until").fill(local(slot.endTime));await page.getByRole("button",{name:"Join waiting list"}).click();await expect(page.getByText("Changes saved.")).toBeVisible();
  expect((await request.patch(`/api/v1/appointments/${appointment.id}/cancel`,{headers:{...headers,Authorization:`Bearer ${patientToken}`}})).ok()).toBeTruthy();
  await expect(page.getByRole("button",{name:"Accept appointment offer"})).toBeVisible({timeout:45000});await page.getByRole("button",{name:"Accept appointment offer"}).click();await expect(page.getByRole("link",{name:"View your booked appointment"})).toBeVisible();
  await page.getByRole("link",{name:"View your booked appointment"}).click();await expect(page.locator(".appointment-card").filter({hasText:"Waiting-list offer accepted"})).toBeVisible();
});
