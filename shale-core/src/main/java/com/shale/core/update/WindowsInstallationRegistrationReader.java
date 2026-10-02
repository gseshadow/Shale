package com.shale.core.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only, fail-closed view of the MSI-owned HKLM installation authority. */
public final class WindowsInstallationRegistrationReader {
	public static final String REGISTRY_LOCATION = "HKLM\\SOFTWARE\\Shale\\Installations";

	public enum Classification { VALID, STALE, INVALID_SCHEMA, INVALID_OWNER, INVALID_PATH,
		DUPLICATE_INSTALLATION_ID, REPARSE_UNSAFE, UNAVAILABLE }
	public record Result(Classification classification, WindowsInstallationRegistration registration) {}
	@FunctionalInterface public interface Source { List<Map<String, String>> records() throws IOException; }
	@FunctionalInterface public interface PathInspection { State inspect(Path path); }
	public enum State { PRESENT, MISSING, REPARSE_UNSAFE, UNAVAILABLE }

	private final Source source;
	private final PathInspection paths;
	public WindowsInstallationRegistrationReader(Source source, PathInspection paths) {
		this.source=source; this.paths=paths;
	}

	public List<Result> read() {
		final List<Map<String,String>> raw;
		try { raw=List.copyOf(source.records()); }
		catch (RuntimeException | IOException failure) { return List.of(new Result(Classification.UNAVAILABLE,null)); }
		List<Result> results=new ArrayList<>(); Map<UUID,List<Integer>> positions=new HashMap<>();
		for (Map<String,String> values:raw) {
			Result result=parse(Map.copyOf(values)); results.add(result);
			if (result.registration()!=null) positions.computeIfAbsent(result.registration().installationId(), ignored->new ArrayList<>()).add(results.size()-1);
		}
		for (List<Integer> duplicates:positions.values()) if (duplicates.size()>1)
			for (int index:duplicates) results.set(index,new Result(Classification.DUPLICATE_INSTALLATION_ID,results.get(index).registration()));
		return List.copyOf(results);
	}

	private Result parse(Map<String,String> value) {
		if (!"1".equals(value.get("schemaVersion"))) return result(Classification.INVALID_SCHEMA);
		final UUID installationId;
		try { installationId=UUID.fromString(value.get("installationId")); }
		catch (NullPointerException | IllegalArgumentException invalid) { return result(Classification.INVALID_SCHEMA); }
		if (value.containsKey("_recordKey") && !installationId.toString().equalsIgnoreCase(value.get("_recordKey"))) return result(Classification.INVALID_SCHEMA);
		if (value.get("ownerSid")==null || !value.get("ownerSid").matches("S-1-(?:\\d+-){1,14}\\d+")) return result(Classification.INVALID_OWNER);
		final WindowsInstallationRegistration registration;
		try { registration=new WindowsInstallationRegistration(1,installationId,value.get("ownerSid"),Path.of(value.get("installRoot")),Path.of(value.get("supportRoot"))); }
		catch (NullPointerException | IllegalArgumentException invalid) {
			return result(Classification.INVALID_PATH);
		}
		String install=windows(registration.installRoot()), support=windows(registration.supportRoot());
		if (!install.equals(support) || !install.matches("^[a-z]:\\\\users\\\\[^\\\\]+\\\\appdata\\\\local\\\\shale$")) return new Result(Classification.INVALID_PATH,registration);
		State installState=paths.inspect(registration.installRoot()), supportState=paths.inspect(registration.supportRoot());
		if (installState==State.REPARSE_UNSAFE || supportState==State.REPARSE_UNSAFE) return new Result(Classification.REPARSE_UNSAFE,registration);
		if (installState==State.MISSING || supportState==State.MISSING) return new Result(Classification.STALE,registration);
		if (installState==State.UNAVAILABLE || supportState==State.UNAVAILABLE) return new Result(Classification.UNAVAILABLE,registration);
		return new Result(Classification.VALID,registration);
	}
	private static Result result(Classification classification) { return new Result(classification,null); }
	private static String windows(Path path) { return path.toString().replace('/','\\').toLowerCase(Locale.ROOT); }

	/** Production filesystem inspection; every existing ancestor is rejected if it is a link/reparse-like object. */
	public static State inspectPath(Path path) {
		try {
			if (!Files.exists(path)) return State.MISSING;
			for (Path cursor=path;cursor!=null;cursor=cursor.getParent())
				if (Files.isSymbolicLink(cursor) || Files.readAttributes(cursor,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS).isOther()) return State.REPARSE_UNSAFE;
			return State.PRESENT;
		} catch (RuntimeException | IOException failure) { return State.UNAVAILABLE; }
	}

	/** HKLM source used only on Windows; it never writes, repairs, or deletes registration. */
	public static Source windowsRegistrySource() {
		return () -> {
			Process process=new ProcessBuilder("reg.exe","query",REGISTRY_LOCATION,"/s","/reg:64").redirectErrorStream(true).start();
			List<String> lines=new ArrayList<>(process.inputReader().lines().toList());
			try { if (process.waitFor()!=0) throw new IOException("registration registry unavailable"); }
			catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("registration registry interrupted",interrupted); }
			List<Map<String,String>> records=new ArrayList<>(); Map<String,String> current=null; Set<String> allowed=Set.of("schemaVersion","installationId","ownerSid","installRoot","supportRoot");
			for (String line:lines) {
				String trimmed=line.trim();
				if (trimmed.startsWith("HKEY_LOCAL_MACHINE\\") && !trimmed.equalsIgnoreCase("HKEY_LOCAL_MACHINE\\SOFTWARE\\Shale\\Installations")) { current=new HashMap<>(); current.put("_recordKey",trimmed.substring(trimmed.lastIndexOf('\\')+1)); records.add(current); continue; }
				if (current==null) continue;
				String[] parts=trimmed.split("\\s+REG_(?:SZ|DWORD)\\s+",2);
				if (parts.length==2 && allowed.contains(parts[0])) current.put(parts[0],parts[1].startsWith("0x") ? Integer.toString(Integer.decode(parts[1])) : parts[1]);
			}
			return records;
		};
	}
}
