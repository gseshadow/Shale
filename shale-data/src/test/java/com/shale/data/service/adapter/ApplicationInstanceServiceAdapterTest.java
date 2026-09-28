package com.shale.data.service.adapter;
import static org.junit.jupiter.api.Assertions.*;import java.time.Instant;import java.util.UUID;import org.junit.jupiter.api.Test;import com.shale.core.dto.ApplicationInstanceView;import com.shale.core.model.*;
class ApplicationInstanceServiceAdapterTest{
	@Test void delegatesEnrollmentAndEndWithoutChangingAuthenticatedScope(){var g=new Gateway();var a=new ApplicationInstanceServiceAdapter(g);var m=UUID.randomUUID();var v=new SemanticVersion(1,0,130);assertEquals(5,a.enroll(7,9,m,ClientType.DESKTOP,v).id());assertEquals(7,g.t);assertEquals(9,g.u);assertSame(v,g.v);assertNotNull(a.end(7,9,5).endedAt());assertEquals(5,g.id);}
	static final class Gateway implements ApplicationInstanceServiceAdapter.Gateway{int t,u;long id;SemanticVersion v;UUID m;public ApplicationInstanceView enroll(int t,int u,UUID m,ClientType c,SemanticVersion v){this.t=t;this.u=u;this.m=m;this.v=v;return new ApplicationInstanceView(5,m,c,v,Instant.EPOCH,null);}public ApplicationInstanceView end(int t,int u,long id){this.id=id;return new ApplicationInstanceView(id,m,ClientType.DESKTOP,v,Instant.EPOCH,Instant.EPOCH.plusSeconds(1));}}
}
