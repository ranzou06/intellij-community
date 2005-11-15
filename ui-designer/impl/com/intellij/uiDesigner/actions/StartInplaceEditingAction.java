package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.propertyInspector.Property;

import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StartInplaceEditingAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.StartInplaceEditingAction");

  private final GuiEditor myEditor;

  public StartInplaceEditingAction(final GuiEditor editor) {
    LOG.assertTrue(editor != null);
    myEditor = editor;
  }

  public void actionPerformed(final AnActionEvent e) {
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);
    final RadComponent component = selection.get(0);
    final Property defaultInplaceProperty = component.getDefaultInplaceProperty();
    myEditor.getInplaceEditingLayer().startInplaceEditing(component, defaultInplaceProperty, null, true);
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);

    // Inplace editing can be started only if single component is selected
    if(selection.size() != 1){
      presentation.setEnabled(false);
      return;
    }

    // Selected component should have "inplace" property
    final RadComponent component = selection.get(0);
    presentation.setEnabled(component.getDefaultInplaceProperty() != null);
  }
}
