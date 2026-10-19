// Actual CSS cascade/contrast audit for synthetic populated routes/editors.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
const {intercept,routes,editors}=require('./fixtures.cjs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.BROWSER_EXECUTABLE_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const records=[];
 try {
  for(const theme of ['light','dark']) {
   const context=await browser.newContext({viewport:{width:1280,height:900}}),state={mode:'normal',calls:[],unexpected:[],writes:0};await intercept(context,state);const page=await context.newPage();
   for(const [name,url,button,field] of [...routes,...editors]) {
    await page.goto((process.env.REVIEW_ORIGIN||'http://127.0.0.1:5173')+url);await page.locator('.shale-authenticated h1').waitFor();await page.getByLabel('Theme (this session)').selectOption(theme);
    if(button) {await page.getByRole('button',{name:button,exact:true}).click();await page.locator(field).first().waitFor();}
    else if(['/cases','/contacts','/organizations'].includes(url)) {await page.locator('main input[type="search"]').fill('Synthetic');await page.getByRole('button',{name:'Search',exact:true}).click();}
    else if(url==='/case-workspace') await page.getByRole('button',{name:'Assigned',exact:true}).click();
    await page.waitForFunction(()=>!document.querySelector('main .loading-state'));
    const record=await page.evaluate(()=>{
     const rgb=c=>{const a=c.match(/[\d.]+/g)?.map(Number);return a?.length>=3?[...a.slice(0,3),a[3]??1]:[0,0,0,0];};
     const composite=(front,back)=>front.slice(0,3).map((n,i)=>n*front[3]+back[i]*(1-front[3]));
     const lum=c=>c.slice(0,3).map(n=>n/255).map(n=>n<=.04045?n/12.92:((n+.055)/1.055)**2.4).reduce((a,n,i)=>a+n*[.2126,.7152,.0722][i],0);
     const ratio=(a,b)=>(Math.max(lum(a),lum(b))+.05)/(Math.min(lum(a),lum(b))+.05);
     function background(e){let chain=[];for(let p=e;p;p=p.parentElement) chain.unshift(p);return chain.reduce((c,p)=>composite(rgb(getComputedStyle(p).backgroundColor),c),[255,255,255]);}
     let checked=0;const failures=[],skipped=[];
     for(const e of document.querySelectorAll('main *')) {
      if(!e.getBoundingClientRect().height||e.closest(':disabled,fieldset:disabled')) continue;
      if(![...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim())) continue;
      if(e.closest('.shale-button,.entity-card-activation')) {skipped.push(e.tagName+' action: pseudo-plane covered by token audit');continue;}
      if(e.tagName==='OPTION') continue;
      const s=getComputedStyle(e),bg=background(e),fg=composite(rgb(s.color),bg),r=ratio(fg,bg),large=parseFloat(s.fontSize)>=24||(parseFloat(s.fontSize)>=18.66&&Number(s.fontWeight)>=700),minimum=large?3:4.5;
      checked++;if(r+.01<minimum) failures.push({tag:e.tagName,class:e.className,ratio:r,minimum,foreground:s.color,background:bg});
     }
     const images=[...document.querySelectorAll('main .entity-card,main .detail-header,main .case-edit-form,main .status-choice-card')].map(e=>getComputedStyle(e).backgroundImage).filter(c=>c!=='none');
     return {checked,failures,skippedActions:skipped.length,nonActionGradients:images};
    });
    assert.deepEqual(record.failures,[],name+' '+theme+' actual text contrast');assert.deepEqual(record.nonActionGradients,[],name+' gradient ownership');records.push({name,theme,...record});
   }
   assert.deepEqual(state.unexpected,[]);await context.close();
  }
  fs.writeFileSync('shale-web/docs/theme-evidence/paint-observations.json',JSON.stringify({browser:await browser.version(),records,syntheticOnly:true,limits:'Computed solid text/ancestor backgrounds; shared action pseudo-planes and gradients checked separately with token pairs.'},null,2));console.log(JSON.stringify({scenarios:records.length,textPairs:records.reduce((n,r)=>n+r.checked,0)}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
