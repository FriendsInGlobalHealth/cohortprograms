package org.openmrs.module.cohortprograms.web;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 4/1/21.
 */
@Component
public class UrlMappingModifierPostProcessor implements BeanPostProcessor {
	
	private final static String URL_MAPPING_BEAN_NAME_1X = "urlMapping";
	
	private final static String URL_MAPPING_BEAN_NAME_2X = "legacyUiUrlMapping";
	
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		//		if (URL_MAPPING_BEAN_NAME_1X.contentEquals(beanName) || URL_MAPPING_BEAN_NAME_2X.contentEquals(beanName)) {
		//			System.out.println("-------------------->>>>>>>>>> Here we are: BEAN NAME :-> " + beanName);
		//			SimpleUrlHandlerMapping handlerMapping = (SimpleUrlHandlerMapping) bean;
		//			Map<String, ?> urlMap = handlerMapping.getUrlMap();
		//
		//			Map<String, ?> modifiedUrlMap = reconfigureUrlMap(urlMap);
		//			handlerMapping.setUrlMap(new HashMap<String, Object>());
		//			handlerMapping.setUrlMap(modifiedUrlMap);
		//
		//			urlMap = handlerMapping.getUrlMap();
		//			for (Map.Entry<String, ?> entry : urlMap.entrySet()) {
		//				if (entry.getKey().endsWith("admin/programs/program.form"))
		//					System.out.println("AFTER: " + entry.getKey() + " -> " + entry.getValue());
		//			}
		//			//			final BeanWrapper bw = new BeanWrapperImpl(bean);
		//			//			Map mappings = (Map) bw.getPropertyValue("urlMap");
		//			//			mappings.put("admin/programs/program.form", "cohortProgramProgramForm");
		//			return handlerMapping;
		//		}
		return bean;
	}
	
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		//		if (URL_MAPPING_BEAN_NAME_1X.contentEquals(beanName) || URL_MAPPING_BEAN_NAME_2X.contentEquals(beanName)) {
		//			System.out.println("-------------------->>>>>>>>>> Here we are: BEAN NAME :-> " + beanName);
		//			SimpleUrlHandlerMapping handlerMapping = (SimpleUrlHandlerMapping) bean;
		//			Map<String, ?> urlMap = handlerMapping.getUrlMap();
		//			handlerMapping.setUrlMap(reconfigureUrlMap(urlMap));
		//
		//			urlMap = handlerMapping.getUrlMap();
		//			for (Map.Entry<String, ?> entry : urlMap.entrySet()) {
		//				if (entry.getKey().endsWith("admin/programs/program.form"))
		//					System.out.println("AFTER: " + entry.getKey() + " -> " + entry.getValue());
		//			}
		//			//			final BeanWrapper bw = new BeanWrapperImpl(bean);
		//			//			Map mappings = (Map) bw.getPropertyValue("urlMap");
		//			//			mappings.put("admin/programs/program.form", "cohortProgramProgramForm");
		//			return handlerMapping;
		//		}
		return bean;
	}
	
	protected Map<String, ?> reconfigureUrlMap(Map<String, ?> urlMap) {
		Map<String, Object> map = new HashMap<String, Object>();
		for (Map.Entry<String, ?> entry : urlMap.entrySet()) {
			if (entry.getKey().endsWith("admin/programs/program.form"))
				System.out.println("BEFORE: " + entry.getKey() + " -> " + entry.getValue());
			map.put(entry.getKey(), entry.getValue());
		}
		map.put("admin/programs/program.form", "cohortProgramProgramForm");
		return map;
	}
}
