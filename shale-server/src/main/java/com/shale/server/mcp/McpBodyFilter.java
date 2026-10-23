package com.shale.server.mcp;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.filter.OncePerRequestFilter;

/** Nonblocking upload admission on /mcp only. No credential/authority is retained here. */
final class McpBodyFilter extends OncePerRequestFilter {
    static final String ADMISSION=McpBodyFilter.class.getName()+".admission";
    private final Semaphore active=new Semaphore(5);
    static final class Admission {
        final long deadline=System.nanoTime()+10_000_000_000L;
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final AtomicBoolean dispatched=new AtomicBoolean(),released=new AtomicBoolean();
    }
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if(!request.getMethod().equals("POST")){chain.doFilter(request,response);return;}
        if(request.getAttribute(ADMISSION) instanceof Admission admission) {
            try {chain.doFilter(new McpGate.BodyRequest(request,admission.bytes.toByteArray()),response);}
            finally {release(request,admission);}return;
        }
        response.setHeader("Cache-Control","no-store");
        if(request.getDispatcherType()==DispatcherType.ASYNC){reject(response,503,"unavailable");return;}
        if(request.getContentLengthLong()>8192 || request.getHeader("Content-Encoding")!=null){reject(response,413,"body_limit");return;}
        if(!active.tryAcquire()){response.setHeader("Retry-After","1");reject(response,429,"throttled");return;}
        var admission=new Admission();request.setAttribute(ADMISSION,admission);
        AsyncContext async;
        try {async=request.startAsync();}catch(RuntimeException unavailable){release(request,admission);reject(response,503,"unavailable");return;}
        async.setTimeout(10000);
        async.addListener(new AsyncListener() {
            @Override public void onComplete(AsyncEvent event){release(request,admission);}
            @Override public void onError(AsyncEvent event){admission.dispatched.set(true);release(request,admission);}
            @Override public void onTimeout(AsyncEvent event)throws IOException {
                if(admission.dispatched.compareAndSet(false,true)){reject(response,504,"deadline");async.complete();}
            }
            @Override public void onStartAsync(AsyncEvent event){}
        });
        var input=request.getInputStream();
        input.setReadListener(new ReadListener() {
            private void stop(int status,String code)throws IOException {
                if(admission.dispatched.compareAndSet(false,true)){reject(response,status,code);async.complete();}
            }
            @Override public void onDataAvailable()throws IOException {
                var buffer=new byte[1024];
                while(input.isReady() && !input.isFinished() && !admission.dispatched.get()) {
                    if(System.nanoTime()>=admission.deadline){stop(504,"deadline");return;}
                    int count=input.read(buffer,0,Math.min(buffer.length,8193-admission.bytes.size()));
                    if(count<0)return;if(count==0)return;
                    admission.bytes.write(buffer,0,count);
                    if(admission.bytes.size()>8192){stop(413,"body_limit");return;}
                }
            }
            @Override public void onAllDataRead() {if(admission.dispatched.compareAndSet(false,true))async.dispatch();}
            @Override public void onError(Throwable failure){admission.dispatched.set(true);release(request,admission);async.complete();}
        });
    }
    private void release(HttpServletRequest request,Admission admission) {
        if(admission.released.compareAndSet(false,true)){request.removeAttribute(ADMISSION);active.release();}
    }
    private static void reject(HttpServletResponse response,int status,String code)throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"error\":\""+code+"\"}");
    }
}
