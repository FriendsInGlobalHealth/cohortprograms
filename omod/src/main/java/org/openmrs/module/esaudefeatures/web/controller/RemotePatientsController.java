package org.openmrs.module.esaudefeatures.web.controller;

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
@RequestMapping({ RemotePatientsController.ROOT_PATH, RemotePatientsController.ALT_ROOT_PATH })
public class RemotePatientsController {
	
	private AdministrationService adminService;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RemotePatientsController.class);
	
	public static final String ROOT_PATH = "module/esaudefeatures/findRemotePatients.form";
	
	public static final String ALT_ROOT_PATH = "module/esaudefeatures/findRemotePatients.htm";
	
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
			String base64encoded = byteArrayToBase64(remoteServerBasicAuth.getBytes(), 0, byteArray.length);
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
	
	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}
	
	/**
	 * Copied from org.apache.solr.common.util.Base64 class to avoid dependency issues between 1.x
	 * instances as opposed to 2.x instances.
	 * 
	 * @param a
	 * @param offset
	 * @param len
	 * @return
	 */
	private static String byteArrayToBase64(byte[] a, int offset, int len) {
		int aLen = len;
		int numFullGroups = aLen / 3;
		int numBytesInPartialGroup = aLen - 3 * numFullGroups;
		int resultLen = 4 * ((aLen + 2) / 3);
		StringBuffer result = new StringBuffer(resultLen);
		char[] intToAlpha = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
		        'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
		        'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8',
		        '9', '+', '/' };
		
		// Translate all full groups from byte array elements to Base64
		int inCursor = offset;
		for (int i = 0; i < numFullGroups; i++) {
			int byte0 = a[inCursor++] & 0xff;
			int byte1 = a[inCursor++] & 0xff;
			int byte2 = a[inCursor++] & 0xff;
			result.append(intToAlpha[byte0 >> 2]);
			result.append(intToAlpha[(byte0 << 4) & 0x3f | (byte1 >> 4)]);
			result.append(intToAlpha[(byte1 << 2) & 0x3f | (byte2 >> 6)]);
			result.append(intToAlpha[byte2 & 0x3f]);
		}
		
		// Translate partial group if present
		if (numBytesInPartialGroup != 0) {
			int byte0 = a[inCursor++] & 0xff;
			result.append(intToAlpha[byte0 >> 2]);
			if (numBytesInPartialGroup == 1) {
				result.append(intToAlpha[(byte0 << 4) & 0x3f]);
				result.append("==");
			} else {
				// assert numBytesInPartialGroup == 2;
				int byte1 = a[inCursor++] & 0xff;
				result.append(intToAlpha[(byte0 << 4) & 0x3f | (byte1 >> 4)]);
				result.append(intToAlpha[(byte1 << 2) & 0x3f]);
				result.append('=');
			}
		}
		return result.toString();
	}
}
