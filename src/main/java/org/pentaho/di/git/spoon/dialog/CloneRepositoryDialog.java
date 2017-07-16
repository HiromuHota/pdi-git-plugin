package org.pentaho.di.git.spoon.dialog;


import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class CloneRepositoryDialog extends Dialog {

  private Text urlText;
  private Text directoryText;
  private String url;
  private String directory;

  public CloneRepositoryDialog( Shell parentShell ) {
    super( parentShell );
  }

  @Override
  protected Control createDialogArea( Composite parent ) {
    Composite comp = (Composite) super.createDialogArea( parent );

    GridLayout layout = (GridLayout) comp.getLayout();
    layout.numColumns = 3;

    Label urlLabel = new Label( comp, SWT.RIGHT );
    urlLabel.setText( "Source URL: " );
    urlText = new Text( comp, SWT.SINGLE | SWT.BORDER );
    urlText.setLayoutData( new GridData( GridData.FILL_HORIZONTAL ) );
//    urlText.setSize( new Point( 500, 10 ) ); // Does not work
    GridData gridData = new GridData();
    gridData.horizontalAlignment = GridData.FILL;
    gridData.horizontalSpan = 2;
    urlText.setLayoutData( gridData );

    Label directoryLabel = new Label( comp, SWT.RIGHT );
    directoryLabel.setText( "Destination Path: " );

    directoryText = new Text( comp, SWT.SINGLE | SWT.BORDER );
    directoryText.setLayoutData( new GridData( GridData.FILL_HORIZONTAL ) );

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
    return comp;
  }

  @Override
  protected void okPressed() {
    url = urlText.getText();
    directory = directoryText.getText();
    super.okPressed();
  }

  public String getURL() {
    return url;
  }

  public String getDirectory() {
    return directory;
  }

  @Override
  public Point getInitialSize() {
    Point point = super.getInitialSize();
    return new Point( 500, point.y );
  }
}
