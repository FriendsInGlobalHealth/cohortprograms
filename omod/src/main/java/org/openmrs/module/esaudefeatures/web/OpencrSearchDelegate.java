package org.openmrs.module.esaudefeatures.web;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PatientService;
import org.openmrs.module.esaudefeatures.web.controller.OpencrAuthenticationException;
import org.openmrs.module.esaudefeatures.web.dto.TokenDTO;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENCR_REMOTE_SERVER_PASSWORD_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENCR_REMOTE_SERVER_URL_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENCR_REMOTE_SERVER_USERNAME_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/17/22.
 */
@Component("esaudefeatures.opencrSearchDelegate")
public class OpencrSearchDelegate {
	
	private static String cachedToken;
	
	private static final String OPENCR_FHIR_PATIENT_PATH = "ocrux/fhir/Patient";
	
	private static final String OPENCR_JWT_AUTH_PATH = "ocrux/user/authenticate";
	
	public static final String OPENMRS_SYSTEM_FOR_UUID = "http://openmrs.org/uuid";
	
	// Assume text with a number in it is an identifier.
	private static final String IDENTIFIER_REGEX = "^.*\\d+?.*$";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(OpencrSearchDelegate.class);
	
	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
	
	private static TimedConcurrentHashMapCache<String, Bundle.BundleEntryComponent> PATIENTS_CACHE = new TimedConcurrentHashMapCache<String, Bundle.BundleEntryComponent>();
	
	private AdministrationService adminService;
	
	private RemoteOpenmrsSearchDelegate openmrsSearchDelegate;
	
	private ImportHelperService helperService;
	
