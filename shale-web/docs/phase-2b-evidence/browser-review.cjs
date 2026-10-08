const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs = require('fs');
const assert = require('node:assert/strict');
const out = 'shale-web/docs/phase-2b-evidence'; fs.mkdirSync(out, {recursive:true});
const long = 'Synthetic North Meadow Community Association — extended demonstration name for wrapping and accessible controls';
const user = {authenticated:true,userId:1,shaleClientId:1,displayName:long,email:'long.synthetic-demonstration-address@example.invalid',isAdmin:false,isAttorney:false};
const caseRow = {caseId:1,caseName:long,caseNumber:'EXAMPLE-0001',caseStatus:'Open — synthetic review',responsibleAttorney:long,practiceArea:'Community',client:long,intakeDate:null};
const task = {id:12,caseId:1,caseName:long,title:'Synthetic task — '+long,priorityId:null,dueAt:null,completedAt:null};
const detail = {...caseRow,description:'Synthetic description. '+ 'LongUnbrokenDemonstrationValue'.repeat(6),summary:'Synthetic summary only',rowVer:'synthetic',responsibleAttorneyId:1,practiceAreaId:1,relatedContacts:[],statusHistory:[],mappedCaseDates:[]};
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.BROWSER_EXECUTABLE_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const context=await browser.newContext({viewport:{width:1280,height:900}});
 await context.addInitScript(()=>sessionStorage.setItem('shale-web.accessToken','synthetic-fixture-token'));
 const calls=[]; const errors=[]; const unexpected=[];
 await context.route('**/api/**', async route=>{
  const path=new URL(route.request().url()).pathname; calls.push({path,method:route.request().method()});
  let body;
  if(path==='/api/auth/me') body=user;
  else if(path==='/api/auth/logout') body={};
  else if(path==='/api/cases/assigned') body=[caseRow];
  else if(path==='/api/tasks/assigned'||path==='/api/cases/1/tasks') body=[task];
  else if(path==='/api/cases/1') body=detail;
  else if(path==='/api/tasks/12') body={...task,description:'Synthetic task detail',shaleClientId:1};
  else if(path==='/api/users') body=[];
  else if(path==='/api/cases/1/updates'||path.startsWith('/api/lookups/')||path.startsWith('/api/settings/')) body=[];
  else {unexpected.push(path); await route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({message:'Synthetic unavailable detail'})}); return;}
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
 });
 const page=await context.newPage(); page.on('pageerror',e=>errors.push(e.message));
 const observations=[];
 for(const width of [320,360,768,1280]) for(const theme of ['light','dark']) {
  await page.setViewportSize({width,height:900}); await page.goto('http://127.0.0.1:5173/my-shale');
  await page.getByRole('heading',{name:'My Shale',exact:true}).waitFor();
  await page.getByRole('button',{name:/Open case/}).waitFor();
  await page.getByLabel('Theme (this session)').selectOption(theme);
  if(width<768) await page.locator('summary').click();
  const state=await page.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,mainCount:document.querySelectorAll('main').length,controls:[...document.querySelectorAll('.shale-shell-header a,.shale-shell-header button,.shale-shell-header select,.shale-shell-navigation a,summary')].filter(e=>e.getBoundingClientRect().height>0).map(e=>({name:e.textContent.trim(),width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height}))}));
  assert.equal(state.width,state.scrollWidth); assert.equal(state.mainCount,1);
  assert(state.controls.every(c=>c.width>=44&&c.height>=44));
  await page.screenshot({path:`${out}/${width}-${theme}.png`,fullPage:true});
  if(width<768) {await page.getByRole('link',{name:'Cases',exact:true}).focus(); await page.keyboard.press('Escape'); assert(await page.locator('summary').evaluate(e=>e===document.activeElement)); assert(!await page.locator('details').evaluate(e=>e.open));}
  await page.getByRole('button',{name:/Open case/}).click();
  await page.getByRole('heading',{name:long,exact:true,level:1}).waitFor();
  assert(await page.locator('main').evaluate(e=>e===document.activeElement));
  const detailWidth=await page.evaluate(()=>document.documentElement.scrollWidth); assert.equal(detailWidth,width);
  await page.screenshot({path:`${out}/detail-${width}-${theme}.png`,fullPage:true});
  await page.getByRole('button',{name:'Edit details',exact:true}).click();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width);
  await page.getByRole('button',{name:'Cancel',exact:true}).first().click();
  await page.goBack(); await page.getByRole('heading',{name:'My Shale',exact:true}).waitFor();
  await page.goForward(); await page.getByRole('heading',{name:long,exact:true,level:1}).waitFor();
  observations.push({width,theme,...state,detailWidth,editFormReflow:true,history:true,routeFocus:true});
 }
 await page.setViewportSize({width:360,height:900}); await page.goto('http://127.0.0.1:5173/cases/1?review=1#detail');
 await page.getByRole('heading',{name:long,exact:true,level:1}).waitFor(); assert(page.url().endsWith('/cases/1?review=1#detail'));
 await page.getByRole('link',{name:'Skip to content'}).focus(); await page.keyboard.press('Enter'); assert(await page.locator('main').evaluate(e=>e===document.activeElement));
 await page.locator('summary').focus(); await page.keyboard.press('Enter'); assert(await page.locator('details').evaluate(e=>e.open));
 await page.keyboard.press('Tab'); assert.equal(await page.evaluate(()=>document.activeElement.getAttribute('aria-label')),'My Shale');
 await page.keyboard.press('Escape'); await page.locator('summary').focus(); await page.keyboard.press('Space'); assert(await page.locator('details').evaluate(e=>e.open));
 // Chromium accessibility tree availability, not a screen-reader acceptance claim.
 const cdp=await context.newCDPSession(page); const ax=await cdp.send('Accessibility.getFullAXTree');
 const landmarks=ax.nodes.filter(n=>['main','navigation','banner'].includes(n.role?.value)).map(n=>({role:n.role.value,name:n.name?.value}));
 await page.getByRole('button',{name:'Logout'}).click(); await page.getByRole('heading',{name:'Sign in',exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>sessionStorage.getItem('shale-web.accessToken')),null); assert.equal(await page.locator('nav').count(),0);
 // Review all retained list/detail destinations with synthetic empty/error facilities.
 for(const path of ['/tasks','/tasks/12','/cases','/contacts','/contacts/1','/organizations','/organizations/1','/team','/team/1','/settings']){
  await page.goto('http://127.0.0.1:5173'+path); await page.locator('.shale-authenticated h1').waitFor();
  assert.equal(await page.locator('main').count(),1); assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),360);
 }
 // Isolated preview remains request-free even with the synthetic stored operational bearer.
 const before=calls.length; const preview=[];
 for(const width of [320,360,768,1280]) for(const theme of ['light','dark']) {
  await page.setViewportSize({width,height:900});
  await page.goto('http://127.0.0.1:5173/foundation.html'); await page.getByLabel('Preview theme').selectOption(theme);
  assert.equal(calls.length,before); assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width);
  preview.push({width,theme,overflow:false,apiRequests:0});
 }
 await page.setViewportSize({width:360,height:900});
 const zoom={};
 await page.goto('http://127.0.0.1:5173/my-shale'); await page.locator('.shale-authenticated h1').waitFor();
 zoom.before=await page.evaluate(()=>({dpr:devicePixelRatio,width:innerWidth}));
 await page.keyboard.press('Control+Equal'); await page.keyboard.press('Control+Equal');
 zoom.afterShortcut=await page.evaluate(()=>({dpr:devicePixelRatio,width:innerWidth}));
 try {
  const settings=await context.newPage(); await settings.goto('chrome://settings/appearance');
  zoom.settingsTitle=await settings.title(); zoom.settingsSelects=await settings.locator('select').count();
  zoom.settingsZoom=await settings.locator('#zoomLevel').count(); await settings.close();
 } catch(e){zoom.settingsError=e.message;}
 zoom.acceptance=zoom.afterShortcut.dpr!==zoom.before.dpr?'zoom changed; further acceptance needed':'Unavailable: shortcuts did not change native browser zoom in headless Chromium';
 fs.writeFileSync(`${out}/browser-observations.json`,JSON.stringify({browser:await browser.version(),fixture:'Intercepted synthetic API only; no live backend/authentication acceptance',observations,landmarks,logout:true,skipLink:true,nativeCompactKeyboard:true,directLink:true,previewApiRequests:0,preview,errors,expectedSyntheticUnavailableDetails:[...new Set(unexpected)],zoom},null,2));
 assert.equal(errors.length,0);
 await browser.close(); console.log(JSON.stringify({captures:16,widths:4,themes:2,landmarks,errors,zoom}));
})().catch(e=>{console.error(e);process.exit(1)});
