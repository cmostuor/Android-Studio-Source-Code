/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.indexing;

import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Base implementation for indices that produce single value per single file
 *
 * @author Eugene Zhuravlev
 *         Date: Feb 18, 2009
 */
public abstract class SingleEntryFileBasedIndexExtension<V> extends FileBasedIndexExtension<Integer, V>{
  @NotNull
  @Override
  public final KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @NotNull
  @Override
  public abstract SingleEntryIndexer<V> getIndexer();

  @Override
  public boolean isKeyHighlySelective() {
    return true;
  }
}
