package com.shale.data.security.ai;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.Test;
import javax.net.SocketFactory;
import java.io.*;
import java.net.*;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

/** Pinned-driver characterization, entirely in memory. No TLS handshake, server or network connection. */
public class AiCaseDataAzureSqlDriverTimeoutTest {
    private static final ThreadLocal<MemorySocket> LAST=new ThreadLocal<>();

    @Test void actualDatasourceConnectClampsInitialReadsToLoginBudget()throws Exception {
        assertInitialReadBound(15,120_000,15_000);
        assertInitialReadBound(30,120_000,30_000);
    }
    @Test void smallerFiniteSocketBudgetStillWinsDuringLogin()throws Exception {
        assertInitialReadBound(30,777,777);
        assertEquals(777,LAST.get().observedReadTimeout);
    }
    private static void assertInitialReadBound(int login,int socket,int upper)throws Exception {
        var source=new SQLServerDataSource();
        // Literal loopback is only resolved locally. The factory supplies an already-connected
        // in-memory socket; connect() is a fail-fast assertion and never calls Socket.connect.
        source.setURL("jdbc:sqlserver://127.0.0.1:1;databaseName=synthetic");
        source.setUser("synthetic");source.setPassword("synthetic");
        source.setEncrypt("true");source.setTrustServerCertificate(false);
        source.setLoginTimeout(login);source.setSocketTimeout(socket);
        source.setQueryTimeout(20);source.setCancelQueryTimeout(5);
        source.setTransparentNetworkIPResolution(false);source.setConnectRetryCount(0);
        source.setSocketFactoryClass(MemorySocketFactory.class.getName());
        LAST.remove();
        var failure=assertThrows(SQLException.class,source::getConnection);
        assertNotNull(LAST.get(),"Driver must use the in-memory factory");
        int actual=LAST.get().observedReadTimeout;
        assertTrue(actual>0&&actual<=upper,"Initial read must use the smaller finite socket/login budget; actual="+actual);
        assertInstanceOf(SocketTimeoutException.class,failure.getCause());
    }
    @Test void openedConnectionRetainsClampedSocketUntilExplicitlyRestored()throws Exception {
        // Invoke the real TDSChannel.open/getNetworkTimeout/setNetworkTimeout methods.
        // Synthetic state transition characterizes the socket; it is not a successful login.
        Class<?> type=Class.forName("com.microsoft.sqlserver.jdbc.SQLServerConnection");
        var constructor=type.getDeclaredConstructor(String.class);constructor.setAccessible(true);
        var connection=(java.sql.Connection)constructor.newInstance("offline-timeout-characterization");
        field(type,connection,"socketTimeoutMilliseconds",120_000);
        field(type,connection,"socketFactoryClass",MemorySocketFactory.class.getName());
        field(type,connection,"timerExpire",System.currentTimeMillis()+15_000);
        Class<?> channelType=Class.forName("com.microsoft.sqlserver.jdbc.TDSChannel");
        var channelConstructor=channelType.getDeclaredConstructor(type);channelConstructor.setAccessible(true);
        Object channel=channelConstructor.newInstance(connection);
        field(type,connection,"tdsChannel",channel);
        var open=channelType.getDeclaredMethod("open",String.class,int.class,int.class,boolean.class,boolean.class,boolean.class,int.class,String.class);
        open.setAccessible(true);
        open.invoke(channel,"127.0.0.1",1,15_000,false,false,false,15_000,"UsePlatformDefault");
        var state=type.getDeclaredField("state");state.setAccessible(true);
        Object opened=java.util.Arrays.stream(state.getType().getEnumConstants()).filter(e->((Enum<?>)e).name().equals("OPENED")).findFirst().orElseThrow();
        state.set(connection,opened);
        int clamped=connection.getNetworkTimeout();
        assertTrue(clamped>0&&clamped<=15_000,"OPENED state alone must not be mistaken for a restored 120-second socket");
        connection.setNetworkTimeout(null,120_000);
        assertEquals(120_000,connection.getNetworkTimeout());
        LAST.get().close();
    }
    private static void field(Class<?> type,Object target,String name,Object value)throws Exception {
        var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    public static class MemorySocketFactory extends SocketFactory {
        public MemorySocketFactory(){}
        @Override public Socket createSocket(){var socket=new MemorySocket();LAST.set(socket);return socket;}
        @Override public Socket createSocket(String h,int p){throw new AssertionError("No network overload allowed");}
        @Override public Socket createSocket(String h,int p,InetAddress l,int lp){throw new AssertionError("No network overload allowed");}
        @Override public Socket createSocket(InetAddress h,int p){throw new AssertionError("No network overload allowed");}
        @Override public Socket createSocket(InetAddress h,int p,InetAddress l,int lp){throw new AssertionError("No network overload allowed");}
    }
    private static class MemorySocket extends Socket {
        private int timeout,observedReadTimeout=-1;
        @Override public boolean isConnected(){return true;}
        @Override public void connect(SocketAddress address,int timeout){throw new AssertionError("No network connection permitted");}
        @Override public void connect(SocketAddress address){throw new AssertionError("No network connection permitted");}
        @Override public void setTcpNoDelay(boolean on){}
        @Override public void setKeepAlive(boolean on){}
        @Override public <T> Socket setOption(SocketOption<T> name,T value){return this;}
        @Override public synchronized void setSoTimeout(int timeout){this.timeout=timeout;}
        @Override public synchronized int getSoTimeout(){return timeout;}
        @Override public SocketAddress getRemoteSocketAddress(){return new InetSocketAddress("127.0.0.1",1);}
        @Override public InputStream getInputStream(){return new InputStream(){
            @Override public int read()throws IOException {observedReadTimeout=timeout;throw new SocketTimeoutException("Synthetic in-memory read timeout");}
        };}
        @Override public OutputStream getOutputStream(){return OutputStream.nullOutputStream();}
    }
}
