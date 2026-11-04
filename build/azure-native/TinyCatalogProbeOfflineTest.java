import java.lang.reflect.*;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** No driver, DNS, Azure SDK or credentials: execute the real probe using JDBC proxies. */
public class TinyCatalogProbeOfflineTest {
    static void require(boolean value) { if (!value) throw new AssertionError("Offline contract failed"); }
    static final String SECRET = "CANARY credential target SQL SID cause suppressed";
    static Object stub(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        return null;
    }
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    static SQLException fail(String state, int code) { return new SQLException(SECRET, state, code); }
    static final class Fake {
        List<String> sql = new ArrayList<>();
        int opened, queries, timeoutCalls, closed;
        boolean setupFailure, closeFailure;
        int failCheck = -1;
        Throwable failure;
        Connection open(boolean owner) {
            int index = owner ? -1 : opened++;
            return proxy(Connection.class, (p,m,a) -> switch (m.getName()) {
                case "createStatement" -> statement(owner, index);
                case "isClosed" -> index == failCheck && failure instanceof SQLException e && "08S01".equals(e.getSQLState());
                case "close" -> { closed++; if(closeFailure) throw fail("",0); yield null; }
                default -> stub(m.getReturnType());
            });
        }
        Statement statement(boolean owner, int index) {
            int[] ordinal = {0};
            return proxy(Statement.class, (p,m,a) -> {
                switch(m.getName()) {
                    case "setQueryTimeout": require((int)a[0] == 20); timeoutCalls++; return null;
                    case "execute":
                        sql.add((String)a[0]);
                        if(owner) { if(setupFailure) throw fail("",229); return false; }
                        queries++; ordinal[0] = 1;
                        if(index == failCheck) throw failure;
                        return true;
                    case "getMoreResults": return index == 5 && ++ordinal[0] <= 19;
                    case "getUpdateCount": return -1;
                    case "getResultSet":
                        int n = ordinal[0]; int[] calls = {0};
                        return proxy(ResultSet.class, (r,rm,ra) -> switch(rm.getName()) {
                            case "next" -> ++calls[0] == 1;
                            case "getInt" -> n;
                            case "getLong" -> index == 2 ? 6L : 1L;
                            case "getString" -> "A".repeat(64);
                            default -> stub(rm.getReturnType());
                        });
                    default: return stub(m.getReturnType());
                }
            });
        }
    }
    public static void main(String[] args) throws Exception {
        // Driver messages and entire exception trees are toxic input, never output.
        var socket = fail("08S01",0); socket.initCause(new SocketTimeoutException(SECRET));
        var cases = new LinkedHashMap<Throwable,String>();
        cases.put(socket,"SOCKET_TIMEOUT"); cases.put(fail("08S01",0),"OTHER_SANITIZED");
        cases.put(new SQLTimeoutException(SECRET),"QUERY_TIMEOUT");
        for(String state: List.of("HY008","HYT00","HYT01")) cases.put(fail(state,0),"QUERY_TIMEOUT");
        for(int code: List.of(229,230,262,297,300,916)) cases.put(fail("",code),"PERMISSION");
        for(int code: List.of(207,208,209,4104,4121)) cases.put(fail("",code),"BINDING");
        cases.put(fail("",99999),"OTHER_SANITIZED"); cases.put(new IllegalStateException(SECRET),"OTHER_SANITIZED");
        for(var entry: cases.entrySet()) { entry.getKey().addSuppressed(new Exception(SECRET)); require(TinyCatalogProbe.category(entry.getKey()).equals(entry.getValue())); }
        var chain = fail("",0); chain.setNextException(socket);
        require(TinyCatalogProbe.category(chain).equals("OTHER_SANITIZED"));
        String batch = Files.readString(Path.of(args[0]));
        var checks = TinyCatalogProbe.checks(batch);
        require(checks.size()==6 && checks.get(5).sql().equals(batch));
        require(batch.startsWith(checks.get(4).sql()));
        for(int failing=-1; failing<6; failing++) {
            Fake fake = new Fake(); fake.failCheck=failing; fake.failure=socket;
            List<String> evidence = new ArrayList<>();
            TinyCatalogProbe.run(fake::open,batch,"Aa1!"+"a".repeat(64),evidence::add);
            require(fake.opened==6 && fake.queries==6 && fake.closed==7 && evidence.size()==6);
            require(fake.sql.size()==23 && fake.timeoutCalls==23);
            for(int i=0;i<6;i++) {
                String[] parts=evidence.get(i).split("\t");
                require(parts.length==4 && parts[0].equals(TinyCatalogProbe.IDS.get(i)));
                require(parts[1].equals(i==failing ? "SOCKET_TIMEOUT" : "PASS"));
                require(parts[2].matches("[0-9]+") && parts[3].equals("connectionClosed="+(i==failing)));
                require(!evidence.get(i).contains(SECRET));
            }
            // Owner executes only synthetic DDL/seeds/grants, never any catalog read.
            for(String text: fake.sql.subList(0,17)) require(!text.startsWith("SELECT") && !text.contains("FROM sys."));
        }
        Fake setup = new Fake(); setup.setupFailure=true;
        List<String> evidence=new ArrayList<>();
        TinyCatalogProbe.run(setup::open,batch,"Aa1!"+"b".repeat(64),evidence::add);
        require(setup.opened==0 && setup.closed==1 && evidence.size()==1 && evidence.getFirst().startsWith("SETUP\tPERMISSION\t"));
        Map<String,String> env=Map.of("TINY_PROBE_OWNER","owner","TINY_PROBE_OWNER_PASSWORD","private","TINY_PROBE_PASSWORD","independent");
        Properties props=TinyCatalogProbe.properties(false,env);
        require(props.getProperty("socketTimeout").equals("5000") && props.getProperty("connectRetryCount").equals("0"));
        require(props.getProperty("user").equals("catalog_probe") && props.getProperty("password").equals("independent"));
        require(props.getProperty("encrypt").equals("true") && props.getProperty("trustServerCertificate").equals("false"));
        System.out.println("TINY_CATALOG_OFFLINE PASS azureExecuted=false");
    }
}
