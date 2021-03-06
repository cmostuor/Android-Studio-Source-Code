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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReceiverParameter;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import org.jetbrains.annotations.NotNull;

public class PsiReceiverParameterImpl extends PsiParameterImpl implements PsiReceiverParameter {
  public PsiReceiverParameterImpl(@NotNull PsiParameterStub stub) {
    super(stub, JavaStubElementTypes.RECEIVER_PARAMETER);
  }

  public PsiReceiverParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "PsiReceiverParameter";
  }
}
