package com.shale.desktop.ui;

import com.shale.ui.services.UiAuthService;
import com.shale.data.auth.AuthService;
import com.shale.core.model.User;
import com.shale.desktop.session.DesktopSessionEnrollmentLifecycle;

/**
 * Desktop adapter that bridges shale-ui's UiAuthService interface to the real data-layer
 * AuthService.
 */
public final class DesktopUiAuthService implements UiAuthService {

	private final AuthService authService;
	private final DesktopSessionEnrollmentLifecycle enrollment;

	public DesktopUiAuthService(AuthService authService) {
		this(authService,null);
	}
	public DesktopUiAuthService(AuthService authService,DesktopSessionEnrollmentLifecycle enrollment){this.authService=authService;this.enrollment=enrollment;}

	@Override
	public Result login(String email, String password) throws Exception {
		return login(email,password,false);
	}
	@Override public Result login(String email,String password,boolean stayLoggedIn)throws Exception{
		// Call real authentication in shale-data
		User user = authService.login(email, password);
		if(enrollment!=null)enrollment.stage(user.getShaleClientId(),user.getId(),user.getEmail(),password,stayLoggedIn);

		// Convert core User -> UI layer Result record
		return new Result(
				user.getId(), // userId
				user.getShaleClientId(), // tenant identifier for RLS
				user.getEmail(), // email
				user.isAdmin(), // admin
				user.isAttorney() // attorney
		);
	}
	@Override public boolean hasRememberedCredential(){return enrollment!=null&&enrollment.hasRememberedCredential();}
	@Override public Result restore()throws Exception{if(enrollment==null)return null;var r=enrollment.restore();return r==null?null:new Result(r.userId(),r.shaleClientId(),r.email(),r.admin(),r.attorney());}
	@Override public void commitRememberedCredential()throws Exception{if(enrollment!=null)enrollment.commitRemembered();}
	@Override public void clearRememberedCredential(){if(enrollment!=null)enrollment.clearRemembered();}
}
