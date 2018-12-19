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


import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.variables.Variables;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.IVCS;
import org.pentaho.di.ui.core.PropsUI;
import org.pentaho.di.ui.core.gui.WindowProperty;
import org.pentaho.di.ui.core.widget.TextVar;

public class EditRepositoryDialog extends Dialog {

  private Text nameText;
  private Text descText;
  private TextVar directoryText;
  private String directory;
  private Combo typeCombo;

  protected PropsUI props;
  protected GitRepository repo;
  protected VariableSpace space;
  protected static String APPLICATION_NAME;

  public EditRepositoryDialog( Shell parentShell, GitRepository repo ) {
    super( parentShell );
    this.repo = repo;
    props = PropsUI.getInstance();
    APPLICATION_NAME = "Edit Repository";

    space = new Variables();
    space.initializeVariablesFrom( null ); // system vars only
  }

  @Override
  public int open() {
    // Create shell and contents
    setShellStyle( SWT.DIALOG_TRIM | SWT.RESIZE );
    create();

    // Post-configure shell like the title and size
    Shell shell = getShell();
    shell.setText( APPLICATION_NAME );
    WindowProperty winprop = props.getScreen( shell.getText() );
    if ( winprop != null ) {
      winprop.setShell( shell );
    }
    return super.open();
  }

  @Override
  protected Control createDialogArea( Composite parent ) {
    Composite comp = (Composite) super.createDialogArea( parent );

    GridLayout layout = (GridLayout) comp.getLayout();
    layout.numColumns = 3;

    Label nameLabel = new Label( comp, SWT.RIGHT );
    nameLabel.setText( "Name: " );
    nameLabel.setLayoutData( new GridData( GridData.END, GridData.CENTER, false, false ) );
    nameText = new Text( comp, SWT.SINGLE | SWT.BORDER );
    nameText.setText( Const.NVL( repo.getName(), "" ) );
    nameText.setLayoutData( new GridData( GridData.FILL, GridData.CENTER, true, false, 2, 1 ) );

    Label descLabel = new Label( comp, SWT.RIGHT );
    descLabel.setText( "Description: " );
    descLabel.setLayoutData( new GridData( GridData.END, GridData.CENTER, false, false ) );
    descText = new Text( comp, SWT.SINGLE | SWT.BORDER );
    descText.setLayoutData( new GridData( GridData.FILL, GridData.CENTER, true, false, 2, 1 ) );
    descText.setText( Const.NVL( repo.getDescription(), "" ) );

    Label directoryLabel = new Label( comp, SWT.RIGHT );
    directoryLabel.setText( "Directory: " );
    directoryLabel.setLayoutData( new GridData( GridData.END, GridData.CENTER, false, false ) );
    directoryText = new TextVar( space, comp, SWT.SINGLE | SWT.BORDER );
    directoryText.setLayoutData( new GridData( GridData.FILL, GridData.CENTER, true, false ) );
    directoryText.setText( Const.NVL( repo.getDirectory(), "" ) );

    Button directoryButton = new Button( comp, SWT.PUSH );
    directoryButton.setText( "Browse" );
    directoryButton.addSelectionListener( new SelectionAdapter() {
      @Override
      public void widgetSelected( SelectionEvent e ) {
        DirectoryDialog dialog = new DirectoryDialog( getShell(), SWT.OPEN );
        if ( dialog.open() != null ) {
          directoryText.setText( dialog.getFilterPath() );
        }
      }
    } );

    Label typeLabel = new Label( comp, SWT.RIGHT );
    typeLabel.setText( "Type: " );
    typeLabel.setLayoutData( new GridData( GridData.END, GridData.CENTER, false, false ) );
    typeCombo = new Combo( comp, SWT.READ_ONLY );
    typeCombo.setItems( IVCS.GIT, IVCS.SVN );
    if ( repo.getType() != null ) {
      if ( repo.getType().equals( IVCS.GIT ) ) {
        typeCombo.select( 0 );
      } else {
        typeCombo.select( 1 );
      }
    }
    typeCombo.setLayoutData( new GridData( GridData.FILL, GridData.CENTER, true, false, 2, 1 ) );
    return comp;
  }

  @Override
  protected void okPressed() {
    repo.setName( nameText.getText() );
    repo.setDescription( descText.getText() );
    repo.setDirectory( directoryText.getText() );
    repo.setType( typeCombo.getText() );
    directory = directoryText.getText();
    super.okPressed();
  }

  @Override
  public Point getInitialSize() {
    Point point = super.getInitialSize();
    return new Point( 500, point.y );
  }

  public String getDirectory() {
    return directory;
  }

  @Override
  public boolean close() {
    props.setScreen( new WindowProperty( getShell() ) );
    return super.close();
  }
}
