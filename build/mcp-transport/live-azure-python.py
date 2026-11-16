"""Operator runner child. Fixed synthetic Azure endpoint; secrets arrive only on stdin.
No fixture/setup import, SQL, credential expiry mutation, insecure TLS or proxy fallback.
"""
import asyncio
import base64
import hashlib
import importlib.metadata
import platform
import json
import math
import pathlib
import runpy
import secrets
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = 'https://shale-mcp-acceptance.azurewebsites.net'
PROTOCOL = '2026-07-28'
SCOPES = ['cases.search', 'cases.overview.read']
REDIRECT = 'http://127.0.0.1:27891/callback'


def require(condition):
    if not condition:
        raise AssertionError('Acceptance contract failed')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Acceptance:
    def __init__(self, run, inputs):
        self.run = pathlib.Path(run)
        self.inputs = inputs
        self.web = []
        self.families = []
        self.records = []
        self.current = 'https-discovery'
        self.versions = {'python': platform.python_version()}
        for package in ('mcp', 'httpx2'):
            try:
                self.versions[package] = importlib.metadata.version(package)
            except importlib.metadata.PackageNotFoundError:
                self.versions[package] = 'unavailable'
        self.disclosures = 0
        self.overviews = 0
        self.client = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), NoRedirect(),
            urllib.request.HTTPSHandler(context=ssl.create_default_context()))

    def request(self, path, body=None, bearer=None, kind='form', headers=None):
        require(path.startswith('/') and not path.startswith('//'))
        h = {'Accept': 'application/json', **(headers or {})}
        if bearer:
            h['Authorization'] = 'Bearer ' + bearer
        if body is not None:
            h['Content-Type'] = 'application/json' if kind == 'json' else 'application/x-www-form-urlencoded'
            body = (json.dumps(body) if kind == 'json' else urllib.parse.urlencode(body, doseq=True)).encode()
        req = urllib.request.Request(BASE + path, data=body, headers=h)
        try:
            response = self.client.open(req, timeout=12)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read(262145)
            require(len(raw) <= 262144)
            data = json.loads(raw) if raw and 'application/json' in response.headers.get('Content-Type', '') else {}
            return response.status, data, response.headers

    def ok(self, path, body=None, bearer=None, kind='form', status=200):
        code, data, _ = self.request(path, body, bearer, kind)
        require(code == status)
        return data

    def record(self, name):
        self.records.append({'id': name, 'status': 'PASS'})
        self.save('RUNNING')
        print(name, flush=True)

    def save(self, status, cleanup=None):
        result = {'status': status, 'failedCheck': self.current if status == 'FAIL' else None,
                  'protocol': PROTOCOL, 'clientVersions': self.versions, 'records': self.records,
                  'disclosures': self.disclosures, 'overviews': self.overviews,
                  'credentialCleanup': cleanup}
        target = self.run / 'http-report.json'
        temp = target.with_suffix('.tmp')
        temp.write_text(json.dumps(result, indent=2))
        temp.replace(target)

    def login(self, email, tenant, user):
        result = self.ok('/api/auth/login', {'email': email, 'password': self.inputs[email]}, kind='json')
        token = result['accessToken']
        self.web.append(token)
        require(result['user']['userId'] == user and result['user']['shaleClientId'] == tenant)
        return token

    def onboard(self, client, web):
        return self.ok('/oauth/mcp/onboarding', {'client_id': client, 'scope': SCOPES}, web)

    def decide(self, client, state, web, decision='APPROVE', expected=200):
        return self.ok('/oauth/mcp/approval', {
            'client_id': client, 'approval_id': state['approval_id'],
            'approval_version': state['approval_version'], 'decision': decision, 'scope': SCOPES}, web, status=expected)

    def authorize(self, client, web):
        verifier = secrets.token_urlsafe(32)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
        state = secrets.token_urlsafe(24)
        params = {'client_id': client, 'response_type': 'code', 'redirect_uri': REDIRECT,
                  'resource': BASE + '/mcp', 'scope': ' '.join(SCOPES), 'state': state,
                  'code_challenge': challenge, 'code_challenge_method': 'S256'}
        start = self.ok('/oauth/mcp/authorize?' + urllib.parse.urlencode(params), bearer=web)
        consent = urllib.parse.urlsplit(start['redirect'])
        require(consent.scheme == 'https' and consent.netloc == urllib.parse.urlsplit(BASE).netloc
                and consent.path == '/oauth/mcp/consent')
        details = self.ok(consent.path + '?' + consent.query, bearer=web)
        require(set(details['scopes']) == set(SCOPES))
        done = self.ok('/oauth/mcp/authorize', {'client_id': client, 'state': details['state'], 'scope': details['scopes']}, web)
        callback = urllib.parse.urlsplit(done['redirect'])
        require(callback.scheme + '://' + callback.netloc + callback.path == REDIRECT)
        query = urllib.parse.parse_qs(callback.query)
        require(query['state'] == [state])
        exchange = {'grant_type': 'authorization_code', 'client_id': client, 'code': query['code'][0],
                    'redirect_uri': REDIRECT, 'resource': BASE + '/mcp', 'code_verifier': verifier}
        # Wrong S256 verifier must not consume the valid code.
        bad = dict(exchange, code_verifier=secrets.token_urlsafe(32))
        require(self.request('/oauth/mcp/token', bad)[0] in (400, 401))
        family = self.ok('/oauth/mcp/token', exchange)
        issued = time.monotonic()  # TTL starts no earlier than receipt; never under-wait for network time.
        self.families.append((client, family))
        require(family['token_type'].lower() == 'bearer' and 0 < family['expires_in'] <= 300)
        require(self.request('/oauth/mcp/token', exchange)[0] in (400, 401))
        return family, issued

    def raw_discover(self, token=None, extra_headers=None):
        return self.request('/mcp', {'jsonrpc': '2.0', 'id': 'live', 'method': 'server/discover',
            'params': {'_meta': {'io.modelcontextprotocol/protocolVersion': PROTOCOL,
                                'io.modelcontextprotocol/clientCapabilities': {}}}}, token, 'json',
            {'MCP-Protocol-Version': PROTOCOL, 'Mcp-Method': 'server/discover', **(extra_headers or {})})

    async def tools(self, token, tenant, *, full=True):
        import httpx2
        from mcp import Client
        from mcp.client.streamable_http import streamable_http_client
        # Reuse the existing authoritative acceptance result/schema assertions.
        harness = runpy.run_path(str(pathlib.Path(__file__).with_name('authoritative-python.py')))
        own, foreign = (100, 200) if tenant == 7 else (200, 100)
        async with httpx2.AsyncClient(headers={'Authorization': 'Bearer ' + token}, timeout=10, trust_env=False) as http:
            async with Client(streamable_http_client(BASE + '/mcp', http_client=http), mode='auto') as client:
                require(client.protocol_version == PROTOCOL)
                require([harness['obj'](t)['name'] for t in (await client.list_tools()).tools] == ['search_cases', 'get_case_overview'])
                result = harness['value'](await client.call_tool('get_case_overview', {'caseId': own}))
                harness['item'](result['case'])
                require(result['case']['caseId'] == own)
                self.disclosures += 1
                self.overviews += 1
                if full:
                    result = harness['value'](await client.call_tool('search_cases', {'query': 'Literal'}))
                    require([r['caseId'] for r in result['items']] == [own])
                    self.disclosures += 1
                    for case in (foreign, 300, 999):
                        harness['error'](await client.call_tool('get_case_overview', {'caseId': case}), 'CASE_UNAVAILABLE')
                    harness['error'](await client.call_tool('search_cases', {'query': 'Literal', 'tenantId': 8}), 'INVALID_INPUT')

    async def execute(self):
        metadata = self.ok('/.well-known/oauth-authorization-server/oauth/mcp')
        require(metadata['issuer'] == BASE + '/oauth/mcp' and metadata['token_endpoint'] == BASE + '/oauth/mcp/token'
                and metadata['authorization_endpoint'] == BASE + '/oauth/mcp/authorize'
                and metadata['registration_endpoint'] == BASE + '/oauth/mcp/register')
        resource = self.ok('/.well-known/oauth-protected-resource/mcp')
        require(resource['resource'] == BASE + '/mcp' and resource['authorization_servers'] == [BASE + '/oauth/mcp'])
        anonymous = self.raw_discover()
        require(anonymous[0] == 401 and BASE + '/.well-known/oauth-protected-resource/mcp' in anonymous[2].get('WWW-Authenticate', '')
                and self.raw_discover('synthetic-invalid')[0] == 401)
        self.record(self.current)
        self.current = 'synthetic-web-login-registration-approval'
        a = self.login('admin@example.invalid', 7, 71)
        b = self.login('badmin@example.invalid', 8, 81)
        member = self.login('a72@example.invalid', 7, 72)
        client = self.ok('/oauth/mcp/register', {'client_name': 'Synthetic automated acceptance',
            'redirect_uris': [REDIRECT], 'grant_types': ['authorization_code', 'refresh_token'],
            'response_types': ['code'], 'scope': ' '.join(SCOPES), 'token_endpoint_auth_method': 'none'}, kind='json', status=201)['client_id']
        require(client.startswith('dcr.'))
        state = self.onboard(client, member)
        require(not state['admin'] and state['tenant'] == 7)
        self.decide(client, state, member, expected=401)
        self.decide(client, state, b, expected=401)
        for web in (a, b):
            state = self.onboard(client, web)
            require(self.decide(client, state, web)['state'] == 'APPROVED')
        self.record(self.current)
        self.current = 'oauth-s256-state-code-replay'
        fa, _ = self.authorize(client, a)
        fb, _ = self.authorize(client, b)
        expiry, issued = self.authorize(client, a)
        require(self.raw_discover(a)[0] == 401 and self.raw_discover(fa['refresh_token'])[0] == 401)
        require(self.raw_discover(fa['access_token'], {'Origin': 'https://attacker.invalid'})[0] == 403)
        require(self.raw_discover(fa['access_token'], {'MCP-Protocol-Version': '2099-01-01'})[0] == 400)
        self.record(self.current)
        self.current = 'sdk-initialize-tools-case-reads-isolation-denials'
        await self.tools(fa['access_token'], 7)
        await self.tools(fb['access_token'], 8)
        await self.tools(expiry['access_token'], 7, full=False)
        self.record(self.current)
        self.current = 'refresh-rotation-resource-binding'
        require(self.request('/oauth/mcp/token', {'grant_type': 'refresh_token', 'client_id': client,
            'resource': BASE + '/wrong', 'refresh_token': fa['refresh_token']})[0] in (400, 401))
        rotated = self.ok('/oauth/mcp/token', {'grant_type': 'refresh_token', 'client_id': client,
            'resource': BASE + '/mcp', 'refresh_token': fa['refresh_token']})
        self.families.append((client, rotated))
        require(rotated['refresh_token'] != fa['refresh_token'])
        await self.tools(rotated['access_token'], 7, full=False)
        self.record(self.current)
        self.current = 'revocation-access-refresh-denied'
        self.ok('/oauth/mcp/revoke', {'client_id': client, 'token': rotated['access_token']})
        require(self.raw_discover(rotated['access_token'])[0] == 401)
        require(self.request('/oauth/mcp/token', {'grant_type': 'refresh_token', 'client_id': client,
            'resource': BASE + '/mcp', 'refresh_token': rotated['refresh_token']})[0] in (400, 401))
        require(self.request('/oauth/mcp/token', {'grant_type': 'refresh_token', 'client_id': client,
            'resource': BASE + '/mcp', 'refresh_token': fa['refresh_token']})[0] in (400, 401))
        self.ok('/oauth/mcp/revoke', {'client_id': client, 'token': 'synthetic-unknown'})
        self.record(self.current)
        self.current = 'natural-credential-expiry'
        # Keep an independent, unrevoked family. Never update credential timestamps or disable SQL constraints.
        require(self.raw_discover(expiry['access_token'])[0] == 200)
        deadline = issued + math.ceil(expiry['expires_in']) + 2
        while time.monotonic() < deadline:
            await asyncio.sleep(min(10, deadline - time.monotonic()))
        require(self.raw_discover(expiry['access_token'])[0] == 401)
        self.record(self.current)
        self.current = 'approval-revocation-tenant-isolation'
        # Tenant B's fresh family remains usable after tenant A revokes its approval.
        fresh_a, _ = self.authorize(client, a)
        await self.tools(fresh_a['access_token'], 7, full=False)
        state = self.onboard(client, a)
        self.decide(client, state, a, decision='REVOKE')
        require(self.raw_discover(fresh_a['access_token'])[0] == 401)
        require(self.request('/oauth/mcp/token', {'grant_type': 'refresh_token', 'client_id': client,
            'resource': BASE + '/mcp', 'refresh_token': fresh_a['refresh_token']})[0] in (400, 401))
        fresh_b, _ = self.authorize(client, b)
        await self.tools(fresh_b['access_token'], 8, full=False)
        self.record(self.current)

    async def run_all(self):
        status = 'FAIL'
        cleanup = True
        try:
            await self.execute()
            status = 'PASS'
        except Exception:
            pass  # Only closed failedCheck goes to evidence; never exception/payload/token text.
        finally:
            for client, family in self.families:
                try:
                    self.ok('/oauth/mcp/revoke', {'client_id': client, 'token': family['access_token']})
                except Exception:
                    cleanup = False
            for token in self.web:
                try:
                    self.ok('/api/auth/logout', {}, token, 'json')
                except Exception:
                    cleanup = False
            if not cleanup:
                status = 'FAIL'
            self.save(status, cleanup)
        return 0 if status == 'PASS' else 1


if __name__ == '__main__':
    # PS launches the exact script with a single nonsecret output path. No credentials in argv/environment/files.
    try:
        payload = json.loads(sys.stdin.readline())
        sys.exit(asyncio.run(Acceptance(sys.argv[1], payload).run_all()))
    except Exception:
        sys.exit(1)
