import { expect, test, type Page } from "@playwright/test";

const permissions = ["TASK_READ","TASK_MANAGE","NOTIFICATION_MANAGE","REMINDER_MANAGE"];
const user = { id:"u10",email:"ops@example.com",displayName:"Mira Shah",organizationId:"o10",organizationName:"Northstar Studio",organizationSlug:"northstar",permissions };
const auth = { accessToken:"mock.jwt",accessTokenExpiresAt:"2099-01-01T00:00:00Z",user,mfaRequired:false,mfaSetupRequired:false,challengeToken:null };
function envelope(data:unknown){return{success:true,data,meta:{},requestId:"phase10-e2e"}}
async function authRoute(page:Page,granted=permissions){await page.route("**/api/v1/auth/refresh",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope({...auth,user:{...user,permissions:granted}}))}))}

test("task workspace exposes bounded operational work without browser errors", async ({page})=>{
  const errors:string[]=[]; page.on("console",m=>{if(m.type()==="error")errors.push(m.text())});
  await authRoute(page);
  await page.route("**/api/v1/tasks?**",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([{id:"t1",projectId:"p1",onboardingId:"ob1",stepId:"s1",title:"Review submitted access",description:"Verify provider-side access before completion.",taskType:"WORKFLOW_GENERATED",status:"IN_REVIEW",priority:"HIGH",assignedUserId:"u10",assignedUserType:"INTERNAL",assignedRoleId:null,dueAt:"2099-08-25T10:00:00Z",completedAt:null,createdAt:"2026-08-21T08:00:00Z",updatedAt:"2026-08-21T08:00:00Z",version:2}]))}));
  await page.goto("/app/tasks");
  await expect(page.getByRole("heading",{name:"Work that has a clear owner"})).toBeVisible();
  await expect(page.getByText("Review submitted access")).toBeVisible();
  await expect(page.getByText("WORKFLOW_GENERATED")).toBeVisible();
  await expect(page.getByText("IN REVIEW")).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth)).toBe(true);
  expect(errors).toEqual([]);
});

test("notification center exposes traceable transactional updates", async ({page})=>{
  await authRoute(page);
  await page.route("**/api/v1/notifications?**",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([{id:"n1",eventType:"ONBOARDING_READY_FOR_REVIEW",sourceType:"ONBOARDING",sourceId:"ob1",projectId:"p1",title:"Onboarding is ready for review",body:"Every blocking requirement is complete.",actionUrl:"/app/projects/p1/review",readAt:null,createdAt:"2026-08-21T08:00:00Z"}]))}));
  await page.route("**/api/v1/notifications/preferences",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope({inAppEnabled:true,emailEnabled:true}))}));
  await page.goto("/app/notifications");
  await expect(page.getByRole("heading",{name:"A calm, traceable notification center"})).toBeVisible();
  await expect(page.getByText("Onboarding is ready for review")).toBeVisible();
  await expect(page.getByText("Every blocking requirement is complete.")).toBeVisible();
});

test("reminder engine explains bounded suppression policy", async ({page})=>{
  await authRoute(page);
  await page.route("**/api/v1/reminder-policies?**",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([{id:"r1",name:"Client action follow-up",description:"Bounded follow-up",initialDelayMinutes:1440,repeatIntervalMinutes:1440,maximumReminders:3,businessHoursOnly:true,timezone:"Asia/Kolkata",channels:["EMAIL","IN_APP"],stopWhenCompleted:true,status:"ACTIVE",updatedAt:"2026-08-21T08:00:00Z",version:0}]))}));
  await page.route("**/api/v1/reminder-policies/schedules?**",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([]))}));
  await page.goto("/app/reminders");
  await expect(page.getByRole("heading",{name:"Nudge only while action is still valid"})).toBeVisible();
  await expect(page.getByText("Client action follow-up")).toBeVisible();
  await expect(page.getByText(/server suppresses completed, cancelled, paused, invalid and duplicate reminders/i)).toBeVisible();
});

test("Phase 10 routes deny a user without task and notification management permissions", async ({page})=>{
  await authRoute(page,["PROJECT_READ"]);
  await page.goto("/app/tasks");
  await expect(page.getByRole("heading",{name:"Permission required"})).toBeVisible();
  await page.goto("/app/reminders");
  await expect(page.getByRole("heading",{name:"Permission required"})).toBeVisible();
});
