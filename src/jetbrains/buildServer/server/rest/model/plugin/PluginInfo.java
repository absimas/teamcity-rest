/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.model.plugin;

import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import jetbrains.buildServer.server.rest.model.Properties;
import jetbrains.buildServer.web.plugins.bean.ServerPluginInfo;

/**
 * @author Yegor.Yarko
 *         Date: 16.11.2009
 */
@XmlRootElement(name = "plugin")
@XmlType(propOrder = {"loadPath", "version", "displayName", "name",
  "parameters"})
public class PluginInfo {
  ServerPluginInfo myPluginInfo;

  public PluginInfo() {
  }

  public PluginInfo(final ServerPluginInfo pluginInfo) {
    myPluginInfo = pluginInfo;
  }

  @XmlAttribute
  public String getName() {
    return myPluginInfo.getPluginName();
  }

  @XmlAttribute
  public String getDisplayName() {
    return myPluginInfo.getPluginXml().getInfo().getDisplayName();
  }

  @XmlAttribute
  public String getVersion() {
    return myPluginInfo.getPluginVersion();
  }

  @XmlAttribute
  public String getLoadPath() {
    return myPluginInfo.getPluginRoot().getAbsolutePath();
  }

  @XmlElement
  public Properties getParameters() {
    final Map<String, String> params = myPluginInfo.getPluginXml().getInfo().getParameters();
    if (params.size() > 0) {
      return new Properties(params);
    } else {
      return null;
    }
  }
}
