package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import java.util.Objects;

/** Durable cross-instance admission; a separate explicit execute-only endpoint capability. */
public final class AiDcrAdmissionStore {
    private final DbSessionProvider db;
    public AiDcrAdmissionStore(DbSessionProvider db){this.db=Objects.requireNonNull(db);}
    public record Admission(boolean allowed,int retryAfter) {}
    public Admission admit(byte[] sourceHash) {
        if(sourceHash==null||sourceHash.length!=32)throw new IllegalArgumentException("Source digest required.");
        return AiSql.one(AiSql.call(db,"AiDcrAdmissionV3",r->new Admission(r.getBoolean("Allowed"),r.getInt("RetryAfter")),sourceHash,null));
    }
    public boolean enabled(){return AiSql.one(AiSql.call(db,"AiDcrStatusV3",r->r.getBoolean("Enabled")));}
}
