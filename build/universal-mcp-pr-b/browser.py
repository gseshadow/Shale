import asyncio,json,os,re
from pathlib import Path
os.environ['MOZ_DISABLE_CONTENT_SANDBOX']='1'
os.environ['MOZ_DISABLE_RDD_SANDBOX']='1'
os.environ['XDG_CACHE_HOME']='/workspace/Shale/work/pr-b-harness/cache'
os.environ['PLAYWRIGHT_BROWSERS_PATH']='/workspace/Shale/work/pr-b-browsers'
from playwright.async_api import async_playwright
p=Path('/workspace/Shale/work/pr-b-harness');secret=Path('/workspace/Shale/work/pr-b-sql/browser-password').read_text()
async def run():
 events=[]
 try:
  async with async_playwright() as playwright:
   ctx=await playwright.firefox.launch_persistent_context(str(p/'firefox-profile'),headless=True,firefox_user_prefs={'network.dns.localDomains':'shale.test','network.proxy.type':0}, viewport={'width':1100,'height':900})
   page=await ctx.new_page()
   await page.route('**/*',lambda route:route.continue_() if route.request.url.startswith(('https://shale.test/','http://127.0.0.1:2789')) else route.abort())
   for i in range(600):
    if (p/'browser-url').exists():break
    await asyncio.sleep(.5)
   assert (p/'browser-url').exists(),'Stock client did not open browser'
   url=(p/'browser-url').read_text();assert url.startswith('https://shale.test/oauth/mcp/authorize?')
   await page.goto(url);events.append('stock authorization URL opened')
   await page.get_by_role('heading',name='Sign in',exact=True).wait_for()
   await page.get_by_label('Email',exact=True).fill('admin@example.invalid')
   await page.get_by_label('Password',exact=True).fill(secret)
   await page.get_by_role('button',name='Sign in',exact=True).click()
   await page.get_by_role('button',name='Approve tenant application',exact=True).wait_for(timeout=20000)
   events.append('real SQL password login and pending tenant approval')
   await page.screenshot(path=str(p/'tenant-approval.png'))
   await page.get_by_role('button',name='Approve tenant application',exact=True).click()
   await page.get_by_role('button',name='Approve access',exact=True).wait_for(timeout=20000)
   events.append('explicit administrator approval; separate user consent presented')
   await page.screenshot(path=str(p/'user-consent.png'))
   await page.get_by_role('button',name='Approve access',exact=True).click()
   await page.wait_for_url(re.compile(r'^http://127\.0\.0\.1:2789[0-4]/callback'),timeout=15000)
   events.append('explicit user consent returned code to stock callback')
   await asyncio.sleep(2);await ctx.close()
 except Exception as e:
  events.append({'failure':type(e).__name__,'message':'Browser step did not complete; credentials and URLs omitted'})
 (p/'browser-evidence.json').write_text(json.dumps(events,indent=2));print(json.dumps(events))
asyncio.run(run())
