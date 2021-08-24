package org.openmrs.module.esaudefeatures.web.controller;

import org.apache.solr.common.util.Base64;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_PASSWORD_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_URL_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_USERNAME_GP;
import static org.openmrs.util.OpenmrsConstants.OPENMRS_VERSION_SHORT;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 8/5/21.
 */
@Controller("esaudefeatures.remotePatientsController")
@RequestMapping(RemotePatientsController.ROOT_PATH)
public class RemotePatientsController {
	
	private AdministrationService adminService;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RemotePatientsController.class);
	
	public static final String ROOT_PATH = "module/esaudefeatures/findRemotePatients.form";
	
	@RequestMapping(method = RequestMethod.GET)
	public ModelAndView searchRemoteForm(ModelAndView modelAndView) {
		if (modelAndView == null) {
			modelAndView = new ModelAndView();
		}
		
		String remoteServerUrl = adminService.getGlobalProperty(REMOTE_SERVER_URL_GP);
		if (StringUtils.hasText(remoteServerUrl)) {
			modelAndView.getModelMap().addAttribute("remoteServerUrl", remoteServerUrl);
		} else {
			LOGGER.warn("Global property {} not set", REMOTE_SERVER_URL_GP);
		}
		String remoteServerUsername = adminService.getGlobalProperty(REMOTE_SERVER_USERNAME_GP);
		String remoteServerPassword = adminService.getGlobalProperty(REMOTE_SERVER_PASSWORD_GP);
		if (StringUtils.hasText(remoteServerUsername) && StringUtils.hasText(remoteServerPassword)) {
			String remoteServerBasicAuth = new StringBuilder(remoteServerUsername).append(":").append(remoteServerPassword)
			        .toString();
			byte[] byteArray = remoteServerBasicAuth.getBytes();
			String base64encoded = Base64.byteArrayToBase64(remoteServerBasicAuth.getBytes(), 0, byteArray.length);
			modelAndView.getModelMap().addAttribute("remoteServerAuth", base64encoded);
		}
		
		if (StringUtils.isEmpty(remoteServerUsername)) {
			LOGGER.warn("Global property {} not set", REMOTE_SERVER_USERNAME_GP);
		}
		
		if (StringUtils.isEmpty(remoteServerPassword)) {
			LOGGER.warn("Global property {} not set", REMOTE_SERVER_PASSWORD_GP);
		}
		
		// if there's an authenticated user, put them, and their patient set, in the model
		if (Context.getAuthenticatedUser() != null) {
			modelAndView.getModelMap().addAttribute("authenticatedUser", Context.getAuthenticatedUser());
		}
		
		if (OPENMRS_VERSION_SHORT.startsWith("1")) {
			modelAndView.setViewName("module/esaudefeatures/remotePatients/findRemotePatients1x");
		} else {
			modelAndView.setViewName("module/esaudefeatures/remotePatients/findRemotePatients2x");
		}
		return modelAndView;
	}
	
	@RequestMapping(value = "module/esaudefeatures/findRemotePatients.htm")
	public ModelAndView searchRemoteFormAlternativePath(ModelAndView modelAndView) {
		return searchRemoteForm(modelAndView);
	}
	
	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}
}
