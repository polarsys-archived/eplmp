/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.core.configuration;


import org.polarsys.eplmp.core.document.DocumentIteration;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;

/**
 * Class link that gathers a document collection and a given document iteration.
 *
 * @author Taylor Labejof
 * @version 2.0, 25/08/14
 * @since V2.0
 */

@Table(name = "BASELINEDDOCUMENT")
@Entity
@NamedQueries({
        @NamedQuery(name = "BaselinedDocument.existBaselinedDocument", query = "SELECT count(bd) FROM BaselinedDocument bd WHERE bd.baselinedDocumentKey.targetDocumentId = :documentId AND bd.baselinedDocumentKey.targetDocumentVersion = :documentVersion AND bd.baselinedDocumentKey.targetDocumentWorkspaceId = :workspaceId")
})
public class BaselinedDocument implements Serializable {
    @EmbeddedId
    private BaselinedDocumentKey baselinedDocumentKey;

    //@MapsId("documentCollectionId")
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "DOCUMENTCOLLECTION_ID", referencedColumnName = "ID")
    private DocumentCollection documentCollection;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "TARGET_WORKSPACE_ID", referencedColumnName = "WORKSPACE_ID"),
            @JoinColumn(name = "TARGET_DOCUMENTMASTER_ID", referencedColumnName = "DOCUMENTMASTER_ID"),
            @JoinColumn(name = "TARGET_DOCREVISION_VERSION", referencedColumnName = "DOCUMENTREVISION_VERSION"),
            @JoinColumn(name = "TARGET_ITERATION", referencedColumnName = "ITERATION")
    })
    private DocumentIteration targetDocument;

    @Column(name = "TARGET_ITERATION", nullable = false, insertable = false, updatable = false)
    private int targetDocumentIteration;

    public BaselinedDocument() {
    }

    public BaselinedDocument(DocumentCollection documentCollection, DocumentIteration targetDocument) {
        this.documentCollection = documentCollection;
        this.targetDocument = targetDocument;
        this.baselinedDocumentKey = new BaselinedDocumentKey(documentCollection.getId(), targetDocument.getWorkspaceId(), targetDocument.getDocumentMasterId(), targetDocument.getVersion());
        this.targetDocumentIteration = targetDocument.getIteration();
    }

    public BaselinedDocumentKey getKey() {
        return baselinedDocumentKey;
    }

    @XmlTransient
    public DocumentCollection getDocumentCollection() {
        return documentCollection;
    }

    public void setDocumentCollection(DocumentCollection documentCollection) {
        this.documentCollection = documentCollection;
    }

    public DocumentIteration getTargetDocument() {
        return targetDocument;
    }

    public String getTargetDocumentMasterId() {
        return targetDocument.getDocumentMasterId();
    }

    public String getTargetDocumentVersion() {
        return targetDocument.getVersion();
    }

    public int getTargetDocumentIteration() {
        return targetDocumentIteration;
    }

    public void setTargetDocumentIteration(int targetDocumentIteration) {
        this.targetDocumentIteration = targetDocumentIteration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o){
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BaselinedDocument that = (BaselinedDocument) o;

        if (baselinedDocumentKey != null ? !baselinedDocumentKey.equals(that.baselinedDocumentKey) : that.baselinedDocumentKey != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return baselinedDocumentKey != null ? baselinedDocumentKey.hashCode() : 0;
    }
}
