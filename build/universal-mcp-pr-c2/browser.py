"""Drive the existing Shale browser UI, keeping state/code URLs and passwords private."""
import asyncio,json,os,re,sys,shutil
from pathlib import Path
os.environ['MOZ_DISABLE_CONTENT_SANDBOX']='1';os.environ['MOZ_DISABLE_RDD_SANDBOX']='1'
os.environ['XDG_CACHE_HOME']='/workspace/Shale/work/pr-c2-harness/cache'
os.environ['PLAYWRIGHT_BROWSERS_PATH']='/workspace/Shale/work/pr-c2-browsers'
from playwright.async_api import async_playwright
p=Path('/workspace/Shale/work/pr-c2-harness');secret=Path('/workspace/Shale/work/pr-c2-sql-runtime/browser-password').read_text();tenant=sys.argv[1] if len(sys.argv)>1 else 'a'
async def run():
 events=[]
 try:
  async with async_playwright() as playwright:
   profile=p/f'firefox-profile-{tenant}'
   if not profile.exists():
    profile.mkdir()
    for name in ('cert9.db','key4.db','pkcs11.txt'):shutil.copy2(p/'firefox-profile'/name,profile/name)
   ctx=await playwright.firefox.launch_persistent_context(str(profile),headless=True,firefox_user_prefs={'network.dns.localDomains':'shale.test','network.proxy.type':0},viewport={'width':1100,'height':900})
   await ctx.clear_cookies();await ctx.clear_permissions();page=await ctx.new_page()
   await page.route('**/*',lambda route:route.continue_() if route.request.url.startswith(('https://shale.test/','http://localhost:','http://127.0.0.1:')) else route.abort())
   for i in range(600):
    if (p/f'browser-url-{tenant}').exists():break
    await asyncio.sleep(.5)
   url=(p/f'browser-url-{tenant}').read_text();assert url.startswith('https://shale.test/oauth/mcp/authorize?')
   await page.goto(url);events.append('stock authorization URL opened')
   await page.get_by_role('heading',name='Sign in',exact=True).wait_for(timeout=20000)
   await page.get_by_label('Email',exact=True).fill('badmin@example.invalid' if tenant.startswith('b') else 'admin@example.invalid')
   await page.get_by_label('Password',exact=True).fill(secret)
   await page.get_by_role('button',name='Sign in',exact=True).click()
   await page.get_by_role('button',name='Approve tenant application',exact=True).wait_for(timeout=25000)
   events.append('existing SQL password login and pending tenant approval')
   await page.screenshot(path=str(p/f'tenant-approval-{tenant}.png'))
   await page.get_by_role('button',name='Approve tenant application',exact=True).click()
   await page.get_by_role('button',name='Approve access',exact=True).wait_for(timeout=25000)
   events.append('explicit administrator approval; separate consent presented')
   await page.screenshot(path=str(p/f'user-consent-{tenant}.png'))
   await page.get_by_role('button',name='Approve access',exact=True).click()
   await page.wait_for_url(re.compile(r'^http://(?:localhost|127\.0\.0\.1):[0-9]+/(?:oauth/)?callback'),timeout=20000)
   events.append('explicit user consent returned code to stock callback')
   await asyncio.sleep(2);await ctx.close()
 except Exception as e:
  events.append({'failure':type(e).__name__,'message':re.sub(r'https?://[^\s]+','<redacted-url>',str(e).replace(secret,'<redacted>'))[:700]})
 (p/f'browser-{tenant}.json').write_text(json.dumps(events,indent=2));print(json.dumps(events))
asyncio.run(run())
