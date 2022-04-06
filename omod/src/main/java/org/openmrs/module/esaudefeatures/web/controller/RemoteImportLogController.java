package org.openmrs.module.esaudefeatures.web.controller;

import org.openmrs.api.context.Context;
import org.openmrs.module.esaudefeatures.ImportedObject;
import org.openmrs.module.esaudefeatures.api.ImportedObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;
import java.util.List;

import static org.openmrs.util.OpenmrsConstants.OPENMRS_VERSION_SHORT;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 4/5/22.
 */
@Controller("esaudefeatures.remoteImportLogController")
public class RemoteImportLogController {
	
	protected ImportedObjectService ioService;
	
	@Autowired
	public void setIoService(ImportedObjectService ioService) {
		this.ioService = ioService;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/module/esaudefeatures/remoteImportLog.htm")
	public ModelAndView getLogs(@RequestParam(required = false) Date startDate,
	        @RequestParam(required = false) Date endDate,
	        @RequestParam(required = false, defaultValue = "0") Integer startIndex,
	        @RequestParam(required = false) Integer pageSize) {
		ModelAndView modelAndView = new ModelAndView();
		List<ImportedObject> importedObjects = ioService.getImportedObjects(null, startDate, endDate, startIndex, pageSize);
		
		modelAndView.getModelMap().addAttribute("importedObjects", importedObjects);
		if (Context.getAuthenticatedUser() != null) {
			modelAndView.getModelMap().addAttribute("authenticatedUser", Context.getAuthenticatedUser());
		}
		
		if (OPENMRS_VERSION_SHORT.startsWith("1")) {
			modelAndView.setViewName("module/esaudefeatures/remotePatients/remoteImportLog1x");
			//			modelAndView.setViewName("module/esaudefeatures/remotePatients/findRemotePatients1x");
		} else {
			modelAndView.setViewName("module/esaudefeatures/remotePatients/remoteImportLog2x");
		}
		return modelAndView;
	}
}
