import { expect,test,type Page } from "@playwright/test";

const user={id:"fin-legal",email:"finance@example.com",displayName:"Finance Legal",organizationId:"org",organizationName:"Northstar",organizationSlug:"northstar",permissions:["INVOICE_CREATE","INVOICE_SEND","PAYMENT_OVERRIDE","PROJECT_READ","CONTRACT_CREATE","CONTRACT_SEND","ASSET_READ","ASSET_REVIEW"]};
const auth={accessToken:"mock.jwt",accessTokenExpiresAt:"2099-01-01T00:00:00Z",user,mfaRequired:false,mfaSetupRequired:false,challengeToken:null};
const envelope=(data:unknown)=>({success:true,data,meta:{},requestId:"phase78-regression"});
async function mockAuth(page:Page){await page.route("**/api/v1/auth/refresh",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(auth))}))}

test("billing, contracts and assets navigation resolves to real screens",async({page})=>{
 await mockAuth(page);
 await page.route("**/api/v1/invoices?size=100",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([]))}));
 await page.route("**/api/v1/projects?size=100",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([]))}));
 await page.route("**/api/v1/contracts?size=100",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([]))}));
 await page.route("**/api/v1/contract-templates?size=100",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([]))}));
 await page.route("**/api/v1/contract-templates/published-versions",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([]))}));
 await page.route("**/api/v1/assets?size=100",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope([]))}));
 await page.goto("/app/invoices");await expect(page.getByRole("heading",{name:/Invoices, collections/i})).toBeVisible();
 await page.goto("/app/contracts");await expect(page.getByRole("heading",{name:/Freeze legal content/i})).toBeVisible();
 await page.goto("/app/assets");await expect(page.getByRole("heading",{name:/Review files only after security checks/i})).toBeVisible();
});

test("client PAYMENT and CONTRACT actions link to implemented portal routes",async({page})=>{
 const clientUser={...user,id:"client",permissions:["CLIENT_PORTAL"]};
 await page.route("**/api/v1/client-auth/refresh",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope({...auth,user:clientUser}))}));
 const detail={projectId:"p1",projectName:"Launch",clientName:"Acme",serviceName:"Growth",projectStatus:"ONBOARDING",onboardingStatus:"IN_PROGRESS",progressPercent:30,completedRequirements:1,totalRequirements:3,waitingFor:"YOU",blockingReason:"Payment is required.",helpText:"Complete the next requirement.",nextAction:{stepId:"pay-step",title:"Deposit",description:"Pay the deposit",actionType:"PAYMENT"},yourAction:[{id:"contract-step",name:"Agreement",description:null,stepType:"CONTRACT",status:"AVAILABLE",required:true,dueAt:null,lockedReason:null}],waitingForOurTeam:[],locked:[],completed:[]};
 await page.route("**/api/v1/client-portal/projects/p1",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(detail))}));
 await page.goto("/portal/projects/p1");
 await expect(page.getByRole("link",{name:"Review invoice"})).toHaveAttribute("href","/portal/projects/p1/invoices");
 await expect(page.getByRole("link",{name:"Open contracts"})).toHaveAttribute("href","/portal/projects/p1/contracts");
});
