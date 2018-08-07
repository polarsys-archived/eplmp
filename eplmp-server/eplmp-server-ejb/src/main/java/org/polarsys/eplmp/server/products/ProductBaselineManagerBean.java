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

package org.polarsys.eplmp.server.products;

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.configuration.*;
import org.polarsys.eplmp.core.document.DocumentIteration;
import org.polarsys.eplmp.core.document.DocumentLink;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IProductBaselineManagerLocal;
import org.polarsys.eplmp.core.services.IProductManagerLocal;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.core.util.Tools;
import org.polarsys.eplmp.server.configuration.PSFilterVisitor;
import org.polarsys.eplmp.server.configuration.PSFilterVisitorCallbacks;
import org.polarsys.eplmp.server.configuration.filter.LatestPSFilter;
import org.polarsys.eplmp.server.configuration.filter.ReleasedPSFilter;
import org.polarsys.eplmp.server.configuration.spec.ProductBaselineCreationConfigSpec;
import org.polarsys.eplmp.server.dao.*;
import org.polarsys.eplmp.server.factory.ACLFactory;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.*;

@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
@Local(IProductBaselineManagerLocal.class)
@Stateless(name = "ProductBaselineManagerBean")
public class ProductBaselineManagerBean implements IProductBaselineManagerLocal {

    @Inject
    private EntityManager em;

    @Inject
    private ACLDAO aclDAO;

    @Inject
    private ACLFactory aclFactory;

    @Inject
    private ConfigurationItemDAO configurationItemDAO;

    @Inject
    private DocumentCollectionDAO documentCollectionDAO;

    @Inject
    private PartCollectionDAO partCollectionDAO;

    @Inject
    private PathToPathLinkDAO pathToPathLinkDAO;

    @Inject
    private ProductBaselineDAO productBaselineDAO;

    @Inject
    private ProductConfigurationDAO productConfigurationDAO;

    @Inject
    private ProductInstanceIterationDAO productInstanceIterationDAO;

    @Inject
    private IUserManagerLocal userManager;

    @Inject
    private IProductManagerLocal productManager;

    @Inject
    private PSFilterVisitor psFilterVisitor;

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductBaseline createBaseline(ConfigurationItemKey ciKey, String name, ProductBaselineType pType, String description, List<PartIterationKey> partIterationKeys, List<String> substituteLinks, List<String> optionalUsageLinks) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, NotAllowedException, EntityConstraintException, PartMasterNotFoundException, CreationException, BaselineNotFoundException, PathToPathLinkAlreadyExistsException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceWriteAccess(ciKey.getWorkspace());

        if (null == name || name.isEmpty()) {
            throw new NotAllowedException("NotAllowedException61");
        }

        ConfigurationItem configurationItem = configurationItemDAO.loadConfigurationItem(ciKey);

        List<PartIteration> partIterations = new ArrayList<>();
        for (PartIterationKey piKey : partIterationKeys) {
            partIterations.add(em.find(PartIteration.class, piKey));
        }

        ProductBaselineCreationConfigSpec filter = new ProductBaselineCreationConfigSpec(user, pType, partIterations, substituteLinks, optionalUsageLinks);

        List<String> visitedPaths = new ArrayList<>();

        psFilterVisitor.visit(user, filter, configurationItem.getDesignItem(), -1, new PSFilterVisitorCallbacks() {
            @Override
            public void onIndeterminateVersion(PartMaster partMaster, List<PartIteration> partIterations) throws NotAllowedException {
                throw new NotAllowedException("NotAllowedException48");
            }

            @Override
            public void onUnresolvedVersion(PartMaster partMaster) throws NotAllowedException {
                throw new NotAllowedException("NotAllowedException49");
            }

            @Override
            public void onIndeterminatePath(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) throws NotAllowedException {
                throw new NotAllowedException("NotAllowedException50");
            }

            @Override
            public void onUnresolvedPath(List<PartLink> pCurrentPath, List<PartIteration> partIterations) throws NotAllowedException {
                throw new NotAllowedException("NotAllowedException51");
            }

            @Override
            public void onBranchDiscovered(List<PartLink> pCurrentPath, List<PartIteration> copyPartIteration) {
                // Unused here
            }

            @Override
            public void onOptionalPath(List<PartLink> path, List<PartIteration> partIterations) {
                // Unused here
            }

            @Override
            public boolean onPathWalk(List<PartLink> path, List<PartMaster> parts) {
                // Unused here
                String encodedPath = Tools.getPathAsString(path);
                visitedPaths.add(encodedPath);
                return true;
            }
        });

