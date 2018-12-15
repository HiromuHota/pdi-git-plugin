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

package org.pentaho.di.git.spoon.model;

import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.variables.Variables;
import org.pentaho.metastore.persist.MetaStoreAttribute;
import org.pentaho.metastore.persist.MetaStoreElementType;

@MetaStoreElementType(
  name = "Git Repository",
  description = "This defines a Git repository" )
public class GitRepository {

  @MetaStoreAttribute( key = "name" )
  private String name;

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  @MetaStoreAttribute( key = "description" )
  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription( String description ) {
    this.description = description;
  }

  @MetaStoreAttribute( key = "directory" )
  private String directory;

  public String getDirectory() {
    return directory;
  }

  public void setDirectory( String directory ) {
    this.directory = directory;
  }

  @MetaStoreAttribute( key = "type" )
  private String type;

  public String getType() {
    return type;
  }

  public void setType( String type ) {
    this.type = type;
  }


  public String getActualDirectory() {
    VariableSpace space = new Variables();
    space.initializeVariablesFrom( null );
    return space.environmentSubstitute( directory );
  }

}
