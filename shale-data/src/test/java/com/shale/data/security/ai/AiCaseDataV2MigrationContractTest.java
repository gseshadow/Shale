package com.shale.data.security.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiCaseDataV2MigrationContractTest {
    @Test void separateV2SourceAndAllThirteenUnchangedModuleBodiesMatchPackagedReceipt()throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql"));
        var source=Pattern.compile("@sourceHash varbinary\\(32\\)=0x([0-9a-f]{64})").matcher(sql);assertTrue(source.find());
        String zeroed=sql.substring(0,source.start(1))+"0".repeat(64)+sql.substring(source.end(1));
        assertEquals(source.group(1),HexFormat.of().formatHex(AiSql.hash(zeroed.getBytes(StandardCharsets.UTF_8))));
        var v1=new Properties();var v2=new Properties();
        try(var a=getClass().getResourceAsStream("/ai/confinement-modules.properties");var b=getClass().getResourceAsStream("/ai/confinement-v2-modules.properties")){v1.load(a);v2.load(b);}
        assertEquals(source.group(1),v2.remove("Source"));assertNotEquals(v1.remove("Source"),source.group(1));assertEquals(v1,v2);
        var modules=Pattern.compile("SET @InstallDefinition=CONVERT\\(nvarchar\\(max\\),0x([a-f0-9]+)\\)").matcher(sql);int count=0;
        while(modules.find()){
            byte[] bytes=HexFormat.of().parseHex(modules.group(1));String body=new String(bytes,StandardCharsets.UTF_16LE);
            var name=Pattern.compile("CREATE (?:PROCEDURE|FUNCTION|TRIGGER) dbo\\.(\\w+)").matcher(body);assertTrue(name.find());
            assertEquals(v2.remove(name.group(1)),HexFormat.of().formatHex(AiSql.hash(bytes)),name.group(1));count++;
        }
        assertEquals(13,count);assertTrue(v2.isEmpty());
        assertEquals(Files.readString(Path.of("../docs/sql/ai-integration/phase3b8-confinement-verify.sql")),Files.readString(Path.of("src/main/resources/ai/confinement-v2-catalog.sql")));
        assertFalse(sql.contains("CREATE LOGIN"));assertFalse(sql.contains("ADD MEMBER"));assertFalse(sql.contains("Shale.AiCaseDataV1"));
        assertEquals(6,sql.split("GRANT EXECUTE ON OBJECT::dbo\\.AiData",-1).length-1);
    }
}
