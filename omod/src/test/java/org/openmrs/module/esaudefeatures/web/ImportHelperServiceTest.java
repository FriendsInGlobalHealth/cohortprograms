package org.openmrs.module.esaudefeatures.web;

import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openmrs.Patient;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/20/22.
 */
@Component
public class ImportHelperServiceTest extends BaseModuleWebContextSensitiveTest {
	
	private static final String USERNAME = "user1";
	
	private static final String PASSWORD = "pa$$w0rd";
	
	private MockWebServer mockWebServer;
	
	@Mock
	private AdministrationService adminService;
	
	@InjectMocks
	@Autowired
	private ImportHelperService helperService;
	
	@Before
	public void setup() throws Exception {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_URL_GP)).thenReturn(
		    mockWebServer.url("/").toString());
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_USERNAME_GP)).thenReturn(
		    USERNAME);
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_PASSWORD_GP)).thenReturn(
		    PASSWORD);
	}
	
	@Test
	public void getPatientFromOpenmrsPayloadShouldReturnTheCorrectObject() throws Exception {
		final String PATIENT_JSON = IOUtils.toString(getClass().getResourceAsStream("/openmrs_rest_single_patient.json"));
		
		SimpleObject patientObject = SimpleObject.parseJson(PATIENT_JSON);
		Map<String, Object> auditInfo = patientObject.get("auditInfo");
		Boolean voided = patientObject.get("voided");
		Patient patient = helperService.getPatientFromOpenmrsRestPayload(patientObject);
		
		assertEquals(((Map) patientObject.get("person")).get("uuid"), patient.getUuid());
		assertEquals(voided, patient.getVoided());
	}
	
	@Test
	public void importLocationFromRemoteOpenmrsServerShouldImportSingleLocation() {
		
	}
}
