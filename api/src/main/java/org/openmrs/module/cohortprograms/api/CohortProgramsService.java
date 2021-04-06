/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.cohortprograms.api;

import org.openmrs.Cohort;
import org.openmrs.Program;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.cohortprograms.CohortProgramsConfig;
import org.openmrs.module.cohortprograms.ProgramCohort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The main service of this module, which is exposed for other modules. See
 * moduleApplicationContext.xml on how it is wired up.
 */
public interface CohortProgramsService extends OpenmrsService {
	
	@Transactional
	@Authorized(CohortProgramsConfig.MODULE_PRIVILEGE)
	ProgramCohort createProgramCohort(Program program, Cohort cohort) throws APIException;
	
	@Transactional
	@Authorized
	ProgramCohort saveProgramCohort(ProgramCohort programCohort) throws APIException;
	
	@Transactional
	@Authorized
	void deleteProgramCohort(ProgramCohort programCohort) throws APIException;
	
	@Transactional
	@Authorized
	List<Cohort> getCohortsForProgram(Program program) throws APIException;
	
	@Transactional
	@Authorized
	Map<Program, List<Cohort>> getCohortsForPrograms() throws APIException;
	
	@Transactional
	@Authorized
	List<ProgramCohort> addCohortsToProgram(Program program, List<Cohort> cohorts) throws APIException;
	
	@Transactional
	@Authorized
	boolean removeCohortFromProgram(Program program, Cohort cohort) throws APIException;
	
	@Transactional
	@Authorized
	boolean removeCohortsFromProgram(Program program, List<Cohort> cohorts) throws APIException;
	
	boolean isCohortAssociatedWithProgram(Program program, Cohort cohort);
}
