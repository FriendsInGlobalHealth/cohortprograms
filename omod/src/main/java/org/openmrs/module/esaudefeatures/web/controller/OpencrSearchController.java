package org.openmrs.module.esaudefeatures.web.controller;

import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.Patient;
import org.openmrs.module.esaudefeatures.web.OpencrSearchDelegate;
import org.openmrs.module.esaudefeatures.web.exception.RemoteImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/22/21.
 */
@Controller("esaudefeatures.opencrSearchController")
public class OpencrSearchController {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(OpencrSearchController.class);
	
	private OpencrSearchDelegate opencrSearchDelegate;
	
	@Autowired
	public void setOpencrSearchDelegate(OpencrSearchDelegate opencrSearchDelegate) {
		this.opencrSearchDelegate = opencrSearchDelegate;
	}
	
	@ResponseBody
	@RequestMapping(method = RequestMethod.GET, value = "/module/esaudefeatures/opencrRemotePatients.json", produces = {
	        "application/json", "application/json+fhir" })
	public Bundle searchOpencrForPatient(@RequestParam("text") String searchText) throws Exception {
		return opencrSearchDelegate.searchOpencrForPatients(searchText);
	}
	
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(method = RequestMethod.POST, value = "/module/esaudefeatures/opencrPatient.json", produces = { "application/json" })
	@ResponseBody
	public String importPatient(@RequestParam("patientId") String opencrPatientId) {
		try {
			Patient patient = opencrSearchDelegate.importOpencrPatient(opencrPatientId);
			return patient.getPatientId().toString();
		}
		catch (Exception e) {
			LOGGER.error("An error occured while importing patient from opencr with fhirId {}", opencrPatientId, e);
			throw new RemoteImportException(e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
