/*
 * Copyright 2017 Hitachi America, Ltd., R&D.
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

package org.pentaho.di.git.spoon.dialog;


import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.variables.Variables;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.IVCS;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.ui.core.PropsUI;
import org.pentaho.di.ui.core.gui.WindowProperty;
import org.pentaho.di.ui.core.widget.TextVar;
import org.pentaho.di.ui.trans.step.BaseStepDialog;

public class EditRepositoryDialog {
  private static Class<?> PKG = EditRepositoryDialog.class; // package locator

  protected Display display;
  protected final Shell parent;
  protected Shell shell;
  protected PropsUI props;

  protected Text nameText;
  protected Text descText;
  protected TextVar directoryText;
  protected String directory;
  protected Combo typeCombo;

  protected GitRepository repo;

  protected VariableSpace space;
  protected int centerPct;
  protected int margin;

  protected int returnValue;

  public EditRepositoryDialog( Shell parentShell, GitRepository repo ) {
    this.parent = parentShell;
    this.repo = repo;
    props = PropsUI.getInstance();

    space = new Variables();
    space.initializeVariablesFrom( null ); // system vars only
    returnValue = Window.CANCEL;
  }

  protected void createShell(String shellName) {
    display = parent.getDisplay();
    shell = new Shell( parent, SWT.DIALOG_TRIM | SWT.RESIZE );
    shell.setText( shellName );

    FormLayout shellLayout = new FormLayout();
    shellLayout.marginWidth = Const.FORM_MARGIN;
    shellLayout.marginHeight = Const.FORM_MARGIN;
    shell.setLayout( shellLayout );

    centerPct = props.getMiddlePct();
    margin = Const.MARGIN + 5;
  }

  public int open() {
    createShell("Repository settings");
    Control lastControl = addRepositoryControls(shell, centerPct, margin);
    addButtonsManageShell(lastControl);
    return returnValue;
  }

  protected void addButtonsManageShell(Control lastControl) {
    Button wOK = new Button( shell, SWT.PUSH );
    wOK.setText( BaseMessages.getString( PKG, "System.Button.OK" ) );
    wOK.addListener( SWT.Selection, e -> {
      ok();
      dispose();
    } );
    Button wCancel = new Button( shell, SWT.PUSH );
    wCancel.setText( BaseMessages.getString( PKG, "System.Button.Cancel" ) );
    wCancel.addListener( SWT.Selection, e -> dispose() );

    BaseStepDialog.positionBottomRightButtons( shell, new Button[] { wOK, wCancel }, margin, lastControl );

    BaseStepDialog.setSize( shell );

    shell.open();
    while ( !shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }
  }

  protected Control addRepositoryControls( Shell shell, int centerPct, int margin ) {
    // The name line (label/text)
    //
    Label nameLabel = new Label( shell, SWT.RIGHT );
    nameLabel.setText( "Name: " );
    FormData fdNameLabel = new FormData();
    fdNameLabel.top = new FormAttachment( 0, 0 );
    fdNameLabel.left = new FormAttachment( 0, 0 );
    fdNameLabel.right = new FormAttachment( centerPct, 0 );
    nameLabel.setLayoutData( fdNameLabel );
    nameText = new Text( shell, SWT.SINGLE | SWT.BORDER );
    nameText.setText( Const.NVL( repo.getName(), "" ) );
    FormData fdNameText = new FormData();
    fdNameText.top = new FormAttachment( nameLabel, 0, SWT.CENTER );
    fdNameText.left = new FormAttachment( centerPct, margin );
    fdNameText.right = new FormAttachment( 100, 0 );
    nameText.setLayoutData( fdNameText );
    Control lastControl = nameText;

    // The name line (label/text)
    //
    Label descLabel = new Label( shell, SWT.RIGHT );
    descLabel.setText( "Description: " );
    FormData fdDescLabel = new FormData();
    fdDescLabel.top = new FormAttachment( lastControl, margin );
    fdDescLabel.left = new FormAttachment( 0, 0 );
    fdDescLabel.right = new FormAttachment( centerPct, 0 );
    descLabel.setLayoutData( fdDescLabel );
    descText = new Text( shell, SWT.SINGLE | SWT.BORDER );
    descText.setText( Const.NVL( repo.getDescription(), "" ) );
    FormData fdDescText = new FormData();
    fdDescText.top = new FormAttachment( descLabel, 0, SWT.CENTER );
    fdDescText.left = new FormAttachment( centerPct, margin );
    fdDescText.right = new FormAttachment( 100, 0 );
    descText.setLayoutData( fdDescText );
    lastControl = descText;

    // The directory line (label/text)
    //
    // Label
    Label directoryLabel = new Label( shell, SWT.RIGHT );
    directoryLabel.setText( "Directory: " );
    FormData fdDirectoryLabel = new FormData();
    fdDirectoryLabel.top = new FormAttachment( lastControl, margin );
    fdDirectoryLabel.left = new FormAttachment( 0, 0 );
    fdDirectoryLabel.right = new FormAttachment( centerPct, 0 );
    directoryLabel.setLayoutData( fdDirectoryLabel );
    // Button
    Button directoryButton = new Button( shell, SWT.PUSH );
    directoryButton.setText( "Browse" );
    directoryButton.addSelectionListener( new SelectionAdapter() {
      @Override
      public void widgetSelected( SelectionEvent e ) {
        DirectoryDialog dialog = new DirectoryDialog( shell, SWT.OPEN );
        if ( dialog.open() != null ) {
          directoryText.setText( dialog.getFilterPath() );
        }
      }
    } );
    FormData fdDirectoryButton = new FormData();
    fdDirectoryButton.top = new FormAttachment( directoryLabel, 0, SWT.CENTER );
    fdDirectoryButton.right = new FormAttachment( 100, 0 );
    directoryButton.setLayoutData( fdDirectoryButton );
    // Text
    directoryText = new TextVar( space, shell, SWT.SINGLE | SWT.BORDER );
    directoryText.setText( Const.NVL( repo.getDirectory(), "" ) );
    FormData fdDirectoryText = new FormData();
    fdDirectoryText.top = new FormAttachment( directoryLabel, 0, SWT.CENTER );
    fdDirectoryText.left = new FormAttachment( centerPct, margin );
    fdDirectoryText.right = new FormAttachment( directoryButton, -margin );
    directoryText.setLayoutData( fdDirectoryText );
    lastControl = directoryButton;

    // The type combo (label/combo)
    //
    Label typeLabel = new Label( shell, SWT.RIGHT );
    typeLabel.setText( "Type: " );
    FormData fdTypeLabel = new FormData();
    fdTypeLabel.top = new FormAttachment( lastControl, margin );
    fdTypeLabel.left = new FormAttachment( 0, 0 );
    fdTypeLabel.right = new FormAttachment( centerPct, 0 );
    typeLabel.setLayoutData( fdTypeLabel );
    typeCombo = new Combo( shell, SWT.READ_ONLY );
    typeCombo.setItems( IVCS.GIT, IVCS.SVN );
    if ( repo.getType() != null ) {
      if ( repo.getType().equals( IVCS.GIT ) ) {
        typeCombo.select( 0 );
      } else {
        typeCombo.select( 1 );
      }
    }
    FormData fdTypeCombo = new FormData();
    fdTypeCombo.top = new FormAttachment( typeLabel, 0, SWT.CENTER );
    fdTypeCombo.left = new FormAttachment( centerPct, margin );
    fdTypeCombo.right = new FormAttachment( 100, 0 );
    typeCombo.setLayoutData( fdTypeCombo );
    lastControl = typeCombo;

    return lastControl;
  }

  public void ok() {
    repo.setName( nameText.getText() );
    repo.setDescription( descText.getText() );
    repo.setDirectory( directoryText.getText() );
    repo.setType( typeCombo.getText() );
    directory = directoryText.getText();
    returnValue = Window.OK;
  }

  public void dispose() {
    props.setScreen( new WindowProperty( shell ) );
    shell.dispose();
  }
}
