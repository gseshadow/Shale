package com.shale.ui.services;

/**
 * UI-facing auth adapter. Implement this in shale-desktop (or wire to shale-data) and
 * inject into SceneManager.
 */
public interface UiAuthService {
	record Result(int userId, int shaleClientId, String email, boolean admin, boolean attorney) {
	}

	Result login(String email,String password)throws Exception;
	default Result login(String email,String password,boolean stayLoggedIn)throws Exception{return login(email,password);}
	default boolean hasRememberedCredential(){return false;}
	default Result restore() throws Exception{throw new UnsupportedOperationException("Remembered sign-in is unavailable.");}
	default void commitRememberedCredential() throws Exception{}
	default void clearRememberedCredential(){}
}
