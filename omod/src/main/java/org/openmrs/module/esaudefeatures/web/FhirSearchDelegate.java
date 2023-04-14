package org.openmrs.module.esaudefeatures.web;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PatientService;
import org.openmrs.module.esaudefeatures.web.controller.FhirProviderAuthenticationException;
import org.openmrs.module.esaudefeatures.web.dto.JWTTokenDTO;
import org.openmrs.module.esaudefeatures.web.dto.Oauth2TokenDTO;
import org.openmrs.module.esaudefeatures.web.exception.FhirResourceSearchException;
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
import javax.validation.constraints.NotNull;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OAUTH2_CLIENT_ID_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OAUTH2_CLIENT_SECRET_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.FHIR_IDENTIFIER_SYSTEM_FOR_OPENMRS_UUID_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENCR_REMOTE_SERVER_PASSWORD_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.FHIR_REMOTE_SERVER_URL_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENCR_REMOTE_SERVER_USERNAME_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/17/22.
 */
@Component("esaudefeatures.opencrSearchDelegate")
public class FhirSearchDelegate {
	
	private static String cachedToken;
	
	private static final String OPENCR_FHIR_PATIENT_PATH = "ocrux/fhir/Patient";
	
	private static final String SANTE_FHIR_PATIENT_PATH = "fhir/Patient";
	
	private static final String OPENCR_JWT_AUTH_PATH = "ocrux/user/authenticate";
	
	private static final String SANTE_OAUTH2_PATH = "auth/oauth2_token";
	
