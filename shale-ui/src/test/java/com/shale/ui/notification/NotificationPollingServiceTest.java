package com.shale.ui.notification;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NotificationPollingServiceTest {
 @Test void authenticStartBaselinesImmediatelyAndOnlyPresentsPostBaselineRows() {
  ManualScheduler scheduler=new ManualScheduler(); FakeSource source=new FakeSource(10);
  source.pages.add(page(List.of(summary(11,"ASSIGNED",Instant.EPOCH)),11,false));
  List<AppNotification> merged=new ArrayList<>(); List<Long> presented=new ArrayList<>();
  var service=service(source,scheduler,merged,p->{presented.add(p.notificationId());return PresentationResult.PRESENTED;},()->.5,config());
  assertEquals(0,source.calls); // no authentication/start, no polling
  service.start(7,9); assertEquals(0,source.calls);
  scheduler.runNext();
  assertEquals(List.of(11L),merged.stream().map(AppNotification::getDurableNotificationId).toList());
  assertEquals(List.of(11L),presented); assertEquals(10,source.cursors.get(0));
 }

 @Test void historicalBaselineEmptyAndPeriodicPagesAdvanceByScannedCursorIncludingFilteredOnlyPages() {
  ManualScheduler scheduler=new ManualScheduler(); FakeSource source=new FakeSource(50);
  source.pages.add(page(List.of(),55,true)); // five filtered durable rows scanned
  source.pages.add(page(List.of(summary(56,"ASSIGNED",Instant.EPOCH),summary(57,"ASSIGNED",Instant.EPOCH)),57,false));
  source.pages.add(page(List.of(),60,false));
  List<AppNotification> merged=new ArrayList<>();
  var service=service(source,scheduler,merged,p->PresentationResult.UNSUPPORTED,()->.5,config());
  service.start(7,9);scheduler.runNext();
  assertEquals(List.of(50L,55L),source.cursors.subList(0,2));
  assertEquals(List.of(56L,57L),merged.stream().map(AppNotification::getDurableNotificationId).toList());
  assertEquals(1000,scheduler.nextDelay());
  scheduler.runNext(); assertEquals(57,source.cursors.get(2));
  assertEquals(60,source.lastReturnedCursor);
 }

 @Test void idsNotCreatedAtDrivePagingAndPresenterAttemptsAreOnceDespiteDuplicateDelivery() {
  ManualScheduler scheduler=new ManualScheduler();FakeSource source=new FakeSource(4);
  Instant same=Instant.parse("2026-01-01T00:00:00Z");
  source.pages.add(page(List.of(summary(5,"ASSIGNED",same),summary(6,"ASSIGNED",same),summary(6,"ASSIGNED",same)),6,false));
  source.pages.add(page(List.of(summary(7,"ASSIGNED",same)),7,false));
  List<AppNotification> merged=new ArrayList<>();AtomicInteger attempts=new AtomicInteger();
  var service=service(source,scheduler,merged,p->{attempts.incrementAndGet();return PresentationResult.PRESENTED;},()->.5,config());
  service.start(7,9);scheduler.runNext();scheduler.runNext();
  assertEquals(3,attempts.get()); // 5,6,7; duplicate 6 suppressed by session set
  assertEquals(List.of(5L,6L,6L,7L),merged.stream().map(AppNotification::getDurableNotificationId).toList());
 }

 @Test void sessionReplacementStopAndShutdownCancelOldScheduledWorkAndIsolateCursors() {
  ManualScheduler scheduler=new ManualScheduler();FakeSource source=new FakeSource(10,20);
  source.pages.add(page(List.of(),10,false));source.pages.add(page(List.of(),20,false));
  List<AppNotification> merged=new ArrayList<>();var service=service(source,scheduler,merged,p->PresentationResult.UNSUPPORTED,()->.5,config());
  service.start(7,9);scheduler.runNext();service.start(8,10);scheduler.runNext();
  assertEquals(List.of("7:9","8:10"),source.sessions); assertEquals(List.of(10L,20L),source.cursors);
  service.stop();assertFalse(scheduler.hasRunnable());
  service.start(7,9);service.close();assertFalse(scheduler.hasRunnable());assertTrue(scheduler.shutdown);
 }

 @Test void transientBackoffGrowsCapsJittersAndSuccessResetsWhileAuthorizationAndBadCursorStop() {
  ManualScheduler scheduler=new ManualScheduler();FakeSource source=new FakeSource(1);
  source.failures.add(new NotificationRetrievalException(RetrievalFailureKind.TRANSIENT,new RuntimeException("SENSITIVE SQL")));
  source.failures.add(new NotificationRetrievalException(RetrievalFailureKind.TRANSIENT,new RuntimeException("SENSITIVE SQL")));
  source.pages.add(page(List.of(),1,false));source.pages.add(page(List.of(),1,false));
  var cfg=new NotificationPollingService.Config(Duration.ofMillis(1000),Duration.ofMillis(100),Duration.ofMillis(150),.5,10);
  var service=service(source,scheduler,new ArrayList<>(),p->PresentationResult.UNSUPPORTED,()->1.0,cfg);
  service.start(7,9);scheduler.runNext();assertEquals(150,scheduler.nextDelay());
  scheduler.runNext();assertEquals(150,scheduler.nextDelay());
  scheduler.runNext();assertEquals(1000,scheduler.nextDelay()); // success reset
  scheduler.runNext();assertEquals(1000,scheduler.nextDelay());
  service.stop();
  FakeSource denied=new FakeSource(1);denied.failures.add(new NotificationRetrievalException(RetrievalFailureKind.AUTHORIZATION,new RuntimeException("SECRET")));
  var deniedService=service(denied,scheduler,new ArrayList<>(),p->PresentationResult.UNSUPPORTED,()->0.0,cfg);
  deniedService.start(7,9);scheduler.runNext();assertFalse(scheduler.hasRunnable());
  FakeSource invalid=new FakeSource(1);invalid.pages.add(page(List.of(),0,false));
  var invalidService=service(invalid,scheduler,new ArrayList<>(),p->PresentationResult.UNSUPPORTED,()->0.0,cfg);
  invalidService.start(7,9);scheduler.runNext();assertFalse(scheduler.hasRunnable());
 }

 @Test void presenterFailureDoesNotStopMergeOrLaterPolling() {
  ManualScheduler scheduler=new ManualScheduler();FakeSource source=new FakeSource(1);
  source.pages.add(page(List.of(summary(2,"ASSIGNED",Instant.EPOCH)),2,false));source.pages.add(page(List.of(summary(3,"ASSIGNED",Instant.EPOCH)),3,false));
  List<AppNotification> merged=new ArrayList<>();var service=service(source,scheduler,merged,p->{throw new RuntimeException("CLIENT SECRET");},()->.5,config());
  PrintStream original=System.err;ByteArrayOutputStream captured=new ByteArrayOutputStream();System.setErr(new PrintStream(captured));
  try{service.start(7,9);scheduler.runNext();scheduler.runNext();assertEquals(2,merged.size());}finally{System.setErr(original);}
  String logs=captured.toString(StandardCharsets.UTF_8);
  assertFalse(logs.contains("CLIENT SECRET"));assertFalse(logs.contains("PRIVATE TITLE"));assertFalse(logs.contains("PRIVATE MESSAGE"));
 }

 @Test void logoutRejectsUiResultsAlreadyQueuedByOldGeneration() {
  ManualScheduler scheduler=new ManualScheduler();FakeSource source=new FakeSource(1);
  source.pages.add(page(List.of(summary(2,"ASSIGNED",Instant.EPOCH)),2,false));
  List<Runnable> uiQueue=new ArrayList<>();List<AppNotification> merged=new ArrayList<>();
  var service=new NotificationPollingService(source,NotificationPollingServiceTest::map,merged::add,new NotificationPrivacyProjector(),p->PresentationResult.UNSUPPORTED,scheduler,uiQueue::add,()->.5,config(),true);
  service.start(7,9);scheduler.runNext();assertEquals(1,uiQueue.size());service.stop();uiQueue.forEach(Runnable::run);assertTrue(merged.isEmpty());
 }

 private static NotificationPollingService service(FakeSource source,ManualScheduler scheduler,List<AppNotification> merged,DesktopNotificationPresenter presenter,DoubleSupplier jitter,NotificationPollingService.Config cfg){
  return new NotificationPollingService(source,NotificationPollingServiceTest::map,merged::add,new NotificationPrivacyProjector(),presenter,scheduler,Runnable::run,jitter,cfg,true);
 }
 private static NotificationPollingService.Config config(){return new NotificationPollingService.Config(Duration.ofMillis(1000),Duration.ofMillis(100),Duration.ofMillis(800),0,10);}
 private static AppNotification map(NotificationSummary s){return new AppNotification("db-"+s.id(),NotificationCategory.TASK,NotificationSeverity.INFO,"safe","safe",s.createdAt(),!s.read(),false,NotificationTargetScope.USER_SCOPED,s.id(),s.eventKey());}
 private static NotificationSummary summary(long id,String action,Instant time){return new NotificationSummary(id,7,9,"TASK","INFO","PRIVATE TITLE","PRIVATE MESSAGE","TASK",99L,action,"event-"+id,null,"PRIVATE ENTITY",1L,"PRIVATE CASE",null,null,null,null,null,null,time,false);}
 private static NotificationPage page(List<NotificationSummary> items,long next,boolean more){return new NotificationPage(items,NotificationCursor.after(next),more);}

 private static final class FakeSource implements NotificationServicePort {
  final Deque<Long> highs=new ArrayDeque<>();final Deque<NotificationPage> pages=new ArrayDeque<>();final Deque<NotificationRetrievalException> failures=new ArrayDeque<>();
  final List<Long> cursors=new ArrayList<>();final List<String> sessions=new ArrayList<>();int calls;long lastReturnedCursor;
  FakeSource(long... high){for(long h:high)highs.add(h);}
  public long notificationHighWaterMark(int t,int u){calls++;sessions.add(t+":"+u);if(!failures.isEmpty())throw failures.remove();return highs.isEmpty()?0:highs.remove();}
  public NotificationPage listNotifications(int t,int u,NotificationCursor c,int l){calls++;cursors.add(c.afterNotificationId());if(!failures.isEmpty())throw failures.remove();NotificationPage p=pages.isEmpty()?page(List.of(),c.afterNotificationId(),false):pages.remove();lastReturnedCursor=p.nextCursor().afterNotificationId();return p;}
  public List<NotificationSummary> listUnreadNotifications(int t,int u){return List.of();} public int countUnreadNotifications(int t,int u){return 0;} public Optional<NotificationActivationTarget> findActivationTarget(int t,int u,long id){return Optional.empty();} public void markRead(int t,int u,long id){} public void dismiss(int t,int u,long id){}
  public Optional<Long> createTaskAssignedNotification(TaskNotificationCommand c){return Optional.empty();}public Optional<Long> createTaskNoteAddedNotification(TaskNotificationCommand c){return Optional.empty();}public Optional<Long> createTaskDueDateNotification(TaskDueDateNotificationCommand c){return Optional.empty();}public Optional<Long> createTaskActionNotification(TaskActionNotificationCommand c){return Optional.empty();}public Optional<Long> createCalendarEventAssignedNotification(CalendarEventNotificationCommand c){return Optional.empty();}
 }

 private static final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
  final List<Task> tasks=new ArrayList<>();boolean shutdown;long seq;
  public ScheduledFuture<?> schedule(Runnable r,long delay,TimeUnit unit){Task t=new Task(r,unit.toMillis(delay),seq++);tasks.add(t);return t;}
  void runNext(){Task t=tasks.stream().filter(x->!x.cancelled).min(Comparator.comparingLong((Task x)->x.delay).thenComparingLong(x->x.seq)).orElseThrow();tasks.remove(t);t.run();}
  void runAllReady(){while(hasRunnable())runNext();}
  boolean hasRunnable(){return tasks.stream().anyMatch(t->!t.cancelled);}long nextDelay(){return tasks.stream().filter(t->!t.cancelled).mapToLong(t->t.delay).min().orElse(-1);}
  public void shutdown(){shutdown=true;}public List<Runnable> shutdownNow(){shutdown=true;tasks.forEach(t->t.cancel(false));return List.of();}public boolean isShutdown(){return shutdown;}public boolean isTerminated(){return shutdown;}public boolean awaitTermination(long t,TimeUnit u){return true;}public void execute(Runnable r){r.run();}
  public <V> ScheduledFuture<V> schedule(Callable<V> c,long d,TimeUnit u){throw new UnsupportedOperationException();}public ScheduledFuture<?> scheduleAtFixedRate(Runnable r,long a,long b,TimeUnit u){throw new UnsupportedOperationException();}public ScheduledFuture<?> scheduleWithFixedDelay(Runnable r,long a,long b,TimeUnit u){throw new UnsupportedOperationException();}
  final class Task implements ScheduledFuture<Object>{final Runnable r;final long delay,seq;boolean cancelled,done;Task(Runnable r,long d,long s){this.r=r;delay=d;seq=s;}void run(){if(!cancelled){r.run();done=true;}}public long getDelay(TimeUnit u){return u.convert(delay,TimeUnit.MILLISECONDS);}public int compareTo(Delayed d){return Long.compare(getDelay(TimeUnit.MILLISECONDS),d.getDelay(TimeUnit.MILLISECONDS));}public boolean cancel(boolean m){cancelled=true;return true;}public boolean isCancelled(){return cancelled;}public boolean isDone(){return done||cancelled;}public Object get(){return null;}public Object get(long t,TimeUnit u){return null;}}
 }
}
