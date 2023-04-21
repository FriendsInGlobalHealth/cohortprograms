package org.openmrs.module.esaudefeatures.web;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import okhttp3.Credentials;
import okhttp3.Request;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/20/22.
 */
public class UtilsTest {
	
	private static final String USERNAME = "user";
	
	private static final String PASSWORD = "password";
	
	private static final String BASE_URL = "http://server.example.tz";
	
	private final String SINGLE_PATIENT_SANTE_JSON = IOUtils.toString(getClass().getResourceAsStream(
	    "/opencr/single_patient_sante.json"));
	
	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
	
	private final IParser parser = FHIR_CONTEXT.newJsonParser();
	
	private Patient patient = parser.parseResource(Patient.class, SINGLE_PATIENT_SANTE_JSON);
	
	private static final String OPENMRS_UUID_FHIR_IDENTIFIER_SYSTEM_MAPPINGS = "8d793bee-c2cc-11de-8d13-0010c6dffd0f^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/openmrs-num,\n"
	        + "e2b966d0-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/nid-tarv,"
	        + "e2b9682e-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/bi,"
	        + "e2b9698c-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/cdg-ats,"
	        + "e2b96ad6-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/cdg-ptv-pre-natal,"
	        + "e2b96c16-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/cdg-its,"
	        + "e2b96d56-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/cdg-ptc-maternidade,"
	        + "e2b97b70-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/nid-ccr,"
	        + "e2b97cec-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/pcr-num-reg,"
	        + "e2b97e40-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/nit-tb,"
	        + "e2b97f8a-1d5f-11e0-b929-000c29ad1d07^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/num-cancro-cervical,"
	        + "e89c8925-35cc-4a29-9002-6b36bf3fd47f^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/nuic,"
	        + "79ad599a-50df-48f8-865c-0095ec9a9d01^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/nid-disa,"
	        + "1c72703d-fb55-439e-af4f-ef39a1049e19^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/cram-id,"
	        + "bce7c891-27e9-42ec-abb0-aec3a641175e^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/nid-prep,"
	        + "a5d38e09-efcb-4d91-a526-50ce1ba5011a^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/openempi-id,"
	        + "05a29f94-c0ed-11e2-94be-8c13b969e334^http://metadata.epts.e-saude.net/dictionary/patient-identifiers/openmrs-id";
	
	public UtilsTest() throws IOException {
	}
	
	@Test
	public void createBasicAuthGetRequestShouldCreateCorrectRequest() {
		String[] segements = { "seg1", "seg2/seg3" };
		final String EXPECTED_URI = BASE_URL.concat("/").concat(segements[0]).concat("/").concat(segements[1]);
		Request request = Utils.createBasicAuthGetRequest(BASE_URL, USERNAME, PASSWORD, segements, null);
		
		assertEquals(EXPECTED_URI, request.url().uri().toString());
		assertTrue(request.headers().names().contains("Authorization"));
		assertTrue(request.header("Authorization").endsWith(Credentials.basic(USERNAME, PASSWORD)));
		assertTrue(request.method().equalsIgnoreCase("GET"));
	}
	
	@Test
	public void getOpenmrsUuidFromFhirIdentifiers_shouldReturnPatientUuid() {
		String expectedUuid = "83bacb77-58c3-4902-807f-54ad96d4718f";
		String patientUuidFhirSystemValue = "http://metadata.epts.e-saude.net/dictionary/patient-uuid";
		assertEquals(expectedUuid,
		    Utils.getOpenmrsUuidFromFhirIdentifiers(patient.getIdentifier(), patientUuidFhirSystemValue));
	}
	
	@Test
	public void getOpenmrsUuidFromFhirIdentifiers_shouldReturnCorrectOpenmrsIdentifierTypeUuid() {
		String expectedIdentifyTypeUuid = "e2b966d0-1d5f-11e0-b929-000c29ad1d07";
		assertEquals(expectedIdentifyTypeUuid,
		    Utils.getOpenmrsIdentifierTypeUuid(patient.getIdentifier().get(1), OPENMRS_UUID_FHIR_IDENTIFIER_SYSTEM_MAPPINGS));
	}
}
