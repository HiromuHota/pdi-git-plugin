package org.pentaho.di.git.spoon;

import static org.junit.Assert.*;
import static org.pentaho.di.git.spoon.PdiDiff.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.core.KettleClientEnvironment;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.trans.TransMeta;

public class PdiDiffTest {

  @Before
  public void setUp() throws KettleException {
    KettleClientEnvironment.getInstance().setClient( KettleClientEnvironment.ClientType.CARTE );
    KettleEnvironment.init();
  }

  @Test
  public void diffTransTest() throws Exception {
    File file = new File( "src/test/resources/r1.ktr" );
    InputStream xmlStream = new FileInputStream( file );
    TransMeta transMeta = new TransMeta( xmlStream, null, true, null, null );
    transMeta.sortSteps();

    File file2 = new File( "src/test/resources/r2.ktr" );
    InputStream xmlStream2 = new FileInputStream( file2 );
    TransMeta transMeta2 = new TransMeta( xmlStream2, null, true, null, null );
    transMeta2.sortSteps();

    transMeta = compareSteps( transMeta, transMeta2, true );
    transMeta2 = compareSteps( transMeta2, transMeta, false );
    assertEquals( UNCHANGED, transMeta.getStep( 0 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
    assertEquals( CHANGED, transMeta.getStep( 1 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
    assertEquals( REMOVED, transMeta.getStep( 2 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
    assertEquals( ADDED, transMeta2.getStep( 2 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
  }

  @Test
  public void diffJobEntryTest() throws Exception {
    File file = new File( "src/test/resources/r1.kjb" );
    InputStream xmlStream = new FileInputStream( file );
    JobMeta jobMeta = new JobMeta( xmlStream, null, null );

    File file2 = new File( "src/test/resources/r2.kjb" );
    InputStream xmlStream2 = new FileInputStream( file2 );
    JobMeta jobMeta2 = new JobMeta( xmlStream2, null, null );

    jobMeta = compareJobEntries( jobMeta, jobMeta2, true );
    jobMeta2 = compareJobEntries( jobMeta2, jobMeta, false );
    assertEquals( CHANGED, jobMeta.getJobEntry( 0 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
    assertEquals( UNCHANGED, jobMeta.getJobEntry( 1 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
    assertEquals( REMOVED, jobMeta.getJobEntry( 2 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
    assertEquals( ADDED, jobMeta2.getJobEntry( 2 ).getAttribute( ATTR_GIT, ATTR_STATUS ) );
  }
}
