package org.openmrs.module.esaudefeatures.api.impl;

import org.openmrs.User;
import org.openmrs.module.esaudefeatures.ImportedObject;
import org.openmrs.module.esaudefeatures.api.ImportedObjectService;
import org.openmrs.module.esaudefeatures.api.dao.ImportedObjectDao;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/30/22.
 */
public class ImportObjectServiceImpl implements ImportedObjectService {
	
	protected ImportedObjectDao dao;
	
	/**
	 * Injected in moduleApplicationContext.xml
	 */
	public void setDao(ImportedObjectDao dao) {
		this.dao = dao;
	}
	
	@Override
	@Transactional
	public ImportedObject saveImportedObject(@NotNull ImportedObject importedObject) {
		return dao.saveImportedObject(importedObject);
	}
	
	@Override
	@Transactional
	public void saveImportedObjects(@NotNull List<ImportedObject> importedObjects) {
		for (ImportedObject importedObject : importedObjects) {
			dao.saveImportedObject(importedObject);
		}
	}
	
	@Override
	@Transactional
	public Long getCountOfAllImportedObjects() {
		return dao.getCountOfImportedObject(null, null, null);
	}
	
	@Override
	@Transactional
	public Long getCountOfImportedObjectsByUser(@NotNull User importer) {
		return dao.getCountOfImportedObject(importer, null, null);
	}
	
	@Override
	@Transactional
	public Long getCountOfImportedObjects(User importer, Date startDate, Date endDate) {
		return dao.getCountOfImportedObject(importer, startDate, endDate);
	}
	
	@Override
	@Transactional
	public ImportedObject getImportedObjectById(@NotNull Integer id) {
		return dao.getImportedObjectById(id);
	}
	
	@Override
	@Transactional
	public ImportedObject getImportedObjectByObjectUuid(@NotNull String objectUuid) {
		return dao.getImportedObjectByObjectUuid(objectUuid);
	}
	
	@Override
	@Transactional
	public List<ImportedObject> getAllImportedObjects() {
		return dao.getImportedObjects(null, null, null, null, null);
	}
	
	@Override
	@Transactional
	public List<ImportedObject> getImportedObjectsByUser(@NotNull User importer) {
		return dao.getImportedObjects(importer, null, null, null, null);
	}
	
	@Override
	@Transactional
	public List<ImportedObject> getImportedObjects(User importer, Date startDate, Date endDate, Integer startIndex,
	        Integer pageSize) {
		return dao.getImportedObjects(importer, startDate, endDate, startIndex, pageSize);
	}
}
