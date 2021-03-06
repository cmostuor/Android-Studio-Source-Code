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
package com.intellij.ide;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

@State(
  name = "RecentProjectsManager",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", roamingType = RoamingType.DISABLED),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/recentProjects.xml", roamingType = RoamingType.DISABLED)
  },
  storageChooser = LastStorageChooserForWrite.class
)
public class RecentProjectsManager extends RecentProjectsManagerBase {
  public RecentProjectsManager(MessageBus messageBus) {
    super(messageBus);
  }

  @Override
  protected String getProjectPath(@NotNull Project project) {
    return project.getPresentableUrl();
  }

  @Override
  protected void doOpenProject(@NotNull String projectPath, Project projectToClose, boolean forceOpenInNewFrame) {
    ProjectUtil.openProject(projectPath, projectToClose, forceOpenInNewFrame);
  }
}
