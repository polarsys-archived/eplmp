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

package org.polarsys.eplmp.server.rest.dto.baseline;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.polarsys.eplmp.core.configuration.ProductBaselineType;
import org.polarsys.eplmp.server.rest.dto.LightPartLinkListDTO;
import org.polarsys.eplmp.server.rest.dto.PathToPathLinkDTO;
import org.polarsys.eplmp.server.rest.dto.UserDTO;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@XmlRootElement
@ApiModel(value="ProductBaselineDTO", description="This class is the representation of {@link org.polarsys.eplmp.core.configuration.ProductBaseline} entity")
public class ProductBaselineDTO implements Serializable {

    @ApiModelProperty(value = "Baseline id")
    private int id;

    @ApiModelProperty(value = "Baseline name")
    private String name;

    @ApiModelProperty(value = "Baseline description")
    private String description;

    @ApiModelProperty(value = "Baseline creation date")
    private Date creationDate;

    @ApiModelProperty(value = "Configuration item in use")
    private String configurationItemId;

    @ApiModelProperty(value = "Configuration item latest revision")
    private String configurationItemLatestRevision;

    @ApiModelProperty(value = "Baseline type")
    private ProductBaselineType type;

    @ApiModelProperty(value = "Baselined part list")
    private List<BaselinedPartDTO> baselinedParts;

    @ApiModelProperty(value = "Baseline substitute links used, as id list")
    private List<String> substituteLinks;

    @ApiModelProperty(value = "Baseline optional links retained, as id list")
    private List<String> optionalUsageLinks;

    @ApiModelProperty(value = "Baseline substitutes links, as part list")
    private List<LightPartLinkListDTO> substitutesParts;

    @ApiModelProperty(value = "Baseline optional links retained, as part list")
    private List<LightPartLinkListDTO> optionalsParts;

    @ApiModelProperty(value = "Baseline author")
    private UserDTO author;

    @ApiModelProperty(value = "Baseline has obsolete parts flag")
    private boolean hasObsoletePartRevisions;

    @ApiModelProperty(value = "Baseline path to path links in structure")
    private List<PathToPathLinkDTO> pathToPathLinks;

    public ProductBaselineDTO() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public List<BaselinedPartDTO> getBaselinedParts() {
        return baselinedParts;
    }

    public void setBaselinedParts(List<BaselinedPartDTO> baselinedParts) {
        this.baselinedParts = baselinedParts;
    }

    public String getConfigurationItemId() {
        return configurationItemId;
    }

    public void setConfigurationItemId(String configurationItemId) {
        this.configurationItemId = configurationItemId;
    }

    public List<String> getSubstituteLinks() {
        return substituteLinks;
    }

    public void setSubstituteLinks(List<String> substituteLinks) {
        this.substituteLinks = substituteLinks;
    }

    public List<String> getOptionalUsageLinks() {
        return optionalUsageLinks;
    }

    public void setOptionalUsageLinks(List<String> optionalUsageLinks) {
        this.optionalUsageLinks = optionalUsageLinks;
    }

    public String getConfigurationItemLatestRevision() {
        return configurationItemLatestRevision;
    }

    public void setConfigurationItemLatestRevision(String configurationItemLatestRevision) {
        this.configurationItemLatestRevision = configurationItemLatestRevision;
    }

    public List<LightPartLinkListDTO> getSubstitutesParts() {
        return substitutesParts;
    }

    public void setSubstitutesParts(List<LightPartLinkListDTO> substitutesParts) {
        this.substitutesParts = substitutesParts;
    }

    public List<LightPartLinkListDTO> getOptionalsParts() {
        return optionalsParts;
    }

    public void setOptionalsParts(List<LightPartLinkListDTO> optionalsParts) {
        this.optionalsParts = optionalsParts;
    }

    public UserDTO getAuthor() {
        return author;
    }

    public void setAuthor(UserDTO author) {
        this.author = author;
    }

    public boolean isHasObsoletePartRevisions() {
        return hasObsoletePartRevisions;
    }

    public void setHasObsoletePartRevisions(boolean hasObsoletePartRevisions) {
        this.hasObsoletePartRevisions = hasObsoletePartRevisions;
    }

    public List<PathToPathLinkDTO> getPathToPathLinks() {
        return pathToPathLinks;
    }

    public void setPathToPathLinks(List<PathToPathLinkDTO> pathToPathLinks) {
        this.pathToPathLinks = pathToPathLinks;
    }

    public ProductBaselineType getType() {
        return type;
    }

    public void setType(ProductBaselineType type) {
        this.type = type;
    }
}
