package org.openmrs.module.cohortprograms.api.dao;

import org.hibernate.criterion.Restrictions;
import org.openmrs.Cohort;
import org.openmrs.Program;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.cohortprograms.ProgramCohort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/29/21.
 */
@Repository("cohortprograms.programCohortDao")
public class ProgramCohortDao {
	
	@Autowired
	DbSessionFactory sessionFactory;
	
	private DbSession getSession() {
		return sessionFactory.getCurrentSession();
	}
	
	public ProgramCohort getProgramCohortById(Integer id) {
		return (ProgramCohort) sessionFactory.getCurrentSession().createCriteria(ProgramCohort.class)
		        .add(Restrictions.eq("id", id)).uniqueResult();
	}
	
	public List<ProgramCohort> getProgramCohortsByProgram(Program program) {
		return (List) getSession().createCriteria(ProgramCohort.class).add(Restrictions.eq("program", program)).list();
	}
	
	public List<ProgramCohort> getProgramCohortsByCohort(final Cohort cohort) {
		return (List) getSession().createCriteria(ProgramCohort.class).add(Restrictions.eq("cohort", cohort)).list();
	}
	
	public List<ProgramCohort> getAllProgramCohorts() {
		return (List) getSession().createCriteria(ProgramCohort.class).list();
	}
	
	public ProgramCohort saveProgramCohort(ProgramCohort programCohort) {
		getSession().saveOrUpdate(programCohort);
		return programCohort;
	}
}
