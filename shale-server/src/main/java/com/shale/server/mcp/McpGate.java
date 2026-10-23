package com.shale.server.mcp;

import com.shale.data.security.ai.AiBearerParser;
import com.shale.data.security.ai.VerifiedIntegrationContext;
import com.shale.server.oauth.McpOAuthProperties;
import com.shale.server.oauth.OAuthMetadataContract;
import io.modelcontextprotocol.modern.JsonRpc;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse.JSONRPCError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Namespace-only admission. All authority is a request attribute removed in finally; never a session. */
final class McpGate extends OncePerRequestFilter {
    static final String PROTOCOL="2026-07-28", AUTHORITY=McpGate.class.getName()+".authority";
    private static final long MAX_NANOS=10_000_000_000L;
    private final McpTransportProperties properties;
    private final McpOAuthProperties identity;
    private final boolean global;
    private final Function<String,VerifiedIntegrationContext> authenticate;
    private final Semaphore active=new Semaphore(5);
    private final java.util.function.LongSupplier nanoTime;
    private final io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper mapper=McpTransportConfiguration.mapper();
    McpGate(McpTransportProperties properties, McpOAuthProperties identity, boolean global,
            Function<String,VerifiedIntegrationContext> authenticate) {
        this(properties,identity,global,authenticate,System::nanoTime);
    }
    McpGate(McpTransportProperties properties,McpOAuthProperties identity,boolean global,
            Function<String,VerifiedIntegrationContext> authenticate,java.util.function.LongSupplier nanoTime) {
        this.properties=properties;this.identity=identity;this.global=global;this.authenticate=authenticate;this.nanoTime=nanoTime;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        long deadline=nanoTime.getAsLong()+MAX_NANOS;
        response.setHeader("Cache-Control","no-store");
        if (!properties.enabled()) { fail(response,404,"disabled"); return; }
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals(OAuthMetadataContract.PROTECTED_RESOURCE_PATH)) {
            if (!global) { fail(response,404,"disabled");return; }
            if (!request.getMethod().equals("GET")) { response.setHeader("Allow","GET");fail(response,405,"method");return; }
            response.setContentType("application/json");
            response.getOutputStream().write(mapper.writeValueAsBytes(OAuthMetadataContract.protectedResource(identity)));return;
        }
        if (!request.getMethod().equals("POST")) {
            response.setHeader("Allow","POST");fail(response,405,"method");return;
        }
        try { headers(request); }
        catch (Rejected rejected) { fail(response,rejected.status,rejected.code);return; }
        if (!active.tryAcquire()) { response.setHeader("Retry-After","1");fail(response,429,"throttled");return; }
        try {
            VerifiedIntegrationContext authority;
            try {
                if (!global || request.getQueryString()!=null || request.getCookies()!=null && request.getCookies().length>0) throw new SecurityException();
                authority=authenticate.apply(AiBearerParser.extract(Collections.list(request.getHeaders("Authorization"))));
                if (authority==null || authority.credentialId()==null) throw new SecurityException();
            } catch (RuntimeException denied) {
                response.setHeader("WWW-Authenticate","Bearer resource_metadata=\""+identity.publicOrigin()+OAuthMetadataContract.PROTECTED_RESOURCE_PATH+"\"");
                fail(response,401,"unauthorized");return;
            }
            check(deadline);
            byte[] body;
            if (request.getContentLengthLong()>8192) throw new Rejected(413,"body_limit");
            try (var input=request.getInputStream()) { body=input.readNBytes(8193); }
            if (body.length>8192) throw new Rejected(413,"body_limit");
            check(deadline);
            // SDK owns JSON-RPC parsing; this preliminary SDK parse adds only Shale envelope ceilings.
            // The mounted SDK servlet independently validates metadata/header equality and dispatches.
            JsonRpc.JSONRPCMessage message;
            try { message=JsonRpc.deserializeMessage(mapper,new String(body,StandardCharsets.UTF_8)); }
            catch (IOException malformed) { rpc(response,400,null,-32700,"Parse error");return; }
            catch (IllegalArgumentException invalid) { rpc(response,400,null,-32600,"Invalid request");return; }
            if (message instanceof JsonRpc.JSONRPCRequest rpc) {
                if (rpc.id() instanceof String id && (id.length()>64 || !id.chars().allMatch(c->c>=32 && c<=126)) ||
                        rpc.method().isEmpty() || rpc.method().length()>128) {
                    rpc(response,400,null,-32600,"Invalid request");return;
                }
            } else if (!(message instanceof JsonRpc.JSONRPCNotification notification) ||
                    !notification.method().equals("notifications/cancelled")) {
                rpc(response,400,null,-32600,"Invalid request");return;
            }
            request.setAttribute(AUTHORITY,authority);
            var buffered=new ContentCachingResponseWrapper(response);
            chain.doFilter(new BodyRequest(request,body),buffered);
            check(deadline);
            byte[] result=buffered.getContentAsByteArray();
            if (result.length>4096 || buffered.getContentType()!=null && !buffered.getContentType().startsWith("application/json"))
                throw new Rejected(503,"unavailable");
            if (result.length>0) {
                var parsed=mapper.readValue(result,JSONRPCResponse.class);
                if (parsed.error()!=null) {
                    // SDK error strings can contain client method/parameter names. Preserve only closed codes/data.
                    var error=parsed.error();
                    Object data=error.code()==io.modelcontextprotocol.modern.McpSchema.ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION ?
                            Map.of("supported",List.of(PROTOCOL)) : null;
                    String safe=switch(error.code()) {
                        case -32700 -> "Parse error";
                        case -32600 -> "Invalid request";
                        case -32601 -> "Method not found";
                        case -32602 -> "Invalid parameters";
                        case -32020 -> "Header mismatch";
                        default -> "Protocol error";
                    };
                    result=mapper.writeValueAsBytes(JSONRPCResponse.error(parsed.id(),new JSONRPCError(error.code(),safe,data)));
                }
            }
            response.setStatus(buffered.getStatus());response.setContentType("application/json");
            response.setHeader("Cache-Control","no-store");response.setContentLength(result.length);
            check(deadline);response.getOutputStream().write(result);
        } catch (Rejected rejected) { fail(response,rejected.status,rejected.code); }
        catch (IOException disconnected) { throw disconnected; }
        catch (Exception unavailable) { fail(response,503,"unavailable"); }
        finally { request.removeAttribute(AUTHORITY);active.release(); }
    }
    private void headers(HttpServletRequest request) {
        for (var header:Map.of("Authorization",50,"Origin",240,"MCP-Protocol-Version",32,"Accept",256,
                "Content-Type",128,"Mcp-Method",128,"Mcp-Name",256).entrySet()) {
            var values=Collections.list(request.getHeaders(header.getKey()));
            if(values.size()>1) throw new Rejected(400,"headers");
            for(String value:values) if(value.length()>header.getValue() || value.chars().anyMatch(c->c<32 || c>126))
                throw new Rejected(400,"headers");
        }
        var names=Collections.list(request.getHeaderNames());
        if (names.stream().anyMatch(n->n.toLowerCase(Locale.ROOT).startsWith("mcp-param-")) ||
                request.getHeader("Mcp-Session-Id")!=null || request.getHeader("Content-Encoding")!=null)
            throw new Rejected(400,"headers");
        String origin=request.getHeader("Origin");
        if(origin!=null && !properties.allowedOrigins().contains(origin)) throw new Rejected(403,"origin");
        // Missing Origin is permitted for nonbrowser clients, never used as authority.
        try {
            var type=MediaType.parseMediaType(Objects.requireNonNull(request.getContentType()));
            if(!type.getType().equals("application") || !type.getSubtype().equals("json") ||
                    type.getParameters().keySet().stream().anyMatch(p->!p.equals("charset")) ||
                    type.getCharset()!=null && !type.getCharset().equals(StandardCharsets.UTF_8)) throw new Rejected(415,"media");
        } catch (Rejected invalid) { throw invalid; }
        catch (RuntimeException invalid) { throw new Rejected(415,"media"); }
        try {
            String accept=Objects.requireNonNull(request.getHeader("Accept"));
            int specificity=-1;double quality=0;
            for(var type:MediaType.parseMediaTypes(accept)) {
                if(!type.isCompatibleWith(MediaType.APPLICATION_JSON))continue;
                if(type.getParameters().keySet().stream().anyMatch(p->!p.equals("q")))continue;
                int score=type.isWildcardType()?0:type.isWildcardSubtype()?1:2;
                if(score>specificity){specificity=score;quality=type.getQualityValue();}
                else if(score==specificity)quality=Math.max(quality,type.getQualityValue());
            }
            if(quality<=0)throw new Rejected(406,"accept");
        } catch (Rejected invalid) { throw invalid; }
        catch (RuntimeException invalid) { throw new Rejected(406,"accept"); }
        if(request.getHeader("MCP-Protocol-Version")==null || request.getHeader("Mcp-Method")==null)
            throw new Rejected(400,"headers");
    }
    private void check(long deadline) { if(nanoTime.getAsLong()>=deadline || Thread.currentThread().isInterrupted()) throw new Rejected(504,"deadline"); }
    private void rpc(HttpServletResponse response,int status,Object id,int code,String message)throws IOException {
        response.setStatus(status);response.setContentType("application/json");
        response.getOutputStream().write(mapper.writeValueAsBytes(JSONRPCResponse.error(id,new JSONRPCError(code,message))));
    }
    private static void fail(HttpServletResponse response,int status,String code)throws IOException {
        response.setStatus(status);response.setContentType("application/json");
        response.getOutputStream().write(("{\"error\":\""+code+"\"}").getBytes(StandardCharsets.US_ASCII));
    }
    private static final class Rejected extends RuntimeException {
        final int status;final String code;
        Rejected(int status,String code) { super(null,null,false,false);this.status=status;this.code=code; }
    }
    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BodyRequest(HttpServletRequest request,byte[] body) { super(request);this.body=body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public ServletInputStream getInputStream() {
            var stream=new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return stream.read(); }
                @Override public boolean isFinished() { return stream.available()==0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
    }
}
