// Isolated synthetic operator harness; never imported by operational application code.
// Requires a desktop with a display and an independently installed Playwright browser.
const engines = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const readline = require('node:readline/promises');
const origin = process.env.REVIEW_ORIGIN || 'http://127.0.0.1:5173';
const engine = process.env.REVIEW_ENGINE || 'chromium';
const long = 'Synthetic work — ' + 'LongUnbrokenDemonstrationName'.repeat(5);
const task = {id:12,caseId:1,title:long,caseName:null,priorityId:null,dueAt:null,completedAt:null};
(async () => {
 const browser = await engines[engine].launch(engine === 'chromium'
  ? {headless:false, ...(process.env.BROWSER_EXECUTABLE_PATH ? {executablePath:process.env.BROWSER_EXECUTABLE_PATH} : {})}
  : {headless:false});
 // No viewport emulation: operators change actual window size/native browser zoom.
 const context = await browser.newContext({viewport:null});
 await context.addInitScript(() => sessionStorage.setItem('shale-web.accessToken','synthetic-fixture-token'));
 let mode='normal', outcome='failure', releaseReads, releaseCompletion;
 await context.route('**/api/**',async route => {
  const path = new URL(route.request().url()).pathname;
  let body;
  if(path==='/api/auth/me') body={authenticated:true,userId:1,shaleClientId:1,displayName:long,email:'synthetic@example.invalid'};
  else if(path==='/api/cases/assigned'||path==='/api/tasks/assigned') {
   const current=mode;
   if(current==='loading') await new Promise(done => { (releaseReads ||= []).push(done); });
   if(current==='errors') return route.fulfill({status:503,contentType:'application/json',body:'{}'});
   body=current==='empty'||current==='loading'?[]:path.includes('/cases/')
    ? [{caseId:1,caseName:long,caseNumber:null,caseStatus:null,responsibleAttorney:null,solDate:null}]
    : [task,{...task,id:13,title:null}];
  } else if(path==='/api/tasks/12/complete'||path==='/api/tasks/13/complete') {
   await new Promise(done => { releaseCompletion=done; });
   if(outcome==='failure') return route.fulfill({status:409,contentType:'application/json',body:'{}'});
   body={...task,id:Number(path.split('/')[3]),completedAt:'2026-10-08T11:00:00'};
  } else if(path==='/api/tasks/12'||path==='/api/tasks/13') body={...task,id:Number(path.split('/')[3]),description:'Synthetic task',shaleClientId:1};
  else if(path==='/api/cases/1') body={caseId:1,caseName:long,relatedContacts:[],mappedCaseDates:[],statusHistory:[]};
  else if(path==='/api/cases/1/tasks'||path==='/api/cases/1/updates'||path==='/api/cases/search') body=[];
  else return route.abort(); // Never fall through to a live API.
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 const page=await context.newPage(); await page.goto(origin+'/my-shale');
 const input=readline.createInterface({input:process.stdin,output:process.stdout});
 console.log('Synthetic review only. Commands: normal, loading, empty, errors (reload); reads (release loading); success or failure (release pending Complete); quit. Select theme in browser after reload. Click Complete first to inspect pending. API calls never reach a service.');
 for (;;) {
  const command=(await input.question('review> ')).trim();
  if(command==='quit') break;
  if(['normal','loading','empty','errors'].includes(command)) {mode=command;await page.goto(origin+'/my-shale');}
  else if(command==='reads') {for(const done of releaseReads||[]) done();releaseReads=[];}
  else if(['success','failure'].includes(command)) {outcome=command;if(releaseCompletion){releaseCompletion();releaseCompletion=undefined;}else console.log('Click Complete before releasing an outcome.');}
 }
 input.close();await browser.close();
})().catch(error => {console.error(error);process.exit(1);});
