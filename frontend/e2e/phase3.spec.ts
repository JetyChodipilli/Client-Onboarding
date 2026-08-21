import { expect, test, type Page } from "@playwright/test";

const permissions = ["PROJECT_READ","PROJECT_UPDATE","WORKFLOW_READ","WORKFLOW_MANAGE","ONBOARDING_START","ONBOARDING_READ"];
const user = { id:"u1",email:"admin@example.com",displayName:"Asha Rao",organizationId:"o1",organizationName:"Northstar Studio",organizationSlug:"northstar",permissions };
const auth = { accessToken:"mock.jwt",accessTokenExpiresAt:"2099-01-01T00:00:00Z",user,mfaRequired:false,mfaSetupRequired:false,challengeToken:null };
const summary = { id:"w1",name:"Meta Ads Onboarding",description:"Client launch requirements",status:"ACTIVE",latestPublishedVersion:1,latestPublishedVersionId:"v1",draftVersion:2,updatedAt:"2026-08-21T04:00:00Z",version:0 };
const template = { id:"w1",name:summary.name,description:summary.description,status:"ACTIVE",archivedAt:null,createdAt:"2026-08-20T04:00:00Z",updatedAt:summary.updatedAt,version:0,versions:[{id:"v2",versionNumber:2,status:"DRAFT",changeNote:"Revision",publishedAt:null,updatedAt:summary.updatedAt,stepCount:2,version:1},{id:"v1",versionNumber:1,status:"PUBLISHED",changeNote:"Initial",publishedAt:"2026-08-20T05:00:00Z",updatedAt:"2026-08-20T05:00:00Z",stepCount:2,version:1}] };
const steps = [{id:"s1",stepKey:"WELCOME",name:"Welcome & expectations",description:"Review the onboarding path.",stepType:"INSTRUCTION",displayOrder:0,required:true,blocking:false,clientVisible:true,requiresReview:false,dependencyMode:"NONE",conditionExpression:{op:"ALWAYS"},assignedRoleId:null,dueAfterHours:null,reminderPolicyId:null,allowSkip:false,allowReopen:false,configuration:{},dependencyKeys:[]},{id:"s2",stepKey:"ACCESS",name:"Grant Meta access",description:"Provide partner access.",stepType:"PLATFORM_ACCESS",displayOrder:1,required:true,blocking:true,clientVisible:true,requiresReview:true,dependencyMode:"ALL",conditionExpression:{op:"EQ",field:"service.code",value:"META_ADS"},assignedRoleId:null,dueAfterHours:48,reminderPolicyId:null,allowSkip:false,allowReopen:true,configuration:{},dependencyKeys:["WELCOME"]}];
const draft = { ...template.versions[0], templateId:"w1", steps };
const onboarding = { id:"ob1",projectId:"p1",templateId:"w1",templateVersionId:"v1",templateName:summary.name,templateVersionNumber:1,status:"DRAFT",ready:false,progressPercent:0,completedApplicableSteps:0,totalApplicableSteps:2,startedAt:"2026-08-21T04:10:00Z",completedAt:null,updatedAt:"2026-08-21T04:10:00Z",version:0,steps:[{...steps[0],status:"AVAILABLE",dueAt:null,completedAt:null,version:0},{...steps[1],status:"LOCKED",dueAt:"2026-08-23T04:10:00Z",completedAt:null,version:0}] };
function envelope(data:unknown){return{success:true,data,meta:{},requestId:"phase3-e2e"}}
function failure(code:string,message:string){return{success:false,error:{code,message,fieldErrors:[]},requestId:"phase3-e2e"}}
async function authRoute(page:Page,granted=permissions){await page.route("**/api/v1/auth/refresh",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope({...auth,user:{...user,permissions:granted}}))}))}

test("workflow builder is responsive, version-aware, and console-clean", async ({page})=>{
  const errors:string[]=[];page.on("console",msg=>{if(msg.type()==="error")errors.push(msg.text())});await authRoute(page);
  await page.route("**/api/v1/workflow-templates/w1",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(template))}));
  await page.route("**/api/v1/workflow-templates/versions/v2",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(draft))}));
  await page.goto("/app/workflows/w1");
  await expect(page.getByRole("heading",{name:"Meta Ads Onboarding"})).toBeVisible();
  await expect(page.getByText("Version 2").first()).toBeVisible();
  await expect(page.getByDisplayValue("Welcome & expectations")).toBeVisible();
  await expect(page.getByText("Changes affect only this draft until it is published.")).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth)).toBe(true);expect(errors).toEqual([]);
});

test("workflow list covers populated and API error states",async({page})=>{await authRoute(page);let error=false;await page.route("**/api/v1/workflow-templates?**",route=>error?route.fulfill({status:503,contentType:"application/json",body:JSON.stringify(failure("SERVICE_UNAVAILABLE","Workflows are temporarily unavailable."))}):route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([summary]))}));await page.goto("/app/workflows");await expect(page.getByText("Meta Ads Onboarding")).toBeVisible();error=true;await page.getByRole("button",{name:"Refresh workflows"}).click();await expect(page.getByRole("alert")).toContainText("temporarily unavailable")});

test("project onboarding snapshot explains locks and phase boundary",async({page})=>{await authRoute(page);await page.route("**/api/v1/projects/p1/onboarding",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(onboarding))}));await page.goto("/app/projects/p1/onboarding");await expect(page.getByRole("heading",{name:"Meta Ads Onboarding"})).toBeVisible();await expect(page.getByText("Grant Meta access")).toBeVisible();await expect(page.getByText("LOCKED")).toBeVisible();await expect(page.getByText(/Immutable workflow snapshot/)).toBeVisible();});

test("direct workflow routes show permission denial",async({page})=>{await authRoute(page,["PROJECT_READ"]);await page.goto("/app/workflows");await expect(page.getByRole("heading",{name:"Permission required"})).toBeVisible();});
