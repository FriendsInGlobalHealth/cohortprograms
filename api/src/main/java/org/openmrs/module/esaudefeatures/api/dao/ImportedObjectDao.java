package org.openmrs.module.esaudefeatures.api.dao;

import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openmrs.User;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.esaudefeatures.ImportedObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/30/22.
 */
@Repository("esaudefeatures.importedObjectDao")
public class ImportedObjectDao {

    @Autowired
    DbSessionFactory sessionFactory;

    private DbSession getSession() {
        return sessionFactory.getCurrentSession();
    }

    public ImportedObject saveImportedObject(ImportedObject importedObject) {
        getSession().saveOrUpdate(importedObject);
        return importedObject;
    }

    public ImportedObject getImportedObjectById(@NotNull Integer id) {
        return (ImportedObject) getSession()
                .createCriteria(ImportedObject.class)
                .add(Restrictions.eq("id", id))
                .uniqueResult();
    }

    public ImportedObject getImportedObjectByObjectUuid(@NotNull String objectUuid) {
        return (ImportedObject) getSession()
                .createCriteria(ImportedObject.class)
                .add(Restrictions.eq("objectUuid", objectUuid))
                .uniqueResult();
    }

    public Long getCountOfImportedObject(User importer,Date startDate, Date endDate) {
        return (Long) buildCriteria(importer, startDate, endDate, null, null)
                .setProjection(Projections.rowCount())
                .uniqueResult();
    }

    public List<ImportedObject> getImportedObjects(User importer, Date startDate, Date endDate, Integer startIndex, Integer pageSize) {
        return (List<ImportedObject>) buildCriteria(importer, startDate, endDate, startIndex, pageSize).list();
    }

    private Criteria buildCriteria(User importer,Date startDate, Date endDate, Integer startIndex, Integer pageSize) {
        Criteria criteria = getSession().createCriteria(ImportedObject.class);
        if(importer != null) {
            criteria.add(Restrictions.eq("importer", importer));
        }
        if(startDate != null) {
            criteria.add(Restrictions.ge("dateImported", startDate));
        }
        if(endDate != null) {
            criteria.add(Restrictions.lt("dateImported", endDate));
        }
        if(startIndex != null) {
            criteria.setFirstResult(startIndex);
        }
        if(pageSize != null) {
            criteria.setMaxResults(pageSize);
        }

        if(startIndex != null || pageSize != null) {
            criteria.addOrder(Order.asc("dateImported"));
        }
        return criteria;
    }
}
