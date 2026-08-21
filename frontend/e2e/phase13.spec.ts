import { expect,test } from "@playwright/test";

test("frontend emits defense-in-depth browser headers",async({page})=>{
 const response=await page.goto("/login");expect(response).not.toBeNull();const headers=response!.headers();
 expect(headers["x-content-type-options"]).toBe("nosniff");
 expect(headers["x-frame-options"]).toBe("DENY");
 expect(headers["referrer-policy"]).toBe("strict-origin-when-cross-origin");
 expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
 expect(headers["strict-transport-security"]).toContain("31536000");
});

test("protected workspace does not render before authentication",async({page})=>{
 await page.route("**/api/v1/auth/refresh",route=>route.fulfill({status:401,contentType:"application/json",body:JSON.stringify({success:false,error:{code:"UNAUTHORIZED",message:"Authentication required",fieldErrors:[]},requestId:"security-e2e"})}));
 await page.goto("/app/reports");await expect(page).toHaveURL(/\/login/);
});

test("login remains keyboard reachable on mobile",async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto("/login");
 await page.keyboard.press("Tab");await page.keyboard.press("Tab");
 const active=await page.evaluate(()=>document.activeElement?.tagName);expect(active).toMatch(/INPUT|BUTTON|A/);
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth+2);expect(overflow).toBeFalsy();
});
