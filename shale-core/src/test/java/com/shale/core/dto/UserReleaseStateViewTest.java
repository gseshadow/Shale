package com.shale.core.dto;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.shale.core.model.*;
class UserReleaseStateViewTest {@Test void rowVersionIsDefensivelyCopied(){byte[] token={1};var v=new UserReleaseStateView(1,7,9,ClientType.DESKTOP,ReleaseChannel.PRODUCTION,2,new SemanticVersion(1,0,130),Instant.EPOCH,token);token[0]=2;assertEquals(1,v.rowVersion()[0]);byte[] returned=v.rowVersion();returned[0]=3;assertEquals(1,v.rowVersion()[0]);}}
