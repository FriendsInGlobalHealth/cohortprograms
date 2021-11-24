package org.openmrs.module.esaudefeatures.web.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.codehaus.jackson.map.ObjectMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.esaudefeatures.web.dto.TokenDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.servlet.http.HttpServletResponse;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_PASSWORD_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_URL_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_USERNAME_GP;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/22/21.
 */
@Controller("esaudefeatures.opencrSearchController")
@RequestMapping(OpencrSearchController.ROOT_PATH)
public class OpencrSearchController {
	
	private static String cachedToken;
	
	private static final String OPENCR_JWT_AUTH_PATH = "ocrux/user/authenticate";
	
	private static final String OPENCR_FHIR_PATIENT_PATH = "ocrux/fhir/Patient";
	
	public static final String ROOT_PATH = "/module/esaudefeatures/opencrRemotePatients.json";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(OpencrSearchController.class);
	
	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
	
	// Assume text with a number in it is an identifier.
	private static final String IDENTIFIER_REGEX = "^.*\\d+?.*$";
	
	private AdministrationService adminService;
	
	private OkHttpClient createOkHttpClient() throws Exception {
		LOGGER.debug("Initializing the okHttp Client for OpenCR search");
		String skipHostnameVerification = adminService.getGlobalProperty(REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP,
		    "FALSE");
		if (Boolean.parseBoolean(skipHostnameVerification)) {
			LOGGER.debug("Configuring okHttp client to skip hostname verification.");
			return new OkHttpClient.Builder().hostnameVerifier(new HostnameVerifier() {
				
				@Override
				public boolean verify(String hostname, SSLSession session) {
					LOGGER.debug("Skipping hostname {} verification", hostname);
					return true;
				}
			}).build();
		}
		return new OkHttpClient();
	}
	
	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}
	
	@ResponseBody
	@RequestMapping(method = RequestMethod.GET, produces = { "application/json", "application/json+fhir" })
	public Bundle searchOpencrForPatient(@RequestParam("text") String searchText) throws Exception {
		IParser parser = FHIR_CONTEXT.newJsonParser();
		String remoteServerUrl = adminService.getGlobalProperty(REMOTE_SERVER_URL_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			String message = String.format("Could not perform the search, Global property %s not set", REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new OpencrSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addPathSegments(OPENCR_FHIR_PATIENT_PATH)
		        .addQueryParameter("active", "true");
		
		String[] texts = searchText.split("\\s+");
		if (texts.length > 1) {
			for (String text : texts) {
				if (text.matches(IDENTIFIER_REGEX)) {
					// Go for identifier search.
					urlBuilder.addQueryParameter("identifier", text);
				} else {
					urlBuilder.addQueryParameter("name", text);
				}
			}
		} else {
			if (searchText.matches(IDENTIFIER_REGEX)) {
				// Go for identifier search.
				urlBuilder.addQueryParameter("identifier", searchText);
			} else {
				urlBuilder.addQueryParameter("name", searchText);
			}
		}
		LOGGER.trace("Remote OpenCR URL for searching is {} ", urlBuilder.toString());
		String authToken = cachedToken;
		if (authToken == null) {
			try {
				authToken = getServerJWTAuthenticationToken();
			}
			catch (Exception e) {
				LOGGER.error(e.getMessage());
				throw new OpencrSearchException(e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}
		
		String bearerToken = "Bearer ".concat(authToken);
		Request clientRequest = new Request.Builder().url(urlBuilder.build()).addHeader("Authorization", bearerToken)
		        .build();
		
		OkHttpClient okHttpClient = createOkHttpClient();
		Response opencrResponse = okHttpClient.newCall(clientRequest).execute();
		
		if (opencrResponse.isSuccessful() && opencrResponse.code() == HttpServletResponse.SC_OK) {
			return parser.parseResource(Bundle.class, opencrResponse.body().string());
		} else if (opencrResponse.code() == HttpServletResponse.SC_UNAUTHORIZED) {
			// Deal with authentication error.
			if (opencrResponse.body().string().contains("Token expired")) {
				LOGGER.info("Server returned unauthorized, attempting re-authentication");
				try {
					authToken = reAuthenticate();
				}
				catch (OpencrAuthenticationException e) {
					LOGGER.error(e.getMessage());
					throw new OpencrSearchException(e.getMessage(), HttpServletResponse.SC_EXPECTATION_FAILED);
				}
				clientRequest = new Request.Builder().url(urlBuilder.build())
				        .addHeader("Authorization", "Bearer ".concat(authToken)).build();
				opencrResponse = okHttpClient.newCall(clientRequest).execute();
				if (opencrResponse.isSuccessful() && opencrResponse.code() == HttpServletResponse.SC_OK) {
					return parser.parseResource(Bundle.class, opencrResponse.body().string());
				} else {
					// Return error to the client.
					LOGGER.error("Response from {} server: {}", remoteServerUrl, opencrResponse.body().string());
					throw new OpencrSearchException(opencrResponse.body().string(), opencrResponse.code());
				}
			} else {
				LOGGER.error("Response from {} server: {}", remoteServerUrl, opencrResponse.body().string());
				throw new OpencrSearchException(opencrResponse.body().string(), opencrResponse.code());
			}
		}
		throw new OpencrSearchException(opencrResponse.body().string(), opencrResponse.code());
	}
	
	public String reAuthenticate() throws Exception {
		clearServerJWTAuthenticationToken();
		return getServerJWTAuthenticationToken();
	}
	
	public synchronized String getServerJWTAuthenticationToken() throws Exception {
		if (cachedToken != null)
			return cachedToken;
		
		String message;
		
		String remoteServerUrl = adminService.getGlobalProperty(REMOTE_SERVER_URL_GP);
		String remoteServerUsername = adminService.getGlobalProperty(REMOTE_SERVER_USERNAME_GP);
		String remoteServerPassword = adminService.getGlobalProperty(REMOTE_SERVER_PASSWORD_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			message = String.format("Could not authenticate Global property %s not set", REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new OpencrAuthenticationException(message);
		}
		if (StringUtils.isEmpty(remoteServerUsername)) {
			message = String.format("Could not authenticate, Global property %s not set", REMOTE_SERVER_USERNAME_GP);
			LOGGER.warn(message);
			throw new OpencrAuthenticationException(message);
		}
		
		if (StringUtils.isEmpty(remoteServerPassword)) {
			message = String.format("Could not authenticate Global property %s not set", REMOTE_SERVER_PASSWORD_GP);
			LOGGER.warn(message);
			throw new OpencrAuthenticationException(message);
		}
		
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addPathSegments(OPENCR_JWT_AUTH_PATH)
		        .addQueryParameter("username", remoteServerUsername).addQueryParameter("password", remoteServerPassword);
		
		Request authRequest = new Request.Builder().url(urlBuilder.build()).post(RequestBody.create(null, new byte[0]))
		        .header("Content-Length", "0").build();
		OkHttpClient okHttpClient = createOkHttpClient();
		Response authResponse = okHttpClient.newCall(authRequest).execute();
		
		if (authResponse.isSuccessful() && authResponse.code() == HttpServletResponse.SC_OK) {
			ObjectMapper mapper = new ObjectMapper();
			TokenDTO tokenDTO = mapper.readValue(authResponse.body().string(), TokenDTO.class);
			cachedToken = tokenDTO.token;
			return cachedToken;
		} else if (authResponse.code() == HttpServletResponse.SC_BAD_REQUEST) {
			// Deal with authentication error.
			message = "OpenCR user could not be authenticated, ensure correct username and/or password values";
			LOGGER.warn(message);
			throw new OpencrAuthenticationException(message);
		} else {
			LOGGER.warn("Attempt to authenticate with OpenCR returned status code {} and body {}", authResponse.code(),
			    authResponse.body().string());
			throw new OpencrAuthenticationException(authResponse.body().string());
		}
	}
	
	public synchronized void clearServerJWTAuthenticationToken() {
		cachedToken = null;
	}
	
	public synchronized void setCachedToken(String token) {
		cachedToken = token;
	}
}
