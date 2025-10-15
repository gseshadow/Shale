package connections;

public class SQLCorrect {

	public static String getString(String value) {

		String sqlValue = value.replace("'", "''");

		return sqlValue;

	}
}
