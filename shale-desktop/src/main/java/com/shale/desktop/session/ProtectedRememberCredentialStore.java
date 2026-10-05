package com.shale.desktop.session;

import com.shale.core.platform.AppPaths;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;

/** Windows user-scoped DPAPI persistence. The file contains ciphertext only. */
public class ProtectedRememberCredentialStore {
    private final Path file;
    public ProtectedRememberCredentialStore(){this(AppPaths.appSupportDir("Shale").resolve("credentials").resolve("remember.dpapi"));}
    ProtectedRememberCredentialStore(Path file){this.file=file;}
    public boolean available(){return AppPaths.isWindows();}
    public Optional<String> read() throws Exception {if(!available()||!Files.exists(file))return Optional.empty();byte[] plain=Crypt32Util.cryptUnprotectData(Files.readAllBytes(file),WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);return Optional.of(new String(plain,StandardCharsets.US_ASCII));}
    public void write(String credential)throws Exception{if(!available())throw new UnsupportedOperationException("Windows protected storage is unavailable.");byte[] cipher=Crypt32Util.cryptProtectData(credential.getBytes(StandardCharsets.US_ASCII),WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"remember-",".tmp");try{Files.write(temp,cipher,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
    public void clear(){try{Files.deleteIfExists(file);}catch(Exception ignored){}}
}
