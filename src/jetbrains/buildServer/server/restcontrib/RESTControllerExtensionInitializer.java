/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.server.restcontrib;

import jetbrains.buildServer.server.rest.RESTControllerExtensionAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Yegor.Yarko
 *         Date: 20.06.2010
 */
public class RESTControllerExtensionInitializer extends RESTControllerExtensionAdapter {
    @Override
    @NotNull
    public String getPackage() {
        return "jetbrains.buildServer.server.restcontrib";
    }
}
