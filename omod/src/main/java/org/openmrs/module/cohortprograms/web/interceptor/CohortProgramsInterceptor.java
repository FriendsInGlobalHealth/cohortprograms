package org.openmrs.module.cohortprograms.web.interceptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.Patient;
import org.openmrs.Program;
import org.openmrs.api.CohortService;
import org.openmrs.module.cohortprograms.api.CohortProgramsService;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/29/21.
 */
@Component
public class CohortProgramsInterceptor extends HandlerInterceptorAdapter {
	
	private final static Log LOG = LogFactory.getLog(CohortProgramsInterceptor.class);
	
	@Autowired
	@Qualifier("cohortprograms.CohortProgramsService")
	private CohortProgramsService cpService;
	
	@Autowired
	private CohortService cohortService;
	
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		boolean p = request.getServletPath() != null ? request.getServletPath().contains("program") : (request
		        .getRequestURI() != null ? request.getRequestURI().contains("program") : false);
		if (p) {
			System.out.println("Request URI: " + request.getRequestURI());
			System.out.println("Servlet Path: " + request.getServletPath());
			System.out.println("Context Path: " + request.getContextPath());
			System.out.println("Attribute names: [");
			System.out.println("Parameters: [");
			Enumeration<String> params = request.getParameterNames();
			while (params.hasMoreElements()) {
				System.out.print(params.nextElement() + ", ");
			}
			System.out.println("]");
		}
		return true;
	}
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
	        ModelAndView modelAndView) throws Exception {
		//		LOG.warn("It is being called");
		//		System.out.println("++++++++++++++++++++++++++++++++++Interceptor is being called");
		//		System.out.println("View Name: " + (modelAndView != null ? modelAndView.getViewName() : "nothing"));
		String currentViewName = modelAndView != null ? modelAndView.getViewName() : null;
		
		if (currentViewName != null) {
			if (currentViewName.endsWith("programs/programForm")) {
				// Get all cohorts.
				List<Cohort> cohorts = cohortService.getAllCohorts(false);
				modelAndView.getModelMap().addAttribute("cohorts", cohorts);
				
				// Get cohorts already associated with a program if it is an edit.
				if (modelAndView.getModel().containsKey("program")) {
					Program programToEdit = (Program) modelAndView.getModel().get("program");
					List<Cohort> associatedCohorts = cpService.getCohortsForProgram(programToEdit);
					modelAndView.getModelMap().addAttribute("associatedCohorts", associatedCohorts);
					if (!associatedCohorts.isEmpty()) {
						cohorts.removeAll(associatedCohorts);
					}
				}
				
				if (OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1")) {
					modelAndView.setViewName("module/cohortprograms/admin/programs/programForm1x");
				} else {
					modelAndView.setViewName("module/cohortprograms/admin/programs/programForm2x");
				}
				//				System.out.println("Final view name: " + modelAndView.getViewName());
				//				System.out.println("Model: " + modelAndView.getModel());
				//				System.out.println("ModelMap: " + modelAndView.getModelMap());
			} else if (currentViewName.endsWith("portlets/patientPrograms")) {
				// Allow programs for which the patient is a member.
				
				// Get patient
				Patient patient = (Patient) modelAndView.getModel().get("patient");
				List<Program> programsList = (List<Program>) modelAndView.getModel().get("programs");
				
				Map<Program, List<Cohort>> programListMap = cpService.getCohortsForPrograms();
				for (Map.Entry<Program, List<Cohort>> entry : programListMap.entrySet()) {
					Program program = entry.getKey();
					if (programsList.contains(program)) {
						programsList.remove(program);
					}
					
					for (Cohort cohort : entry.getValue()) {
						if (cohort.contains(patient)) {
							programsList.add(program);
							break;
						}
					}
				}
				
				// Get OpenMRS Version
				if (OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1")) {
					modelAndView.setViewName("module/cohortprograms/portlets/patientPrograms1x");
				} else {
					modelAndView.setViewName("module/cohortprograms/portlets/patientPrograms2x");
				}
				//				System.out.println("Final view name: " + modelAndView.getViewName());
			}
		}
		
		//		System.out.println("-------------------------MWISHO------------------------------");
	}
}
