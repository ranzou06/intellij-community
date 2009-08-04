/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class MethodParameterPanel extends AbstractInjectionPanel<MethodParameterInjection> {

  LanguagePanel myLanguagePanel;  // read by reflection
  AdvancedPanel myAdvancedPanel;

  private JPanel myRoot;
  private JPanel myClassPanel;
  private JCheckBox myHierarchy;

  private TreeTableView myParamsTable;

  private final ReferenceEditorWithBrowseButton myClassField;
  private DefaultMutableTreeNode myRootNode;

  private final THashMap<PsiMethod, MethodParameterInjection.MethodInfo> myData = new THashMap<PsiMethod, MethodParameterInjection.MethodInfo>();

  public MethodParameterPanel(MethodParameterInjection injection, final Project project) {
    super(injection, project);

    myClassField = new ReferenceEditorWithBrowseButton(new BrowseClassListener(project), project, new Function<String, Document>() {
      public Document fun(String s) {
        final Document document = PsiUtilEx.createDocument(s, project);
        document.addDocumentListener(new DocumentAdapter() {
          @Override
          public void documentChanged(final DocumentEvent e) {
            updateParamTree();
            updateTree();
          }
        });
        return document;
      }
    }, "");
    myClassPanel.add(myClassField, BorderLayout.CENTER);
    myParamsTable.getTree().setShowsRootHandles(true);
    myParamsTable.getTree().setCellRenderer(new ColoredTreeCellRenderer() {

      public void customizeCellRenderer(final JTree tree,
                                        final Object value,
                                        final boolean selected,
                                        final boolean expanded,
                                        final boolean leaf,
                                        final int row,
                                        final boolean hasFocus) {

        final Object o = ((DefaultMutableTreeNode)value).getUserObject();
        setIcon(o instanceof PsiMethod ? Icons.METHOD_ICON : o instanceof PsiParameter ? Icons.PARAMETER_ICON : null);
        final String name;
        if (o instanceof PsiMethod) {
          name = PsiFormatUtil.formatMethod((PsiMethod)o, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
        }
        else if (o instanceof PsiParameter) {
          name = PsiFormatUtil.formatVariable((PsiParameter)o, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
        }
        else name = null;
        final boolean missing = o instanceof PsiElement && !((PsiElement)o).isPhysical();
        if (name != null) {
          append(name, missing? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }

    });
    init(injection.copy());
    PeerFactory.getInstance().getUIHelper().installTreeTableSpeedSearch(myParamsTable, new Convertor<TreePath, String>() {
      @Nullable
      public String convert(final TreePath o) {
        final Object userObject = ((DefaultMutableTreeNode)o.getLastPathComponent()).getUserObject();
        return userObject instanceof PsiNamedElement? ((PsiNamedElement)userObject).getName() : null;
      }
    });
    new AnAction("Toggle") {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        performToggleAction();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myParamsTable);
  }

  private void performToggleAction() {
    final Collection<DefaultMutableTreeNode> selectedInjections = myParamsTable.getSelection();
    boolean enabledExists = false;
    boolean disabledExists = false;
    for (DefaultMutableTreeNode node : selectedInjections) {
      final Boolean nodeSelected = isNodeSelected(node);
      if (Boolean.TRUE == nodeSelected) enabledExists = true;
      else if (Boolean.FALSE == nodeSelected) disabledExists = true;
      if (enabledExists && disabledExists) break;
    }
    boolean allEnabled = !enabledExists && disabledExists;
    for (DefaultMutableTreeNode node : selectedInjections) {
      setNodeSelected(node, allEnabled);
    }
    myParamsTable.updateUI();
  }

  @Nullable
  private PsiType getClassType() {
    final Document document = myClassField.getEditorTextField().getDocument();
    final PsiDocumentManager dm = PsiDocumentManager.getInstance(myProject);
    dm.commitDocument(document);
    final PsiFile psiFile = dm.getPsiFile(document);
    if (psiFile == null) return null;
    try {
      return ((PsiTypeCodeFragment)psiFile).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e1) {
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e1) {
      return null;
    }
  }

  @Nullable
  private PsiClass findClass(final PsiType type) {
    if (type instanceof PsiClassType) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
      return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        public PsiClass compute() {
          return facade.findClass(type.getCanonicalText(), GlobalSearchScope.allScope(myProject));
        }
      });
    }
    return null;
  }

  private void setPsiClass(String name) {
    myClassField.setText(name);
  }

  private void updateParamTree() {
    rebuildTreeModel();
    refreshTreeStructure();
  }

  private void rebuildTreeModel() {
    myData.clear();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiClass psiClass = findClass(getClassType());
        if (psiClass == null) return;
        final List<PsiMethod> methods = ContainerUtil.findAll(psiClass.getMethods(), new Condition<PsiMethod>() {
          public boolean value(final PsiMethod method) {
            final PsiModifierList modifiers = method.getModifierList();
            if (modifiers.hasModifierProperty(PsiModifier.PRIVATE) || modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
              return false;
            }
            if (MethodParameterInjection.isInjectable(method.getReturnType(), method.getProject())) return true;
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            return null != ContainerUtil.find(parameters, new Condition<PsiParameter>() {
              public boolean value(PsiParameter p) {
                return MethodParameterInjection.isInjectable(p.getType(), p.getProject());
              }
            });
          }
        });
        for (PsiMethod method : methods) {
          myData.put(method, MethodParameterInjection.createMethodInfo(method));
        }
      }
    });
  }

  private void refreshTreeStructure() {
    myRootNode.removeAllChildren();
    final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>(myData.keySet());
    Collections.sort(methods, new Comparator<PsiMethod>() {
      public int compare(final PsiMethod o1, final PsiMethod o2) {
        final int names = o1.getName().compareTo(o2.getName());
        if (names != 0) return names;
        return o1.getParameterList().getParametersCount() - o2.getParameterList().getParametersCount();
      }
    });
    for (PsiMethod method : methods) {
      final PsiParameter[] params = method.getParameterList().getParameters();
      final DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode(method, true);
      myRootNode.add(methodNode);
      for (final PsiParameter parameter : params) {
        methodNode.add(new DefaultMutableTreeNode(parameter, false));
      }
    }
    final ListTreeTableModelOnColumns tableModel = (ListTreeTableModelOnColumns)myParamsTable.getTableModel();
    tableModel.reload();
    TreeUtil.expandAll(myParamsTable.getTree());
    myParamsTable.revalidate();
  }

  private String getClassName() {
    final PsiType type = getClassType();
    if (type == null) {
      return myClassField.getText();
    }
    return type.getCanonicalText();
  }


  protected void apply(final MethodParameterInjection other) {
    final boolean applyMethods = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        other.setClassName(getClassName());
        return getClassType() != null;
      }
    }).booleanValue();
    other.setApplyInHierarchy(myHierarchy.isSelected());
    if (applyMethods) {
      other.setMethodInfos(ContainerUtil.findAll(myData.values(), new Condition<MethodParameterInjection.MethodInfo>() {
        public boolean value(final MethodParameterInjection.MethodInfo methodInfo) {
          return methodInfo.isEnabled();
        }
      }));
    }
  }

  protected void resetImpl() {
    setPsiClass(myOrigInjection.getClassName());
    myHierarchy.setSelected(myOrigInjection.isApplyInHierarchy());

    rebuildTreeModel();
    final THashMap<String, PsiMethod> map = new THashMap<String, PsiMethod>();
    for (PsiMethod method : myData.keySet()) {
      map.put(myData.get(method).getMethodSignature(), method);
    }
    for (MethodParameterInjection.MethodInfo info : myOrigInjection.getMethodInfos()) {
      final PsiMethod method = map.get(info.getMethodSignature());
      if (method != null) {
        final MethodParameterInjection.MethodInfo curInfo = myData.get(method);
        System.arraycopy(info.getParamFlags(), 0, curInfo.getParamFlags(), 0, Math.min(info.getParamFlags().length, curInfo.getParamFlags().length));
        curInfo.setReturnFlag(info.isReturnFlag());
      }
      else {
        final PsiMethod missingMethod = MethodParameterInjection.makeMethod(myProject, info.getMethodSignature());
        myData.put(missingMethod, info.copy());
      }
    }
    refreshTreeStructure();
    final Enumeration enumeration = myRootNode.children();
    while (enumeration.hasMoreElements()) {
      PsiMethod method = (PsiMethod)((DefaultMutableTreeNode)enumeration.nextElement()).getUserObject();
      assert myData.containsKey(method);
    }
  }

  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(myProject, myOrigInjection);
    myRootNode = new DefaultMutableTreeNode(null, true);
    myParamsTable = new MyView(new ListTreeTableModelOnColumns(myRootNode, createColumnInfos()));
    myAdvancedPanel = new AdvancedPanel(myProject, myOrigInjection);    
  }

  private Boolean isNodeSelected(final DefaultMutableTreeNode o) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)userObject;
      return MethodParameterInjection.isInjectable(method.getReturnType(), method.getProject()) ? myData.get(method).isReturnFlag() : null;
    }
    else if (userObject instanceof PsiParameter) {
      final PsiMethod method = getMethodByNode(o);
      final PsiParameter parameter = (PsiParameter)userObject;
      final int index = method.getParameterList().getParameterIndex(parameter);
      return MethodParameterInjection.isInjectable(parameter.getType(), method.getProject()) ? myData.get(method).getParamFlags()[index] : null;
    }
    return null;
  }

  private void setNodeSelected(final DefaultMutableTreeNode o, final boolean value) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod) {
      myData.get((PsiMethod)userObject).setReturnFlag(value);
    }
    else if (userObject instanceof PsiParameter) {
      final PsiMethod method = getMethodByNode(o);
      final int index = method.getParameterList().getParameterIndex((PsiParameter)userObject);
      myData.get(method).getParamFlags()[index] = value;
    }
  }

  private static PsiMethod getMethodByNode(final DefaultMutableTreeNode o) {
    final Object userObject = o.getUserObject();
    if (userObject instanceof PsiMethod) return (PsiMethod)userObject;
    return (PsiMethod)((DefaultMutableTreeNode)o.getParent()).getUserObject();
  }

  private ColumnInfo[] createColumnInfos() {
    return new ColumnInfo[]{
        new ColumnInfo<DefaultMutableTreeNode, Boolean>(" ") { // "" for the first column's name isn't a good idea
          final BooleanTableCellRenderer myRenderer = new BooleanTableCellRenderer();

          public Boolean valueOf(DefaultMutableTreeNode o) {
            return isNodeSelected(o);
          }

          public int getWidth(JTable table) {
            return myRenderer.getPreferredSize().width;
          }

          public TableCellEditor getEditor(DefaultMutableTreeNode o) {
            return new DefaultCellEditor(new JCheckBox());
          }

          public TableCellRenderer getRenderer(DefaultMutableTreeNode o) {
            myRenderer.setEnabled(isCellEditable(o));
            return myRenderer;
          }

          public void setValue(DefaultMutableTreeNode o, Boolean value) {
            setNodeSelected(o, Boolean.TRUE.equals(value));
          }

          public Class<Boolean> getColumnClass() {
            return Boolean.class;
          }

          public boolean isCellEditable(DefaultMutableTreeNode o) {
            return valueOf(o) != null;
          }

        }, new TreeColumnInfo("Method/Parameters")
    };
  }

  private class BrowseClassListener implements ActionListener {
    private final Project myProject;

    public BrowseClassListener(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
      final TreeClassChooser chooser = factory.createAllProjectScopeChooser("Select Class");
      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelectedClass();
      if (psiClass != null) {
        setPsiClass(psiClass.getQualifiedName());
        updateParamTree();
        updateTree();
      }
    }
  }

  private class MyView extends TreeTableView implements TypeSafeDataProvider {
    public MyView(ListTreeTableModelOnColumns treeTableModel) {
      super(treeTableModel);
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (LangDataKeys.PSI_ELEMENT.equals(key)) {
        final Collection selection = getSelection();
        if (!selection.isEmpty()) {
          final Object o = ((DefaultMutableTreeNode)selection.iterator().next()).getUserObject();
          if (o instanceof PsiElement) sink.put(LangDataKeys.PSI_ELEMENT, (PsiElement)o);
        }
      }
    }
  }
}
