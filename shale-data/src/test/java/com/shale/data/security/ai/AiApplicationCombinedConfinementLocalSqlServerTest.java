package com.shale.data.security.ai;

import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Permitted reverse installation order is review evidence, never a deployment workaround. */
class AiApplicationCombinedConfinementLocalSqlServerTest extends AiApplicationConfinementLocalSqlServerTest {
    @Override void beforeConfinement(Connection c)throws Exception {sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql")));}
    @Test void unpreparedReverseOrderCannotObtainCaseReadiness()throws Exception {
        try(Connection c=owner(db)){sql(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")));sql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3-verify.sql")));}
        assertThrows(IllegalStateException.class,()->attestation().verify(),"An unsealed registry must not silently fall back to historical Case acceptance.");
    }
}
