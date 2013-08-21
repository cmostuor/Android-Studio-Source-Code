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
package com.intellij.tasks.generic;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;


class HighlightedSelectorsTable extends TableView<Selector> {

  private final FileType myValueFileType;
  private final Project myProject;

  public HighlightedSelectorsTable(@NotNull FileType valueFileType, @NotNull Project project) {
    this(valueFileType, project, ContainerUtil.<Selector>emptyList());
  }

  public HighlightedSelectorsTable(@NotNull final FileType valueFileType, @NotNull final Project project, @NotNull final List<Selector> selectors) {
    super(new ListTableModel<Selector>(new ColumnInfo[]{
      new ColumnInfo<Selector, String>("Name") {
        @Nullable
        @Override
        public String valueOf(Selector selector) {
          return selector.getName();
        }
      },
      new ColumnInfo<Selector, String>("Path") {
        @Nullable
        @Override
        public String valueOf(Selector selector) {
          return selector.getPath();
        }

        @Override
        public boolean isCellEditable(Selector selector) {
          return true;
        }

        @Override
        public void setValue(Selector selector, String value) {
          selector.setPath(value);
        }

        @Nullable
        @Override
        public TableCellRenderer getRenderer(Selector selector) {
          return new LanguageTextFieldRenderer(((LanguageFileType)valueFileType).getLanguage(), project);
        }
      }
    }, selectors, 0));
    myValueFileType = valueFileType;
    myProject = project;
  }
  public List<Selector> getSelectors() {
    return getItems();
  }

  private static class LanguageTextFieldRenderer implements TableCellRenderer {
    private final Project myProject;
    private final Language myLanguage;

    private LanguageTextFieldRenderer(Language language, Project project) {
      myProject = project;
      myLanguage = language;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return new LanguageTextField(myLanguage, myProject, (String)value);
    }
  }
}