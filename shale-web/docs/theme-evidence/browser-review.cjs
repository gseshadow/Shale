const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {intercept,routes,editors}=require('./fixtures.cjs');
const origin=process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const out=process.env.EVIDENCE_DIR || 'shale-web/docs/theme-evidence/captures';
fs.mkdirSync(out,{recursive:true});
const records=[],errors=[];
async function open(page,url,theme) {
 await page.goto(origin+url); await page.locator('.shale-authenticated h1').waitFor();
 await page.getByLabel('Theme (this session)').selectOption(theme);
}
async function populate(page,url) {
 if(['/cases','/contacts','/organizations'].includes(url)) {
  await page.locator('main input[type="search"]').fill('Synthetic');
  await page.getByRole('button',{name:'Search',exact:true}).click();
 } else if(url==='/case-workspace') await page.getByRole('button',{name:'Assigned',exact:true}).click();
}
async function capture(page,name,theme,width,state) {
 await page.evaluate(()=>document.fonts.ready);
 const checks=await page.evaluate(()=>{
  const owner=document.querySelector('.shale-authenticated'),content=document.querySelector('.shale-route-content');
  const selectors=['.shale-shell-main','.shale-route-content','.tasks-page','.cases-page','.contacts-page','.organizations-page','.team-page','.settings-card','.detail-shell','.detail-header','.entity-card','.detail-sections > section','.case-edit-form','.status-choice-card','.status.error','input[type="text"]','select','option','textarea','dialog[open]'];
  const paint=selectors.flatMap(selector=>[...document.querySelectorAll(selector)].slice(0,3).map(e=>{const s=getComputedStyle(e);return {selector,bg:s.backgroundColor,image:s.backgroundImage,color:s.color,scheme:s.colorScheme,border:s.borderColor};}));
  return {width:innerWidth,scrollWidth:document.documentElement.scrollWidth,theme:owner.dataset.theme,scheme:getComputedStyle(content).colorScheme,route:content.dataset.route,paint};
 });
 assert.equal(checks.width,checks.scrollWidth, name+' page overflow');
 assert.equal(checks.scheme,theme, name+' native theme ownership');
 assert.equal(checks.theme,theme);
 const jpg=name+'-'+width+'-'+theme+'-'+state+'.jpg';
 await page.screenshot({path:path.join(out,jpg),fullPage:true,type:'jpeg',quality:72});
 records.push({name,width,theme,state,screenshot:jpg,...checks});
}
async function editor(page,row) {
 const [name,url,button,field]=row;
 await page.getByRole('button',{name:button,exact:true}).click(); await page.locator(field).first().waitFor();
 // Wait for asynchronous select options so the normal capture is hydrated.
 if(name==='case-assignment'||name==='task-editor'||name==='new-case') await page.waitForFunction(()=>!document.querySelector('.case-edit-form select:disabled'));
}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.BROWSER_EXECUTABLE_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 try {
  // Main routes and all existing editors in both themes at every requested width.
  for(const width of (process.env.REVIEW_PHASE ? [] : [320,360,768,1280])) for(const theme of ['light','dark']) {
   const context=await browser.newContext({viewport:{width,height:900}}),state={mode:'normal',calls:[],unexpected:[],writes:0}; await intercept(context,state);
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
   for(const [name,url] of routes) {
    await open(page,url,theme);await populate(page,url);
    if(url!=='/case-workspace') await page.waitForFunction(()=>!document.querySelector('main .loading-state'));
    await capture(page,name,theme,width,'populated');
   }
   for(const row of editors) {
    await open(page,row[1],theme);await editor(page,row);
    const field=page.locator(row[3]).first();await page.keyboard.press('Tab');await field.focus();
    assert.equal(await field.evaluate(e=>getComputedStyle(e).outlineStyle),'solid',row[0]+' keyboard focus');
    await capture(page,row[0],theme,width,'editor');
   }
   assert.deepEqual(state.unexpected,[]); assert.equal(state.writes,0);await context.close();
   console.log(JSON.stringify({width,theme,main:routes.length,editors:editors.length}));
  }
  if(records.length) fs.writeFileSync(path.join(out,'matrix-observations.json'),JSON.stringify({browser:await browser.version(),records},null,2));
  // Loading/empty/error states on every applicable route, safely fulfilled locally.
  for(const theme of ['light','dark']) for(const mode of ['loading','empty','error']) {
   for(const [name,url] of routes) {
    const context=await browser.newContext({viewport:{width:360,height:900}}),state={mode,calls:[],unexpected:[],writes:0};await intercept(context,state);
    const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
    await open(page,url,theme);await populate(page,url);
    if(mode!=='loading') await page.waitForFunction(()=>!document.querySelector('main .loading-state'));
    if(url==='/case-workspace') await (mode==='error' ? page.getByRole('alert') : page.getByText(mode==='loading'?'Loading Case page…':'No cases on this page.',{exact:true}).last()).waitFor();
    await capture(page,name,theme,360,mode);state.release?.();await context.close();
   }
   console.log(JSON.stringify({theme,mode,main:routes.length}));
  }
  // Pending and validation on real forms; writes intercepted, never forwarded.
  for(const theme of ['light','dark']) for(const row of editors) {
   const context=await browser.newContext({viewport:{width:360,height:900}}),state={mode:'normal',calls:[],unexpected:[],writes:0};await intercept(context,state);
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await open(page,row[1],theme);await editor(page,row);
   const form=page.locator(row[3]).first().locator('xpath=ancestor::form');
   if(['new-case','case-core','case-task','task-editor','new-contact','contact-editor','new-organization','organization-editor'].includes(row[0])) await page.locator(row[3]).first().fill('Synthetic changed name');
   if(row[0]==='new-case') {await page.locator('#new-case-practice-area').selectOption('1');await page.locator('#new-case-attorney').selectOption('1');}
   if(row[0]==='case-update') await page.locator(row[3]).fill('Synthetic changed update');
   if(row[0]==='case-status') await page.locator(row[3]).first().check();
   // Invoke the native form event to exercise existing validation/server feedback.
   state.mode='pending';await form.evaluate(e=>e.requestSubmit());
   await page.waitForFunction(()=>!!document.querySelector('.case-edit-form button:disabled'));
   await capture(page,row[0],theme,360,'pending-disabled');
   state.release(); await page.locator('main [role="alert"]').first().waitFor();
   await capture(page,row[0],theme,360,'validation');
   if(row[0]==='contact-editor'||row[0]==='organization-editor') {
    await form.getByRole('button',{name:'Cancel',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.waitFor();
    assert.equal(await dialog.evaluate(e=>getComputedStyle(e).colorScheme),theme);
    await capture(page,row[0],theme,360,'discard-dialog');
    assert.equal(await page.getByRole('button',{name:'Keep editing'}).evaluate(e=>e===document.activeElement),true);
    await page.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});
   }
   assert.deepEqual(state.unexpected,[]);await context.close();
  }
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'observations.json'),JSON.stringify({browser:await browser.version(),syntheticOnly:true,records,errors,physicalDevices:false,screenReaderSpeech:false},null,2));
  console.log(JSON.stringify({captures:records.length,errors}));
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
