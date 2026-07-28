package com.shale.ui.notification;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationCenterServiceReconciliationTest {
	@Test void incrementalMergeDeduplicatesDurableIdsWithoutClearingOrReplacingLocalReadState() {
		NotificationCenterService center=NotificationCenterService.empty();
		AppNotification existing=notification(1,true);
		assertTrue(center.mergeIncrementalOnUiThread(existing));
		assertTrue(center.mergeIncrementalOnUiThread(notification(2,true)));
		center.markRead(existing);
		assertFalse(center.mergeIncrementalOnUiThread(notification(1,true)));
		assertEquals(2,center.getNotificationsNewestFirst().size());
		assertFalse(existing.isUnread());
		assertEquals(1,center.getUnreadCount());
	}

	@Test void dismissedDurableNotificationIsNotRecreatedAndEmptyInputDoesNotClear() {
		NotificationCenterService center=NotificationCenterService.empty();
		AppNotification item=notification(3,true);center.mergeIncrementalOnUiThread(item);center.dismissOnUiThread(item);
		assertTrue(center.getNotificationsNewestFirst().isEmpty());
		assertFalse(center.mergeIncrementalOnUiThread(notification(3,true)));
		center.pushNotifications(java.util.List.of());
		assertTrue(center.getNotificationsNewestFirst().isEmpty());
	}

	@Test void liveThenPollAndPollThenLiveProduceOneEntryAndNoDuplicateObject() {
		NotificationCenterService center=NotificationCenterService.empty();
		AppNotification live=notification(8,true);AppNotification poll=notification(8,true);
		assertTrue(center.mergeIncrementalOnUiThread(live));assertFalse(center.mergeIncrementalOnUiThread(poll));
		assertSame(live,center.getNotificationsNewestFirst().get(0));
		NotificationCenterService reverse=NotificationCenterService.empty();
		assertTrue(reverse.mergeIncrementalOnUiThread(poll));assertFalse(reverse.mergeIncrementalOnUiThread(live));
		assertSame(poll,reverse.getNotificationsNewestFirst().get(0));
	}

	private static AppNotification notification(long id,boolean unread){return new AppNotification("db-"+id,NotificationCategory.TASK,NotificationSeverity.INFO,"title","message",Instant.ofEpochSecond(id),unread,false,NotificationTargetScope.USER_SCOPED,id,"event-"+id);}
}
