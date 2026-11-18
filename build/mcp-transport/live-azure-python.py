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
SUBSTEPS = frozenset(('LOGIN_ADMIN', 'LOGIN_BADMIN', 'LOGIN_MEMBER', 'REGISTER_CLIENT',
    'ONBOARD_MEMBER', 'DENY_MEMBER_APPROVAL', 'DENY_CROSS_TENANT_APPROVAL',
    'ONBOARD_ADMIN_A', 'APPROVE_ADMIN_A', 'ONBOARD_ADMIN_B', 'APPROVE_ADMIN_B',
    'REJECT_WEB_JWT', 'REJECT_REFRESH_TOKEN', 'REJECT_ORIGIN', 'REJECT_PROTOCOL',
    'SDK_DISCOVERY', 'SDK_LIST_TOOLS', 'SDK_TOOL_CALL'))
OPERATIONS = {'/api/auth/login': 'WEB_LOGIN', '/api/auth/logout': 'WEB_LOGOUT',
    '/oauth/mcp/register': 'CLIENT_REGISTRATION', '/oauth/mcp/onboarding': 'ONBOARDING',
    '/oauth/mcp/approval': 'APPROVAL', '/oauth/mcp/authorize': 'AUTHORIZATION',
    '/oauth/mcp/consent': 'CONSENT', '/oauth/mcp/token': 'TOKEN',
    '/oauth/mcp/revoke': 'REVOCATION', '/mcp': 'MCP',
    '/.well-known/oauth-authorization-server/oauth/mcp': 'AUTHORIZATION_METADATA',
    '/.well-known/oauth-protected-resource/mcp': 'RESOURCE_METADATA'}


def require(condition):
    if not condition:
        raise AssertionError('Acceptance contract failed')