	// Assume text with a number in it is an identifier.
	private static final String IDENTIFIER_REGEX = "^.*\\d+?.*$";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FhirSearchDelegate.class);
	
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
	
	private Patient fetchPatientFromFhirServerById(final String id, String fhirProvider) throws Exception {
		IParser parser = FHIR_CONTEXT.newJsonParser();
		String remoteServerUrl = adminService.getGlobalProperty(FHIR_REMOTE_SERVER_URL_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			String message = String.format("Could not perform the search, Global property %s not set",
			    FHIR_REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new FhirResourceSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		final String fhirPath = "OPENCR".equalsIgnoreCase(fhirProvider) ? OPENCR_FHIR_PATIENT_PATH : SANTE_FHIR_PATIENT_PATH;
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addPathSegments(fhirPath)
		        .addPathSegment(id);
		
		LOGGER.trace("Remote OpenCR URL for searching is {} ", urlBuilder.toString());
		
		String authToken = null;
		if ("OPENCR".equalsIgnoreCase(fhirProvider)) {
			authToken = getServerJWTAuthenticationToken();
		} else if ("SANTEMPI".equalsIgnoreCase(fhirProvider)) {
			authToken = getServerOauth2AuthenticationToken();
		}
		
		String bearerToken = "Bearer ".concat(authToken);
		Request clientRequest = new Request.Builder().url(urlBuilder.build()).addHeader("Authorization", bearerToken)
		        .build();
		
		OkHttpClient okHttpClient = createOkHttpClient();
		Response fhirResponse = okHttpClient.newCall(clientRequest).execute();
		if (fhirResponse.isSuccessful() && fhirResponse.code() == HttpServletResponse.SC_OK) {
			return parser.parseResource(Patient.class, fhirResponse.body().string());
		} else if (fhirResponse.code() == HttpServletResponse.SC_UNAUTHORIZED || fhirResponse.code() == HttpServletResponse.SC_FORBIDDEN) {
			// Deal with authentication error.
			final String responseBody = fhirResponse.body().string();
			if (responseBody.contains("Token expired") || responseBody.contains("is expired")) {
				LOGGER.info("Server returned unauthorized, attempting re-authentication");
				try {
					authToken = reAuthenticate(fhirProvider);
				}
				catch (FhirProviderAuthenticationException e) {
					LOGGER.error(e.getMessage());
					throw new FhirResourceSearchException(e.getMessage(), HttpServletResponse.SC_EXPECTATION_FAILED);
				}
				clientRequest = new Request.Builder().url(urlBuilder.build())
				        .addHeader("Authorization", "Bearer ".concat(authToken)).build();
				fhirResponse = okHttpClient.newCall(clientRequest).execute();
				if (fhirResponse.isSuccessful() && fhirResponse.code() == HttpServletResponse.SC_OK) {
					return parser.parseResource(Patient.class, fhirResponse.body().string());
				} else {
					// Return error to the client.
					LOGGER.error("Response from {} server: {}", remoteServerUrl, fhirResponse.body().string());
					throw new FhirResourceSearchException(fhirResponse.body().string(), fhirResponse.code());
				}
			} else {
				LOGGER.error("Response from {} server: {}", remoteServerUrl, fhirResponse.body().string());
				throw new FhirResourceSearchException(fhirResponse.body().string(), fhirResponse.code());
			}
		}
		throw new FhirResourceSearchException(fhirResponse.body().string(), fhirResponse.code());
	}
	
	public Bundle searchForPatients(final String searchText, final String fhirProvider) throws Exception {
		IParser parser = FHIR_CONTEXT.newJsonParser();
		String remoteServerUrl = adminService.getGlobalProperty(FHIR_REMOTE_SERVER_URL_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			String message = String.format("Could not perform the search, Global property %s not set",
			    FHIR_REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new FhirResourceSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		final String fhirPath = "OPENCR".equalsIgnoreCase(fhirProvider) ? OPENCR_FHIR_PATIENT_PATH : SANTE_FHIR_PATIENT_PATH;
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addPathSegments(fhirPath)
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
		LOGGER.trace("Remote FHIR URL for searching is {} ", urlBuilder.toString());
		String authToken = cachedToken;
		if (authToken == null) {
			try {
				if ("OPENCR".equalsIgnoreCase(fhirProvider)) {
					authToken = getServerJWTAuthenticationToken();
				} else if ("SANTEMPI".equalsIgnoreCase(fhirProvider)) {
					authToken = getServerOauth2AuthenticationToken();
				}
			}
			catch (Exception e) {
				LOGGER.error(e.getMessage());
				throw new FhirResourceSearchException(e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}
		
		String bearerToken = "Bearer ".concat(authToken);
		Request clientRequest = new Request.Builder().url(urlBuilder.build()).addHeader("Authorization", bearerToken)
		        .build();
		
		OkHttpClient okHttpClient = createOkHttpClient();
		Response fhirResponse = okHttpClient.newCall(clientRequest).execute();
		
		try {
			if (fhirResponse.isSuccessful() && fhirResponse.code() == HttpServletResponse.SC_OK) {
				Bundle bundle = parser.parseResource(Bundle.class, fhirResponse.body().string());
				stashEntriesIntoCache(bundle);
				return bundle;
			} else if (fhirResponse.code() == HttpServletResponse.SC_UNAUTHORIZED || fhirResponse.code() == HttpServletResponse.SC_FORBIDDEN) {
				// Deal with authentication error.
				final String responseString = fhirResponse.body().string();
				if (responseString.contains("Token expired") || responseString.contains("is expired")) {
					LOGGER.info("Server returned unauthorized, attempting re-authentication");
					try {
						authToken = reAuthenticate(fhirProvider);
					}
					catch (FhirProviderAuthenticationException e) {
						LOGGER.error(e.getMessage());
						throw new FhirResourceSearchException(e.getMessage(), HttpServletResponse.SC_EXPECTATION_FAILED);
					}
					clientRequest = new Request.Builder().url(urlBuilder.build())
					        .addHeader("Authorization", "Bearer ".concat(authToken)).build();
					fhirResponse = okHttpClient.newCall(clientRequest).execute();
					if (fhirResponse.isSuccessful() && fhirResponse.code() == HttpServletResponse.SC_OK) {
						Bundle bundle = parser.parseResource(Bundle.class, fhirResponse.body().string());
						stashEntriesIntoCache(bundle);
						return bundle;
					} else {
						// Return error to the client.
						LOGGER.error("Response from {} server: {}", remoteServerUrl, fhirResponse.body().string());
						throw new FhirResourceSearchException(fhirResponse.body().string(), fhirResponse.code());
					}
				} else {
					LOGGER.error("Response from {} server: {}", remoteServerUrl, fhirResponse.body().string());
					throw new FhirResourceSearchException(fhirResponse.body().string(), fhirResponse.code());
				}
			}
			throw new FhirResourceSearchException(fhirResponse.body().string(), fhirResponse.code());
		}
		finally {
			if (fhirResponse != null) {
				fhirResponse.close();
			}
		}
	}
	
	@Transactional
	public org.openmrs.Patient importPatient(final String fhirPatientId, final String fhirProvider) throws Exception {
		//Check if patient is in cache.
		Bundle.BundleEntryComponent patientEntry = PATIENTS_CACHE.get(fhirPatientId);
		Patient patient;
		if (patientEntry == null) {
			// Get it from openCR server (this means the cache expired already)
			patient = fetchPatientFromFhirServerById(fhirPatientId, fhirProvider);
		} else {
			patient = (Patient) patientEntry.getResource();
		}
		
		if (patient != null) {
			return importPatient(patient);
		}
		return null;
	}
	
	@Transactional
	public org.openmrs.Patient importPatient(@NotNull final Patient patientResource) throws Exception {
		//Find the corresponding patient from the central server.
		String patientUuidConceptMap = adminService.getGlobalProperty(FHIR_IDENTIFIER_SYSTEM_FOR_OPENMRS_UUID_GP);
		String opencrPatientUuidCode = patientUuidConceptMap.split(":")[0];
		String openmrsUuid = Utils.getOpenmrsUuidFromFhirIdentifiers(patientResource.getIdentifier(),
																	 opencrPatientUuidCode);
		
		org.openmrs.Patient opPatient = null;
		if (openmrsUuid != null) {
			// Fetch patient from central server.
			SimpleObject patientObject = openmrsSearchDelegate.getRemotePatientByUuid(openmrsUuid);
			if (patientObject != null) {
				opPatient = helperService.getPatientFromOpenmrsRestPayload(patientObject);
				helperService.updateOpenmrsPatientWithMPIData(opPatient, patientResource);
			}
		}
		
		if (opPatient == null) {
			opPatient = helperService.getPatientFromFhirPatientResource(patientResource);
		}
		return patientService.savePatient(opPatient);
	}
	
	public String reAuthenticate(final String fhirProvider) throws Exception {
		clearServerAuthenticationToken();
		if ("OPENCR".equalsIgnoreCase(fhirProvider)) {
			return getServerJWTAuthenticationToken();
		}
		return getServerOauth2AuthenticationToken();
	}
	
	public synchronized String getServerJWTAuthenticationToken() throws Exception {
		if (cachedToken != null)
			return cachedToken;
		
		String message;
		
		String remoteServerUrl = adminService.getGlobalProperty(FHIR_REMOTE_SERVER_URL_GP);
		String remoteServerUsername = adminService.getGlobalProperty(OPENCR_REMOTE_SERVER_USERNAME_GP);
		String remoteServerPassword = adminService.getGlobalProperty(OPENCR_REMOTE_SERVER_PASSWORD_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			message = String.format("Could not authenticate Global property %s not set", FHIR_REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new FhirProviderAuthenticationException(message);
		}
		if (StringUtils.isEmpty(remoteServerUsername)) {
			message = String.format("Could not authenticate, Global property %s not set", OPENCR_REMOTE_SERVER_USERNAME_GP);
			LOGGER.warn(message);
			throw new FhirProviderAuthenticationException(message);
		}
		
		if (StringUtils.isEmpty(remoteServerPassword)) {
			message = String.format("Could not authenticate Global property %s not set", OPENCR_REMOTE_SERVER_PASSWORD_GP);
			LOGGER.warn(message);
			throw new FhirProviderAuthenticationException(message);
		}
		
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder()
		        .addEncodedPathSegments(OPENCR_JWT_AUTH_PATH).addEncodedQueryParameter("username", remoteServerUsername)
		        .addEncodedQueryParameter("password", remoteServerPassword);
		
		Request authRequest = new Request.Builder().url(urlBuilder.build()).post(RequestBody.create(null, new byte[0]))
		        .header("Content-Length", "0").build();
		OkHttpClient okHttpClient = createOkHttpClient();
		Response authResponse = okHttpClient.newCall(authRequest).execute();
		
		message = "OpenCR user could not be authenticated, ensure correct username and/or password values";
		try {
			if (authResponse.isSuccessful() && authResponse.code() == HttpServletResponse.SC_OK) {
				ObjectMapper mapper = new ObjectMapper();
				JWTTokenDTO tokenDTO = mapper.readValue(authResponse.body().string(), JWTTokenDTO.class);
				if (tokenDTO.token == null) {
					LOGGER.warn(message);
					throw new FhirProviderAuthenticationException(message);
				}
				cachedToken = tokenDTO.token;
				return cachedToken;
			} else if (authResponse.code() == HttpServletResponse.SC_BAD_REQUEST) {
				// Deal with authentication error.
				LOGGER.warn(message);
				throw new FhirProviderAuthenticationException(message);
			} else {
				LOGGER.warn("Attempt to authenticate with OpenCR returned status code {} and body {}", authResponse.code(),
				    authResponse.body().string());
				throw new FhirProviderAuthenticationException(authResponse.body().string());
			}
		}
		finally {
			if (authResponse != null) {
				authResponse.close();
			}
		}
	}
	
	public synchronized String getServerOauth2AuthenticationToken() throws Exception {
		if (cachedToken != null)
			return cachedToken;
		
		String message;
		
		String remoteServerUrl = adminService.getGlobalProperty(FHIR_REMOTE_SERVER_URL_GP);
		String oauth2ClientId = adminService.getGlobalProperty(OAUTH2_CLIENT_ID_GP);
		String oauth2ClientSecret = adminService.getGlobalProperty(OAUTH2_CLIENT_SECRET_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			message = String.format("Could not authenticate Global property %s not set", FHIR_REMOTE_SERVER_URL_GP);
			LOGGER.warn(message);
			throw new FhirProviderAuthenticationException(message);
		}
		if (StringUtils.isEmpty(oauth2ClientId)) {
			message = String.format("Could not authenticate, Global property %s not set", OAUTH2_CLIENT_ID_GP);
			LOGGER.warn(message);
			throw new FhirProviderAuthenticationException(message);
		}
		
		if (StringUtils.isEmpty(oauth2ClientSecret)) {
			message = String.format("Could not authenticate Global property %s not set", OPENCR_REMOTE_SERVER_PASSWORD_GP);
			LOGGER.warn(message);
			throw new FhirProviderAuthenticationException(message);
		}
		
		HttpUrl.Builder urlBuilder = HttpUrl.parse(remoteServerUrl).newBuilder().addEncodedPathSegments(SANTE_OAUTH2_PATH);
		
		RequestBody formBody = new FormBody.Builder().addEncoded("client_id", oauth2ClientId)
		        .addEncoded("client_secret", oauth2ClientSecret).addEncoded("scope", "*")
		        .addEncoded("grant_type", "client_credentials").build();
		
		Request authRequest = new Request.Builder().url(urlBuilder.build()).post(formBody)
		        .header("ContentType", "application/x-www-form-urlencoded").build();
		
		OkHttpClient okHttpClient = createOkHttpClient();
		Response authResponse = okHttpClient.newCall(authRequest).execute();
		
		message = oauth2ClientId + " client ID could not be authenticated, ensure correct values for client ID and secret";
		try {
			if (authResponse.isSuccessful() && authResponse.code() == HttpServletResponse.SC_OK) {
				ObjectMapper mapper = new ObjectMapper();
				Oauth2TokenDTO tokenDTO = mapper.readValue(authResponse.body().string(), Oauth2TokenDTO.class);
				if (tokenDTO.error != null) {
					LOGGER.warn(tokenDTO.error_description);
					throw new FhirProviderAuthenticationException(tokenDTO.error_description);
				}
				
				if (tokenDTO.access_token != null) {
					cachedToken = tokenDTO.access_token;
					return cachedToken;
				}
				throw new FhirProviderAuthenticationException(message);
			} else if (authResponse.code() == HttpServletResponse.SC_BAD_REQUEST) {
				// Deal with authentication error.
				LOGGER.warn(message);
				throw new FhirProviderAuthenticationException(message);
			} else {
				LOGGER.warn("Attempt to authenticate with SanteMPI returned status code {} and body {}",
				    authResponse.code(), authResponse.body().string());
				throw new FhirProviderAuthenticationException(authResponse.body().string());
			}
		}
		finally {
			if (authResponse != null) {
				authResponse.close();
			}
		}
	}
	
	public synchronized void clearServerAuthenticationToken() {
		cachedToken = null;
	}
	
	public synchronized void setCachedToken(String token) {
		cachedToken = token;
	}
	
	private OkHttpClient createOkHttpClient() throws Exception {
		LOGGER.debug("Initializing the okHttp Client for OpenCR search");
		String skipHostnameVerification = adminService.getGlobalProperty(REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP,
		    "FALSE");
		return Utils.createOkHttpClient(Boolean.parseBoolean(skipHostnameVerification));
	}
	
	private static void stashEntriesIntoCache(Bundle bundle) {
		for (Bundle.BundleEntryComponent entryComponent : bundle.getEntry()) {
			if (entryComponent.getResource().getResourceType().equals(ResourceType.Patient)) {
				PATIENTS_CACHE.put(entryComponent.getResource().getId(), entryComponent);
			}
		}
	}
}
