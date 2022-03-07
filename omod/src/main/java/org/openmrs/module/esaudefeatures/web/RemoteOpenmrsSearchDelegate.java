package org.openmrs.module.esaudefeatures.web;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang.NotImplementedException;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/17/22.
 */
@Component
public class RemoteOpenmrsSearchDelegate {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteOpenmrsSearchDelegate.class);
	
	private static final String OPENMRS_REST_PATIENT_PATH = "ws/rest/v1/patient";
	
	private AdministrationService adminService;
	
	private ImportHelperService helperService;
	
	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}
	
	@Autowired
	public void setHelperService(ImportHelperService helperService) {
		this.helperService = helperService;
	}
	
	public SimpleObject searchPatients(final String searchText) throws Exception {
		String[] urlUsernamePassword = helperService.getRemoteOpenmrsHostUsernamePassword();
		String remoteServerUrl = urlUsernamePassword[0];
		String remoteServerUsername = urlUsernamePassword[1];
		String remoteServerPassword = urlUsernamePassword[2];
		boolean skipHostnameVerification = Boolean.parseBoolean(adminService.getGlobalProperty(
		    REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP, "FALSE"));
		
		String[] pathSegments = { OPENMRS_REST_PATIENT_PATH };
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put("q", searchText);
		queryParams.put("v", "full");
		Request fetchRequest = Utils.createBasicAuthGetRequest(remoteServerUrl, remoteServerUsername, remoteServerPassword,
		    pathSegments, queryParams);
		
		OkHttpClient okHttpClient = Utils.createOkHttpClient(skipHostnameVerification);
		
		Response response = okHttpClient.newCall(fetchRequest).execute();
		
		if (response.isSuccessful() && response.code() == HttpServletResponse.SC_OK) {
			return parseServerResponse(response);
		}
		String errorMessage = String.format("Error fetching response from server %s", remoteServerUrl);
		LOGGER.error(errorMessage);
		throw new RemoteOpenmrsSearchException(errorMessage, response.code());
	}
	
	public SimpleObject getRemotePatientByUuid(final String uuid) throws Exception {
		String[] urlUsernamePassword = helperService.getRemoteOpenmrsHostUsernamePassword();
		String remoteServerUrl = urlUsernamePassword[0];
		String remoteServerUsername = urlUsernamePassword[1];
		String remoteServerPassword = urlUsernamePassword[2];
		boolean skipHostnameVerification = Boolean.parseBoolean(adminService.getGlobalProperty(
		    REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP, "FALSE"));
		
		String[] pathSegments = { OPENMRS_REST_PATIENT_PATH, uuid };
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put("v", "full");
		Request fetchRequest = Utils.createBasicAuthGetRequest(remoteServerUrl, remoteServerUsername, remoteServerPassword,
		    pathSegments, queryParams);
		
		OkHttpClient okHttpClient = Utils.createOkHttpClient(skipHostnameVerification);
		
		Response response = okHttpClient.newCall(fetchRequest).execute();
		
		if (response.isSuccessful() && response.code() == HttpServletResponse.SC_OK) {
			return parseServerResponse(response);
		} else if (response.code() == HttpServletResponse.SC_NOT_FOUND) {
			LOGGER.debug("Patient with uuid {} does not exist on the server {}", uuid, remoteServerUrl);
			return null;
		}
		LOGGER.error("Response from {} server: {}", response.request().url().toString(), response.body().string());
		throw new RemoteOpenmrsSearchException(response.body().string(), response.code());
	}
	
	private SimpleObject parseServerResponse(Response response) {
		try {
			SimpleObject object = SimpleObject.parseJson(response.body().string());
			return object;
		}
		catch (IOException ioe) {
			LOGGER.error("Error processing response from server", ioe);
			throw new RemoteOpenmrsSearchException(ioe.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