def closed_errors(error):
    pending, seen, result = [error], set(), []
    for _ in range(32):
        if not pending:
            break
        current = pending.pop()
        if id(current) in seen:
            continue
        seen.add(id(current))
        reason = current.reason if isinstance(current, urllib.error.URLError) else current
        name, module = type(current).__name__, type(current).__module__
        code = None
        if module == 'mcp.shared.exceptions' and name == 'MCPError':
            kind = 'MCP_ERROR'
            value = current.code
            code = value if type(value) is int and -(2**31) <= value < 2**31 else None
        elif isinstance(reason, TimeoutError) or (module.startswith('httpx2') and name in ('ReadTimeout', 'ConnectTimeout', 'WriteTimeout', 'PoolTimeout')):
            kind = 'TIMEOUT'
        elif isinstance(current, (urllib.error.URLError, OSError)) or (module.startswith('httpx2') and name in ('HTTPStatusError', 'TransportError', 'ReadError', 'ConnectError', 'RemoteProtocolError')):
            kind = 'HTTP_TRANSPORT'
        elif isinstance(current, json.JSONDecodeError):
            kind = 'RESPONSE_JSON'
        elif isinstance(current, (KeyError, TypeError)) or (module.startswith('pydantic') and name == 'ValidationError'):
            kind = 'RESPONSE_SCHEMA'
        elif isinstance(current, AssertionError):
            kind = 'ASSERTION'
        else:
            kind = 'UNKNOWN'
        if kind != 'UNKNOWN' and {'kind': kind, 'code': code} not in result:
            result.append({'kind': kind, 'code': code})
        children = getattr(current, 'exceptions', ())
        if isinstance(children, (tuple, list)):
            pending.extend(child for child in children[:32] if isinstance(child, BaseException))
    return result or [{'kind': 'UNKNOWN', 'code': None}]


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
        self.substep = None
        self.last_http = None
        self.failure_diagnostic = None
        self.sdk_requests = []
        self.sdk_errors = []
        self.last_refresh_at = None
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
        self.last_http = {'operation': OPERATIONS.get(path.split('?', 1)[0], 'UNKNOWN'), 'status': None}
        req = urllib.request.Request(BASE + path, data=body, headers=h)
        try:
            response = self.client.open(req, timeout=660)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            self.last_http['status'] = int(response.status)
            raw = response.read(262145)
            require(len(raw) <= 262144)
            data = json.loads(raw) if raw and 'application/json' in response.headers.get('Content-Type', '') else {}
            return response.status, data, response.headers

    def ok(self, path, body=None, bearer=None, kind='form', status=200):
        code, data, _ = self.request(path, body, bearer, kind)
        if self.last_http is not None:
            self.last_http['expectedStatus'] = status
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
                  'credentialCleanup': cleanup, 'failureDiagnostic': self.failure_diagnostic,
                  'sdkDiagnostics': {'requests': self.sdk_requests, 'errors': self.sdk_errors}}
        target = self.run / 'http-report.json'
        temp = target.with_suffix('.tmp')
        temp.write_text(json.dumps(result, indent=2))
        temp.replace(target)

    def login(self, email, tenant, user):
        self.substep = {'admin@example.invalid': 'LOGIN_ADMIN', 'badmin@example.invalid': 'LOGIN_BADMIN',
                        'a72@example.invalid': 'LOGIN_MEMBER'}.get(email)
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

    def expect_discovery_status(self, token, expected, extra_headers=None):
        code, _, _ = self.raw_discover(token, extra_headers)
        if self.last_http is not None:
            self.last_http['expectedStatus'] = expected
        require(code == expected)

    def renew_access(self, client_id, family):
        refreshed = self.ok('/oauth/mcp/token', {'grant_type': 'refresh_token', 'client_id': client_id,
            'resource': BASE + '/mcp', 'refresh_token': family['refresh_token']})
        require(refreshed['token_type'].lower() == 'bearer' and 0 < refreshed['expires_in'] <= 300
                and refreshed['refresh_token'] != family['refresh_token'])
        # Snapshot every issued generation before replacement so cleanup retains them all.
        self.families.append((client_id, dict(family)))
        family.update(refreshed)
        self.last_refresh_at = time.monotonic()
        return family['access_token']

    async def tools(self, token, tenant, *, full=True):
        import httpx2
        from mcp import Client
        from mcp.client.streamable_http import streamable_http_client
        # SDK 2.3.0's auto-discovery has a separate fixed 10s limit, independent of
        # Client.read_timeout_seconds. Override only that timer in this fixed-target
        # operator process; preserve auto negotiation and all real result assertions.
        sdk_session = importlib.import_module('mcp.client.session')
        require(sdk_session.DISCOVER_TIMEOUT_SECONDS in (10.0, 660.0))
        sdk_session.DISCOVER_TIMEOUT_SECONDS = 660.0
        self.versions['sdk_discovery_timeout_seconds'] = 660.0
        # Long full-catalog verification must not accidentally age a five-minute
        # access token across multiple requests. Rotate through the real OAuth
        # endpoint before each SDK request; never extend SQL credential timestamps.
        owned = [(client_id, family) for client_id, family in self.families if family.get('access_token') == token]
        require(bool(owned))
        client_id, family = owned[0]

        def renew():
            return self.renew_access(client_id, family)

        token = renew()
        # Reuse the existing authoritative acceptance result/schema assertions.
        harness = runpy.run_path(str(pathlib.Path(__file__).with_name('authoritative-python.py')))
        own, foreign = (100, 200) if tenant == 7 else (200, 100)
        async def observe_request(request):
            require(str(request.url) == BASE + '/mcp')
            require(len(self.sdk_requests) < 128)
            now = time.monotonic()
            age = None if self.last_refresh_at is None else int(max(0, min(3_600_000, (now-self.last_refresh_at)*1000)))
            entry = {'substep': self.substep if self.substep in SUBSTEPS else 'UNKNOWN',
                     'status': None, 'elapsedMillis': None, 'refreshAgeMillis': age}
            self.sdk_requests.append(entry)
            request.extensions['shale_acceptance_observation'] = (now, entry)
            self.last_http = {'operation': 'MCP', 'status': None}

        async def observe_response(response):
            start, entry = response.request.extensions['shale_acceptance_observation']
            entry['status'] = int(response.status_code)
            entry['elapsedMillis'] = int(max(0, min(3_600_000, (time.monotonic()-start)*1000)))
            self.last_http = {'operation': 'MCP', 'status': int(response.status_code)}

        self.substep = 'SDK_DISCOVERY'
        async with httpx2.AsyncClient(headers={'Authorization': 'Bearer ' + token}, timeout=660, trust_env=False,
                event_hooks={'request': [observe_request], 'response': [observe_response]}) as http:
            async with Client(streamable_http_client(BASE + '/mcp', http_client=http), mode='auto', read_timeout_seconds=660) as client:
                require(client.protocol_version == PROTOCOL)

                async def call(name, arguments):
                    self.substep = 'SDK_TOOL_CALL'
                    http.headers['Authorization'] = 'Bearer ' + renew()
                    return await client.call_tool(name, arguments)

                self.substep = 'SDK_LIST_TOOLS'
                http.headers['Authorization'] = 'Bearer ' + renew()
                require([harness['obj'](t)['name'] for t in (await client.list_tools()).tools] == ['search_cases', 'get_case_overview'])
                result = harness['value'](await call('get_case_overview', {'caseId': own}))
                harness['item'](result['case'])
                require(result['case']['caseId'] == own)
                self.disclosures += 1
                self.overviews += 1
                if full:
                    result = harness['value'](await call('search_cases', {'query': 'Literal'}))
                    require([r['caseId'] for r in result['items']] == [own])
                    self.disclosures += 1
                    for case in (foreign, 300, 999):
                        harness['error'](await call('get_case_overview', {'caseId': case}), 'CASE_UNAVAILABLE')
                    harness['error'](await call('search_cases', {'query': 'Literal', 'tenantId': 8}), 'INVALID_INPUT')
        self.substep = None

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
        self.substep = 'REGISTER_CLIENT'
        client = self.ok('/oauth/mcp/register', {'client_name': 'Synthetic automated acceptance',
            'redirect_uris': [REDIRECT], 'grant_types': ['authorization_code', 'refresh_token'],
            'response_types': ['code'], 'scope': ' '.join(SCOPES), 'token_endpoint_auth_method': 'none'}, kind='json', status=201)['client_id']
        require(client.startswith('dcr.'))
        self.substep = 'ONBOARD_MEMBER'
        state = self.onboard(client, member)
        require(not state['admin'] and state['tenant'] == 7)
        self.substep = 'DENY_MEMBER_APPROVAL'
        self.decide(client, state, member, expected=401)
        self.substep = 'DENY_CROSS_TENANT_APPROVAL'
        self.decide(client, state, b, expected=401)
        for web, tenant_label in ((a, 'A'), (b, 'B')):
            self.substep = 'ONBOARD_ADMIN_' + tenant_label
            state = self.onboard(client, web)
            self.substep = 'APPROVE_ADMIN_' + tenant_label
            require(self.decide(client, state, web)['state'] == 'APPROVED')
        self.record(self.current)
        self.substep = None
        self.current = 'oauth-s256-state-code-replay'
        fa, _ = self.authorize(client, a)
        fb, _ = self.authorize(client, b)
        expiry, issued = self.authorize(client, a)
        # Real WEB JWTs exceed McpGate's 50-character Authorization ceiling.
        # The header check precedes authentication: require its exact 400 rejection.
        self.substep = 'REJECT_WEB_JWT'
        require(len('Bearer ' + a) > 50)
        self.expect_discovery_status(a, 400)
        self.substep = 'REJECT_REFRESH_TOKEN'
        self.expect_discovery_status(fa['refresh_token'], 401)
        self.substep = 'REJECT_ORIGIN'
        self.expect_discovery_status(self.renew_access(client, fa), 403, {'Origin': 'https://attacker.invalid'})
        self.substep = 'REJECT_PROTOCOL'
        self.expect_discovery_status(self.renew_access(client, fa), 400, {'MCP-Protocol-Version': '2099-01-01'})
        self.substep = None
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
        # Issue the independent expiry probe here: earlier families may naturally
        # expire during the deliberately slow SDK tests. Do not refresh this probe.
        expiry, issued = self.authorize(client, a)
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
        except Exception as error:
            # Capture BEFORE cleanup replaces the last HTTP operation. Never retain payload/error text.
            self.sdk_errors = closed_errors(error)
            kind = self.sdk_errors[0]['kind']
            last = self.last_http or {}
            number = lambda value: value if type(value) is int and 100 <= value <= 599 else None
            self.failure_diagnostic = {
                'substep': self.substep if self.substep in SUBSTEPS else 'UNKNOWN', 'kind': kind,
                'operation': last.get('operation') if last.get('operation') in OPERATIONS.values() else 'UNKNOWN',
                'status': number(last.get('status')), 'expectedStatus': number(last.get('expectedStatus'))}

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