        // Visitor has finished, and should have thrown an exception if errors
        ProductBaseline baseline = new ProductBaseline(user, configurationItem, name, pType, description);
        DocumentCollection documentCollection = baseline.getDocumentCollection();
        PartCollection partCollection = baseline.getPartCollection();

        partCollectionDAO.createPartCollection(partCollection);
        documentCollectionDAO.createDocumentCollection(documentCollection);

        partCollection.setCreationDate(new Date());
        partCollection.setAuthor(user);

        documentCollection.setCreationDate(new Date());
        documentCollection.setAuthor(user);


        for (PartIteration partIteration : filter.getRetainedPartIterations()) {
            baseline.addBaselinedPart(partIteration);
            for (DocumentLink docLink : partIteration.getLinkedDocuments()) {
                DocumentIteration docI = docLink.getTargetDocument().getLastCheckedInIteration();
                if (docI != null)
                    baseline.addBaselinedDocument(docI);
            }
        }

        baseline.getSubstituteLinks().addAll(filter.getRetainedSubstituteLinks());
        baseline.getOptionalUsageLinks().addAll(filter.getRetainedOptionalUsageLinks());

        productBaselineDAO.createBaseline(baseline);

        // Copy PathToPathLink list to baseline
        List<PathToPathLink> links = pathToPathLinkDAO.getPathToPathLinkFromPathList(configurationItem, visitedPaths);
        for (PathToPathLink link : links) {
            PathToPathLink clone = link.clone();
            pathToPathLinkDAO.createPathToPathLink(clone);
            baseline.addPathToPathLink(clone);
        }

