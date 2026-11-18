"""Exercise the actual HTTP/OAuth workflow and SDK assertions with a stateful local fake.
No socket, HTTP library, Azure CLI or SQL connection is opened.
"""
import asyncio
import base64
import contextlib
import hashlib
import importlib.util
import json
import pathlib
import sys
import tempfile
import types
import unittest
import urllib.parse
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('live_driver', ROOT / 'build/mcp-transport/live-azure-python.py')
live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live)


WEB_TOKENS = {user: 'web-' + str(user) + '-' + 'x' * 64 for user in (71, 72, 81)}


class FakeAcceptance(live.Acceptance):
    def __init__(self, directory):
        super().__init__(directory, {'admin@example.invalid': 'synthetic', 'badmin@example.invalid': 'synthetic', 'a72@example.invalid': 'synthetic'})
        self.clock = 1000
        self.counter = 0
        self.tokens = {}
        self.codes = {}
        self.intents = {}
        self.approvals = {}
        self.cases = []
        self.spoof_tenant = False
        self.accept_expired = False
        self.wrong_web_denial = False
        self.accept_web_token = False
        self.web_denials = []
        self.slow_oauth_denials = False
        self.protocol_probe_ages = []
        self.web_sessions = {}
        self.slow_lifecycle = False

    def request(self, path, body=None, bearer=None, kind='form', headers=None):
        body = body or {}
        browser = path.startswith('/oauth/mcp/authorize') or path.startswith('/oauth/mcp/consent') or path in ('/oauth/mcp/onboarding','/oauth/mcp/approval')
        profile = self.web_sessions.get(bearer)
        if browser and (not profile or profile['expiry'] <= self.clock):
            return 401, {}, {}

        if path.startswith('/.well-known/oauth-authorization'):
            return 200, {'issuer': live.BASE + '/oauth/mcp', 'authorization_endpoint': live.BASE + '/oauth/mcp/authorize',
                         'token_endpoint': live.BASE + '/oauth/mcp/token', 'registration_endpoint': live.BASE + '/oauth/mcp/register'}, {}
        if path.startswith('/.well-known/oauth-protected'):
            return 200, {'resource': live.BASE + '/mcp', 'authorization_servers': [live.BASE + '/oauth/mcp']}, {}
        if path == '/api/auth/login':
            tenant, user = {'admin@example.invalid': (7, 71), 'badmin@example.invalid': (8, 81), 'a72@example.invalid': (7, 72)}[body['email']]
            self.counter += 1
            web = WEB_TOKENS[user] + '-' + str(self.counter)
            self.web_sessions[web] = {'user': user, 'tenant': tenant, 'created': self.clock, 'expiry': self.clock + 3600}
            return 200, {'accessToken': web, 'user': {'userId': user, 'shaleClientId': tenant}}, {}
        if path == '/api/auth/logout':
            return 200, {}, {}
        if path == '/oauth/mcp/register':
            return 201, {'client_id': 'dcr.synthetic'}, {}
        if path in ('/oauth/mcp/onboarding', '/oauth/mcp/approval'):
            tenant = profile['tenant']
            if path.endswith('approval'):
                if profile['user'] == 72 or body['approval_id'] != str(tenant):
                    return 401, {}, {}
                self.approvals[tenant] = body['decision'] == 'APPROVE'
            return 200, {'state': 'APPROVED' if self.approvals.get(tenant) else 'PENDING', 'admin': profile['user'] != 72,
                         'tenant': tenant, 'approval_id': str(tenant), 'approval_version': '0000000000000001'}, {}
        if path.startswith('/oauth/mcp/authorize?'):
            params = {k: v[0] for k, v in urllib.parse.parse_qs(urllib.parse.urlsplit(path).query).items()}
            self.counter += 1
            intent = 'intent-' + str(self.counter)
            self.intents[intent] = (params, bearer)
            return 200, {'redirect': live.BASE + '/oauth/mcp/consent?state=' + intent}, {}
        if path.startswith('/oauth/mcp/consent?'):
            state = urllib.parse.parse_qs(urllib.parse.urlsplit(path).query)['state'][0]
            return 200, {'scopes': live.SCOPES, 'state': state}, {}
        if path == '/oauth/mcp/authorize':
            params, web = self.intents[body['state']]
            code = 'code-' + body['state']
            self.codes[code] = (params, web)
            return 200, {'redirect': live.REDIRECT + '?' + urllib.parse.urlencode({'state': params['state'], 'code': code})}, {}
        if path == '/oauth/mcp/token':
            if body.get('resource') != live.BASE + '/mcp':
                return 400, {}, {}
            if body['grant_type'] == 'authorization_code':
                if body['code'] not in self.codes:
                    return 400, {}, {}
                params, web = self.codes[body['code']]
                actual = base64.urlsafe_b64encode(hashlib.sha256(body['code_verifier'].encode()).digest()).decode().rstrip('=')
                if params['code_challenge'] != actual:
                    return 400, {}, {}
                del self.codes[body['code']]
                tenant = self.web_sessions[web]['tenant']
                grant_expiry = min(self.clock + 3600, self.web_sessions[web]['expiry'])
            else:
                old = self.tokens.get(body['refresh_token'])
                if not old or old['revoked'] or old['used'] or not self.approvals.get(old['tenant']):
                    return 400, {}, {}
                if old['grant_expiry'] <= self.clock or self.web_sessions[old['web']]['expiry'] <= self.clock:
                    return 400, {}, {}
                tenant = old['tenant']
                web, grant_expiry = old['web'], old['grant_expiry']
                old['used'] = True
            self.counter += 1
            access, refresh = 'access-' + str(self.counter), 'refresh-' + str(self.counter)
            family = {'tenant': tenant, 'expiry': self.clock + 300, 'revoked': False, 'used': False, 'kind': 'ACCESS', 'web': web, 'grant_expiry': grant_expiry}
            self.tokens[access] = family
            self.tokens[refresh] = dict(family, kind='REFRESH')
            # Keep the family's shared revocation membership explicitly.
            self.tokens[access]['peer'] = refresh
            self.tokens[refresh]['peer'] = access
            return 200, {'access_token': access, 'refresh_token': refresh, 'token_type': 'Bearer', 'expires_in': 300}, {}
        if path == '/oauth/mcp/revoke':
            family = self.tokens.get(body['token'])
            if family:
                family['revoked'] = True
                self.tokens[family['peer']]['revoked'] = True
            return 200, {}, {}
        if path == '/mcp':
            if self.slow_lifecycle and self.current == 'oauth-s256-state-code-replay':
                self.clock += 120
            if self.slow_oauth_denials and self.current == 'oauth-s256-state-code-replay':
                self.clock += 180
            if bearer and len('Bearer ' + bearer) > 50:
                status = 200 if self.accept_web_token else 401 if self.wrong_web_denial else 400
                self.web_denials.append(status)
                return status, {}, {}
            if headers and headers.get('Origin') == 'https://attacker.invalid':
                return 403, {}, {}
            token = self.tokens.get(bearer)
            good = token and token['kind'] == 'ACCESS' and not token['revoked'] and self.approvals.get(token['tenant']) and token['grant_expiry'] > self.clock and self.web_sessions[token['web']]['expiry'] > self.clock and (self.accept_expired or token['expiry'] > self.clock)
            if headers and headers.get('MCP-Protocol-Version') == '2099-01-01':
                self.protocol_probe_ages.append(None if not token else 300 - (token['expiry'] - self.clock))
                return (400 if good else 401), {}, {}
            return (200 if good else 401), {}, {'WWW-Authenticate': 'Bearer resource_metadata="' + live.BASE + '/.well-known/oauth-protected-resource/mcp"'}
        raise AssertionError('Unexpected route')

    def fresh_authority(self, client, email, tenant, user):
        if getattr(self, 'reuse_initial_authority', False):
            web = next(token for token, profile in self.web_sessions.items() if profile['user'] == user)
            family = next(family for _, family in self.families if self.tokens[family['access_token']]['tenant'] == tenant)
            return web, family, 0
        return super().fresh_authority(client, email, tenant, user)

    def renew_access(self, client_id, family):
        if getattr(self, 'skip_probe_renewal', False) and self.current == 'oauth-s256-state-code-replay':
            return family['access_token']
        return super().renew_access(client_id, family)

    async def sleep(self, seconds):
        self.clock += seconds

    def client_modules(self):
        acceptance = self

        def admitted(token):
            if acceptance.slow_lifecycle:
                acceptance.clock += 147
            if getattr(acceptance, 'slow_mcp', False):
                acceptance.clock += 120
            live.require(acceptance.raw_discover(token)[0] == 200)

        required = json.loads((ROOT / 'shale-server/src/main/resources/mcp/search-cases.json').read_text())['outputSchema']['$defs']['Case']['required']

        class Http:
            def __init__(self, headers=None, **kwargs):
                live.require(kwargs.get('timeout') == 660)
                self.headers = headers
            @property
            def token(self):
                return self.headers['Authorization'][7:]
            async def __aenter__(self):
                return self
            async def __aexit__(self, *args):
                pass

        class Client:
            protocol_version = live.PROTOCOL
            def __init__(self, token, **kwargs):
                live.require(kwargs.get('mode') == 'auto' and kwargs.get('read_timeout_seconds') == 660)
                self.http = token
            @property
            def token(self):
                return self.http.token
            async def __aenter__(self):
                admitted(self.token)
                return self
            async def __aexit__(self, *args):
                pass
            async def list_tools(self):
                admitted(self.token)
                return types.SimpleNamespace(tools=[{'name': 'search_cases'}, {'name': 'get_case_overview'}])
            async def call_tool(self, tool, args):
                admitted(self.token)
                tenant = acceptance.tokens[self.token]['tenant']
                own = 100 if tenant == 7 else 200
                def error(code):
                    return {'isError': True, 'content': [{'text': json.dumps({'code': code})}]}
                if 'tenantId' in args:
                    return error('INVALID_INPUT')
                if tool == 'get_case_overview' and args['caseId'] != own:
                    return error('CASE_UNAVAILABLE')
                item = dict.fromkeys(required)
                item['caseId'] = (200 if own == 100 else 100) if acceptance.spoof_tenant else own
                result = {'case': item} if tool == 'get_case_overview' else {'items': [item]}
                acceptance.cases.append((tenant, tool))
                return {'isError': False, 'structuredContent': result, 'content': [{'text': json.dumps(result)}]}

        return {'httpx2': types.SimpleNamespace(AsyncClient=Http), 'mcp': types.SimpleNamespace(Client=Client),
                'mcp.client.session': types.SimpleNamespace(DISCOVER_TIMEOUT_SECONDS=10.0),
                'mcp.client.streamable_http': types.SimpleNamespace(streamable_http_client=lambda url, http_client: http_client)}


