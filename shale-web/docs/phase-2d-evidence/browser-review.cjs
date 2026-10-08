// Advisory evidence only. No fixture or browser package is imported by application code.
const engines = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const AxeBuilder = require(process.env.AXE_MODULE || '@axe-core/playwright').default;
const fs = require('node:fs');
const assert = require('node:assert/strict');
const engine = process.env.REVIEW_ENGINE || 'chromium';
const out = `shale-web/docs/phase-2d-evidence/${engine}`; fs.mkdirSync(out, {recursive:true});
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const long = 'Synthetic North Meadow Community Association — extended demonstration name for wrapping';
const unbroken = 'LongUnbrokenSyntheticDemonstrationValue'.repeat(4);
const user = {authenticated:true,userId:1,shaleClientId:1,displayName:long,email:'synthetic@example.invalid',isAdmin:false,isAttorney:false};
const cases = [
 {caseId:1,caseName:long,caseNumber:unbroken,caseStatus:'Open — extended synthetic review status',responsibleAttorney:unbroken,solDate:'2026-12-01',intakeDate:null},
 {caseId:2,caseName:null,caseNumber:null,caseStatus:null,responsibleAttorney:null,solDate:null,intakeDate:null}
];
const tasks = [
 {id:12,caseId:1,caseName:long,title:'Synthetic task — '+unbroken,priorityId:2,dueAt:'2026-10-10',completedAt:null},
 {id:13,caseId:1,caseName:null,title:null,priorityId:null,dueAt:null,completedAt:null},
 {id:14,caseId:1,caseName:long,title:'Previously completed synthetic task',priorityId:null,dueAt:null,completedAt:'2026-10-08T10:00:00'}
];
(async()=>{
 const browser=await engines[engine].launch(engine === 'chromium' ? {executablePath:process.env.BROWSER_EXECUTABLE_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox']} : {headless:true});
 const context=await browser.newContext({viewport:{width:1280,height:900}});
 await context.addInitScript(()=>sessionStorage.setItem('shale-web.accessToken','synthetic-fixture-token'));
 let mode='normal', mutation='fail', releaseReads, releaseCompletion;
 let readGate, completionGate;
 const calls=[], errors=[], observations=[], scans=[];
 await context.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname, method=route.request().method(); calls.push({path,method});
  let body;
  if(path==='/api/auth/me') body=user;
  else if(path==='/api/auth/logout') body={};
  else if(path==='/api/cases/assigned'||path==='/api/tasks/assigned') {
   const readMode=mode;
   if(readMode==='loading') await readGate;
   if(readMode==='errors') return route.fulfill({status:503,contentType:'application/json',body:'{}'});
   body=readMode==='empty'||readMode==='loading'?[]:path.includes('/cases/')?cases:tasks;
  } else if(path==='/api/tasks/12/complete'&&method==='PATCH') {
   await completionGate;
   if(mutation==='fail') return route.fulfill({status:409,contentType:'application/json',body:'{}'});
   body={...tasks[0],completedAt:'2026-10-08T11:00:00'};
  } else if(path==='/api/tasks/12') body={...tasks[0],description:'Synthetic task description',shaleClientId:1};
  else if(path==='/api/cases/1') body={...cases[0],relatedContacts:[],mappedCaseDates:[],statusHistory:[]};
  else if(path==='/api/cases/1/tasks') body=[];
  else if(path==='/api/cases/1/updates') body=[];
  else if(path==='/api/cases/search'||path==='/api/contacts/search') body=[];
  else throw new Error('Unexpected request '+method+' '+path);
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
 });
 const page=await context.newPage(); page.on('pageerror',e=>errors.push(e.message));
 async function scan(width,theme,state) {
  const results=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21a','wcag21aa']).analyze();
  scans.push({width,theme,state,violations:results.violations.map(v=>({id:v.id,impact:v.impact,description:v.description,nodes:v.nodes.map(n=>({target:n.target,summary:n.failureSummary}))})),incomplete:results.incomplete.map(v=>({id:v.id,nodes:v.nodes.map(n=>({target:n.target,summary:n.failureSummary}))}))});
  assert.equal(results.violations.length,0,JSON.stringify(scans.at(-1)));
 }
 async function capture(width,theme,state) {
  const geometry=await page.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,mainCount:document.querySelectorAll('main').length,
   controls:[...document.querySelectorAll('.shale-presentation button')].map(e=>({name:e.textContent,width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height})),
   clipped:[...document.querySelectorAll('.shale-presentation h1,.shale-presentation h2,.entity-card-activation,.metadata-row dd,.shale-presentation .status-pill')].filter(e=>e.scrollWidth>e.clientWidth+1).map(e=>e.textContent)}));
  assert.equal(geometry.scrollWidth,width,state+' overflow'); assert.equal(geometry.mainCount,1);
  assert(geometry.controls.every(c=>c.width>=44&&c.height>=44)); assert.deepEqual(geometry.clipped,[]);
  observations.push({width,theme,state,...geometry});
  await page.screenshot({path:`${out}/${state}-${width}-${theme}.png`,fullPage:true});
 }
 for(const width of [320,360,768,1280]) for(const theme of ['light','dark']) {
  await page.setViewportSize({width,height:900}); mode='normal';
  await page.goto(origin+'/my-shale'); await page.getByRole('button',{name:'Open task Task 13',exact:true}).waitFor();
  await page.getByLabel('Theme (this session)').selectOption(theme);
  const details=page.locator('.shale-shell-navigation details'), summary=page.locator('.shale-shell-navigation summary');
  const active=page.getByRole('link',{name:'My Shale',exact:true});
  if(width<768) {
   assert.equal(await details.evaluate(e=>e.open),false);
   await summary.focus(); await page.keyboard.press('Enter'); assert.equal(await details.evaluate(e=>e.open),true);
   await active.focus(); await page.keyboard.press('Escape'); assert.equal(await details.evaluate(e=>e.open),false);
   assert(await summary.evaluate(e=>e===document.activeElement));
   await page.keyboard.press('Space'); assert.equal(await details.evaluate(e=>e.open),true);
  } else assert.equal(await details.evaluate(e=>e.open),true);
  assert.equal(await active.getAttribute('aria-current'),'page');
  // Reach every visible native shell/card control with Tab and inspect visible outlines.
  await page.getByRole('link',{name:'Skip to content'}).focus();
  const keyboard=[];
  for(let i=0;i<30;i++) {
   const current=await page.evaluate(()=>{const e=document.activeElement,r=e.getBoundingClientRect(),c=getComputedStyle(e);return {name:e.getAttribute('aria-label')||e.textContent,tag:e.tagName,outline:c.outlineStyle,outlineWidth:c.outlineWidth,visible:r.width>0&&r.height>0};});
   if(current.tag==='BODY') break;
   assert(current.visible); assert.equal(current.outline,'solid'); assert(parseFloat(current.outlineWidth)>=3);
   keyboard.push(current); await page.keyboard.press('Tab');
  }
  const targets=await page.locator('.shale-shell').evaluate(e=>[...e.querySelectorAll('a,button,summary,select')].filter(e=>e.getBoundingClientRect().height>0&&!e.matches('.shale-skip')).map(e=>({name:e.textContent,width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height})));
  assert(targets.every(t=>t.width>=44&&t.height>=44));
  await page.getByRole('link',{name:'Skip to content'}).focus(); await page.keyboard.press('Enter');
  assert(await page.getByRole('main').evaluate(e=>e===document.activeElement));
  if(width<768&&!await details.evaluate(e=>e.open)) await summary.click();
  await page.getByRole('link',{name:'Cases',exact:true}).click(); await page.getByRole('heading',{name:'Cases',exact:true,level:1}).waitFor();
  assert(await page.getByRole('main').evaluate(e=>e===document.activeElement));
  if(width<768) assert.equal(await details.evaluate(e=>e.open),false);
  await page.goBack(); await page.getByRole('button',{name:'Open task Task 13',exact:true}).waitFor();
  await page.goForward(); await page.getByRole('heading',{name:'Cases',exact:true,level:1}).waitFor();
  await page.goBack(); await page.getByRole('button',{name:'Open task Task 13',exact:true}).waitFor();
  if(width<768) await summary.click();
  assert.equal(await active.getAttribute('aria-current'),'page');
  if(width<768) await summary.click();
  observations.push({width,theme,state:'shell',keyboard,targets,history:true,skip:true,routeFocus:true,activeDestination:true});
  const open=page.getByRole('button',{name:/^Open task Synthetic task/}), card=open.locator('xpath=ancestor::article');
  await page.keyboard.press('Tab'); await open.focus();
  assert.equal(await open.evaluate(e=>getComputedStyle(e).outlineStyle),'solid');
  assert.equal(await open.evaluate(e=>getComputedStyle(e).outlineWidth),'3px');
  await capture(width,theme,'normal'); await scan(width,theme,'normal');
  await page.keyboard.press('Tab'); assert(await card.getByRole('button',{name:'Complete',exact:true}).evaluate(e=>e===document.activeElement));
  assert.equal(await card.getByRole('button',{name:'Complete',exact:true}).evaluate(e=>getComputedStyle(e).outlineWidth),'3px');
  const live=page.getByRole('region',{name:'My Tasks',exact:true}).getByRole('status');
  assert.equal(await live.textContent(),''); const liveHandle=await live.elementHandle();
  mutation='fail'; completionGate=new Promise(done=>{releaseCompletion=done;});
  const before=calls.filter(c=>c.method==='PATCH').length;
  await page.keyboard.press('Space'); await card.getByRole('button',{name:'Completing…',exact:true}).waitFor();
  assert(await card.getByRole('button',{name:'Completing…'}).isDisabled()); assert(new URL(page.url()).pathname==='/my-shale');
  assert.equal(await live.textContent(),'Completing '+tasks[0].title+'…');
  assert.equal(await live.getAttribute('aria-live'),'polite'); assert.equal(await live.getAttribute('aria-atomic'),'true');
  await capture(width,theme,'pending'); await scan(width,theme,'pending'); releaseCompletion();
  await page.getByRole('alert').waitFor(); await card.getByRole('button',{name:'Complete',exact:true}).waitFor();
  assert.equal(calls.filter(c=>c.method==='PATCH').length,before+1);
  assert.equal(await page.getByRole('list',{name:'Assigned tasks'}).count(),1); assert(new URL(page.url()).pathname==='/my-shale');
  assert.equal(await live.textContent(),''); assert(await liveHandle.evaluate(e=>e.isConnected));
  await capture(width,theme,'failure'); await scan(width,theme,'failure');
  // Background pointer activation and native keyboard activation both preserve existing routes.
  await card.locator('dd').first().click(); await page.getByRole('heading',{name:tasks[0].title,exact:true,level:1}).waitFor();
  assert(page.url().endsWith('/tasks/12')); await page.goBack(); await open.waitFor();
  await open.focus(); await page.keyboard.press('Enter'); await page.getByRole('heading',{name:tasks[0].title,exact:true,level:1}).waitFor();
  await page.goBack(); await open.waitFor(); await open.focus(); await page.keyboard.press('Space');
  await page.getByRole('heading',{name:tasks[0].title,exact:true,level:1}).waitFor(); await page.goBack(); await open.waitFor();
  // Enter Complete, authoritative success, no competing card navigation or read reload.
  mutation='success'; completionGate=new Promise(done=>{releaseCompletion=done;});
  const readsBefore=calls.filter(c=>c.path==='/api/tasks/assigned').length;
  await card.getByRole('button',{name:'Complete',exact:true}).focus(); await page.keyboard.press('Enter');
  await card.getByRole('button',{name:'Completing…'}).waitFor(); releaseCompletion();
  await card.getByText('Completed',{exact:true}).waitFor();
  assert.equal(await live.textContent(),'Completed '+tasks[0].title+'.'); assert(new URL(page.url()).pathname==='/my-shale');
  assert.equal(calls.filter(c=>c.path==='/api/tasks/assigned').length,readsBefore);
  await capture(width,theme,'completed'); await scan(width,theme,'completed');
  await page.getByRole('button',{name:'Open case '+long,exact:true}).focus(); await page.keyboard.press('Enter');
  await page.getByRole('heading',{name:long,exact:true,level:1}).waitFor(); assert(page.url().endsWith('/cases/1'));
  assert.equal(await page.locator('.shale-presentation').count(),0); assert.equal(await page.locator('.legacy-route-content').count(),1);
  // Distinct load/empty/error facilities at every requested width/theme.
  for(const state of ['loading','empty','errors']) {
   mode=state; if(state==='loading') readGate=new Promise(done=>{releaseReads=done;});
   await page.goto(origin+'/my-shale'); await page.getByRole('heading',{name:'My Shale',exact:true}).waitFor();
   await page.getByLabel('Theme (this session)').selectOption(theme);
   if(state==='loading') await page.getByText('Loading your tasks…',{exact:true}).waitFor();
   else if(state==='empty') await page.getByText('No assigned tasks were found.',{exact:true}).waitFor();
   else {await page.getByText('Shale could not load your cases.',{exact:true}).waitFor();await page.getByText('Shale could not load your tasks.',{exact:true}).waitFor();}
   await capture(width,theme,state); await scan(width,theme,state);
   if(state==='loading') {releaseReads(); await page.getByText('No assigned tasks were found.',{exact:true}).waitFor();}
  }
 }
 mode='normal'; await page.goto(origin+'/my-shale'); await page.getByRole('button',{name:'Open task Task 13',exact:true}).waitFor();
 const accessibility=await page.locator('main').ariaSnapshot();
 // Confirm other-route shared task actions retain legacy presentation in both themes.
 const isolation=[];
 for(const theme of ['light','dark']) {
  await page.goto(origin+'/tasks'); await page.getByRole('button',{name:'Open task Task 13',exact:true}).waitFor(); await page.getByLabel('Theme (this session)').selectOption(theme);
  assert.equal(await page.locator('.shale-presentation').count(),0);
  const legacy=await page.locator('.secondary-button').first().evaluate(e=>({background:getComputedStyle(e).backgroundColor,color:getComputedStyle(e).color}));
  assert.equal(legacy.background,'rgb(255, 255, 255)'); isolation.push({theme,...legacy});
 }
 const preview=[]; const beforePreview=calls.length;
 for(const width of [320,360,768,1280]) for(const theme of ['light','dark']) {
  await page.setViewportSize({width,height:900}); await page.goto(origin+'/foundation.html'); await page.getByLabel('Preview theme').selectOption(theme);
  assert.equal(calls.length,beforePreview); assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width); preview.push({width,theme,apiRequests:0});
 }
 fs.writeFileSync(`${out}/browser-observations.json`,JSON.stringify({browser:await browser.version(),fixture:'Synthetic interception only; no live authentication/backend/device/screen-reader acceptance',observations,accessibility,isolation,preview,errors,engine,mutationCalls:calls.filter(c=>c.method==='PATCH').length},null,2));
 fs.writeFileSync(`${out}/accessibility-observations.json`,JSON.stringify(scans,null,2));
 assert.equal(errors.length,0);await browser.close(); console.log(JSON.stringify({captures:observations.length,axeScans:scans.length,errors,engine}));
})().catch(e=>{console.error(e);process.exit(1);});
