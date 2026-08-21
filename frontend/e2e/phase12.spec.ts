import { expect,test,type Page } from "@playwright/test";

const user={id:"r12",email:"reporter@example.com",displayName:"Arun Shah",organizationId:"o12",organizationName:"Northstar Studio",organizationSlug:"northstar",permissions:["REPORT_READ"]};
const auth={accessToken:"mock.jwt",accessTokenExpiresAt:"2099-01-01T00:00:00Z",user,mfaRequired:false,mfaSetupRequired:false,challengeToken:null};
const snapshot={generatedAt:"2026-08-21T10:00:00Z",operational:{totalOnboardings:20,activeOnboardings:8,awaitingClientAction:3,awaitingInternalReview:2,overdueSteps:1,pendingPayments:2,pendingContracts:1,missingAssets:1,missingAccess:2,readyForApproval:1,completedOnboardings:12,completionRatePercent:60,averageOnboardingHours:72},financial:{invoicesSent:10,invoicesPaid:8,invoicesOverdue:1,invoicesPartiallyPaid:1,averagePaymentHours:20,currencies:[{currency:"USD",invoicesSent:7,invoicesPaid:6,invoicedMinor:700000,collectedMinor:600000,refundedMinor:25000},{currency:"INR",invoicesSent:3,invoicesPaid:2,invoicedMinor:300000,collectedMinor:200000,refundedMinor:0}]},contracts:{contractsSent:9,contractsSigned:7,contractsDeclined:1,contractsExpired:1,completionRatePercent:77.78,averageSignatureHours:16},durations:{averageOnboardingHours:72,medianOnboardingHours:64,averageClientActionWaitingHours:18,averageInternalReviewWaitingHours:8,averageActivationHours:3},funnel:[{stepType:"FORM",total:20,completed:17,needsRevision:2,failed:1,completionRatePercent:85},{stepType:"FILE_UPLOAD",total:18,completed:15,needsRevision:2,failed:1,completionRatePercent:83.33}]};
const onboardings=[{onboardingId:"ob12",projectId:"p12",projectName:"Growth launch",clientId:"c12",clientName:"Acme Labs",onboardingStatus:"IN_PROGRESS",projectStatus:"ONBOARDING",progressPercent:68,overdueSteps:1,startedAt:"2026-08-20T08:00:00Z",completedAt:null}];
function envelope(data:unknown){return{success:true,data,meta:{},requestId:"phase12-e2e"}}
async function mock(page:Page){
 await page.route("**/api/v1/auth/refresh",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(auth))}));
 await page.route("**/api/v1/reports/snapshot",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(snapshot))}));
 await page.route("**/api/v1/reports/onboardings?size=12",r=>r.fulfill({status:200,contentType:"application/json",body:JSON.stringify(envelope(onboardings))}));
}

test("reports surface tenant-scoped operational analytics",async({page})=>{
 const errors:string[]=[];page.on("console",m=>{if(m.type()==="error")errors.push(m.text())});await mock(page);await page.goto("/app/reports");
 await expect(page.getByRole("heading",{name:/See where onboarding is moving/i})).toBeVisible();
 await expect(page.getByText("Onboarding funnel")).toBeVisible();
 await expect(page.getByText("Growth launch")).toBeVisible();
 await expect(page.locator("body")).not.toContainText(/undefined|NaN/);expect(errors).toEqual([]);
});

test("reports are responsive without viewport overflow",async({page})=>{
 await mock(page);await page.setViewportSize({width:390,height:844});await page.goto("/app/reports");
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth+2);expect(overflow).toBeFalsy();
});
