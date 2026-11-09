import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** No JDBC/network. Run with the production JDK and equivalent effective timezone inputs.
 * A new process is corroborating evidence, not proof of another running JVM's default zone. */
class BindingTimeZoneProbe {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.systemDefault();
        System.out.println("-- Observed JavaTimeZone=" + zone.getId());
        System.out.println("-- JDK=" + System.getProperty("java.runtime.version"));
        var format = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
        for (int year = 2025; year <= 2028; year++) {
            for (int month : List.of(1, 3, 4, 7, 10, 11, 12)) {
                Instant instant = LocalDate.of(year, month, 15).atStartOfDay(ZoneOffset.UTC).toInstant();
                System.out.printf("INSERT @BindWallTimes VALUES('%s','%s');%n",
                    format.format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC)),
                    format.format(LocalDateTime.ofInstant(instant, zone)));
            }
        }
    }
}
