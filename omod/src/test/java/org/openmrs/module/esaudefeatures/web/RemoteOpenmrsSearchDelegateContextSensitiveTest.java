package org.openmrs.module.esaudefeatures.web;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PatientService;
import org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertNotNull;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/10/22.
 */
public class RemoteOpenmrsSearchDelegateContextSensitiveTest extends BaseModuleWebContextSensitiveTest {
	
	@Autowired
	AdministrationService adminService;
	
	@Autowired
	PatientService patientService;
	
	@Autowired
	RemoteOpenmrsSearchDelegate delegate;
	
	@Before
	public void setUp() throws Exception {
		executeDataSet("patient_identifier_type.xml");
		adminService.setGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_URL_GP,
		    "https://qa-refapp.openmrs.org/openmrs");
		adminService.setGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_USERNAME_GP, "admin");
		adminService.setGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_PASSWORD_GP, "Admin123");
	}
	
	@Test
	@Ignore("Depends on Openmrs QA server which may or may not be available or without expected data")
	public void importPatientWithUuidShouldImportPatient() throws Exception {
		String patientUuid = "57357acf-cc3d-439d-92e0-aa8db1ae051c";
		Patient patient = delegate.importPatientWithUuid(patientUuid);
		assertNotNull(patient);
	}
}
