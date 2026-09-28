package com.shale.data.service.adapter;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.*;
import com.shale.core.model.*;
class ApplicationReleaseReadServiceAdapterTest {
	@Test void delegatesReadsAndPreservesEmptyPolicyAndSemanticVersions(){Fake f=new Fake();var a=new ApplicationReleaseReadServiceAdapter(f);assertTrue(a.findCurrentPolicy(ReleaseChannel.PRODUCTION).isEmpty());var releases=a.listPublishedReleasesAfter(ReleaseChannel.PRODUCTION,new SemanticVersion(1,0,99));assertEquals(new SemanticVersion(1,0,130),releases.get(0).version());assertEquals(7,a.listReleaseItems(4).get(0).id());assertEquals(4,f.itemId);}
	private static final class Fake implements ApplicationReleaseReadServiceAdapter.Gateway {long itemId;public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel c){return Optional.empty();}public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel c,SemanticVersion v){return List.of(new ApplicationReleaseView(2,new SemanticVersion(1,0,130),c,PublicationStatus.PUBLISHED,Instant.EPOCH,"summary",new byte[]{1}));}public List<ApplicationReleaseItemView> listReleaseItems(long id){itemId=id;return List.of(new ApplicationReleaseItemView(7,id,0,ReleaseItemType.FIX,"t","b",null,true));}}
}
