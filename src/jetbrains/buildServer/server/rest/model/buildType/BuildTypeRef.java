/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.APIController;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.data.BuildTypeFinder;
import jetbrains.buildServer.server.rest.data.DataProvider;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.util.BeanContext;
import jetbrains.buildServer.server.rest.util.BuildTypeOrTemplate;
import jetbrains.buildServer.serverSide.BuildTypeTemplate;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
@XmlRootElement(name = "buildType-ref")
@XmlType(name = "buildType-ref", propOrder = {"id", "internalId", "name", "href", "projectName", "projectId", "projectInternalId", "webUrl"})
public class BuildTypeRef {
  @Nullable protected BuildTypeOrTemplate myBuildType;
  private DataProvider myDataProvider;
  private ApiUrlBuilder myApiUrlBuilder;

  /**
   * @return External id of the build configuration
   */
  @XmlAttribute public String id;
  @XmlAttribute public String internalId;
  /**
   * This is used only when posting a link to a build type.
   */
  @XmlAttribute public String locator;

  public BuildTypeRef() {
  }

  public BuildTypeRef(@NotNull final SBuildType buildType, @NotNull final DataProvider dataProvider, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = new BuildTypeOrTemplate(buildType);
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;

    init(myBuildType);
  }

  private void init(@NotNull final BuildTypeOrTemplate buildType) {
    id = buildType.getId();
    internalId = TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME) ? buildType.getInternalId() : null;
  }

  public BuildTypeRef(@NotNull final BuildTypeTemplate buildType, @NotNull final DataProvider dataProvider, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = new BuildTypeOrTemplate(buildType);
    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;

    init(myBuildType);
  }

  /**
   * Creates a reference on a missing build type which has only external id known.
   * @param externalId
   */
  public BuildTypeRef(@Nullable final String externalId, @Nullable final String internalId, @NotNull final DataProvider dataProvider, @NotNull final ApiUrlBuilder apiUrlBuilder) {
    myBuildType = null;
    id = externalId;
    this.internalId = internalId; //todo: check usages: externalId should actually be NotNull and internal id should never be necessary

    myDataProvider = dataProvider;
    myApiUrlBuilder = apiUrlBuilder;
  }

  @XmlAttribute
  public String getName() {
    return myBuildType == null ? null : myBuildType.getName();
  }

  @XmlAttribute
  public String getHref() {
    return myBuildType == null ? null : myApiUrlBuilder.getHref(myBuildType);
  }

  @XmlAttribute
  public String getProjectId() {
    return myBuildType == null ? null : myBuildType.getProject().getExternalId();
  }

  @XmlAttribute
  public String getProjectInternalId() {
    return myBuildType == null ? null : (TeamCityProperties.getBoolean(APIController.INCLUDE_INTERNAL_ID_PROPERTY_NAME)
           ? myBuildType.getProject().getProjectId()
           : null);
  }

  @XmlAttribute
  public String getProjectName() {
    return myBuildType == null ? null : myBuildType.getProject().getName();
  }

  @XmlAttribute
  public String getWebUrl() {
    return myBuildType == null ? null : (myBuildType.isBuildType() ? myDataProvider.getBuildTypeUrl(myBuildType.getBuildType()) : null);
  }

  @Nullable
  public String getExternalIdFromPosted(@NotNull final BeanContext context) {
    if (id != null) {
      if (internalId == null) {
        return id;
      }
      String externalByInternal = context.getSingletonService(ProjectManager.class).getBuildTypeExternalIdByInternalId(internalId);
      if (externalByInternal == null || id.equals(externalByInternal)) {
        return id;
      }
      throw new BadRequestException("Both external id '" + id + "' and internal id '" + internalId + "' attributes are present and they reference different build types.");
    }
    if (internalId != null) {
      return context.getSingletonService(ProjectManager.class).getBuildTypeExternalIdByInternalId(internalId);
    }
    if (locator != null){
      return context.getSingletonService(BuildTypeFinder.class).getBuildType(null, locator).getExternalId();
    }
    throw new BadRequestException("Could not find build type by the data. Either 'id' or 'internalId' or 'locator' attributes should be specified.");
  }

  @Nullable
  public String getInternalIdFromPosted(@NotNull final BeanContext context) {
    if (internalId != null) {
      if (id == null) {
        return internalId;
      }
      String internalByExternal = context.getSingletonService(ProjectManager.class).getBuildTypeInternalIdByExternalId(id);
      if (internalByExternal == null || internalId.equals(internalByExternal)) {
        return internalId;
      }
      throw new BadRequestException("Both id '" + id + "' and internal id '" + internalId + "' attributes are present and they reference different build types.");
    }
    if (id != null) {
      return context.getSingletonService(ProjectManager.class).getBuildTypeInternalIdByExternalId(id);
    }
    if (locator != null){
      return context.getSingletonService(BuildTypeFinder.class).getBuildType(null, locator).getBuildTypeId();
    }
    throw new BadRequestException("Could not find build type by the data. Either 'id' or 'internalId' or 'locator' attributes should be specified.");
  }
}