        return baseline;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<ProductBaseline> getAllBaselines(String workspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(workspaceId);
        return productBaselineDAO.findBaselines(workspaceId);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<ProductBaseline> getBaselines(ConfigurationItemKey configurationItemKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(configurationItemKey.getWorkspace());
        return productBaselineDAO.findBaselines(configurationItemKey.getId(), configurationItemKey.getWorkspace());
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteBaseline(String pWorkspaceId, int baselineId) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, BaselineNotFoundException, UserNotActiveException, EntityConstraintException, WorkspaceNotEnabledException {

        userManager.checkWorkspaceReadAccess(pWorkspaceId);

        ProductBaseline productBaseline = productBaselineDAO.loadBaseline(baselineId);

        userManager.checkWorkspaceWriteAccess(productBaseline.getConfigurationItem().getWorkspaceId());

        if (productInstanceIterationDAO.isBaselinedUsed(productBaseline)) {
            throw new EntityConstraintException("EntityConstraintException16");
        }

        productBaselineDAO.deleteBaseline(productBaseline);

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductBaseline getBaseline(int baselineId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, BaselineNotFoundException, WorkspaceNotEnabledException {
        ProductBaseline productBaseline = productBaselineDAO.loadBaseline(baselineId);
        userManager.checkWorkspaceReadAccess(productBaseline.getConfigurationItem().getWorkspaceId());
        return productBaseline;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductBaseline getBaselineById(int baselineId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        ProductBaseline productBaseline = productBaselineDAO.findBaselineById(baselineId);
        Workspace workspace = productBaseline.getConfigurationItem().getWorkspace();
        userManager.checkWorkspaceReadAccess(workspace.getId());
        return productBaseline;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<BaselinedPart> getBaselinedPartWithReference(int baselineId, String q, int maxResults) throws BaselineNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        ProductBaseline productBaseline = productBaselineDAO.loadBaseline(baselineId);
        userManager.checkWorkspaceReadAccess(productBaseline.getConfigurationItem().getWorkspaceId());
        return productBaselineDAO.findBaselinedPartWithReferenceLike(productBaseline.getPartCollection().getId(), q, maxResults);
    }


    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<PathChoice> getBaselineCreationPathChoices(ConfigurationItemKey ciKey, ProductBaselineType type) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, PartMasterNotFoundException, NotAllowedException, EntityConstraintException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());
        ConfigurationItem configurationItem = configurationItemDAO.loadConfigurationItem(ciKey);

        ProductStructureFilter filter;

        if (type == null || type.equals(ProductBaselineType.RELEASED)) {
            filter = new ReleasedPSFilter(user, true);
        } else {
            filter = new LatestPSFilter(user, true);
        }

        List<PathChoice> choices = new ArrayList<>();

        psFilterVisitor.visit(user, filter, configurationItem.getDesignItem(), -1, new PSFilterVisitorCallbacks() {
            @Override
            public void onIndeterminateVersion(PartMaster partMaster, List<PartIteration> partIterations) throws NotAllowedException {
                // Unused here
            }

            @Override
            public void onIndeterminatePath(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) {
                addPartChoice(pCurrentPath, pCurrentPathPartIterations);
            }

            @Override
            public void onUnresolvedPath(List<PartLink> pCurrentPath, List<PartIteration> partIterations) throws NotAllowedException {
                // Unused here
            }

            @Override
            public void onBranchDiscovered(List<PartLink> pCurrentPath, List<PartIteration> copyPartIteration) {
                // Unused here
            }

            @Override
            public void onOptionalPath(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) {
                addPartChoice(pCurrentPath, pCurrentPathPartIterations);
            }

            @Override
            public boolean onPathWalk(List<PartLink> path, List<PartMaster> parts) {
                // Unused here
                return true;
            }

            @Override
            public void onUnresolvedVersion(PartMaster partMaster) {
                // Unused here
            }

            private void addPartChoice(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) {
                List<ResolvedPartLink> resolvedPath = new ArrayList<>();
                for (int i = 0; i < pCurrentPathPartIterations.size(); i++) {
                    resolvedPath.add(new ResolvedPartLink(pCurrentPathPartIterations.get(i), pCurrentPath.get(i)));
                }
                choices.add(new PathChoice(resolvedPath, pCurrentPath.get(pCurrentPath.size() - 1)));
            }
        });

        return choices;
    }


    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<PartIteration> getBaselineCreationVersionsChoices(ConfigurationItemKey ciKey) throws ConfigurationItemNotFoundException, UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, PartMasterNotFoundException, NotAllowedException, EntityConstraintException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());

        ConfigurationItem configurationItem = configurationItemDAO.loadConfigurationItem(ciKey);

        Set<PartIteration> parts = new HashSet<>();

        ProductStructureFilter filter = new ReleasedPSFilter(user, true);

        psFilterVisitor.visit(user, filter, configurationItem.getDesignItem(), -1, new PSFilterVisitorCallbacks() {
            @Override
            public void onIndeterminateVersion(PartMaster partMaster, List<PartIteration> partIterations) throws NotAllowedException {
                parts.addAll(partIterations);
            }

            @Override
            public void onIndeterminatePath(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) {
                // Unused here
            }

            @Override
            public void onUnresolvedPath(List<PartLink> pCurrentPath, List<PartIteration> partIterations) throws NotAllowedException {
                // Unused here
            }

            @Override
            public void onBranchDiscovered(List<PartLink> pCurrentPath, List<PartIteration> copyPartIteration) {
                // Unused here
            }

            @Override
            public void onOptionalPath(List<PartLink> path, List<PartIteration> partIterations) {
                // Unused here
            }

            @Override
            public boolean onPathWalk(List<PartLink> path, List<PartMaster> parts) {
                // Unused here
                return true;
            }

            @Override
            public void onUnresolvedVersion(PartMaster partMaster) {
                // Unused here
            }
        });

        return new ArrayList<>(parts);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductConfiguration createProductConfiguration(ConfigurationItemKey ciKey, String name, String description, List<String> substituteLinks, List<String> optionalUsageLinks, Map<String, String> aclUserEntries, Map<String, String> aclUserGroupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, CreationException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceWriteAccess(ciKey.getWorkspace());

        ConfigurationItem configurationItem = configurationItemDAO.loadConfigurationItem(ciKey);

        ProductConfiguration productConfiguration = new ProductConfiguration(user, configurationItem, name, description, null);

        if (aclUserEntries != null && !aclUserEntries.isEmpty() || aclUserGroupEntries != null && !aclUserGroupEntries.isEmpty()) {
            ACL acl = aclFactory.createACL(ciKey.getWorkspace(), aclUserEntries, aclUserGroupEntries);
            productConfiguration.setAcl(acl);
        }
        productConfiguration.setOptionalUsageLinks(new HashSet<>(optionalUsageLinks));
        productConfiguration.setSubstituteLinks(new HashSet<>(substituteLinks));

        productConfigurationDAO.createProductConfiguration(productConfiguration);
        return productConfiguration;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<ProductConfiguration> getAllProductConfigurations(String workspaceId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        List<ProductConfiguration> productConfigurations = productConfigurationDAO.getAllProductConfigurations(workspaceId);

        ListIterator<ProductConfiguration> ite = productConfigurations.listIterator();

        while (ite.hasNext()) {
            ProductConfiguration next = ite.next();
            try {
                checkProductConfigurationReadAccess(workspaceId, next, user);
            } catch (AccessRightException e) {
                ite.remove();
            }
        }

        return productConfigurations;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<ProductConfiguration> getAllProductConfigurationsByConfigurationItemId(ConfigurationItemKey ciKey) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());

        List<ProductConfiguration> productConfigurations = productConfigurationDAO.getAllProductConfigurationsByConfigurationItem(ciKey);

        ListIterator<ProductConfiguration> ite = productConfigurations.listIterator();

        while (ite.hasNext()) {
            ProductConfiguration next = ite.next();
            try {
                checkProductConfigurationReadAccess(ciKey.getWorkspace(), next, user);
            } catch (AccessRightException e) {
                ite.remove();
            }
        }

        return productConfigurations;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductConfiguration getProductConfiguration(ConfigurationItemKey ciKey, int productConfigurationId)
            throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductConfigurationNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());

        ProductConfiguration productConfiguration = productConfigurationDAO.getProductConfiguration(productConfigurationId);

        String workspaceId = productConfiguration.getConfigurationItem().getWorkspace().getId();

        if (!workspaceId.equals(ciKey.getWorkspace())) {
            throw new AccessRightException(user);
        }

        checkProductConfigurationReadAccess(workspaceId, productConfiguration, user);

        return productConfiguration;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public ProductConfiguration updateProductConfiguration(ConfigurationItemKey ciKey, int productConfigurationId, String name, String description, List<String> substituteLinks, List<String> optionalUsageLinks) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductConfigurationNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());
        ProductConfiguration productConfiguration = productConfigurationDAO.getProductConfiguration(productConfigurationId);

        String workspaceId = productConfiguration.getConfigurationItem().getWorkspace().getId();

        if (!workspaceId.equals(ciKey.getWorkspace())) {
            throw new AccessRightException(user);
        }

        checkProductConfigurationWriteAccess(workspaceId, productConfiguration, user);

        productConfiguration.setName(name);
        productConfiguration.setDescription(description);
        productConfiguration.setSubstituteLinks(new HashSet<>(substituteLinks));
        productConfiguration.setOptionalUsageLinks(new HashSet<>(optionalUsageLinks));

        return null;
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void deleteProductConfiguration(ConfigurationItemKey ciKey, int productConfigurationId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductConfigurationNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());
        ProductConfiguration productConfiguration = productConfigurationDAO.getProductConfiguration(productConfigurationId);

        String workspaceId = productConfiguration.getConfigurationItem().getWorkspace().getId();

        if (!workspaceId.equals(ciKey.getWorkspace())) {
            throw new AccessRightException(user);
        }

        checkProductConfigurationWriteAccess(workspaceId, productConfiguration, user);

        productConfigurationDAO.deleteProductConfiguration(productConfiguration);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void updateACLForConfiguration(ConfigurationItemKey ciKey, int productConfigurationId, Map<String, String> userEntries, Map<String, String> groupEntries) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductConfigurationNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        // Check the read access to the workspace
        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());

