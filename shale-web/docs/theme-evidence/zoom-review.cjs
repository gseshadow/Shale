// Native Chromium Settings zoom, fixed physical viewport. Intercepted synthetic API only.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {intercept,routes,editors}=require('./fixtures.cjs');
const out=process.env.EVIDENCE_DIR||'shale-web/docs/theme-evidence/zoom';fs.mkdirSync(out,{recursive:true});
const origin=process.env.REVIEW_ORIGIN||'http://127.0.0.1:5173';
(async()=>{
 const context=await chromium.launchPersistentContext(process.env.ZOOM_PROFILE||'/tmp/shale-theme-zoom-profile',{executablePath:process.env.BROWSER_EXECUTABLE_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox'],viewport:{width:1280,height:900}});
 const state={mode:'normal',calls:[],unexpected:[],writes:0};await intercept(context,state);
 const errors=[],records=[];
 try {
  const settings=await context.newPage();await settings.goto('chrome://settings/appearance');await settings.locator('#zoomLevel').selectOption({label:'100%'});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(origin+'/tasks');await page.locator('.shale-authenticated h1').waitFor();
  const baseline=await page.evaluate(()=>({dpr:devicePixelRatio,width:innerWidth,height:innerHeight}));
  for(const zoom of [200,400]) {
   await settings.locator('#zoomLevel').selectOption({label:zoom+'%'});
   for(const theme of ['light','dark']) {
    for(const [name,url,button,field] of [...routes,...editors]) {
     await page.goto(origin+url);await page.locator('.shale-authenticated h1').waitFor();await page.getByLabel('Theme (this session)').selectOption(theme);
     if(button) {
      await page.getByRole('button',{name:button,exact:true}).click();await page.locator(field).first().waitFor();
      if(['new-case','case-assignment','task-editor'].includes(name)) await page.waitForFunction(()=>!document.querySelector('.case-edit-form select:disabled'));
      await page.keyboard.press('Tab');await page.locator(field).first().focus();
      assert.equal(await page.locator(field).first().evaluate(e=>getComputedStyle(e).outlineStyle),'solid');
     } else if(['/cases','/contacts','/organizations'].includes(url)) {
      await page.locator('main input[type="search"]').fill('Synthetic');await page.getByRole('button',{name:'Search',exact:true}).click();
     } else if(url==='/case-workspace') await page.getByRole('button',{name:'Assigned',exact:true}).click();
     await page.waitForFunction(()=>!document.querySelector('main .loading-state'));
     const metrics=await page.evaluate(()=>({dpr:devicePixelRatio,width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth}));
     assert.equal(metrics.dpr,baseline.dpr*zoom/100);assert.equal(metrics.width,Math.round(baseline.width*100/zoom));assert.equal(metrics.scrollWidth,metrics.width,name+' zoom overflow');
     const cdp=await context.newCDPSession(page),layout=await cdp.send('Page.getLayoutMetrics');
     const result=await cdp.send('Page.captureScreenshot',{format:'jpeg',quality:72,captureBeyondViewport:true,clip:{x:0,y:0,width:layout.contentSize.width,height:layout.contentSize.height,scale:100/zoom}});
     const screenshot=`${name}-${zoom}-${theme}.jpg`;fs.writeFileSync(path.join(out,screenshot),Buffer.from(result.data,'base64'));await cdp.detach();
     records.push({name,url,zoom,theme,...metrics,screenshot,keyboardFocus:!!button});
    }
    console.log(JSON.stringify({zoom,theme,scenarios:routes.length+editors.length}));
   }
  }
  await settings.locator('#zoomLevel').selectOption({label:'100%'});assert.deepEqual(errors,[]);assert.deepEqual(state.unexpected,[]);assert.equal(state.writes,0);
  fs.writeFileSync(path.join(out,'observations.json'),JSON.stringify({browser:context.browser().version(),mechanism:'Native Chromium Settings Page zoom; fixed physical viewport 1280x900; CDP export at CSS resolution.',baseline,records,errors,syntheticOnly:true,liveAcceptance:false},null,2));
 } finally {await context.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
