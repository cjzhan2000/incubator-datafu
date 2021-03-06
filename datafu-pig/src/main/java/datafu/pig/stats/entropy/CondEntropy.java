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

package datafu.pig.stats.entropy;

import org.apache.pig.AccumulatorEvalFunc;

import java.io.IOException;
import java.util.List;

import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.impl.logicalLayer.schema.Schema;



/**
 * Calculate conditional entropy H(Y|X) of random variables X and Y following conditional entropy's 
 * {@link <a href="http://en.wikipedia.org/wiki/Conditional_entropy" target="_blank">wiki definition</a>}, 
 * X is the conditional variable and Y is the variable that conditions on X.
 * <p>
 * Each tuple of the input bag has 2 fields, the 1st field is an object instance of variable X and
 * the 2nd field is an object instance of variable Y. An exception will be thrown if the number of fields is not 2.
 * </p> 
 * <p>
 * This UDF's constructor definition and parameters are the same as that of {@link datafu.pig.stats.entropy.Entropy}
 * </p>
 * <p>
 * Note:
 * <ul>
 *     <li>The input bag to this UDF must be <b>sorted</b> on X and Y, with X in the first sort order.
 *     An exception will be thrown if the input bag is not sorted.
 *     <li>The returned entropy value is of double type.
 * </ul>
 * </p>
 * <p>
 * How to use: 
 * </p>
 * <p>
 * This UDF calculates conditional entropy given raw data tuples of X and Y without the need to pre-compute per tuple occurrence frequency.
 * </p>
 * <p>
 * It could be used in a nested FOREACH after a GROUP BY, in which we sort the inner bag and use the sorted bag as this UDF's input.
 * </p>
 * <p>
 * Example:
 * <pre>
 * {@code
 * --define empirical conditional entropy with Euler's number as the logarithm base
 * define CondEntropy datafu.pig.stats.entropy.CondEntropy();
 *
 * input = LOAD 'input' AS (grp: chararray, valX: double, valY: double);
 *
 * -- calculate conditional entropy H(Y|X) in each group
 * input_group_g = GROUP input BY grp;
 * entropy_group = FOREACH input_group_g {
 *   input_val = input.(valX, valY)
 *   input_ordered = ORDER input_val BY $0, $1;
 *   GENERATE FLATTEN(group) AS group, CondEntropy(input_ordered) AS cond_entropy; 
 * }
 * }
 * </pre>
 * </p>
 * Use case to calculate mutual information:
 * <p>
 * <pre>
 * {@code
 * ------------
 * -- calculate mutual information I(X, Y) using conditional entropy UDF and entropy UDF
 * -- I(X, Y) = H(Y) - H(Y|X)
 * ------------
 * 
 * define CondEntropy datafu.pig.stats.entropy.CondEntropy();
 * define Entropy datafu.pig.stats.entropy.Entropy();
 * 
 * input = LOAD 'input' AS (grp: chararray, valX: double, valY: double);
 * 
 * -- calculate the I(X,Y) in each group
 * input_group_g = GROUP input BY grp;
 * mutual_information = FOREACH input_group_g {
 *      input_val_x_y = input.(valX, valY);
 *      input_val_x_y_ordered = ORDER input_val_x_y BY $0,$1;
 *      input_val_y = input.valY;
 *      input_val_y_ordered = ORDER input_val_y BY $0;
 *      cond_h_x_y = CondEntropy(input_val_x_y_ordered);
 *      h_y = Entropy(input_val_y_ordered);
 *      GENERATE FLATTEN(group), h_y - cond_h_x_y;
 * }
 * }
 * </pre>
 * </p>
 * @see Entropy
 */
public class CondEntropy extends AccumulatorEvalFunc<Double> {
    //last visited tuple of <x,y>
    private Tuple xy;
    
    //number of occurrence of last visited <x,y>
    private long cxy;
    
    //number of occurrence of last visited x
    private long cx;
    
    //comparison result between the present tuple and the last visited tuple
    private int lastCmp;
    
    //entropy estimator for H(x,y)
    private EntropyEstimator combEstimator;
    