        ProductConfiguration productConfiguration = productConfigurationDAO.getProductConfiguration(productConfigurationId);

        String workspaceId = productConfiguration.getConfigurationItem().getWorkspaceId();

        if (!workspaceId.equals(ciKey.getWorkspace())) {
            throw new AccessRightException(user);
        }

        checkProductConfigurationWriteAccess(workspaceId, productConfiguration, user);

        if (productConfiguration.getAcl() == null) {
            ACL acl = aclFactory.createACL(workspaceId, userEntries, groupEntries);
            productConfiguration.setAcl(acl);
        } else {
            aclFactory.updateACL(workspaceId, productConfiguration.getAcl(), userEntries, groupEntries);
        }
    }

    private User checkProductConfigurationWriteAccess(String workspaceId, ProductConfiguration productConfiguration, User user) throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, WorkspaceNotEnabledException {
        if (user.isAdministrator()) {
            // Check if it is the workspace's administrator
            return user;
        }
        if (productConfiguration.getAcl() == null) {
            // Check if the item haven't ACL
            return userManager.checkWorkspaceWriteAccess(workspaceId);
        } else if (productConfiguration.getAcl().hasWriteAccess(user)) {
            // Check if there is a write access
            return user;
        } else {
            // Else throw a AccessRightException
            throw new AccessRightException(user);
        }
    }


    private User checkProductConfigurationReadAccess(String workspaceId, ProductConfiguration productConfiguration, User user) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, AccessRightException, WorkspaceNotEnabledException {
        if (user.isAdministrator()) {
            // Check if it is the workspace's administrator
            return user;
        }
        if (productConfiguration.getAcl() == null) {
            // Check if the item haven't ACL
            return userManager.checkWorkspaceReadAccess(workspaceId);
        } else if (productConfiguration.getAcl().hasReadAccess(user)) {
            // Check if there is a write access
            return user;
        } else {
            // Else throw a AccessRightException
            throw new AccessRightException(user);
        }
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public void removeACLFromConfiguration(ConfigurationItemKey ciKey, int productConfigurationId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, ProductConfigurationNotFoundException, AccessRightException, WorkspaceNotEnabledException {

        User user = userManager.checkWorkspaceReadAccess(ciKey.getWorkspace());

        ProductConfiguration productConfiguration = productConfigurationDAO.getProductConfiguration(productConfigurationId);

        String workspaceId = productConfiguration.getConfigurationItem().getWorkspaceId();

        if (!workspaceId.equals(ciKey.getWorkspace())) {
            throw new AccessRightException(user);
        }

        checkProductConfigurationWriteAccess(workspaceId, productConfiguration, user);

        ACL acl = productConfiguration.getAcl();
        if (acl != null) {
            aclDAO.removeACLEntries(acl);
            productConfiguration.setAcl(null);
        }

    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<PartRevision> getObsoletePartRevisionsInBaseline(String workspaceId, int baselineId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, BaselineNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(workspaceId);
        ProductBaseline baseline = productBaselineDAO.loadBaseline(baselineId);
        return productBaselineDAO.findObsoletePartsInBaseline(workspaceId, baseline);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<PathToPathLink> getPathToPathLinkFromSourceAndTarget(String workspaceId, String configurationItemId, int baselineId, String sourcePath, String targetPath) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, BaselineNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(workspaceId);
        ProductBaseline baseline = productBaselineDAO.loadBaseline(baselineId);
        return pathToPathLinkDAO.getPathToPathLinkFromSourceAndTarget(baseline, sourcePath, targetPath);
    }

    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    @Override
    public List<String> getPathToPathLinkTypes(String workspaceId, String configurationItemId, int baselineId) throws UserNotFoundException, UserNotActiveException, WorkspaceNotFoundException, BaselineNotFoundException, WorkspaceNotEnabledException {
        userManager.checkWorkspaceReadAccess(workspaceId);
        ProductBaseline baseline = productBaselineDAO.loadBaseline(baselineId);
        return pathToPathLinkDAO.getDistinctPathToPathLinkTypes(baseline);
    }

}
