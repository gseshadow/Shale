package com.shale.desktop.session;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

/** Coordinates the one post-JDBC exchange per authenticated desktop generation. */
public final class DesktopSessionEnrollmentLifecycle {
    public enum State { NOT_CONFIGURED, PENDING, ENROLLED, COMPATIBILITY, SECURITY_REJECTED }
    private static final Logger log=LoggerFactory.getLogger(DesktopSessionEnrollmentLifecycle.class);
    private final DesktopSessionEnrollmentClient client;private final DesktopServerSession session;private final AtomicLong generation=new AtomicLong();
    private volatile Pending pending;private volatile State state;
    public DesktopSessionEnrollmentLifecycle(DesktopSessionEnrollmentClient client,DesktopServerSession session){this.client=client;this.session=java.util.Objects.requireNonNull(session);state=client==null?State.NOT_CONFIGURED:State.COMPATIBILITY;}
    public void stage(int tenant,int user,String email,String password){long g=generation.incrementAndGet();session.clear();pending=client==null?null:new Pending(g,tenant,user,email,password);state=client==null?State.NOT_CONFIGURED:State.PENDING;}
    public void enroll(Long instanceId){Pending value=pending;if(value==null||client==null)return;try{var credential=client.enroll(value.email,value.password,instanceId);if(value.generation==generation.get()){session.install(credential);state=State.ENROLLED;log.info("Desktop durable session enrollment succeeded.");}}
        catch(DesktopSessionEnrollmentClient.EnrollmentException e){if(value.generation==generation.get()){state=(e.failure()==DesktopSessionEnrollmentClient.Failure.ENDPOINT_UNAVAILABLE||e.failure()==DesktopSessionEnrollmentClient.Failure.TRANSIENT)?State.COMPATIBILITY:State.SECURITY_REJECTED;log.warn("Desktop durable session enrollment unavailable: {}",e.failure());}}
        finally{if(pending==value)pending=null;}}
    public void logout(){generation.incrementAndGet();pending=null;session.current().ifPresent(c->{if(client!=null)client.logout(c.accessToken());});session.clear();}
    public void shutdown(){generation.incrementAndGet();pending=null;session.clear();}
    public State state(){return state;} public DesktopServerSession session(){return session;}
    private record Pending(long generation,int tenant,int user,String email,String password){}
}
