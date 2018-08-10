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

package org.polarsys.eplmp.server.indexer;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.cluster.Health;
import io.searchbox.core.*;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.mapping.PutMapping;
import io.searchbox.params.SearchType;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.polarsys.eplmp.core.common.Account;
import org.polarsys.eplmp.core.common.BinaryResource;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.document.*;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.query.DocumentSearchQuery;
import org.polarsys.eplmp.core.query.PartSearchQuery;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.*;
import org.polarsys.eplmp.i18n.PropertiesLoader;
import org.polarsys.eplmp.server.dao.*;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Asynchronous;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Morgan Guimard
 */
@Stateless(name = "IndexerManagerBean")
@Local(IIndexerManagerLocal.class)
@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
public class IndexerManagerBean implements IIndexerManagerLocal {

    @Inject
    private JestClient esClient;

    @Inject
    private DocumentMasterDAO documentMasterDAO;

    @Inject
    private DocumentRevisionDAO documentRevisionDAO;

    @Inject
    private PartMasterDAO partMasterDAO;

    @Inject
    private PartRevisionDAO partRevisionDAO;

    @Inject
    private WorkspaceDAO workspaceDAO;

    @Inject
    private IAccountManagerLocal accountManager;

    @Inject
    private INotifierLocal mailer;

    @Inject
    private IndexerConfigManager indexerConfigManager;

    @Inject
    private IBinaryStorageManagerLocal storageManager;

    @Inject
    private IContextManagerLocal contextManager;

    @Inject
    private IUserManagerLocal userManager;

    private static final String I18N_CONF = "/org/polarsys/eplmp/core/i18n/LocalStrings";

    private static final String ANALYZER_SETTING_RESOURCE = "/org/polarsys/eplmp/server/indexer/analyzer-setting.json";

    private static final Logger LOGGER = Logger.getLogger(IndexerManagerBean.class.getName());

