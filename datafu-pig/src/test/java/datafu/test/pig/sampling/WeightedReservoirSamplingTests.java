/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package datafu.test.pig.sampling;

import java.io.IOException;
import java.util.*;

import junit.framework.Assert;

import org.adrianwalker.multilinestring.Multiline;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.pigunit.PigTest;
import org.testng.annotations.Test;

import datafu.pig.sampling.WeightedReservoirSample;
import datafu.test.pig.PigTests;

/**
 * Tests for {@link WeightedReservoirSample}.
 *
 * @author wjian 
 *
 */
public class WeightedReservoirSamplingTests extends PigTests
{
  /**
  

  DEFINE ReservoirSample datafu.pig.sampling.WeightedReservoirSample('$RESERVOIR_SIZE','2');
  DEFINE Assert datafu.pig.util.Assert();
  
  data = LOAD 'input' AS (A_id:int, B_id:chararray, C:int, W:double);
  sampled = FOREACH (GROUP data BY A_id) GENERATE group as A_id, ReservoirSample(data.(B_id,C,W)) as sample_data;
  sampled = FILTER sampled BY Assert((SIZE(sample_data) <= $RESERVOIR_SIZE ? 1 : 0), 'must be <= $RESERVOIR_SIZE');
  sampled = FOREACH sampled GENERATE A_id, FLATTEN(sample_data);
  STORE sampled INTO 'output';

   */
  @Multiline
  private String weightedReservoirSampleGroupTest;
  
  /**
   * Verifies that WeightedReservoirSample works when data grouped by a key.
   * In particular it ensures that the reservoir is not reused across keys.
   * 
   * <p>
   * This confirms the fix for DATAFU-11.
   * </p>
   * 
   * @throws Exception
   */
  @Test
  public void weightedReservoirSampleGroupTest() throws Exception
  {    
    // first value is the key.  second to last value matches the key so we can
    // verify the register is reset for each key.  values should not
    // bleed across to other keys.
    writeLinesToFile("input",
                     "1\tB1\t1\t1.0",
                     "1\tB1\t1\t1.0",
                     "1\tB3\t1\t1.0",
                     "1\tB4\t1\t1.0",
                     "2\tB1\t2\t1.0",
                     "2\tB2\t2\t1.0",
                     "3\tB1\t3\t1.0",
                     "3\tB1\t3\t1.0",
                     "3\tB3\t3\t1.0",
                     "4\tB1\t4\t1.0",
                     "4\tB2\t4\t1.0",
                     "4\tB3\t4\t1.0",
                     "4\tB4\t4\t1.0",
                     "5\tB1\t5\t1.0",
                     "6\tB2\t6\t1.0",
                     "6\tB2\t6\t1.0",
                     "6\tB3\t6\t1.0",
                     "7\tB1\t7\t1.0",
                     "7\tB2\t7\t1.0",
                     "7\tB3\t7\t1.0",
                     "8\tB1\t8\t1.0",
                     "8\tB2\t8\t1.0",
                     "9\tB3\t9\t1.0",
                     "9\tB3\t9\t1.0",
                     "9\tB6\t9\t1.0",
                     "9\tB5\t9\t1.0",
                     "10\tB1\t10\t1.0",
                     "10\tB2\t10\t1.0",
                     "10\tB2\t10\t1.0",
                     "10\tB2\t10\t1.0",
                     "10\tB6\t10\t1.0",
                     "10\tB7\t10\t1.0");
   
    for(int i=1; i<=3; i++) {
      int reservoirSize = i ;
      PigTest test = createPigTestFromString(weightedReservoirSampleGroupTest, "RESERVOIR_SIZE="+reservoirSize);
      test.runScript();
      
      List<Tuple> tuples = getLinesForAlias(test, "sampled");
      
      for (Tuple tuple : tuples)
      {
        Assert.assertEquals(((Number)tuple.get(0)).intValue(), ((Number)tuple.get(2)).intValue());
      }
    }
  }
  
