package org.openmrs.module.esaudefeatures.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.Concept;
import org.openmrs.Program;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.context.Context;
import org.openmrs.module.esaudefeatures.ProgramCohort;
import org.openmrs.module.esaudefeatures.api.CohortProgramsService;
import org.openmrs.module.esaudefeatures.web.CohortEditor;
import org.openmrs.module.esaudefeatures.web.dto.ProgramDTO;
import org.openmrs.propertyeditor.ConceptEditor;
import org.openmrs.propertyeditor.WorkflowCollectionEditor;
import org.openmrs.web.WebConstants;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashSet;
import java.util.Set;

/**
 * @uthor Willa Mhawila<mmhawila@juutech.co.tz> on 4/1/21.
 */
public class EsaudeFeaturesProgramFormController extends SimpleFormController {
	
	protected CohortProgramsService cpService;
	
	protected final Log log = LogFactory.getLog(getClass());
	
	public void setCpService(CohortProgramsService cpService) {
		this.cpService = cpService;
	}
	
	protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
		super.initBinder(request, binder);
		
		// this depends on this form being a "session-form" (defined in openrms-servlet.xml)
		ProgramDTO programDTO = (ProgramDTO) binder.getTarget();
		
		binder.registerCustomEditor(Concept.class, new ConceptEditor());
		binder.registerCustomEditor(Cohort.class, new CohortEditor());
		binder.registerCustomEditor(java.util.Collection.class, "allWorkflows", new WorkflowCollectionEditor(programDTO));
	}
	
	/**
	 * This is called prior to displaying a form for the first time. It tells Spring the
	 * form/command object to load into the request
	 * 
	 * @see org.springframework.web.servlet.mvc.AbstractFormController#formBackingObject(javax.servlet.http.HttpServletRequest)
	 */
	protected Object formBackingObject(HttpServletRequest request) throws ServletException {
		log.debug("called formBackingObject");
		ProgramDTO progromDTO = new ProgramDTO();
		
		if (Context.isAuthenticated()) {
			ProgramWorkflowService ps = Context.getProgramWorkflowService();
			String programId = request.getParameter("programId");
			if (programId != null) {
				Program program = ps.getProgram(Integer.valueOf(programId));
				progromDTO = new ProgramDTO(program);
				if (request.getMethod().equalsIgnoreCase("get")) {
					progromDTO.setCohorts(new HashSet<Cohort>(cpService.getCohortsForProgram(program)));
				}
			}
		}
		
		if (progromDTO == null) {
			progromDTO = new ProgramDTO();
		}
		
		return progromDTO;
	}
	
	/**
	 * The onSubmit function receives the form/command object that was modified by the input form
	 * and saves it to the db
	 * 
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#onSubmit(javax.servlet.http.HttpServletRequest,
	 *      javax.servlet.http.HttpServletResponse, java.lang.Object,
	 *      org.springframework.validation.BindException)
	 * @should save workflows with program
	 * @should edit existing workflows within programs
	 */
	protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, Object obj,
	        BindException errors) throws Exception {
		log.debug("about to save " + obj);
		HttpSession httpSession = request.getSession();
		
		String view = getFormView();
		
		if (Context.isAuthenticated()) {
			ProgramDTO p = (ProgramDTO) obj;
			try {
				Program program = Context.getProgramWorkflowService().saveProgram(p.getProgram());
				Set<Cohort> existingCohorts = new HashSet<Cohort>(cpService.getCohortsForProgram(program));
				Set<Cohort> updatedCohortsList = p.getCohorts();
				if (!updatedCohortsList.isEmpty()) {
					for (Cohort cohort : updatedCohortsList) {
						if (!cpService.isCohortAssociatedWithProgram(program, cohort)) {
							cpService.saveProgramCohort(new ProgramCohort(program, cohort));
						}
					}
				}
				
				if (existingCohorts != null && !existingCohorts.isEmpty()) {
					for (Cohort cohort : existingCohorts) {
						if (!updatedCohortsList.contains(cohort)) {
							cpService.removeCohortFromProgram(program, cohort);
						}
					}
				}
				httpSession.setAttribute(WebConstants.OPENMRS_MSG_ATTR, "esaudefeatures.program.saved");
			}
			catch (Exception e) {
				log.warn("Error saving Program", e);
				httpSession.setAttribute(WebConstants.OPENMRS_ERROR_ATTR, e.getMessage());
			}
			String contextPath = request.getContextPath();
			if (StringUtils.hasText(contextPath)) {
				view = contextPath + getSuccessView();
			} else {
				view = getSuccessView();
			}
		}
		
		return new ModelAndView(new RedirectView(view));
	}
}
