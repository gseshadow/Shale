package com.shale.server.mcp;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class McpInstallationMonitorTest {
    @Test void backgroundRefreshDoesNotBlockStartupAndCloseStopsFurtherWork() {
        var executor=mock(ScheduledExecutorService.class);var verify=new AtomicInteger();var withdraw=new AtomicInteger();
        var monitor=new McpInstallationMonitor(verify::incrementAndGet,withdraw::incrementAndGet,executor);
        var task=ArgumentCaptor.forClass(Runnable.class);
        verify(executor).scheduleWithFixedDelay(task.capture(),eq(5L),eq(5L),eq(TimeUnit.MINUTES));
        assertEquals(0,verify.get());task.getValue().run();assertEquals(1,verify.get());assertEquals(0,withdraw.get());
        monitor.close();task.getValue().run();assertEquals(1,verify.get());assertEquals(1,withdraw.get());
        verify(executor).shutdownNow();
    }
    @Test void failedRefreshWithdrawsOnceAndNeverAutomaticallyReopensTheWorker() {
        var executor=mock(ScheduledExecutorService.class);var withdraw=new AtomicInteger();var calls=new AtomicInteger();
        new McpInstallationMonitor(()->{calls.incrementAndGet();throw new IllegalStateException("PRIVATE_SENTINEL");},withdraw::incrementAndGet,executor);
        var task=ArgumentCaptor.forClass(Runnable.class);verify(executor).scheduleWithFixedDelay(task.capture(),anyLong(),anyLong(),any());
        task.getValue().run();task.getValue().run();assertEquals(1,calls.get());assertEquals(1,withdraw.get());verify(executor).shutdownNow();
    }
    @Test void schedulerFailureCannotLeaveAnUnmonitoredReadyWorker() {
        var executor=mock(ScheduledExecutorService.class);var withdraw=new AtomicInteger();
        when(executor.scheduleWithFixedDelay(any(),anyLong(),anyLong(),any())).thenThrow(new RejectedExecutionException());
        assertThrows(RejectedExecutionException.class,()->new McpInstallationMonitor(()->{},withdraw::incrementAndGet,executor));
        assertEquals(1,withdraw.get());verify(executor).shutdownNow();
    }
    @Test void externalRefreshVerifiesItsPredecessorOnceAndRenewsBothBoundaries() {
        var legacy=mock(McpAiCaseData.class);var external=mock(com.shale.data.security.ai.AiCimdCaseData.class);
        var provider=mock(org.springframework.beans.factory.ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(external);
        try(var monitor=new McpTransportConfiguration().mcpInstallationMonitor(legacy,provider)) {
            monitor.refresh();verify(external).ready(anyLong());verify(legacy).verifiedByExternalRefresh();
            verify(legacy,never()).ready(anyLong());
        }
        verify(legacy).close();verify(external).close();
    }
    @Test void externalIntegrityFailureWithdrawsLegacyAsWellWithoutNamespaceFallback() {
        var legacy=mock(McpAiCaseData.class);var external=mock(com.shale.data.security.ai.AiCimdCaseData.class);
        var provider=mock(org.springframework.beans.factory.ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(external);
        doThrow(new IllegalStateException("Synthetic drift")).when(external).ready(anyLong());
        try(var monitor=new McpTransportConfiguration().mcpInstallationMonitor(legacy,provider)) {
            monitor.refresh();monitor.refresh();verify(external,times(1)).ready(anyLong());
            verify(legacy).close();verify(external).close();verify(legacy,never()).verifiedByExternalRefresh();
        }
    }

}