	private PatientService patientService;
	
	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}
	
	@Autowired
	public void setOpenmrsSearchDelegate(RemoteOpenmrsSearchDelegate openmrsSearchDelegate) {
		this.openmrsSearchDelegate = openmrsSearchDelegate;
	}
	
	@Autowired
	public void setHelperService(ImportHelperService helperService) {
		this.helperService = helperService;
	}
	
	@Autowired
	public void setPatientService(PatientService patientService) {
		this.patientService = patientService;
	}
	
	public Patient fetchPatientFromOpencrServerByFhirId(final String id) throws Exception {
		IParser parser = FHIR_CONTEXT.newJsonParser();
		String remoteServerUrl = adminService.getGlobalProperty(OPENCR_REMOTE_SERVER_URL_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			String message = String.format("Could not perform the search, Global property %s not set",
			    OPENCR_REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new OpencrSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addPathSegments(OPENCR_FHIR_PATIENT_PATH)
		        .addPathSegment(id);
		
		LOGGER.trace("Remote OpenCR URL for searching is {} ", urlBuilder.toString());
		
		String authToken = getServerJWTAuthenticationToken();
		
		String bearerToken = "Bearer ".concat(authToken);
		Request clientRequest = new Request.Builder().url(urlBuilder.build()).addHeader("Authorization", bearerToken)
		        .build();
		
		OkHttpClient okHttpClient = createOkHttpClient();
		Response opencrResponse = okHttpClient.newCall(clientRequest).execute();
		if (opencrResponse.isSuccessful() && opencrResponse.code() == HttpServletResponse.SC_OK) {
			return parser.parseResource(Patient.class, opencrResponse.body().string());
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
					return parser.parseResource(Patient.class, opencrResponse.body().string());
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
	
	public Bundle searchOpencrForPatients(final String searchText) throws Exception {
		IParser parser = FHIR_CONTEXT.newJsonParser();
		String remoteServerUrl = adminService.getGlobalProperty(OPENCR_REMOTE_SERVER_URL_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			String message = String.format("Could not perform the search, Global property %s not set",
			    OPENCR_REMOTE_SERVER_URL_GP);
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
			Bundle bundle = parser.parseResource(Bundle.class, opencrResponse.body().string());
			stashEntriesIntoCache(bundle);
			return bundle;
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
					Bundle bundle = parser.parseResource(Bundle.class, opencrResponse.body().string());
					stashEntriesIntoCache(bundle);
					return bundle;
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
	
	@Transactional
	public org.openmrs.Patient importOpencrPatient(final String fhirPatientId) throws Exception {
		//Check if patient is in cache.
		Bundle.BundleEntryComponent patientEntry = PATIENTS_CACHE.get(fhirPatientId);
		Patient patient = null;
		if (patientEntry == null) {
			// Get it from openCR server (this means the cache expired already)
			patient = fetchPatientFromOpencrServerByFhirId(fhirPatientId);
		} else {
			patient = (Patient) patientEntry.getResource();
		}
		
		if (patient != null) {
			return importOpencrPatient(patient);
		}
		return null;
	}
	
	@Transactional
	public org.openmrs.Patient importOpencrPatient(final Patient patientResource) throws Exception {
		//Find the corresponding patient from the central server.
		String openmrsUuid = getOpenmrsUuid(patientResource.getIdentifier());
		
		if (openmrsUuid != null) {
			// Fetch patient from central server.
			SimpleObject patientObject = openmrsSearchDelegate.getRemotePatientByUuid(openmrsUuid);
			org.openmrs.Patient opPatient = helperService.getPatientFromOpenmrsRestPayload(patientObject);
			// Apply changes from OpenCR server
			helperService.updateOpenmrsPatientWithMPIData(opPatient, patientResource);
			return patientService.savePatient(opPatient);
		} else {
			// Just import as new.
			org.openmrs.Patient opPatient = helperService.getPatientFromOpencrPatientResource(patientResource);
			return patientService.savePatient(opPatient);
		}
	}
	
	public String reAuthenticate() throws Exception {
		clearServerJWTAuthenticationToken();
		return getServerJWTAuthenticationToken();
	}
	
	public synchronized String getServerJWTAuthenticationToken() throws Exception {
		if (cachedToken != null)
			return cachedToken;
		
		String message;
		
		String remoteServerUrl = adminService.getGlobalProperty(OPENCR_REMOTE_SERVER_URL_GP);
		String remoteServerUsername = adminService.getGlobalProperty(OPENCR_REMOTE_SERVER_USERNAME_GP);
		String remoteServerPassword = adminService.getGlobalProperty(OPENCR_REMOTE_SERVER_PASSWORD_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			message = String.format("Could not authenticate Global property %s not set", OPENCR_REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new OpencrAuthenticationException(message);
		}
		if (StringUtils.isEmpty(remoteServerUsername)) {
			message = String.format("Could not authenticate, Global property %s not set", OPENCR_REMOTE_SERVER_USERNAME_GP);
			LOGGER.warn(message);
			throw new OpencrAuthenticationException(message);
		}
		
		if (StringUtils.isEmpty(remoteServerPassword)) {
			message = String.format("Could not authenticate Global property %s not set", OPENCR_REMOTE_SERVER_PASSWORD_GP);
			LOGGER.warn(message);
			throw new OpencrAuthenticationException(message);
		}
		
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addPathSegments(OPENCR_JWT_AUTH_PATH)
		        .addQueryParameter("username", remoteServerUsername).addQueryParameter("password", remoteServerPassword);
		
		Request authRequest = new Request.Builder().url(urlBuilder.build()).post(RequestBody.create(null, new byte[0]))
		        .header("Content-Length", "0").build();
		OkHttpClient okHttpClient = createOkHttpClient();
		Response authResponse = okHttpClient.newCall(authRequest).execute();
		
		message = "OpenCR user could not be authenticated, ensure correct username and/or password values";
		if (authResponse.isSuccessful() && authResponse.code() == HttpServletResponse.SC_OK) {
			ObjectMapper mapper = new ObjectMapper();
			TokenDTO tokenDTO = mapper.readValue(authResponse.body().string(), TokenDTO.class);
			if (tokenDTO.token == null) {
				LOGGER.warn(message);
				throw new OpencrAuthenticationException(message);
			}
			cachedToken = tokenDTO.token;
			return cachedToken;
		} else if (authResponse.code() == HttpServletResponse.SC_BAD_REQUEST) {
			// Deal with authentication error.
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
	
	private static void stashEntriesIntoCache(Bundle bundle) {
		for (Bundle.BundleEntryComponent entryComponent : bundle.getEntry()) {
			if (entryComponent.getResource().getResourceType().equals(ResourceType.Patient)) {
				PATIENTS_CACHE.put(entryComponent.getResource().getId(), entryComponent);
			}
		}
	}
	
	private String getOpenmrsUuid(List<Identifier> identifiers) {
		for (int i = 0; i < identifiers.size(); i++) {
			Identifier identifier = identifiers.get(i);
			if (identifier.hasSystem() && OPENMRS_SYSTEM_FOR_UUID.equalsIgnoreCase(identifier.getSystem())) {
				return identifier.getValue();
			}
		}
		return null;
	}
}
