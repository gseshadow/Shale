package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.UserReleaseStateView;
import com.shale.core.model.*;

class UserReleaseStateServiceAdapterTest {
	@Test void delegatesCurrentActorReadAndAcknowledgementWithoutLosingConcurrencyToken(){Fake f=new Fake();var a=new UserReleaseStateServiceAdapter(f);assertTrue(a.findCurrent(7,9,ClientType.DESKTOP,ReleaseChannel.PRODUCTION).isEmpty());byte[] rv={3};var v=a.acknowledge(7,9,ClientType.DESKTOP,ReleaseChannel.PRODUCTION,12,rv);assertEquals(new SemanticVersion(1,0,130),v.version());assertArrayEquals(rv,f.expected);rv[0]=8;assertEquals(3,f.expected[0]);}
	private static final class Fake implements UserReleaseStateServiceAdapter.Gateway {byte[] expected;public Optional<UserReleaseStateView> findCurrent(int t,int u,ClientType c,ReleaseChannel r){return Optional.empty();}public UserReleaseStateView acknowledge(int t,int u,ClientType c,ReleaseChannel r,long id,byte[] rv){expected=rv.clone();return new UserReleaseStateView(1,t,u,c,r,id,new SemanticVersion(1,0,130),Instant.EPOCH,new byte[]{4});}}
}