    //entropy estimator for H(x)
    private EntropyEstimator condXEstimator;
    
    public CondEntropy() throws ExecException
    {
      this(EntropyEstimator.EMPIRICAL_ESTIMATOR);
    }
    
    public CondEntropy(String type) throws ExecException 
    {
      this(type, EntropyUtil.LOG);
    }

    public CondEntropy(String type, String base) throws ExecException
    {
      try {
          this.combEstimator = EntropyEstimator.createEstimator(type, base);
          this.condXEstimator = EntropyEstimator.createEstimator(type, base);
      } catch (IllegalArgumentException ex) {
          throw new ExecException(String.format(
                  "Fail to initialize StreamingCondEntropy with entropy estimator of type (%s), base: (%s). Exception: (%s)",
                  type, base, ex)); 
      }
      cleanup();
    }
    
    /*
     * Accumulate occurrence frequency of <x,y> and x
     * as we stream through the input bag
     */
    @Override
    public void accumulate(Tuple input) throws IOException
    {
      for (Tuple t : (DataBag) input.get(0)) {

        if (this.xy != null)
        {
            int cmp = t.compareTo(this.xy);
            
            //check if the comparison result is different from previous compare result
            if ((cmp < 0 && this.lastCmp > 0)
                || (cmp > 0 && this.lastCmp < 0)) {
                throw new ExecException("Out of order! previous tuple: " + this.xy + ", present tuple: " + t
                                        + ", comparsion: " + cmp + ", previous comparsion: " + this.lastCmp);
            }
            if (cmp != 0) {
               //different <x,y>
               this.combEstimator.accumulate(this.cxy);
               this.cxy = 0;
               this.lastCmp = cmp;
               if(DataType.compare(this.xy.get(0), t.get(0)) != 0) {
                  //different x
                   this.condXEstimator.accumulate(this.cx);
                   this.cx = 0;
               }
            } 
        }

        //set tuple t as the next tuple for comparison
        this.xy = t;

        //accumulate cx
        this.cx++;
        
        //accumulate cxy
        this.cxy++;
      }
    }
    
    @Override
    public Double getValue()
    {
      //do not miss the last tuple
      try {
          this.combEstimator.accumulate(this.cxy);
          this.condXEstimator.accumulate(this.cx);
      } catch (ExecException ex) {
          throw new RuntimeException("Error while accumulating sample frequency: " + ex);
      }
      
      //Chain rule: H(Y|X) = H(X, Y) - H(X)
      return this.combEstimator.getEntropy() - this.condXEstimator.getEntropy();
    }
    
    @Override
    public void cleanup()
    {
      this.xy = null;
      this.cxy = 0;
      this.cx = 0;
      this.lastCmp = 0;
      this.combEstimator.reset();
      this.condXEstimator.reset();
    }
    
    @Override
    public Schema outputSchema(Schema input)
    {
        try {
            Schema.FieldSchema inputFieldSchema = input.getField(0);

            if (inputFieldSchema.type != DataType.BAG)
            {
              throw new RuntimeException("Expected a BAG as input");
            }
            
            Schema inputBagSchema = inputFieldSchema.schema;
            
            if (inputBagSchema.getField(0).type != DataType.TUPLE)
            {
              throw new RuntimeException(String.format("Expected input bag to contain a TUPLE, but instead found %s",
                                                       DataType.findTypeName(inputBagSchema.getField(0).type)));
            }
            
            Schema tupleSchema = inputBagSchema.getField(0).schema;
            
            if(tupleSchema == null) {
                throw new RuntimeException("The tuple of the input bag has no schema");
            }
            
            List<Schema.FieldSchema> fieldSchemaList = tupleSchema.getFields();
            
            if(fieldSchemaList == null || fieldSchemaList.size() != 2) {
                throw new RuntimeException("The field schema of the input tuple is null or its size is not 2");
            }
            
            return new Schema(new Schema.FieldSchema(getSchemaName(this.getClass()
                                                                   .getName()
                                                                   .toLowerCase(), input),
                                                 DataType.DOUBLE));
          } catch (FrontendException e) {
            throw new RuntimeException(e);
          }
     }
}
