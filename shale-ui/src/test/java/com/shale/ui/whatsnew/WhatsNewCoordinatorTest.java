package com.shale.ui.whatsnew;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.*;
import com.shale.core.model.*;
import com.shale.core.service.*;

final class WhatsNewCoordinatorTest {
	private static final Executor DIRECT = Runnable::run;

	@Test void aggregatesSkippedPublishedProductionReleasesThroughRunningVersionInNumericOrder() {
		FakeReleases catalog = new FakeReleases(List.of(
			release(1, "1.0.126", ReleaseChannel.PRODUCTION, PublicationStatus.PUBLISHED),
			release(4, "1.0.125", ReleaseChannel.PILOT, PublicationStatus.PUBLISHED),
			release(3, "1.0.125", ReleaseChannel.PRODUCTION, PublicationStatus.PUBLISHED),
			release(8, "1.0.124", ReleaseChannel.PRODUCTION, PublicationStatus.DRAFT),
			release(2, "1.0.123", ReleaseChannel.PRODUCTION, PublicationStatus.PUBLISHED),
			release(7, "1.0.121", ReleaseChannel.PRODUCTION, PublicationStatus.PUBLISHED)));
		catalog.items.addAll(List.of(item(31,3,0,ReleaseItemType.FIX,true), item(21,2,0,ReleaseItemType.FEATURE,true),
				item(71,7,0,ReleaseItemType.IMPROVEMENT,true)));
		FakeStates states = new FakeStates(state(20, "1.0.120", new byte[]{9}));
		RecordingPresenter presenter = new RecordingPresenter();
		coordinator(catalog, states, "1.0.125", presenter).start(7, 9);

		assertNotNull(presenter.value, "one aggregate dialog should be offered");
		assertEquals(List.of("1.0.121", "1.0.123", "1.0.125"), presenter.value.releases().stream().map(r -> r.version().toString()).toList());
		assertEquals("1.0.125", presenter.value.targetVersion().toString());
		assertEquals(0, states.acknowledgements, "loading or showing must never acknowledge");
		presenter.dismiss.run();
		assertEquals(3, states.targetId, "dismissal advances directly to the highest represented release");
	}

	@Test void firstRunShowsOnlyNewestApplicableReleaseAndPreservesDaoItemOrder() {
		FakeReleases catalog = new FakeReleases(List.of(release(1,"1.0.121",ReleaseChannel.PRODUCTION,PublicationStatus.PUBLISHED),
				release(2,"1.0.125",ReleaseChannel.PRODUCTION,PublicationStatus.PUBLISHED)));
		catalog.items.addAll(List.of(item(22,2,2,ReleaseItemType.LINK,true), item(21,2,1,ReleaseItemType.IMPORTANT,true),
				item(20,2,1,ReleaseItemType.VIDEO,true), item(19,2,0,ReleaseItemType.FIX,false), item(10,1,0,ReleaseItemType.FEATURE,true)));
		RecordingPresenter presenter = new RecordingPresenter(); FakeStates states = new FakeStates(null);
		coordinator(catalog, states, "1.0.125", presenter).start(7,9);
		assertEquals(1, presenter.value.releases().size(), "first deployment must not dump historical notes");
		assertEquals(List.of(20L,21L,22L), presenter.value.releases().get(0).items().stream().map(WhatsNewPresentation.Item::id).toList());
		assertEquals(List.of(ReleaseItemType.VIDEO,ReleaseItemType.IMPORTANT,ReleaseItemType.LINK), presenter.value.releases().get(0).items().stream().map(WhatsNewPresentation.Item::type).toList());
	}

	@Test void firstRunWithEmptyNewestReleaseSilentlyAcknowledgesItAndShowsNothing() {
		FakeReleases catalog = new FakeReleases(List.of(release(1,"1.0.124",ReleaseChannel.PRODUCTION,PublicationStatus.PUBLISHED),
				release(2,"1.0.125",ReleaseChannel.PRODUCTION,PublicationStatus.PUBLISHED)));
		catalog.items.add(item(10,1,0,ReleaseItemType.FEATURE,true));
		RecordingPresenter presenter = new RecordingPresenter(); FakeStates states = new FakeStates(null);
		coordinator(catalog, states, "1.0.125", presenter).start(7,9);
		assertNull(presenter.value);
		assertEquals(2, states.targetId, "successfully evaluated empty content should not repeat forever");
	}

	@Test void emptyCatalogDoesNothingAndCreatesNoState() {
		FakeStates states = new FakeStates(null); RecordingPresenter presenter = new RecordingPresenter();
		coordinator(new FakeReleases(List.of()), states, "1.0.125", presenter).start(7,9);
		assertNull(presenter.value); assertEquals(0, states.acknowledgements);
	}

