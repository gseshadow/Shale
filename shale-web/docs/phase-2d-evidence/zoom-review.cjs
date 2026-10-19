// Native Chromium default page zoom via chrome://settings in a disposable profile.
// This uses browser zoom, not CDP scale, CSS zoom, viewport or font-size emulation.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('node:fs'), assert=require('node:assert/strict');
const out='shale-web/docs/phase-2d-evidence';
const origin=process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
(async()=>{
 const context=await chromium.launchPersistentContext(process.env.ZOOM_PROFILE || '/tmp/shale-phase2c-zoom-profile',{executablePath:process.env.BROWSER_EXECUTABLE_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox'],viewport:{width:1280,height:900}});
 await context.addInitScript(()=>sessionStorage.setItem('shale-web.accessToken','synthetic-fixture-token'));
 const long='Synthetic extended work name — '+'UnbrokenSyntheticName'.repeat(7);
 const errors=[],records=[];
 await context.route('**/api/**',route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/auth/me') return route.fulfill({contentType:'application/json',body:JSON.stringify({authenticated:true,userId:1,shaleClientId:1,displayName:long,email:'synthetic@example.invalid'})});
  if(path==='/api/cases/assigned') return route.fulfill({contentType:'application/json',body:JSON.stringify([{caseId:1,caseName:long,caseNumber:null,caseStatus:'Open',responsibleAttorney:null,solDate:null}])});
  if(path==='/api/tasks/assigned') return route.fulfill({contentType:'application/json',body:JSON.stringify([{id:12,caseId:1,title:long,caseName:null,priorityId:null,dueAt:null,completedAt:null}])});
  if(path==='/api/tasks/12/complete') return route.fulfill({status:409,contentType:'application/json',body:'{}'});
  throw new Error('Unexpected synthetic zoom request '+path);
 });
 const settings=await context.newPage(); await settings.goto('chrome://settings/appearance');
 const options=await settings.locator('#zoomLevel').evaluate(e=>[...e.options].map(o=>({value:o.value,text:o.text})));
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
 await settings.locator('#zoomLevel').selectOption({label:'100%'});
 await page.goto(origin+'/my-shale');await page.getByRole('button',{name:'Complete',exact:true}).waitFor();
 const baseline=await page.evaluate(()=>({dpr:devicePixelRatio,width:innerWidth,height:innerHeight}));
 for(const zoom of [200,400]) {
  await settings.locator('#zoomLevel').selectOption({label:zoom+'%'});
  for(const theme of ['light','dark']) {
   await page.goto(origin+'/my-shale');await page.getByRole('button',{name:'Complete',exact:true}).waitFor();
   await page.getByLabel('Theme (this session)').selectOption(theme);
   const geometry=await page.evaluate(()=>({dpr:devicePixelRatio,width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth}));
   assert.equal(geometry.dpr,baseline.dpr*zoom/100,'Native zoom must change devicePixelRatio');
   assert.equal(geometry.width,Math.round(baseline.width*100/zoom),'Native zoom must change CSS viewport');
   assert.equal(geometry.scrollWidth,geometry.width);
   const open=page.getByRole('button',{name:'Open task '+long,exact:true});await open.focus();
   assert.equal(await open.evaluate(e=>getComputedStyle(e).outlineWidth),'3px');
   const cdp=await context.newCDPSession(page);
   const metrics=await cdp.send('Page.getLayoutMetrics');
   const capture=await cdp.send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width:metrics.contentSize.width,height:metrics.contentSize.height,scale:100/zoom}});
   fs.writeFileSync(`${out}/zoom-${zoom}-${theme}.png`,Buffer.from(capture.data,'base64'));
   await cdp.detach();
   await page.keyboard.press('Tab');assert(await page.getByRole('button',{name:'Complete',exact:true}).evaluate(e=>e===document.activeElement));
   await page.keyboard.press('Enter');await page.getByRole('alert').waitFor();
   assert(page.url().endsWith('/my-shale'));assert(await page.getByRole('button',{name:'Complete',exact:true}).isEnabled());
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),geometry.width);
   records.push({zoom,theme,...geometry,nativeViewportZoom:metrics.cssVisualViewport.zoom,visibleFocus:true,keyboardCompleteFailureRetainsCards:true});
  }
 }
 await settings.locator('#zoomLevel').selectOption({label:'100%'});
 fs.writeFileSync(`${out}/zoom-observations.json`,JSON.stringify({browser:await context.browser().version(),mechanism:'Native Chromium Settings > Appearance > Page zoom in disposable persistent profile. Physical viewport remains 1280×900.',baseline,options,records,errors,screenshotExport:'CDP contentSize in native device-independent coordinates with scale 100/zoom exports the full zoomed layout at CSS resolution. Export scaling does not alter browser zoom.',limits:'Headless Chromium native page zoom only; physical devices and screen readers not exercised.'},null,2));
 assert.equal(errors.length,0);await context.close();console.log(JSON.stringify({baseline,records,errors}));
})().catch(e=>{console.error(e);process.exit(1);});
