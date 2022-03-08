package org.openmrs.module.esaudefeatures.web.extension;

import org.openmrs.module.esaudefeatures.web.controller.OpenmrsSearchController;
import org.openmrs.module.web.extension.LinkExt;
import org.openmrs.util.OpenmrsUtil;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 8/5/21.
 */
public class GutterListExt extends LinkExt {
	
	public String getLabel() {
		return OpenmrsUtil.getMessage("esaudefeatures.remote.patients");
	}
	
	public String getUrl() {
		return OpenmrsSearchController.ALT_ROOT_PATH;
	}
	
	/**
	 * Returns the required privilege in order to see this section. Can be a comma delimited list of
	 * privileges. If the default empty string is returned, only an authenticated user is required
	 * 
	 * @return Privilege string
	 */
	public String getRequiredPrivilege() {
		return "";
	}
	
}