 /** 
   
 
  define WeightedSample datafu.pig.sampling.WeightedReservoirSample('1','1'); 

  data = LOAD 'input' AS (v1:chararray,v2:INT);
  data_g = group data all;
  sampled = FOREACH data_g GENERATE WeightedSample(data);
  --describe sampled; 
   
  STORE sampled INTO 'output'; 
 
  */ 
  @Multiline 
  private String weightedSampleTest; 
   
  @Test 
  public void weightedSampleTest() throws Exception 
  {
     Map<String, Integer> count = new HashMap<String, Integer>();

     count.put("a", 0);
     count.put("b", 0);
     count.put("c", 0);
     count.put("d", 0);

     PigTest test = createPigTestFromString(weightedSampleTest); 
 
     writeLinesToFile("input",  
                "a\t100","b\t1","c\t5","d\t2");

     for(int i = 0; i < 10; i++) {
        test.runScript();

        List<Tuple> output = this.getLinesForAlias(test, "sampled");

        Tuple t = output.get(0);

        DataBag sampleBag = (DataBag)t.get(0);

        for(Iterator<Tuple> sampleIter = sampleBag.iterator(); sampleIter.hasNext();) {
           Tuple st = sampleIter.next();
           String key = (String)st.get(0);
           count.put(key, count.get(key) + 1);
        }              
     }

     String maxKey = "";
     int maxCount = 0;
     for(String key : count.keySet()) {
        if(maxCount < count.get(key)) {
           maxKey = key; 
           maxCount = count.get(key);
        } 
     }

     Assert.assertEquals(maxKey, "a");
  }

  @Test
  public void weightedReservoirSampleAccumulateTest() throws IOException
  {
     WeightedReservoirSample sampler = new WeightedReservoirSample("10", "1");

     for (int i=0; i<100; i++)
     {
       Tuple t = TupleFactory.getInstance().newTuple(2);
       t.set(0, i);
       t.set(1, i + 1);
       DataBag bag = BagFactory.getInstance().newDefaultBag();
       bag.add(t);
       Tuple input = TupleFactory.getInstance().newTuple(bag);
       sampler.accumulate(input);
     }

     DataBag result = sampler.getValue();
     verifyNoRepeatAllFound(result, 10, 0, 100); 
  }

  @Test
  public void weightedReservoirSampleAlgebraicTest() throws IOException
  {
    WeightedReservoirSample.Initial initialSampler = new WeightedReservoirSample.Initial("10", "1");
    WeightedReservoirSample.Intermediate intermediateSampler = new WeightedReservoirSample.Intermediate("10", "1");
    WeightedReservoirSample.Final finalSampler = new WeightedReservoirSample.Final("10", "1");
    
    DataBag bag = BagFactory.getInstance().newDefaultBag();
    for (int i=0; i<100; i++)
    {
      Tuple t = TupleFactory.getInstance().newTuple(2);
      t.set(0, i);
      t.set(1, i + 1);
      bag.add(t);
    }
    
    Tuple input = TupleFactory.getInstance().newTuple(bag);
    
    Tuple intermediateTuple = initialSampler.exec(input);  
    DataBag intermediateBag = BagFactory.getInstance().newDefaultBag(Arrays.asList(intermediateTuple));
    intermediateTuple = intermediateSampler.exec(TupleFactory.getInstance().newTuple(intermediateBag));  
    intermediateBag = BagFactory.getInstance().newDefaultBag(Arrays.asList(intermediateTuple));
    DataBag result = finalSampler.exec(TupleFactory.getInstance().newTuple(intermediateBag));
    verifyNoRepeatAllFound(result, 10, 0, 100); 
   }

  private void verifyNoRepeatAllFound(DataBag result,
                                      int expectedResultSize,
                                      int left,
                                      int right) throws ExecException
  {
    Assert.assertEquals(expectedResultSize, result.size());
    
    // all must be found, no repeats
    Set<Integer> found = new HashSet<Integer>();
    for (Tuple t : result)
    {
      Integer i = (Integer)t.get(0);
      Assert.assertTrue(i>=left && i<right);
      Assert.assertFalse(String.format("Found duplicate of %d",i), found.contains(i));
      found.add(i);
    }
  }

