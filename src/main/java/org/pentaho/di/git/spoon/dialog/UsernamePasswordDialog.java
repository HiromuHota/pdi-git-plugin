package org.pentaho.di.git.spoon.dialog;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class UsernamePasswordDialog extends Dialog {

  private Text usernameText;
  private Text passwordText;
  private String username;
  private String password;

  public UsernamePasswordDialog( Shell parentShell ) {
    super( parentShell );
  }

  @Override
  protected Control createDialogArea( Composite parent ) {
    Composite comp = (Composite) super.createDialogArea( parent );

    GridLayout layout = (GridLayout) comp.getLayout();
    layout.numColumns = 2;

    Label usernameLabel = new Label( comp, SWT.RIGHT );
    usernameLabel.setText( "Username: " );
    usernameText = new Text( comp, SWT.SINGLE | SWT.BORDER );
    usernameText.setLayoutData( new GridData( GridData.FILL_HORIZONTAL ) );

    Label passwordLabel = new Label( comp, SWT.RIGHT );
    passwordLabel.setText( "Password: " );
    passwordText = new Text( comp, SWT.SINGLE | SWT.BORDER | SWT.PASSWORD );
    passwordText.setLayoutData( new GridData( GridData.FILL_HORIZONTAL ) );

    return comp;
  }

  @Override
  protected void okPressed() {
    username = usernameText.getText();
    password = passwordText.getText();
    super.okPressed();
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
