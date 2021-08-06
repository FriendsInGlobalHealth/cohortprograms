package org.openmrs.module.esaudefeatures.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.openmrs.util.OpenmrsConstants.OPENMRS_VERSION_SHORT;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 8/5/21.
 */
@Controller("esaudefeatures.remotePatientsController")
@RequestMapping(RemotePatientsController.ROOT_PATH)
public class RemotePatientsController {
	
	public static final String ROOT_PATH = "module/esaudefeatures/findRemotePatients.form";
	
	@RequestMapping(method = RequestMethod.GET)
	public String searchRemoteForm() {
		
		if (OPENMRS_VERSION_SHORT.startsWith("1")) {
			return "/module/esaudefeatures/remotePatients/findRemotePatients1x";
		} else {
			return "/module/esaudefeatures/remotePatients/findRemotePatients2x";
		}
	}
}