  @Test
  public void weightedReservoirSampleLimitExecTest() throws IOException
  {
    WeightedReservoirSample sampler = new WeightedReservoirSample("100", "1");
   
    DataBag bag = BagFactory.getInstance().newDefaultBag();
    for (int i=0; i<10; i++)
    {
      Tuple t = TupleFactory.getInstance().newTuple(2);
      t.set(0, i);
      t.set(1, 1); // score is equal for all
      bag.add(t);
    }
   
    Tuple input = TupleFactory.getInstance().newTuple(1);
    input.set(0, bag);
   
    DataBag result = sampler.exec(input);
   
    verifyNoRepeatAllFound(result, 10, 0, 10); 

    Set<Integer> found = new HashSet<Integer>();
    for (Tuple t : result)
    {
      Integer i = (Integer)t.get(0);
      found.add(i);
    }

    for(int i = 0; i < 10; i++)
    {
      Assert.assertTrue(found.contains(i));
    }
  }

  @Test
  public void invalidConstructorArgTest() throws Exception
  {
    try {
         WeightedReservoirSample sampler = new WeightedReservoirSample("1", "-1");
         Assert.fail( "Testcase should fail");
    } catch (Exception ex) {
         Assert.assertTrue(ex.getMessage().indexOf("Invalid negative index of weight field argument for WeightedReserviorSample constructor: -1") >= 0);
    }
  }

  @Test
  public void invalidWeightTest() throws Exception
  {
    PigTest test = createPigTestFromString(weightedSampleTest);

    writeLinesToFile("input",  
                "a\t100","b\t1","c\t0","d\t2");
    try {
         test.runScript();
         List<Tuple> output = this.getLinesForAlias(test, "sampled");
         Assert.fail( "Testcase should fail");
    } catch (Exception ex) {
    }
  }

 /** 
   
 
  define WeightedSample datafu.pig.sampling.WeightedReservoirSample('1','1'); 

  data = LOAD 'input' AS (v1:chararray);
  data_g = group data all;
  sampled = FOREACH data_g GENERATE WeightedSample(data);
  describe sampled; 
   
  STORE sampled INTO 'output'; 
 
  */ 
  @Multiline 
  private String invalidInputTupleSizeTest; 
 
  @Test
  public void invalidInputTupleSizeTest() throws Exception
  {
    PigTest test = createPigTestFromString(invalidInputTupleSizeTest);

    writeLinesToFile("input",  
                "a","b","c","d");
    try {
         test.runScript();
         List<Tuple> output = this.getLinesForAlias(test, "sampled");
         Assert.fail( "Testcase should fail");
    } catch (Exception ex) {
         Assert.assertTrue(ex.getMessage().indexOf("The field schema of the input tuple is null or the tuple size is no more than the weight field index: 1") >= 0);
    }
  }

 /** 
   
 
  define WeightedSample datafu.pig.sampling.WeightedReservoirSample('1','0'); 

  data = LOAD 'input' AS (v1:chararray, v2:INT);
  data_g = group data all;
  sampled = FOREACH data_g GENERATE WeightedSample(data);
  describe sampled; 
   
  STORE sampled INTO 'output'; 
 
  */ 
  @Multiline 
  private String invalidWeightFieldSchemaTest; 
 
  @Test
  public void invalidWeightFieldSchemaTest() throws Exception
  {
    PigTest test = createPigTestFromString(invalidWeightFieldSchemaTest);

    writeLinesToFile("input",  
                "a\t100","b\t1","c\t5","d\t2");
    try {
         test.runScript();
         List<Tuple> output = this.getLinesForAlias(test, "sampled");
         Assert.fail( "Testcase should fail");
    } catch (Exception ex) {
         Assert.assertTrue(ex.getMessage().indexOf("Expect the type of the weight field of the input tuple to be of ([int, long, float, double]), but instead found (chararray), weight field: 0") >= 0);
    }
  }
}
