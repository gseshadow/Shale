import json,time
from tools.mcp_tool_discovery import register_mcp_servers
from tools.mcp_tool_handlers import _make_tool_handler
names=register_mcp_servers({'shale':{'url':'https://shale.test/mcp','auth':'oauth','connect_timeout':30}})
print(json.dumps({'registered':names}),flush=True)
for tool,args in [('search_cases',{'query':'Literal','page':0}),('get_case_overview',{'caseId':100})]:
 start=time.monotonic();result=_make_tool_handler('shale',tool,10)(args)
 body=json.loads(result);assert 'error' not in body,result
 print(json.dumps({'tool':tool,'ms':round((time.monotonic()-start)*1000),'result':body}),flush=True)
