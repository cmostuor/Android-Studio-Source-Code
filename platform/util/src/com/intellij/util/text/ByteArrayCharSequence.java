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
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

public class ByteArrayCharSequence implements CharSequence {
  private final byte[] myChars;

  public ByteArrayCharSequence(@NotNull byte... chars) {
    myChars = chars;
  }

  @Override
  public final int length() {
    return myChars.length;
  }

  @Override
  public final char charAt(int index) {
    return (char)myChars[index];
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return start == 0 && end == length() ? this : new CharSequenceSubSequence(this, start, end);
  }

  @Override
  @NotNull
  public String toString() {
    return StringFactory.createShared(CharArrayUtil.fromSequence(this, 0, length()));
  }

}