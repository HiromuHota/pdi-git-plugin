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

import java.io.File;
import java.net.URISyntaxException;

import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
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

  public int open() {
    createShell("Clone Repository");
    Control lastControl = addRepositoryControls(shell, centerPct, margin);

    // URL
    //
    Label urlLabel = new Label( shell, SWT.RIGHT );
    urlLabel.setText( "Source URL: " );
    FormData fdUrlLabel = new FormData();
    fdUrlLabel.left = new FormAttachment( 0, 0 );
    fdUrlLabel.right = new FormAttachment( centerPct, 0 );
    fdUrlLabel.top = new FormAttachment( lastControl, margin );
    urlLabel.setLayoutData( fdUrlLabel );

    urlText = new Text( shell, SWT.SINGLE | SWT.BORDER );
    FormData fdUrlText = new FormData();
    fdUrlText.left = new FormAttachment( centerPct, margin );
    fdUrlText.right = new FormAttachment( 100, 0 );
    fdUrlText.top = new FormAttachment( urlLabel, 0, SWT.CENTER);
    urlText.setLayoutData( fdUrlText );

    // Clone As...
    //
    Label cloneAsLabel = new Label( shell, SWT.RIGHT );
    cloneAsLabel.setText( "Clone As: " );
    FormData fdCloneAsLabel = new FormData();
    fdCloneAsLabel.left = new FormAttachment( 0, 0 );
    fdCloneAsLabel.right = new FormAttachment( centerPct, 0 );
    fdCloneAsLabel.top = new FormAttachment( lastControl, margin );
    cloneAsLabel.setLayoutData( fdCloneAsLabel );

    cloneAsText = new Text( shell, SWT.SINGLE | SWT.BORDER );
    FormData fdCloneAsText = new FormData();
    fdCloneAsText.left = new FormAttachment( centerPct, margin );
    fdCloneAsText.right = new FormAttachment( 100, 0 );
    fdCloneAsText.top = new FormAttachment( cloneAsLabel, 0, SWT.CENTER);
    cloneAsText.setLayoutData( fdCloneAsText );

    urlText.addModifyListener( event -> {
      String url = ( (Text) event.widget ).getText();
      URIish uri;
      try {
        uri = new URIish( url );
        cloneAsText.setText( uri.getHumanishName() );
      } catch ( URISyntaxException e ) {
        //        e.printStackTrace();
      }
    } );
    lastControl = cloneAsText;
    
    addButtonsManageShell(lastControl);
    return returnValue;
  }

  @Override
  public void ok() {
    super.ok();
    url = urlText.getText();
    cloneAs = cloneAsText.getText();
    repo.setDirectory( directory + File.separator + cloneAs );
  }

  public String getURL() {
    return url;
  }
}
