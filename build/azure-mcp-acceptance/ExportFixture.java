package com.shale.data.verification;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline only. Uses the existing parser unchanged; no JDBC, process or network API. */
public final class ExportFixture {
    static final String HASH="4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f";
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("fixture path and new output path required");
        // Parser checks exact bytes/hash/size and rejects data, USE, SQLCMD directives and unsafe DDL.
        var batches=CaseReadVerificationSafety.schema(Path.of(args[0]),HASH);
        Files.writeString(Path.of(args[1]),render(batches),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        System.out.println("PASS authoritative DDL parser; batches="+batches.size());
    }
    static String render(List<String> batches) {
        StringBuilder out=new StringBuilder(":on error exit\n"
                + ":r docs/sql/ai-integration/azure-acceptance/target.sql\n"
                + ":r docs/sql/ai-integration/azure-acceptance/owner.sql\nGO\n"
                + ":r docs/sql/ai-integration/azure-acceptance/empty.sql\nGO\n");
        for(String batch:batches) {
            if(CaseReadVerificationSafety.mask(batch).stripLeading().toUpperCase(Locale.ROOT).startsWith("SET ")) {
                out.append(batch).append("\nGO\n");
            } else {
                // Preserve exact module-definition bytes: a SQLCMD separator newline must not
                // become part of CREATE FUNCTION source. No hand normalization/re-hashing.
                out.append("DECLARE @FixtureBatch nvarchar(max)=CONVERT(nvarchar(max),0x")
                   .append(HexFormat.of().formatHex(batch.getBytes(StandardCharsets.UTF_16LE)))
                   .append("); EXEC sys.sp_executesql @FixtureBatch;\nGO\n");
            }
        }
        return out.toString();
    }
}
