package com.shale.ui.util;

import java.util.IdentityHashMap;

/** Log exception types/frames without messages that JDBC or serializers can fill with contact data. */
public final class SafeDiagnostics {
    private SafeDiagnostics() {}
    public static Throwable forLogging(Throwable failure){return sanitize(failure,new IdentityHashMap<>(),0);}
    private static Throwable sanitize(Throwable failure,IdentityHashMap<Throwable,Boolean> seen,int depth){
        if(failure==null||depth>=20||seen.put(failure,Boolean.TRUE)!=null)return null;
        var diagnostic=new LoggedFailure(failure.getClass().getName(),sanitize(failure.getCause(),seen,depth+1));
        diagnostic.setStackTrace(failure.getStackTrace());
        for(Throwable suppressed:failure.getSuppressed()){Throwable safe=sanitize(suppressed,seen,depth+1);if(safe!=null)diagnostic.addSuppressed(safe);}
        return diagnostic;
    }
    private static final class LoggedFailure extends RuntimeException {
        private static final long serialVersionUID=1L;
        LoggedFailure(String originalClass,Throwable cause){super(originalClass,cause);}
    }
}
