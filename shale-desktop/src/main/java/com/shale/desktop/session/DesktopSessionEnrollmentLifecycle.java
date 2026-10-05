package com.shale.desktop.session;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

/** Coordinates the one post-JDBC exchange per authenticated desktop generation. */
public final class DesktopSessionEnrollmentLifecycle {
    public enum State { NOT_CONFIGURED, PENDING, ENROLLED, COMPATIBILITY, SECURITY_REJECTED }
    private static final Logger log=LoggerFactory.getLogger(DesktopSessionEnrollmentLifecycle.class);
    private final DesktopSessionEnrollmentClient client;private final DesktopServerSession session;private final AtomicLong generation=new AtomicLong();
    private volatile Pending pending;private volatile State state;
	private final ProtectedRememberCredentialStore protectedStore;private final java.util.UUID installationId;
	private volatile String pendingRememberCredential;
	private volatile SessionPolicyInvalidationCoordinator acceleration;
    public DesktopSessionEnrollmentLifecycle(DesktopSessionEnrollmentClient client,DesktopServerSession session){this(client,session,new ProtectedRememberCredentialStore(),null);}
    public DesktopSessionEnrollmentLifecycle(DesktopSessionEnrollmentClient client,DesktopServerSession session,ProtectedRememberCredentialStore store,java.util.UUID installationId){this.client=client;this.session=java.util.Objects.requireNonNull(session);this.protectedStore=java.util.Objects.requireNonNull(store);this.installationId=installationId;state=client==null?State.NOT_CONFIGURED:State.COMPATIBILITY;}
    public void stage(int tenant,int user,String email,String password){stage(tenant,user,email,password,false);}
    public void stage(int tenant,int user,String email,String password,boolean remember){long g=generation.incrementAndGet();session.clear();pendingRememberCredential=null;if(!remember)protectedStore.clear();boolean protectedRemember=remember&&protectedStore.available()&&installationId!=null;if(remember&&!protectedRemember)log.warn("Stay logged in unavailable because protected storage or installation identity is unavailable.");pending=client==null?null:new Pending(g,tenant,user,email,password,protectedRemember);state=client==null?State.NOT_CONFIGURED:State.PENDING;}
    public void enroll(Long instanceId){Pending value=pending;if(value==null||client==null)return;try{var result=value.remember?client.enrollRemembered(value.email,value.password,instanceId,requiredInstallation()):new DesktopSessionEnrollmentClient.EnrollmentResult(client.enroll(value.email,value.password,instanceId),null,null,null,null,false,false);if(value.generation==generation.get()){session.install(result.credential());pendingRememberCredential=result.rememberCredential();state=State.ENROLLED;log.info("Desktop durable session enrollment succeeded.");}}
        catch(DesktopSessionEnrollmentClient.EnrollmentException e){if(value.generation==generation.get()){state=(e.failure()==DesktopSessionEnrollmentClient.Failure.ENDPOINT_UNAVAILABLE||e.failure()==DesktopSessionEnrollmentClient.Failure.TRANSIENT)?State.COMPATIBILITY:State.SECURITY_REJECTED;log.warn("Desktop durable session enrollment unavailable: {}",e.failure());}}
        finally{if(pending==value)pending=null;}}
    public UiRestore restore()throws DesktopSessionEnrollmentClient.EnrollmentException{if(client==null||installationId==null)return null;String saved;try{var value=protectedStore.read();if(value.isEmpty())return null;saved=value.get();}catch(Exception e){protectedStore.clear();throw new IllegalStateException("Saved sign-in could not be read. Please sign in again.");}
        String presented=saved,replacement=ServerAuthSessionServiceSecret.newSecret();boolean interrupted=saved.startsWith("v1\n");
        if(interrupted){String[] parts=saved.split("\\n",3);if(parts.length!=3){protectedStore.clear();throw new IllegalStateException("Saved sign-in could not be read. Please sign in again.");}presented=parts[2];}
        try{protectedStore.write("v1\n"+presented+"\n"+replacement);}catch(Exception e){throw new IllegalStateException("Stay logged in protected storage is unavailable.");}
        DesktopSessionEnrollmentClient.EnrollmentResult result;
        try{result=client.restore(presented,replacement,installationId);}catch(DesktopSessionEnrollmentClient.EnrollmentException first){
            if(!interrupted||first.failure()!=DesktopSessionEnrollmentClient.Failure.SECURITY_REJECTED){if(first.failure()!=DesktopSessionEnrollmentClient.Failure.TRANSIENT)protectedStore.clear();throw first;}
            String[] parts=saved.split("\\n",3);replacement=parts[2];result=client.restore(parts[1],replacement,installationId);
        }
        session.install(result.credential());pendingRememberCredential=result.rememberCredential();state=State.ENROLLED;return new UiRestore(result.userId(),result.shaleClientId(),result.email(),result.admin(),result.attorney());}
    public boolean hasRememberedCredential(){try{return protectedStore.read().isPresent();}catch(Exception e){protectedStore.clear();return false;}}
    public void commitRemembered()throws Exception{String value=pendingRememberCredential;if(value!=null){protectedStore.write(value);pendingRememberCredential=null;}}
    public void clearRemembered(){pendingRememberCredential=null;protectedStore.clear();}
    public void logout(){generation.incrementAndGet();pending=null;clearRemembered();stopAcceleration();try{session.current().ifPresent(c->{if(client!=null)client.logout(c.accessToken());});}finally{session.clear();}}
	public synchronized void startAcceleration(com.shale.desktop.live.LiveEventDispatcher dispatcher,int tenant,long loginGeneration,java.util.function.LongSupplier currentGeneration,Runnable policyRefresh,Runnable confirmedRevocation){stopAcceleration();if(client!=null&&state==State.ENROLLED){var sid=session.current().orElseThrow().sessionId();acceleration=new SessionPolicyInvalidationCoordinator(dispatcher,client,session,tenant,loginGeneration,currentGeneration,java.util.concurrent.ForkJoinPool.commonPool(),policyRefresh,()->{confirmedRevocation(sid);confirmedRevocation.run();});}}
	public synchronized void stopAcceleration(){if(acceleration!=null){acceleration.close();acceleration=null;}}
    public void confirmedRevocation(java.util.UUID sessionId){generation.incrementAndGet();pending=null;clearRemembered();stopAcceleration();session.clearIf(sessionId);state=State.SECURITY_REJECTED;}
    public void shutdown(){generation.incrementAndGet();pending=null;stopAcceleration();session.clear();}
    public State state(){return state;} public DesktopServerSession session(){return session;}
    private java.util.UUID requiredInstallation(){if(installationId==null)throw new IllegalStateException("Protected installation identity is unavailable.");return installationId;}
    public record UiRestore(int userId,int shaleClientId,String email,boolean admin,boolean attorney){}
    private record Pending(long generation,int tenant,int user,String email,String password,boolean remember){}
	private static final class ServerAuthSessionServiceSecret{static String newSecret(){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);}}
}
