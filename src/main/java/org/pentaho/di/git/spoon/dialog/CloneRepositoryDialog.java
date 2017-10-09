package org.pentaho.di.git.spoon.dialog;

import java.io.File;
import java.net.URISyntaxException;

import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.git.spoon.model.GitRepository;

public class CloneRepositoryDialog extends EditRepositoryDialog {

  private Text urlText;
  private String url;
  private Text cloneAsText;
  private String cloneAs;

  public CloneRepositoryDialog( Shell parentShell, GitRepository repo ) {
    super( parentShell, repo );
  }

  @Override
  protected Control createDialogArea( Composite parent ) {
    Composite comp = (Composite) super.createDialogArea( parent );

    GridLayout layout = (GridLayout) comp.getLayout();
    layout.numColumns = 3;

    Label urlLabel = new Label( comp, SWT.RIGHT );
    urlLabel.setText( "Source URL: " );
    urlLabel.setLayoutData( new GridData( GridData.END, GridData.CENTER, false, false ) );
    urlText = new Text( comp, SWT.SINGLE | SWT.BORDER );
    urlText.setLayoutData( new GridData( GridData.FILL, GridData.CENTER, true, false, 2, 1 ) );

    Label cloneAsLabel = new Label( comp, SWT.RIGHT );
    cloneAsLabel.setText( "Clone As: " );
    cloneAsLabel.setLayoutData( new GridData( GridData.END, GridData.CENTER, false, false ) );
    cloneAsText = new Text( comp, SWT.SINGLE | SWT.BORDER );
    cloneAsText.setLayoutData( new GridData( GridData.FILL, GridData.CENTER, true, false, 2, 1 ) );

    urlText.addModifyListener( event -> {
      String url = ( (Text) event.widget ).getText();
      URIish uri;
      try {
        uri = new URIish( url );
        cloneAsText.setText( uri.getHumanishName() );
      } catch ( Exception e ) {
//        e.printStackTrace();
      }
    } );

    return comp;
  }

  @Override
  protected void okPressed() {
    url = urlText.getText();
    cloneAs = cloneAsText.getText();
    super.okPressed();
    repo.setDirectory( getDirectory() + File.separator + cloneAs );
  }

  public String getURL() {
    return url;
  }

  public String getCloneAs() {
    return cloneAs;
  }
}
