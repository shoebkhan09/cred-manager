package org.gluu.credmngr;

import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zul.Messagebox;

/**
 * @author jgomer 2017/06/22
 */
public class ViewModel {

	// Detects whether client is mobile device or not (such as Android or iOS device)
	public boolean isMobile(){
		return Executions.getCurrent().getBrowser("mobile") !=null;
	}

	public void setMobile(boolean mobile) {
		this.mobile = mobile;
	}

	private boolean mobile;

}
