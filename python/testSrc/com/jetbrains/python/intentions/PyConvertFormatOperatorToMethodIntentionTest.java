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
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : ktisha
 */
public class PyConvertFormatOperatorToMethodIntentionTest extends PyIntentionTestCase {

  public void testSimple() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testMulti() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testEscaped() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testUnicode() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  // PY-9176
  public void testConcatenated() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20752
  public void testTupleReference() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20798
  public void testDictReference() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);    
  }

  // PY-20798
  public void testDictCallReference() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20754
  public void testBytes() {
    doNegativeTest(PyBundle.message("INTN.replace.with.method"));
  }

  // PY-20800
  public void testRepr() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
  
  // PY-20803
  public void testStarWidth() {
    doNegativeTest(PyBundle.message("INTN.replace.with.method"));
  }
  
  // PY-20803
  public void testStarPrecision() {
    doNegativeTest(PyBundle.message("INTN.replace.with.method"));
  }

  // PY-20803
  public void testStarWidthPrecision() {
    doNegativeTest(PyBundle.message("INTN.replace.with.method"));    
  }
  
  // PY-20876
  public void testSet() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
}