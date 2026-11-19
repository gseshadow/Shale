package com.shale.server.mcp;
import com.shale.data.security.ai.*;
import com.shale.server.oauth.McpOAuthProperties;
import javax.sql.DataSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** No SQL claims: proves request authorization is never cached with installation integrity. */
class McpAdmissionVerificationTest {
    @Test void installationIsVerifiedAtConstructionButEveryRequestStillAuthenticatesLive() {
        var source=mock(DataSource.class);var reviewer=mock(DataSource.class);
        var identity=new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,false);
        String token="a".repeat(43);long deadline=System.nanoTime()+10_000_000_000L;
        try(var attestation=mockConstruction(AiCaseDataAttestation.class);var access=mockStatic(AiCaseDataAccess.class)) {
            var data=new McpAiCaseData(source,reviewer,List.of(),"db",identity);
            assertEquals(1,attestation.constructed().size());verify(attestation.constructed().getFirst()).verify();
            var proof=mock(AiCaseDataRequest.class);
            access.when(()->AiCaseDataAccess.authenticate(any(DataSource.class),eq(token),eq(identity.issuer()),eq(identity.resource()),eq(deadline)))
                    .thenReturn(proof).thenThrow(new SecurityException("Synthetic revoked credential"));
            assertSame(proof,data.authenticate(token,deadline));
            assertThrows(SecurityException.class,()->data.authenticate(token,deadline));
            data.requireInstallationReady();
            access.verify(()->AiCaseDataAccess.authenticate(any(DataSource.class),eq(token),eq(identity.issuer()),eq(identity.resource()),eq(deadline)),times(2));
            assertEquals(1,attestation.constructed().size(),"Discovery and repeated RPCs must not reconstruct the catalog verifier");
            data.close();assertThrows(AiInstallationReadiness.Unavailable.class,()->data.authenticate(token,deadline));
            verifyNoInteractions(source,reviewer);
        }
    }
    @Test void failedStartupVerificationCannotPublishAReadyBoundary() {
        try(var ignored=mockConstruction(AiCaseDataAttestation.class,(mock,context)->doThrow(new IllegalStateException("Synthetic drift")).when(mock).verify())) {
            assertThrows(IllegalStateException.class,()->new McpAiCaseData(mock(DataSource.class),mock(DataSource.class),List.of(),"db",new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,false)));
        }
    }
}
