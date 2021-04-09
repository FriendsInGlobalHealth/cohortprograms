/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.cohortprograms.api.impl;

import org.openmrs.Cohort;
import org.openmrs.Program;
import org.openmrs.api.APIException;
import org.openmrs.api.UserService;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.cohortprograms.ProgramCohort;
import org.openmrs.module.cohortprograms.api.CohortProgramsService;
import org.openmrs.module.cohortprograms.api.dao.ProgramCohortDao;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CohortProgramsServiceImpl extends BaseOpenmrsService implements CohortProgramsService {
	
	protected ProgramCohortDao dao;
	
	/**
	 * Injected in moduleApplicationContext.xml
	 */
	public void setDao(ProgramCohortDao dao) {
		this.dao = dao;
	}
	
	@Override
	public ProgramCohort createProgramCohort(@NotNull final Program program, @NotNull final Cohort cohort)
	        throws APIException {
		ProgramCohort programCohort = new ProgramCohort(program, cohort);
		return dao.saveProgramCohort(programCohort);
	}
	
	@Override
	public ProgramCohort saveProgramCohort(@NotNull final ProgramCohort programCohort) throws APIException {
		return dao.saveProgramCohort(programCohort);
	}
	
	@Override
	public List<Cohort> getCohortsForProgram(@NotNull final Program program) throws APIException {
		List<ProgramCohort> items = dao.getProgramCohortsByProgram(program);
		List<Cohort> cohorts = new ArrayList<Cohort>();
		
		if (items != null && !items.isEmpty()) {
			for (ProgramCohort programCohort : items) {
				cohorts.add(programCohort.getCohort());
			}
		}
		return cohorts;
	}
	
	@Override
	public Map<Program, List<Cohort>> getCohortsForPrograms() throws APIException {
		List<ProgramCohort> list = dao.getAllProgramCohorts();
		Map<Program, List<Cohort>> mappedList = new HashMap<Program, List<Cohort>>();
		if (list != null && !list.isEmpty()) {
			for (ProgramCohort programCohort : list) {
				Program program = programCohort.getProgram();
				if (!mappedList.containsKey(program)) {
					mappedList.put(program, new ArrayList<Cohort>(Arrays.asList(programCohort.getCohort())));
				} else {
					mappedList.get(program).add(programCohort.getCohort());
				}
			}
		}
		return mappedList;
	}
	
	@Override
	public void deleteProgramCohort(@NotNull final ProgramCohort programCohort) throws APIException {
		dao.deleteProgramCohort(programCohort);
	}
	
	@Override
	public List<ProgramCohort> addCohortsToProgram(@NotNull final Program program, @NotNull final List<Cohort> cohorts)
	        throws APIException {
		List<ProgramCohort> addedProgramCohorts = new ArrayList<ProgramCohort>();
		for (Cohort cohort : cohorts) {
			if (!dao.isCohortAssociatedWithProgram(program, cohort)) {
				ProgramCohort created = createProgramCohort(program, cohort);
				addedProgramCohorts.add(created);
			}
		}
		return addedProgramCohorts;
	}
	
	@Override
	public boolean removeCohortFromProgram(@NotNull final Program program, @NotNull final Cohort cohort) throws APIException {
		ProgramCohort programCohort = dao.getProgramCohortByProgramAndCohort(program, cohort);
		
		if (programCohort != null) {
			dao.deleteProgramCohort(programCohort);
			return true;
		}
		return false;
	}
	
	@Override
	public boolean removeCohortsFromProgram(@NotNull final Program program, @NotNull final List<Cohort> cohorts)
	        throws APIException {
		boolean status = true;
		for (Cohort cohort : cohorts) {
			status = status && removeCohortFromProgram(program, cohort);
		}
		return status;
	}
	
	@Override
	public boolean isCohortAssociatedWithProgram(@NotNull final Program program, @NotNull final Cohort cohort) {
		return dao.isCohortAssociatedWithProgram(program, cohort);
	}
}
