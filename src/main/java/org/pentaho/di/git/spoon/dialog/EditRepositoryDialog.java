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
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.IVCS;

public class EditRepositoryDialog extends Dialog {

  private Text nameText;
  private Text descText;
  private Text directoryText;
  private String directory;
  private Combo typeCombo;

  protected GitRepository repo;

  public EditRepositoryDialog( Shell parentShell, GitRepository repo ) {
    super( parentShell );
    this.repo = repo;
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
    directoryText = new Text( comp, SWT.SINGLE | SWT.BORDER );
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
    if ( repo.getType().equals( IVCS.GIT ) ) {
      typeCombo.select( 0 );
    } else {
      typeCombo.select( 1 );
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
}
