package connections;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionResources {
	public static String user = "Prime";
	public static String password = "XIYaxZdR5HcZsdqZAmoTLjkQGQIbaK7C";
	public static String targetDatabase = "Shale";
	public static String url = "jdbc:sqlserver://shale.database.windows.net:1433;database= Shale";
	private static Connection connection;

	public static Connection getConnection() {

		try {
			connection = DriverManager.getConnection(url, user, password);
			return connection;
		} catch (SQLException e) {
			/*
			 * Need to add error display here to show connection to server has been lost
			 */
			e.printStackTrace();
			return null;
		}
	}
}