	@Test void guardRunsOnceUntilLogoutResetAndDismissFailureDoesNotReopen() {
		FakeReleases catalog = catalogWithContent(); FakeStates states = new FakeStates(null); states.failAcknowledge = true;
		RecordingPresenter presenter = new RecordingPresenter(); WhatsNewCoordinator coordinator = coordinator(catalog,states,"1.0.125",presenter);
		coordinator.start(7,9); coordinator.start(7,9); presenter.dismiss.run(); coordinator.start(7,9);
		assertEquals(1, presenter.shows.get(), "failure must wait until a later authenticated launch");
		coordinator.reset(); coordinator.start(7,9);
		assertEquals(2, presenter.shows.get(), "logout permits a fresh evaluation");
	}

	@Test void staleAcknowledgementAlreadySupersededIsTreatedAsSuccess() {
		FakeReleases catalog = catalogWithContent(); FakeStates states = new FakeStates(null);
		states.failAcknowledge = true; states.afterFailure = state(99,"1.0.130",new byte[]{2});
		RecordingPresenter presenter = new RecordingPresenter();
		assertDoesNotThrow(() -> { coordinator(catalog,states,"1.0.125",presenter).start(7,9); presenter.dismiss.run(); });
		assertEquals(2, states.reads, "a failed optimistic write must reload durable state once");
	}

	@Test void readAndVersionFailuresAreNonBlockingAndNeverPresentOrAcknowledge() {
		for (Failure failure : Failure.values()) {
			FakeReleases catalog = catalogWithContent(); FakeStates states = new FakeStates(null);
			String version = "1.0.125";
			if (failure == Failure.STATE) states.failRead = true;
			if (failure == Failure.CATALOG) catalog.failRead = true;
			if (failure == Failure.ITEMS) catalog.failItems = true;
			if (failure == Failure.VERSION) version = "not-a-version";
			RecordingPresenter presenter = new RecordingPresenter();
			String resolvedVersion = version;
			assertDoesNotThrow(() -> coordinator(catalog,states,resolvedVersion,presenter).start(7,9), failure.name());
			assertNull(presenter.value, failure.name()); assertEquals(0, states.acknowledgements, failure.name());
		}
	}

	private enum Failure { STATE, CATALOG, ITEMS, VERSION }
	private static WhatsNewCoordinator coordinator(FakeReleases r, FakeStates s, String version, RecordingPresenter p) {
		return new WhatsNewCoordinator(r,s,()->version,DIRECT,Runnable::run,p);
	}
	private static FakeReleases catalogWithContent() { FakeReleases f=new FakeReleases(List.of(release(2,"1.0.125",ReleaseChannel.PRODUCTION,PublicationStatus.PUBLISHED))); f.items.add(item(1,2,0,ReleaseItemType.FEATURE,true)); return f; }
	private static ApplicationReleaseView release(long id,String v,ReleaseChannel c,PublicationStatus s){return new ApplicationReleaseView(id,SemanticVersion.parse(v),c,s,Instant.EPOCH,"Summary "+v,new byte[]{1});}
	private static ApplicationReleaseItemView item(long id,long release,int order,ReleaseItemType type,boolean active){return new ApplicationReleaseItemView(id,release,order,type,"Title "+id,"Body "+id,"https://example.com/"+id,active);}
	private static UserReleaseStateView state(long id,String version,byte[] rv){return new UserReleaseStateView(id,7,9,ClientType.DESKTOP,ReleaseChannel.PRODUCTION,id,SemanticVersion.parse(version),Instant.EPOCH,rv);}

	private static final class RecordingPresenter implements WhatsNewCoordinator.Presenter { WhatsNewPresentation value; Runnable dismiss; AtomicInteger shows=new AtomicInteger(); public void show(WhatsNewPresentation p,Runnable d){value=p;dismiss=d;shows.incrementAndGet();} }
	private static final class FakeReleases implements ApplicationReleaseReadServicePort {
		final List<ApplicationReleaseView> values; final List<ApplicationReleaseItemView> items=new ArrayList<>(); boolean failRead,failItems;
		FakeReleases(List<ApplicationReleaseView> values){this.values=values;}
		public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel c){return Optional.empty();}
		public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel c,SemanticVersion v){if(failRead)throw new IllegalStateException("offline");return values;}
		public List<ApplicationReleaseItemView> listReleaseItems(long id){if(failItems)throw new IllegalStateException("offline");return items.stream().filter(i->i.releaseId()==id).toList();}
	}
	private static final class FakeStates implements UserReleaseStateServicePort {
		UserReleaseStateView current,afterFailure; boolean failRead,failAcknowledge; int reads,acknowledgements; long targetId;
		FakeStates(UserReleaseStateView current){this.current=current;}
		public Optional<UserReleaseStateView> findCurrent(int t,int u,ClientType c,ReleaseChannel r){reads++;if(failRead)throw new IllegalStateException("offline");if(afterFailure!=null&&acknowledgements>0)return Optional.of(afterFailure);return Optional.ofNullable(current);}
		public UserReleaseStateView acknowledge(int t,int u,ClientType c,ReleaseChannel r,long id,byte[] rv){acknowledgements++;targetId=id;if(failAcknowledge)throw new IllegalStateException("stale");return state(id,"1.0.125",new byte[]{3});}
	}
}
