package org.openmrs.module.esaudefeatures.web;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
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
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_PASSWORD_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_URL_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_USERNAME_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/17/22.
 */
@Component
public class RemoteOpenmrsSearchDelegate {
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteOpenmrsSearchDelegate.class);

	private static final String OPENMRS_REST_PATIENT_PATH = "ws/rest/v1/patient";
	private AdministrationService adminService;

	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}

	public SimpleObject searchPatients(final String searchText) throws Exception {
		throw new NotImplementedException("Will be when I get around to this");
	}

	public SimpleObject getRemotePatientByUuid(final String uuid) throws Exception {
		String message = "Could not fetch patient, Global property %s not set";
		String remoteServerUrl = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_URL_GP);
		String remoteServerUsername = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_USERNAME_GP);
		String remoteServerPassword = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_PASSWORD_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			LOGGER.warn(String.format(message, OPENMRS_REMOTE_SERVER_URL_GP));
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		if (StringUtils.isEmpty(remoteServerUsername)) {
			LOGGER.warn(String.format(message, OPENMRS_REMOTE_SERVER_USERNAME_GP));
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		if (StringUtils.isEmpty(remoteServerPassword)) {
			LOGGER.warn(String.format(message, OPENMRS_REMOTE_SERVER_PASSWORD_GP));
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

		boolean skipHostnameVerification = Boolean.parseBoolean(adminService.getGlobalProperty(REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP,
				"FALSE"));

		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addPathSegments(OPENMRS_REST_PATIENT_PATH)
				.addPathSegment(uuid).addQueryParameter("v", "full");

		String credentials = Credentials.basic(remoteServerUsername, remoteServerPassword);
		Request authRequest = new Request.Builder().url(urlBuilder.build()).get()
				.header("Content-Length", "0").header("Authorization", credentials).build();

		OkHttpClient okHttpClient = Utils.createOkHttpClient(skipHostnameVerification);

		Response response = okHttpClient.newCall(authRequest).execute();

		if (response.isSuccessful() && response.code() == HttpServletResponse.SC_OK) {
			try {
				SimpleObject object = SimpleObject.parseJson(response.body().string());
				return object;
			} catch (IOException ioe) {
				LOGGER.error("Error processing response {} from server", response.body().string());
				LOGGER.error(ioe.getMessage());
				throw new RemoteOpenmrsSearchException(ioe.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}
		LOGGER.error("Response from {} server: {}", response.request().url().toString(), response.body().string());
		throw new RemoteOpenmrsSearchException(response.body().string(), response.code());
	}
}
