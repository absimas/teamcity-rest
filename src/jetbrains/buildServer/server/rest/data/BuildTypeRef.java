/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import javax.xml.bind.annotation.XmlAttribute;
import jetbrains.buildServer.server.rest.request.BuildTypeRequest;
import jetbrains.buildServer.serverSide.SBuildType;

/**
 * User: Yegor Yarko
 * Date: 29.03.2009
 */
public class BuildTypeRef {
  protected SBuildType myBuildType;

  public BuildTypeRef() {
  }

  public BuildTypeRef(SBuildType buildType) {
    myBuildType = buildType;
  }

  @XmlAttribute
  public String getHref() {
    return BuildTypeRequest.getBuildTypeHref(myBuildType);
  }

  @XmlAttribute
  public String getId() {
    return myBuildType.getBuildTypeId();
  }

  @XmlAttribute
  public String getName() {
    return myBuildType.getName();
  }
}
