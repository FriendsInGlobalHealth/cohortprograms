package org.openmrs.module.esaudefeatures.api;

import org.openmrs.User;
import org.openmrs.module.esaudefeatures.ImportedObject;

import java.util.Date;
import java.util.List;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/30/22.
 */
public interface ImportedObjectService {
    ImportedObject saveImportedObject(ImportedObject importedObject);
    void saveImportedObjects(List<ImportedObject> importedObjects);

    Long getCountOfAllImportedObjects();
    Long getCountOfImportedObjectsByUser(User importer);
    Long getCountOfImportedObjects(User importer, Date startDate, Date endDate);

    ImportedObject getImportedObjectById(Integer id);
    ImportedObject getImportedObjectByObjectUuid(String objectUuid);

    List<ImportedObject> getAllImportedObjects();
    List<ImportedObject> getImportedObjectsByUser(User importer);
    List<ImportedObject> getImportedObjects(User importer, Date startDate, Date endDate, Integer startIndex, Integer pageSize);
}