    @Override
    @RolesAllowed({UserGroupMapping.ADMIN_ROLE_ID, UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void createWorkspaceIndex(String workspaceId) {
        try {
            createIndex(IndexerUtils.formatIndexName(workspaceId));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot create index for workspace [" + workspaceId + "]", e);
        }
    }

    @Override
    @RolesAllowed({UserGroupMapping.ADMIN_ROLE_ID, UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void deleteWorkspaceIndex(String workspaceId) throws AccountNotFoundException {
        Account account = accountManager.getMyAccount();
        doDeleteWorkspaceIndex(account, workspaceId);
    }

    @Override
    @Asynchronous
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void indexDocumentIteration(DocumentIteration documentIteration) {
        doIndexDocumentIteration(documentIteration);
    }

    @Override
    @Asynchronous
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void indexDocumentIterations(List<DocumentIteration> documentIterations) {
        documentIterations.forEach(this::doIndexDocumentIteration);
    }

    @Override
    @Asynchronous
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void indexPartIteration(PartIteration partIteration) {
        doIndexPartIteration(partIteration);
    }

    @Override
    @Asynchronous
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void indexPartIterations(List<PartIteration> partIterations) {
        partIterations.forEach(this::doIndexPartIteration);
    }

    @Override
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void removeDocumentIterationFromIndex(DocumentIteration documentIteration) {
        try {
            DocumentResult result = esClient.execute(deleteRequest(documentIteration));
            if (!result.isSucceeded()) {
                LOGGER.log(Level.WARNING, "Cannot delete document " + documentIteration + ": " + result.getErrorMessage());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot delete document " + documentIteration + ": The Elasticsearch cluster does not seem to respond");
        }
    }

    @Override
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void removePartIterationFromIndex(PartIteration partIteration) {
        try {
            DocumentResult result = esClient.execute(deleteRequest(partIteration));
            if (!result.isSucceeded()) {
                LOGGER.log(Level.WARNING, "Cannot delete part iteration " + partIteration + ": " + result.getErrorMessage());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot delete part iteration " + partIteration + ": The Elasticsearch cluster does not seem to respond");
        }
    }

    @Override
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public List<DocumentRevision> searchDocumentRevisions(DocumentSearchQuery documentSearchQuery, int from, int size) throws AccountNotFoundException, NotAllowedException {

        String workspaceId = documentSearchQuery.getWorkspaceId();
        QueryBuilder query = IndexerQueryBuilder.getSearchQueryBuilder(documentSearchQuery);
        LOGGER.log(Level.FINE, query.toString());
        SearchResult searchResult;

        try {
            searchResult = esClient.execute(new Search.Builder(
                            new SearchSourceBuilder()
                                    .query(query)
                                    .from(from)
                                    .size(size)
                                    .toString())
                            .addIndex(IndexerUtils.formatIndexName(workspaceId))
                            .addType(IndexerMapping.DOCUMENT_TYPE)
                            .setSearchType(SearchType.QUERY_THEN_FETCH)
                            .build()
            );

        } catch (IOException e) {
            throw new NotAllowedException("IndexerNotAvailableForSearch");
        }

        if (searchResult.isSucceeded()) {

            List<SearchResult.Hit<Map, Void>> hits = searchResult.getHits(Map.class);
            Set<DocumentIterationKey> documentIterationKeys = new HashSet<>();

            if (hits != null) {
                for (SearchResult.Hit<Map, Void> hit : hits) {
                    Map<?, ?> source = hit.source;
                    documentIterationKeys.add(IndexerMapping.getDocumentIterationKey(source));
                }
            }

            LOGGER.log(Level.INFO, "Results: " + documentIterationKeys.size());
            return documentIterationKeysToDocumentRevisions(documentSearchQuery.isFetchHeadOnly(), documentIterationKeys);

        } else {
            throw new NotAllowedException(searchResult.getErrorMessage());
        }
    }

    @Override
    @RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID})
    public List<PartRevision> searchPartRevisions(PartSearchQuery partSearchQuery, int from, int size) throws AccountNotFoundException, NotAllowedException {

        String workspaceId = partSearchQuery.getWorkspaceId();
        QueryBuilder query = IndexerQueryBuilder.getSearchQueryBuilder(partSearchQuery);
        LOGGER.log(Level.FINE, query.toString());
        SearchResult searchResult;

        try {
            searchResult = esClient.execute(new Search.Builder(
                            new SearchSourceBuilder()
                                    .query(query)
                                    .from(from)
                                    .size(size)
                                    .toString())
                            .addIndex(IndexerUtils.formatIndexName(workspaceId))
                            .addType(IndexerMapping.PART_TYPE)
                            .setSearchType(SearchType.QUERY_THEN_FETCH)
                            .build()
            );

        } catch (IOException e) {
            throw new NotAllowedException("IndexerNotAvailableForSearch");
        }

        if (searchResult.isSucceeded()) {
            List<SearchResult.Hit<Map, Void>> hits = searchResult.getHits(Map.class);
            Set<PartIterationKey> partIterationKeys = new HashSet<>();

            if (hits != null) {
                for (SearchResult.Hit<Map, Void> hit : hits) {
                    Map<?, ?> source = hit.source;
                    partIterationKeys.add(IndexerMapping.getPartIterationKey(source));
                }
            }

            LOGGER.log(Level.INFO, "Results: " + partIterationKeys.size());
            return partIterationKeysToPartRevisions(partSearchQuery.isFetchHeadOnly(), partIterationKeys);
        } else {
            throw new NotAllowedException(searchResult.getErrorMessage());
        }
    }

    @Override
    @RolesAllowed({UserGroupMapping.ADMIN_ROLE_ID})
    public void indexAllWorkspacesData() throws AccountNotFoundException {
        Account account = accountManager.getMyAccount();
        for (Workspace workspace : workspaceDAO.getAll()) {
            doIndexWorkspaceData(account, workspace.getId());
        }
    }

    @Override
    @RolesAllowed({UserGroupMapping.ADMIN_ROLE_ID, UserGroupMapping.REGULAR_USER_ROLE_ID})
    public void indexWorkspaceData(String workspaceId)
            throws WorkspaceNotFoundException, AccountNotFoundException, AccessRightException {
        Account account = accountManager.getMyAccount();
        if (!contextManager.isCallerInRole(UserGroupMapping.ADMIN_ROLE_ID)) {
            userManager.checkAdmin(workspaceId);
        }
        doIndexWorkspaceData(account, workspaceId);
    }

    @Asynchronous
    private void doIndexWorkspaceData(Account account, String workspaceId) {
        try {
            // Clear workspace if exists, or recreate
            doDeleteWorkspaceIndex(account, workspaceId);
            Bulk.Builder bb = new Bulk.Builder().defaultIndex(workspaceId);
            bulkWorkspaceRequestBuilder(bb, workspaceId);

            BulkResult result = esClient.execute(bb.build());

            if (result.isSucceeded()) {
                mailer.sendBulkIndexationSuccess(account);
            } else {
                String failureMessage = result.getErrorMessage();
                LOGGER.log(Level.WARNING, "Failures while bulk indexing workspace [" + workspaceId + "]: \n" + failureMessage);
                mailer.sendBulkIndexationFailure(account, failureMessage);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot index the whole workspace: The Elasticsearch server does not seem to respond");
            mailer.sendBulkIndexationFailure(account, getString("IndexerNotAvailableForRequest", account.getLocale()));
        }

    }

    @Override
    public boolean ping() {
        try {
            JestResult result = esClient.execute(new Health.Builder().build());
            return result.isSucceeded();
        } catch (IOException e) {
            return false;
        }
    }

    private void doIndexDocumentIteration(DocumentIteration documentIteration) {
        try {
            DocumentResult execute = esClient.execute(indexRequest(documentIteration));
            if (execute.isSucceeded()) {
                LOGGER.log(Level.INFO, "Document iteration [" + documentIteration.getKey() + "] indexed");
            } else {
                LOGGER.log(Level.WARNING, "The document " + documentIteration.getKey() + " cannot be indexed : \n" + execute.getErrorMessage());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "The document " + documentIteration.getKey() + " cannot be indexed.", e);
        }

    }


    private void doIndexPartIteration(PartIteration partIteration) {
        try {
            DocumentResult result = esClient.execute(indexRequest(partIteration));
            if (!result.isSucceeded()) {
                LOGGER.log(Level.WARNING, "Cannot index part iteration " + partIteration + ": " + result.getErrorMessage());
            } else {
                LOGGER.log(Level.INFO, "Part iteration [" + partIteration.getKey() + "] indexed");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "The part " + partIteration.getKey() + " cannot be indexed.", e);
        }

    }

    private void doDeleteWorkspaceIndex(Account account, String workspaceId) {
        try {
            DocumentResult result = esClient.execute(new Delete.Builder(IndexerUtils.formatIndexName(workspaceId))
                    .build());
            if (!result.isSucceeded()) {
                LOGGER.log(Level.WARNING, "Cannot delete index for workspace [" + workspaceId + "] : " + result.getErrorMessage());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Cannot delete index for workspace [" + workspaceId
                    + "]: The Elasticsearch server does not seem to respond. Consider deleting it manually.");
            mailer.sendWorkspaceIndexationFailure(account, workspaceId, getString("IndexerNotAvailableForRequest", account.getLocale()));
        }
    }

    private void createIndex(String pIndex) throws IOException {

        Settings settings = Settings.builder()
                .loadFromStream(ANALYZER_SETTING_RESOURCE, this.getClass().getResourceAsStream(ANALYZER_SETTING_RESOURCE))
                .put("number_of_shards", indexerConfigManager.getNumberOfShards())
                .put("number_of_replicas", indexerConfigManager.getNumberOfReplicas())
                .put("auto_expand_replicas", indexerConfigManager.getAutoExpandReplicas())
                .build();

        JestResult result = esClient.execute(new CreateIndex.Builder(pIndex)
                .settings(settings.getAsMap())
                .build());

        if (!result.isSucceeded()) {
            LOGGER.log(Level.WARNING, "Cannot create index settings: " + result.getErrorMessage());
            return;
        }

        result = esClient.execute(new PutMapping.Builder(pIndex,
                IndexerMapping.DEFAULT_TYPE,
                IndexerMapping.createSourceMapping()).build());


        if (!result.isSucceeded()) {
            LOGGER.log(Level.WARNING, "Cannot create index DEFAULT_TYPE mappings: " + result.getErrorMessage());
            return;
        }

        result = esClient.execute(new PutMapping.Builder(pIndex,
                IndexerMapping.PART_TYPE,
                IndexerMapping.createPartIterationMapping()).build());


        if (!result.isSucceeded()) {
            LOGGER.log(Level.WARNING, "Cannot create index PART_TYPE mappings: " + result.getErrorMessage());
            return;
        }

        result = esClient.execute(new PutMapping.Builder(pIndex,
                IndexerMapping.DOCUMENT_TYPE,
                IndexerMapping.createDocumentIterationMapping()).build());


        if (!result.isSucceeded()) {
            LOGGER.log(Level.WARNING, "Cannot create index DOCUMENT_TYPE mappings: " + result.getErrorMessage());
            return;
        }

        LOGGER.log(Level.INFO, "Index created [" + pIndex + "]");

    }

    private Bulk.Builder bulkWorkspaceRequestBuilder(Bulk.Builder pBulkRequest, String workspaceId) {
        String index = IndexerUtils.formatIndexName(workspaceId);

        try {
            createIndex(index);
        } catch (IOException e) {
            //Throw an application exception?
            LOGGER.log(Level.WARNING, "Cannot create index for workspace [" + workspaceId + "]", e);
        }

        bulkDocumentsIndexRequestBuilder(pBulkRequest, workspaceId);
        bulkPartsIndexRequestBuilder(pBulkRequest, workspaceId);

        return pBulkRequest;
    }

    private Bulk.Builder bulkDocumentsIndexRequestBuilder(Bulk.Builder pBulkRequest, String workspaceId) {
        Long countByWorkspace = documentMasterDAO.getCountByWorkspace(workspaceId);
        Integer limit = 1000;
        int numberOfPage = (int) Math.ceil(countByWorkspace.doubleValue() / limit.doubleValue());

        for (int pageIndex = 0; pageIndex < numberOfPage; pageIndex++) {
            int offset = pageIndex * limit;
            for (DocumentMaster docM : documentMasterDAO.getPaginatedByWorkspace(workspaceId, limit, offset)) {
                for (DocumentRevision docR : docM.getDocumentRevisions()) {
                    docR.getDocumentIterations().stream().filter(documentIteration -> documentIteration.getCheckInDate() != null)
                            .forEach(documentIteration -> {
                                try {
                                    pBulkRequest.addAction(indexRequest(documentIteration));
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "The document " + documentIteration + " cannot be indexed.", e);
                                }
                            });
                }
            }
        }

        return pBulkRequest;
    }

    private Bulk.Builder bulkPartsIndexRequestBuilder(Bulk.Builder pBulkRequest, String workspaceId) {
        Long countByWorkspace = partMasterDAO.getCountByWorkspace(workspaceId);
        Integer limit = 1000;
        int numberOfPage = (int) Math.ceil(countByWorkspace.doubleValue() / limit.doubleValue());

        for (int pageIndex = 0; pageIndex < numberOfPage; pageIndex++) {
            int offset = pageIndex * limit;
            for (PartMaster partMaster : partMasterDAO.getPaginatedByWorkspace(workspaceId, limit, offset)) {
                for (PartRevision partRev : partMaster.getPartRevisions()) {
                    partRev.getPartIterations().stream().filter(partIteration -> partIteration.getCheckInDate() != null)
                            .forEach(partIteration -> {
                                try {
                                    pBulkRequest.addAction(indexRequest(partIteration));
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "The part " + partIteration.getKey() + " cannot be indexed.", e);
                                }
                            });
                }
            }
        }
        return pBulkRequest;
    }


    private Update indexRequest(DocumentIteration documentIteration) throws IOException {
        Map<String, String> contentInputs = getContentInputs(documentIteration.getAttachedFiles());
        try (XContentBuilder xcb = XContentFactory.jsonBuilder()) {
            xcb.startObject()
                    .field("doc_as_upsert", true)
                    .startObject("doc");
            IndexerMapping.documentIterationToJSON(xcb, documentIteration, contentInputs);
            xcb.endObject().endObject();
            return new Update.Builder(xcb.string())
                    .index(IndexerUtils.formatIndexName(documentIteration.getWorkspaceId()))
                    .type(IndexerMapping.DOCUMENT_TYPE)
                    .id(documentIteration.getKey().toString())
                    .build();
        }
    }

    private Update indexRequest(PartIteration partIteration) throws IOException {
        Map<String, String> contentInputs = getContentInputs(partIteration.getAttachedFiles());
        try (XContentBuilder xcb = XContentFactory.jsonBuilder()) {
            xcb.startObject()
                    .field("doc_as_upsert", true)
                    .startObject("doc");
            IndexerMapping.partIterationToJSON(xcb, partIteration, contentInputs);
            xcb.endObject().endObject();
            return new Update.Builder(xcb.string())
                    .index(IndexerUtils.formatIndexName(partIteration.getWorkspaceId()))
                    .type(IndexerMapping.PART_TYPE)
                    .id(partIteration.getKey().toString())
                    .build();
        }
    }

    private Map<String, String> getContentInputs(Set<BinaryResource> attachedFiles) {
        Map<String, String> contentInputs = new HashMap<>();
        for (BinaryResource bin : attachedFiles) {
            try (InputStream in = storageManager.getBinaryResourceInputStream(bin)) {
                contentInputs.put(bin.getName(), IndexerUtils.streamToString(bin.getFullName(), in));
            } catch (StorageException | IOException e) {
                LOGGER.log(Level.WARNING, "Cannot read file " + bin.getFullName(), e);
            }
        }
        return contentInputs;
    }

    private Delete deleteRequest(DocumentIteration documentIteration) {
        return new Delete.Builder(documentIteration.getKey().toString())
                .index(IndexerUtils.formatIndexName(documentIteration.getWorkspaceId()))
                .type(IndexerMapping.DOCUMENT_TYPE)
                .build();
    }

    private Delete deleteRequest(PartIteration partIteration) {
        return new Delete.Builder(partIteration.getKey().toString())
                .index(IndexerUtils.formatIndexName(partIteration.getWorkspaceId()))
                .type(IndexerMapping.PART_TYPE)
                .build();
    }

    private String getString(String key, Locale locale) {
        Properties properties = PropertiesLoader.loadLocalizedProperties(locale, I18N_CONF, getClass());
        return properties.getProperty(key);
    }

    private List<DocumentRevision> documentIterationKeysToDocumentRevisions(boolean fetchHeadOnly, Set<DocumentIterationKey> documentIterationKeys) {

        Set<DocumentRevision> documentRevisions = new HashSet<>();

        for (DocumentIterationKey documentIterationKey : documentIterationKeys) {

            DocumentRevision documentRevision = getDocumentRevision(documentIterationKey.getDocumentRevision());

            if (documentRevision != null && !documentRevisions.contains(documentRevision)) {
                if (fetchHeadOnly) {
                    if (documentRevision.getLastCheckedInIteration().getKey().equals(documentIterationKey)) {
                        documentRevisions.add(documentRevision);
                    }
                } else {
                    documentRevisions.add(documentRevision);
                }
            }
        }

        return new ArrayList<>(documentRevisions);
    }

    private DocumentRevision getDocumentRevision(DocumentRevisionKey documentRevisionKey) {
        try {
            return documentRevisionDAO.loadDocR(documentRevisionKey);
        } catch (DocumentRevisionNotFoundException e) {
            LOGGER.log(Level.INFO, "Cannot infer document revision from key [" + documentRevisionKey + "]", e);
            return null;
        }
    }


    private List<PartRevision> partIterationKeysToPartRevisions(boolean fetchHeadOnly, Set<PartIterationKey> partIterationKeys) {

        Set<PartRevision> partRevisions = new HashSet<>();

        for (PartIterationKey partIterationKey : partIterationKeys) {

            PartRevision partRevision = getPartRevision(partIterationKey.getPartRevision());

            if (partRevision != null && !partRevisions.contains(partRevision)) {
                if (fetchHeadOnly) {
                    if (partRevision.getLastCheckedInIteration().getKey().equals(partIterationKey)) {
                        partRevisions.add(partRevision);
                    }
                } else {
                    partRevisions.add(partRevision);
                }
            }
        }

        return new ArrayList<>(partRevisions);

    }

    private PartRevision getPartRevision(PartRevisionKey partRevisionKey) {
        PartRevision partRevision = partRevisionDAO.loadPartR(partRevisionKey);

        if (partRevision == null) {
            LOGGER.log(Level.INFO, "Cannot infer part revision from key [" + partRevisionKey + "]");
        }
        return partRevision;
    }

}
