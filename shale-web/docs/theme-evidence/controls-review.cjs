const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
const {intercept}=require('./fixtures.cjs');
const out='shale-web/docs/theme-evidence/controls';fs.mkdirSync(out,{recursive:true});
const origin=process.env.REVIEW_ORIGIN||'http://127.0.0.1:5173';
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.BROWSER_EXECUTABLE_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const records=[];
 try{
  for(const theme of ['light','dark']) {
   for(const url of ['/my-shale','/tasks']) {
    const context=await browser.newContext({viewport:{width:360,height:900}}),state={mode:'normal',calls:[],unexpected:[],writes:0};await intercept(context,state);const page=await context.newPage();
    await page.goto(origin+url);await page.getByRole('button',{name:'Complete',exact:true}).waitFor();await page.getByLabel('Theme (this session)').selectOption(theme);
    state.mode='pending';await page.getByRole('button',{name:'Complete',exact:true}).click();await page.getByRole('button',{name:'Completing…',exact:true}).waitFor();
    assert(await page.getByRole('button',{name:'Completing…',exact:true}).isDisabled());
    const name=url.slice(1);await page.screenshot({path:`${out}/${name}-${theme}-pending.jpg`,fullPage:true,type:'jpeg',quality:72});state.release();await page.getByRole('alert').waitFor();await page.screenshot({path:`${out}/${name}-${theme}-error.jpg`,fullPage:true,type:'jpeg',quality:72});records.push({name,theme,pending:true,error:true,noLiveMutation:true});await context.close();
   }
   const context=await browser.newContext({viewport:{width:360,height:900}}),state={mode:'normal',nonAdmin:true,calls:[],unexpected:[],writes:0};await intercept(context,state);const page=await context.newPage();
   await page.goto(origin+'/settings');await page.getByRole('heading',{name:'Settings',exact:true}).waitFor();await page.getByLabel('Theme (this session)').selectOption(theme);
   await page.screenshot({path:`${out}/nonadmin-settings-${theme}.jpg`,fullPage:true,type:'jpeg',quality:72});assert(!state.calls.some(c=>c.path.startsWith('/api/settings')));
   state.nonAdmin=false;await page.goto(origin+'/tasks/12');await page.getByRole('button',{name:'Edit task',exact:true}).click();const select=page.getByLabel('Priority',{exact:true});await select.locator('option[value="1"]').waitFor({state:'attached'});await page.getByLabel('Theme (this session)').selectOption(theme);
   await select.focus();await page.keyboard.press('ArrowDown');await page.keyboard.press('Enter');
   const paint=await select.evaluate(e=>({scheme:getComputedStyle(e).colorScheme,background:getComputedStyle(e).backgroundColor,color:getComputedStyle(e).color,optionBackground:getComputedStyle(e.options[1]).backgroundColor,optionColor:getComputedStyle(e.options[1]).color}));
   assert.equal(paint.scheme,theme);assert.equal(paint.background,paint.optionBackground);assert.equal(paint.color,paint.optionColor);assert.equal(state.writes,0);
   await select.click();await page.screenshot({path:`${out}/native-select-${theme}.jpg`,fullPage:true,type:'jpeg',quality:72});await page.keyboard.press('Escape');records.push({name:'native-select-and-nonadmin',theme,...paint,noAdministrativeReads:true});await context.close();
  }
  fs.writeFileSync(`${out}/observations.json`,JSON.stringify({browser:await browser.version(),records,limits:'Native Chromium keyboard/select plus inherited option paint; other OS popup implementations not verified.'},null,2));console.log(JSON.stringify({scenarios:records.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
