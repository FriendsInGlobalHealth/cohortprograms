package org.openmrs.module.esaudefeatures;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.openmrs.User;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Date;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/30/22.
 */
@Entity(name = "esaudefeatures.ImportedObject")
@Table(name = "esaudefeatures_imported_object")
public class ImportedObject {
	
	private static final long serialVersionUID = 1L;
	
	@Id
	@GeneratedValue
	@Column(name = "imported_object_id")
	private Integer id;
	
	@Column(nullable = false)
	private String type;
	
	@ManyToOne(optional = false)
	@JoinColumn(name = "importer")
	private User importer;
	
	@Column(name = "date_imported", nullable = false)
	private Date dateImported;
	
	@Column(name = "object_uuid")
	private String objectUuid;
	
	public ImportedObject() {
	}
	
	public ImportedObject(String type, User importer, Date dateImported, String objectUuid) {
		this.type = type;
		this.importer = importer;
		this.dateImported = dateImported;
		this.objectUuid = objectUuid;
	}
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public Date getDateImported() {
		return dateImported;
	}
	
	public void setDateImported(Date dateImported) {
		this.dateImported = dateImported;
	}
	
	public User getImporter() {
		return importer;
	}
	
	public void setImporter(User importer) {
		this.importer = importer;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		
		if (!(o instanceof ImportedObject))
			return false;
		
		ImportedObject that = (ImportedObject) o;
		
		return new EqualsBuilder().append(getId(), that.getId()).isEquals();
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).append(getId()).toHashCode();
	}
}
