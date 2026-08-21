import { expect, test, type Page } from "@playwright/test";

const fullPermissions=["ONBOARDING_READ","ONBOARDING_REVIEW","ONBOARDING_APPROVE","PROJECT_ACTIVATE"];
const user={id:"u11",email:"reviewer@example.com",displayName:"Leena Rao",organizationId:"o11",organizationName:"Northstar Studio",organizationSlug:"northstar",permissions:fullPermissions};
const auth={accessToken:"mock.jwt",accessTokenExpiresAt:"2099-01-01T00:00:00Z",user,mfaRequired:false,mfaSetupRequired:false,challengeToken:null};
const step={id:"s11",stepKey:"BRAND",name:"Brand assets",description:null,stepType:"FILE_UPLOAD",displayOrder:0,required:true,blocking:true,clientVisible:true,requiresReview:true,dependencyMode:"NONE",dependencyKeys:[],conditionExpression:{op:"ALWAYS"},assignedRoleId:null,dueAt:null,reminderPolicyId:null,allowSkip:false,allowReopen:true,configuration:{},status:"COMPLETED",completedAt:"2026-08-21T08:00:00Z",version:3};
const onboarding={id:"ob11",projectId:"p11",templateId:"wt11",templateVersionId:"wv11",templateName:"Client launch",templateVersionNumber:3,status:"AWAITING_INTERNAL_REVIEW",ready:true,progressPercent:100,completedApplicableSteps:1,totalApplicableSteps:1,startedAt:"2026-08-20T08:00:00Z",completedAt:null,updatedAt:"2026-08-21T08:00:00Z",version:5,steps:[step]};
const checklist={onboardingId:"ob11",projectId:"p11",onboardingStatus:"AWAITING_INTERNAL_REVIEW",projectStatus:"ONBOARDING",ready:true,blockingTotal:1,blockingCompleted:1,requiredTotal:1,requiredCompleted:1,canApprove:true,onboardingVersion:5,projectVersion:4,requirements:[{stepId:"s11",stepKey:"BRAND",name:"Brand assets",stepType:"FILE_UPLOAD",displayOrder:0,required:true,blocking:true,clientVisible:true,status:"COMPLETED",satisfied:true,revisable:true}],history:[]};
function envelope(data:unknown){return{success:true,data,meta:{},requestId:"phase11-e2e"}}
async function authRoute(page:Page,permissions=fullPermissions){await page.route("**/api/v1/auth/refresh",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope({...auth,user:{...user,permissions}}))}))}

test("final review separates evidence, approval, revision and activation",async({page})=>{
  const errors:string[]=[];page.on("console",m=>{if(m.type()==="error")errors.push(m.text())});await authRoute(page);
  await page.route("**/api/v1/projects/p11/review",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(checklist))}));
  await page.goto("/app/projects/p11/review");
  await expect(page.getByRole("heading",{name:"Readiness is evidence, not a button"})).toBeVisible();
  await expect(page.getByText("Brand assets")).toBeVisible();
  await expect(page.getByText("All blockers complete")).toBeVisible();
  await expect(page.getByRole("button",{name:"Approve onboarding"})).toBeVisible();
  await expect(page.getByRole("button",{name:"Request revision"})).toBeVisible();
  await expect(page.getByRole("button",{name:"Activate project"})).toHaveCount(0);
  expect(errors).toEqual([]);
});

test("PROJECT_ACTIVATE-only user can inspect READY evidence and perform the separate activation",async({page})=>{
  await authRoute(page,["ONBOARDING_READ","PROJECT_ACTIVATE"]);
  const ready={...checklist,onboardingStatus:"COMPLETED",projectStatus:"READY",canApprove:false,onboardingVersion:7,projectVersion:8,history:[{id:"rv1",action:"APPROVED",reviewerUserId:"u11",reason:null,revisionStepIds:[],onboardingVersion:7,createdAt:"2026-08-21T09:00:00Z"}]};
  await page.route("**/api/v1/projects/p11/review",route=>route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(ready))}));
  await page.route("**/api/v1/projects/p11/activate",async route=>{expect(route.request().postDataJSON()).toEqual({version:8});await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope({id:"p11",status:"ACTIVE",version:9}))})});
  page.on("dialog",dialog=>void dialog.accept());
  await page.goto("/app/projects/p11/review");
  await expect(page.getByText("Ready for activation")).toBeVisible();
  await page.getByRole("button",{name:"Activate project"}).click();
  await expect(page.getByText("Project is active")).toBeVisible();
  await expect(page.getByText("Project activated.")).toBeVisible();
});

test("final review screen is permission-gated",async({page})=>{
  await authRoute(page,["PROJECT_READ"]);
  await page.goto("/app/projects/p11/review");
  await expect(page.getByRole("heading",{name:"Permission required"})).toBeVisible();
});