class LiveDriverTest(unittest.TestCase):
    def campaign(self, mutation=None):
        with tempfile.TemporaryDirectory() as directory:
            a = FakeAcceptance(directory)
            if mutation:
                setattr(a, mutation, True)
            if mutation == 'reuse_initial_authority':
                a.slow_lifecycle = True
            if mutation == 'skip_probe_renewal':
                a.slow_oauth_denials = True
            with patch.dict(sys.modules, a.client_modules()), patch.object(live.time, 'monotonic', lambda: a.clock), patch.object(live.asyncio, 'sleep', a.sleep):
                code = asyncio.run(a.run_all())
            return code, json.loads((pathlib.Path(directory) / 'http-report.json').read_text()), a

    def test_complete_workflow_with_real_harness_assertions(self):
        code, report, a = self.campaign()
        self.assertEqual(code, 0, report)
        self.assertEqual(report['status'], 'PASS')
        self.assertEqual(a.versions['sdk_discovery_timeout_seconds'], 660.0)
        self.assertEqual(report['disclosures'], 8)
        self.assertEqual(report['overviews'], 6)
        self.assertEqual(len(report['records']), 8)
        self.assertGreaterEqual(a.clock, 1302)
        self.assertTrue(all(family['revoked'] for family in a.tokens.values()))

    def test_real_size_web_jwt_requires_header_rejection_and_never_acceptance(self):
        code, report, a = self.campaign()
        self.assertEqual(code, 0, report)
        self.assertEqual(a.web_denials, [400])
        for mutation in ('wrong_web_denial', 'accept_web_token'):
            code, report, _ = self.campaign(mutation)
            self.assertEqual(code, 1, mutation)
            self.assertEqual(report['failedCheck'], 'oauth-s256-state-code-replay')
            self.assertEqual(report['failureDiagnostic']['substep'], 'REJECT_WEB_JWT')

    def test_slow_verification_uses_real_refresh_and_keeps_the_natural_expiry_probe_independent(self):
        code, report, a = self.campaign('slow_mcp')
        self.assertEqual(code, 0, report)
        self.assertEqual(report['status'], 'PASS')
        self.assertEqual(report['disclosures'], 8)
        self.assertEqual(report['overviews'], 6)
        self.assertGreater(a.clock, 4000, 'Simulate verification taking minutes across many requests')
        self.assertTrue(all(family['expires_in'] <= 300 for _, family in a.families))
        self.assertTrue(all(token['revoked'] for token in a.tokens.values()))

    def test_slow_denial_checks_refresh_before_the_authenticated_protocol_probe(self):
        code, report, a = self.campaign('slow_oauth_denials')
        self.assertEqual(code, 0, report)
        self.assertEqual(a.protocol_probe_ages, [180])
        self.assertTrue(all(token['revoked'] for token in a.tokens.values()))
        code, report, stale = self.campaign('skip_probe_renewal')
        self.assertEqual(code, 1, report)
        self.assertEqual(report['failureDiagnostic']['substep'], 'REJECT_PROTOCOL')
        self.assertGreater(stale.protocol_probe_ages[0], 300)

    def test_full_slow_campaign_uses_fresh_stage_web_sessions_and_fixed_grant_lifetimes(self):
        code, report, a = self.campaign('slow_lifecycle')
        self.assertEqual(code, 0, report)
        self.assertGreater(a.clock, 4600, 'The full scenario must cross the original one-hour lifetime')
        self.assertEqual(report['disclosures'], 8)
        self.assertEqual(report['overviews'], 6)
        self.assertTrue(all(token['revoked'] for token in a.tokens.values()))
        self.assertTrue(all(profile['expiry']-profile['created'] == 3600 for profile in a.web_sessions.values()))
        code, report, _ = self.campaign('reuse_initial_authority')
        self.assertEqual(code, 1)
        self.assertEqual(report['failedCheck'], 'refresh-rotation-resource-binding')
        self.assertEqual(report['disclosures'], 5)
        self.assertEqual(report['overviews'], 3)

    def test_cross_tenant_result_is_not_accepted_as_success(self):
        code, report, _ = self.campaign('spoof_tenant')
        self.assertEqual(code, 1)
        self.assertEqual(report['failedCheck'], 'sdk-initialize-tools-case-reads-isolation-denials')

    def test_natural_expiry_failure_cannot_be_hidden_by_revocation(self):
        code, report, _ = self.campaign('accept_expired')
        self.assertEqual(code, 1)
        self.assertEqual(report['failedCheck'], 'natural-credential-expiry')


if __name__ == '__main__':
    unittest.main()
