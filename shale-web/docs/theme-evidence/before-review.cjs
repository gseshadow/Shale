// Run only against a separate untouched checkout of the pinned base, served on port 5174.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
const {intercept}=require('./fixtures.cjs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.BROWSER_EXECUTABLE_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const out='shale-web/docs/theme-evidence/before';fs.mkdirSync(out,{recursive:true});const records=[];
 try{const context=await browser.newContext({viewport:{width:1280,height:900}}),state={mode:'normal',calls:[],unexpected:[],writes:0};await intercept(context,state);const page=await context.newPage();
  for(const [name,url] of [['tasks','/tasks'],['workspace','/case-workspace'],['overview','/case-workspace/1'],['case-detail','/cases/1']]) {
   await page.goto((process.env.BASE_ORIGIN||'http://127.0.0.1:5174')+url);await page.locator('.shale-authenticated h1').waitFor();await page.getByLabel('Theme (this session)').selectOption('dark');await page.waitForFunction(()=>!document.querySelector('main .loading-state'));
   const paint=await page.evaluate(()=>{const frame=document.querySelector('.legacy-route-content');return {theme:document.querySelector('.shale-authenticated').dataset.theme,frame:getComputedStyle(frame).backgroundColor,scheme:getComputedStyle(frame).colorScheme,card:getComputedStyle(document.querySelector('.entity-card')||document.querySelector('main section')).backgroundImage};});
   assert.equal(paint.scheme,'light');await page.screenshot({path:`${out}/${name}-1280-dark.jpg`,fullPage:true,type:'jpeg',quality:72});records.push({name,url,...paint});
  }
  assert.equal(state.writes,0);assert.deepEqual(state.unexpected,[]);fs.writeFileSync(`${out}/observations.json`,JSON.stringify({base:'d0d43655765b74e0c1e83ecefe59451307e7b749',syntheticOnly:true,records},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
