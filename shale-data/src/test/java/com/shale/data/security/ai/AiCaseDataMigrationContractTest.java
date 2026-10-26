package com.shale.data.security.ai;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Build/source receipt checks supplement the actual-login migration, parity and attack tests. */
class AiCaseDataMigrationContractTest {
    @Test void additiveSourceAndEveryInstalledModuleMatchPackagedAttestation()throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/ai-integration/phase3b7-confinement.sql")).replace("\r\n","\n");
        var source=Pattern.compile("@sourceHash varbinary\\(32\\)=0x([0-9a-f]{64})").matcher(sql);assertTrue(source.find());
        String zeroed=sql.substring(0,source.start(1))+"0".repeat(64)+sql.substring(source.end(1));
        assertEquals(source.group(1),HexFormat.of().formatHex(AiSql.hash(zeroed.getBytes(StandardCharsets.UTF_8))));
        var manifest=new Properties();try(var in=getClass().getResourceAsStream("/ai/confinement-modules.properties")){manifest.load(in);}assertEquals(source.group(1),manifest.remove("Source"));
        int modules=0,publicModules=0,position=0;
        while((position=sql.indexOf("EXEC(N'CREATE ",position))>=0) {
            int start=position+7,end=start;
            while(end<sql.length()) {
                if(sql.charAt(end)=='\'') {if(end+1<sql.length()&&sql.charAt(end+1)=='\''){end+=2;continue;}break;}
                end++;
            }
            String body=sql.substring(start,end).replace("''","'");position=end+1;
            var name=Pattern.compile("CREATE (?:PROCEDURE|FUNCTION|TRIGGER) dbo\\.(\\w+)").matcher(body);if(!name.find())continue;
            assertEquals(manifest.remove(name.group(1)),HexFormat.of().formatHex(AiSql.hash(body.getBytes(StandardCharsets.UTF_16LE))),name.group(1));modules++;
            if(sql.contains("GRANT EXECUTE ON OBJECT::dbo."+name.group(1)+" TO AiCaseDataRuntime")) {
                publicModules++;assertTrue(body.contains("@ProofDigest varbinary(max)"));assertTrue(body.indexOf("DATALENGTH(@ProofDigest)<>32")<body.indexOf("CONVERT(binary(32),@ProofDigest)"));
                assertFalse(body.contains("EXECUTE AS OWNER"));assertFalse(body.contains("sp_executesql"));
            }
        }
        assertEquals(13,modules);assertEquals(6,publicModules);assertTrue(manifest.isEmpty());
        assertFalse(sql.contains("CREATE LOGIN"));assertFalse(sql.contains("ADD MEMBER"));assertFalse(sql.contains("GRANT SELECT ON OBJECT::dbo.Cases"));assertFalse(sql.contains("ALTER PROCEDURE"));
        assertEquals(Files.readString(Path.of("../docs/sql/ai-integration/phase3b7-confinement-verify.sql")),Files.readString(Path.of("src/main/resources/ai/confinement-catalog.sql")));
    }
}
